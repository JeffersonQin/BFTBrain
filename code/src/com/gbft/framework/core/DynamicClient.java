package com.gbft.framework.core;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

import com.gbft.framework.coordination.CoordinatorUnit;
import com.gbft.framework.data.RequestData;

public class DynamicClient extends Client {

    public Map<Long, Long> intervals;
    public Long startTime;
    public Long expected;
    private final int maxactive;

    ConcurrentLinkedQueue<Long> timings;
    private LongAdder total;
    private static int window = 200;

    public DynamicClient(int id, CoordinatorUnit coordinator) {
        super(id, coordinator);
        intervals = new ConcurrentHashMap<>();
        maxactive = pipelinePlugin.getMaxActiveSequences();

        timings = new ConcurrentLinkedQueue<>();
        IntStream.range(0, window).forEach(i -> timings.add(intervalns));

        total = new LongAdder();
        total.add(intervalns * window);
    }

    @Override
    public void execute(long seqnum) {
        super.execute(seqnum);

        var checkpoint = checkpointManager.getCheckpointForSeq(seqnum);

        var tally = checkpoint.getMessageTally();
        var viewnum = tally.getMaxQuorum(seqnum);
        var replies = tally.getQuorumReplies(seqnum, viewnum);

        if (viewnum > currentViewNum) {
            currentViewNum = viewnum;
        }

        if (replies != null) {
            updateInterval(replies.keySet(), seqnum);
        }
    }

    public void updateInterval(Set<Long> reqnums, long seqnum) {
        var now = System.nanoTime();
        var diff = now - expected;
        var next = (diff + intervalns) / (blockSize + 1); // 2; //(diff + blockSize * intervalns) / (blockSize * 2);

        var out = timings.remove();
        timings.add(next);
        total.add(next - out);
        expected = now + blockSize * intervals.get(seqnum + 1);
    }

    @Override
    protected RequestGenerator createRequestGenerator() {
        return new DynamicRequestGenerator();
    }

    protected class DynamicRequestGenerator extends RequestGenerator {
        @Override
        public void init() {
            threads.add(new Thread(new DynamicRequestGeneratorRunner()));
        }

        protected class DynamicRequestGeneratorRunner implements Runnable {
            @Override
            public void run() {

                startTime = System.nanoTime();
                expected = startTime + blockSize * maxactive * intervalns;

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

        @Override
        protected void sendRequest(RequestData request) {
            super.sendRequest(request);
            var seqnum = nextRequestNum / blockSize;
            if (!intervals.containsKey(seqnum)) {
                intervals.put(seqnum, total.longValue() / window);
            }
            intervalns = intervals.get(seqnum);
        }
    }
}
