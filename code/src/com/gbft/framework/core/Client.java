package com.gbft.framework.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import com.gbft.framework.coordination.CoordinatorUnit;
import com.gbft.framework.data.RequestData;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.statemachine.Transition.UpdateMode;
import com.gbft.framework.utils.AdvanceConfig;
import com.gbft.framework.utils.BenchmarkManager;
import com.gbft.framework.utils.Config;
import com.gbft.framework.utils.MessageTally.QuorumId;
import com.gbft.framework.utils.Printer;
import com.gbft.framework.utils.Printer.Verbosity;

public class Client extends Entity {

    protected long nextRequestNum;
    protected long intervalns;
    protected final int requestTargetRole;

    protected ClientDataset dataset;

    private RequestGenerator requestGenerator;

    public Client(int id, CoordinatorUnit coordinator) {
        super(id, coordinator);

        intervalns = Config.integer("benchmark.request-interval-micros") * 1000L;
        var targetConfig = Config.string("protocol.general.request-target");
        requestTargetRole = StateMachine.roles.indexOf(targetConfig);

        dataset = new ClientDataset(id);
        nextRequestNum = 0L;

        requestGenerator = createRequestGenerator();
        requestGenerator.init();
    }

    protected RequestGenerator createRequestGenerator() {
        if (Config.bool("benchmark.closed-loop.enable")) {
            return new ClosedLoopRequestGenerator();
        } else {
            return new RequestGenerator();
        }
    }

    @Override
    protected boolean checkMessageTally(long seqnum, QuorumId quorumId, UpdateMode updateMode) {
        var checkpoint = checkpointManager.getCheckpointForSeq(seqnum);

        var tally = checkpoint.getMessageTally();
        var viewnum = tally.getMaxQuorum(seqnum, quorumId);
        if (viewnum != null && viewnum >= currentViewNum) {
            var block = tally.getQuorumBlock(seqnum, viewnum);
            if (block != null) {
                registerBlock(seqnum, block);
            }
            return true;
        }

        return false;
    }

    @Override
    protected void execute(long seqnum) {
        var checkpoint = checkpointManager.getCheckpointForSeq(seqnum);

        var tally = checkpoint.getMessageTally();
        var viewnum = tally.getMaxQuorum(seqnum);
        var replies = tally.getQuorumReplies(seqnum, viewnum);
        currentViewNum = viewnum;

        if (replies != null) {
            var now = System.nanoTime();
            for (var entry : replies.entrySet()) {
                var reqnum = entry.getKey();
                var request = checkpoint.getRequest(reqnum);
                dataset.update(request, entry.getValue());

                // benchmarkManager.requestExecuted(reqnum, now);
            }

            requestGenerator.execute();

        }
    }

    @Override
    public Map<String, String> reportBenchmark() {
        var benchmark = benchmarkManager.getBenchmarkById(reportnum);

        var report = new HashMap<String, String>();
        var executeMax = benchmark.max(BenchmarkManager.REQUEST_EXECUTE);
        var executeAvg = benchmark.average(BenchmarkManager.REQUEST_EXECUTE);
        var executeCount = benchmark.count(BenchmarkManager.REQUEST_EXECUTE);
        report.put("request-execute",
                "avg: " + Printer.timeFormat(executeAvg, true) + ", max: " + Printer.timeFormat(executeMax, true) + ", count: "
                        + executeCount);
        report.put("request-interval", Printer.timeFormat(intervalns, true));

        var interval = Config.integer("benchmark.benchmark-interval-ms");
        var throughput = executeCount / (interval / 1000.0);
        report.put("throughput", String.format("%.2freq/s", throughput));
        report.put("last-executed-sequence", "num: " + lastExecutedSequenceNum);
        report.put("current-episode", "value: " + currentEpisodeNum.get());
        report.put("current-protocol", "value: " + checkpointManager.getCheckpoint(currentEpisodeNum.get()).getProtocol());

        var blockCount = benchmark.count(BenchmarkManager.BLOCK_EXECUTE);
        var timeoutCount = benchmark.count(BenchmarkManager.TIMEOUT);
        report.put("slow-path", String.format("ratio: %.2f",  (double) timeoutCount / (double) blockCount));

        reportnum += 1;
        return report;
    }

