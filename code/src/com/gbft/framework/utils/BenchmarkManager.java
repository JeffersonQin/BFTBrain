package com.gbft.framework.utils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import com.gbft.framework.core.Entity;
import com.gbft.framework.data.RequestData;

public class BenchmarkManager {

    private Entity entity;
    private CheckpointManager checkpointManager;

    private long start;
    private Map<Integer, Benchmark> slots;
    private Map<Integer, Benchmark> episodes;
    private Map<Integer, ConcurrentHashMap<Long, Long>> starts;

    public final long benchmarkInterval;

    public static final int REQUEST_QUEUE = 0;
    public static final int REQUEST_EXECUTE = 1;
    public static final int MESSAGE_PROCESS = 2;
    public static final int BLOCK_EXECUTE = 3;

    public static final int TIMEOUT = 4;
    public static final int VIEW_CHANGE = 5;

    public static final int CONNECTION_SEND = 6;
    public static final int SENDER_THREAD_WRITE = 7;
    public static final int RECEIVER_THREAD_INQUEUE_CLIENT = 8;
    public static final int RECEIVER_THREAD_INQUEUE_REPLICA = 9;
    public static final int COORDINATOR_UNIT_SEND = 10;
    public static final int CONNECTION_BEGIN_SEND = 11;
    public static final int TRANSITION = 12;
    public static final int CONDITION_MET = 13;
    public static final int STATE_UPDATE = 14;
    public static final int BEGIN_WHILE_LOOP = 15;
    public static final int CREATE_REQUEST_BLOCK = 16;
    public static final int IF1 = 17;
    public static final int IF2 = 18;
    public static final int IF3 = 19;

    public BenchmarkManager(Entity entity) {
        this.entity = entity;
        if (entity != null) {
            checkpointManager = entity.getCheckpointManager();
        }

        slots = new ConcurrentHashMap<>();
        episodes = new ConcurrentHashMap<>();
        starts = new ConcurrentHashMap<>();

        benchmarkInterval = Config.integer("benchmark.benchmark-interval-ms") * 1000000L;
    }

    public void start() {
        start = System.nanoTime();
    }

    public void recordClientReadOnly(List<RequestData> requests, long timestamp) {
        for (var request : requests) {
            var duration = timestamp - DataUtils.toLong(request.getTimestamp());
            add(REQUEST_EXECUTE, duration, timestamp);
        }
    }

    public void sequenceStarted(long seqnum, long timestamp) {
        start(BLOCK_EXECUTE, seqnum, timestamp);
        start(TIMEOUT, seqnum, 0);

        var requests = checkpointManager.getCheckpointForSeq(seqnum).getRequestBlock(seqnum);
        // ERROR: requests can be null, NPE
        for (var request : requests) {
            var duration = timestamp - DataUtils.toLong(request.getTimestamp());
            add(REQUEST_QUEUE, duration, timestamp);
        }
    }

    public void sequenceExecuted(long seqnum, long timestamp) {
        var checkpoint = checkpointManager.getCheckpointForSeq(seqnum);
        var requests = checkpoint.getRequestBlock(seqnum);

        var start = starts.get(BLOCK_EXECUTE).remove(seqnum);
        var duration = timestamp - start;
        add(BLOCK_EXECUTE, duration, timestamp);

        var timeout = starts.get(TIMEOUT).remove(seqnum);
        if (timeout != null && timeout == 1) {
            add(TIMEOUT, 0, timestamp);
        }

        for (var request : requests) {
            duration = timestamp - DataUtils.toLong(request.getTimestamp());
            add(REQUEST_EXECUTE, duration, timestamp);
            addByEpisode(REQUEST_EXECUTE, duration, entity.currentEpisodeNum.get());
        }
    }

    public void messageProcessed(long start, long timestamp) {
        var duration = timestamp - start;
        add(MESSAGE_PROCESS, duration, timestamp);
    }

    public void requestExecuted(long reqnum, long timestamp) {
        var seqnum = entity.getRequestSequence(reqnum);
        var request = checkpointManager.getCheckpointForSeq(seqnum).getRequest(reqnum);
        var duration = timestamp - DataUtils.toLong(request.getTimestamp());
        add(REQUEST_EXECUTE, duration, timestamp);
    }

    public void count(int measure, long timestamp) {
        getBenchmark(timestamp).counters.computeIfAbsent(measure, m -> new LongAdder()).increment();
    }

    public void add(int measure, long duration, long timestamp) {
        var benchmark = getBenchmark(timestamp);
        addByBenchmark(measure, duration, benchmark);
    }

    public void addByBenchmark(int measure, long duration, Benchmark benchmark) {
        benchmark.totals.computeIfAbsent(measure, m -> new LongAdder()).add(duration);
        benchmark.counters.computeIfAbsent(measure, m -> new LongAdder()).increment();

        var max = benchmark.maxvalues.getOrDefault(measure, new AtomicLong(0L));
        if (max.get() < duration) {
            benchmark.maxvalues.computeIfAbsent(measure, m -> new AtomicLong(0L)).compareAndSet(max.get(), duration);
        }
    }

    public void addByEpisode(int measure, long duration, int episode) {
        var benchmark = getBenchmarkByEpisode(episode);
        addByBenchmark(measure, duration, benchmark);
    }

    public void start(int measure, long index, long timestamp) {
        starts.computeIfAbsent(measure, m -> new ConcurrentHashMap<>()).put(index, timestamp);
    }

    private Benchmark getBenchmark(long timestamp) {
        var slot = (timestamp - start) / benchmarkInterval;
        return slots.computeIfAbsent((int) slot, s -> new Benchmark());
    }

    public Benchmark getBenchmarkById(int num) {
        return slots.getOrDefault(num, new Benchmark());
    }

    public Benchmark getBenchmarkByEpisode(int episode) {
        return episodes.computeIfAbsent(episode, e -> new Benchmark());
    }

    public class Benchmark {
        public ConcurrentHashMap<Integer, LongAdder> counters;
        public ConcurrentHashMap<Integer, AtomicLong> maxvalues;
        public ConcurrentHashMap<Integer, LongAdder> totals;

        Benchmark() {
            counters = new ConcurrentHashMap<>();
            maxvalues = new ConcurrentHashMap<>();
            totals = new ConcurrentHashMap<>();
        }

        public long max(int measure) {
            return maxvalues.getOrDefault(measure, new AtomicLong(0L)).get();
        }

        public long total(int measure) {
            return totals.getOrDefault(measure, new LongAdder()).longValue();
        }

        public int count(int measure) {
            return counters.getOrDefault(measure, new LongAdder()).intValue();
        }

        public long average(int measure) {
            if (count(measure) == 0) {
                return 0;
            }

            return total(measure) / count(measure);
        }
    }

}