    @Override
    public boolean isClient() {
        return true;
    }

    public class RequestGenerator {

        public void init() {
            threads.add(new Thread(new RequestGeneratorRunner()));
        }

        protected class RequestGeneratorRunner implements Runnable {
            @Override
            public void run() {

                while (running) {
                    var next = System.nanoTime() + intervalns;

                    var request = dataset.createRequest(nextRequestNum);
                    nextRequestNum += 1;

                    sendRequest(request);

                    while (System.nanoTime() < next) {
                        LockSupport.parkNanos(intervalns / 3);
                    }
                }
            }
        }

        protected void sendRequest(RequestData request) {
            var reqnum = request.getRequestNum();
            var seqnum = reqnum / blockSize;
            var view = currentViewNum;

            // wait to know the leader mode if necessary
            var episode = getEpisodeNum(seqnum);
            rolePlugin.roleReadLock.lock();
            try {
                if (rolePlugin.episodeLeaderMode.get(episode) == null) {
                    rolePlugin.roleReadLock.unlock();
                    rolePlugin.roleWriteLock.lock();
                    try {
                        while (rolePlugin.episodeLeaderMode.get(episode) == null) {
                            rolePlugin.roleCondition.await();
                        }
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } finally {
                        rolePlugin.roleWriteLock.unlock();
                        rolePlugin.roleReadLock.lock();
                    }
                }
            } finally {
                rolePlugin.roleReadLock.unlock();
            }

            var targets = rolePlugin.getRoleEntities(seqnum, view, StateMachine.NORMAL_PHASE, requestTargetRole);

            if (request.getOperationValue() == RequestData.Operation.READ_ONLY_VALUE) {
                targets = rolePlugin.getRoleEntities(seqnum, view, StateMachine.NORMAL_PHASE, StateMachine.NODE);
            }

            var message = createMessage(null, view, List.of(request), StateMachine.REQUEST, id, targets);
            sendMessage(message);

            if (Printer.verbosity >= Verbosity.VVV) {
                Printer.print(Verbosity.VVV, prefix, "Request created: ", request);
            }
        }

        protected void execute() {}
    }

    public class ClosedLoopRequestGenerator extends RequestGenerator {
        protected final Semaphore semaphore = new Semaphore(Config.integer("benchmark.closed-loop.num-client"));
        protected final int block_size = Config.integer("benchmark.block-size");

        protected AtomicLong nextRequestNum = new AtomicLong(0l);

        protected long reqnumcnt = 0l;

        @Override
        public void init() {
            for (int i = 0; i < Config.integer("benchmark.closed-loop.num-client"); i ++) {
                threads.add(new Thread(new ClosedLoopRequestGeneratorRunner()));
            }
        }
        
        protected class ClosedLoopRequestGeneratorRunner implements Runnable {
            @Override
            public void run() {
                while (running) {
                    try {
                        semaphore.acquire();

                        // sleep for `delay` ms
                        var delay = AdvanceConfig.integer("benchmark.closed-loop.delay-ms");
                        Thread.sleep(delay);

                        var read_only_buf = 0;

                        for (int i = 0; i < block_size + read_only_buf; i ++) {
                            var reqnum = nextRequestNum.getAndIncrement();
                            var request = dataset.createRequest(reqnum);

                            if (request.getOperationValue() == RequestData.Operation.READ_ONLY_VALUE) {
                                read_only_buf ++;
                            }

                            // System.out.println("client " + id + " record " + (++ reqnumcnt));
                            // System.out.println("client " + id + " send request " + reqnum);

                            sendRequest(request);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }


        @Override
        protected void execute() {
            // System.out.println("client " + id + " execute");
            semaphore.release();
        }
    }

}
