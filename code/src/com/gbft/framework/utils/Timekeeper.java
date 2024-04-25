package com.gbft.framework.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import com.gbft.framework.core.Entity;
import com.gbft.framework.data.MessageData;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.statemachine.Transition;
import com.gbft.framework.utils.Printer.Verbosity;

public class Timekeeper implements Runnable {

    public static final int SEQUENCE_MODE = 0;
    public static final int STATE_MODE = 1;

    private static final int st = 3; // 6 // 5 // 4
    private static final int sq = 7; // 15 // 12 // 9

    private Entity entity;

    private long start;
    private long[] counters = new long[] { 1, 1 };
    private Map<Long, Integer> multipliers;
    private Map<Integer, Long> dueLengths;

    // seqnum -> timestamp
    private Map<Long, Long> stateUpdates;

    // seqnum -> timestamp
    private Map<Long, Long> sequenceUpdates;

    DelayQueue<TimeoutPair> timeoutQueue;

    // seqnum -> timeout-pairs
    private Map<Long, List<TimeoutPair>> overdues;

    public Timekeeper(Entity entity) {
        this.entity = entity;

        long intervalns = Config.integer("benchmark.request-interval-micros") * 1000L;

        if (Config.string("benchmark.timeout").equals("fixed")) {
            intervalns = Config.integer("benchmark.timeout-trigger-interval-ms") * 1000000L;
        }

        stateUpdates = new ConcurrentHashMap<>();
        sequenceUpdates = new ConcurrentHashMap<>();
        multipliers = new ConcurrentHashMap<>();
        overdues = new ConcurrentHashMap<>();
        dueLengths = new ConcurrentHashMap<>(Map.of(STATE_MODE, intervalns, SEQUENCE_MODE, intervalns));

        timeoutQueue = new DelayQueue<>();
        entity.registerThread(new Thread(this));
    }

    @Override
    public void run() {
        start = System.nanoTime();
        while (entity.isRunning()) {
            try {
                var pair = timeoutQueue.take();
                // Printer.print(Verbosity.V, entity.prefix, "timeoutQueue.take(), seqnum: " + pair.seqnum);
                if (!checkTimeout(pair)) {
                    continue;
                }

                var seqnum = pair.seqnum;

                // Printer.print(Verbosity.V, entity.prefix, "add to overdues, seqnum: " + pair.seqnum);
                overdues.computeIfAbsent(seqnum, s -> new ArrayList<>()).add(pair);
                multipliers.put(seqnum, multipliers.get(seqnum) + 1);

                entity.stateUpdateLoop(seqnum);
            } catch (InterruptedException e) {

            }
        }
    }

    public boolean checkTimeout(TimeoutPair pair) {
        if (isExpired(pair)) {
            return false;
        }

        var now = System.nanoTime();

        var mode = pair.mode;
        var seqnum = pair.seqnum;
        var lastUpdate = mode == STATE_MODE ? stateUpdates.get(seqnum) : sequenceUpdates.get(seqnum);
        if (lastUpdate == null) {
            lastUpdate = now;
        }

        if (lastUpdate + pair.dueLength > now) {
            pair.duetime = lastUpdate + pair.dueLength;
            timeoutQueue.offer(pair);
            return false;
        }

        return true;
    }

    public void messageReceived(long seqnum, long viewnum, int state, MessageData message) {
        if (entity.isValidMessage(message)) {
            stateUpdates.put(seqnum, System.nanoTime());
            // sequenceUpdates.put(seqnum, now);
        }
    }

    public void stateUpdated(long seqnum, int nextstate) {
        var now = System.nanoTime();

        counters[STATE_MODE] += 1;
        var duelength = (now - start) / counters[STATE_MODE];
        if (!Config.string("benchmark.timeout").equals("fixed")) {
            if (Math.abs(dueLengths.get(STATE_MODE) - duelength) / (double) dueLengths.get(STATE_MODE) > 0.03) {
                //            Printer.print(Verbosity.V, entity.prefix, "DEBUG STATE DUE LENGTH UPDATED " + duelength);
                System.out.println("STATE DUE LENGTH UPDATED to " + duelength);
                dueLengths.put(STATE_MODE, duelength);
            }
        }

        if (nextstate == StateMachine.EXECUTED) {
            counters[SEQUENCE_MODE] += 1;
            duelength = (now - start) / counters[SEQUENCE_MODE];
            if (!Config.string("benchmark.timeout").equals("fixed")) {
                if (Math.abs(dueLengths.get(SEQUENCE_MODE) - duelength) / (double) dueLengths.get(SEQUENCE_MODE) > 0.03) {
                    //                Printer.print(Verbosity.V, entity.prefix, "DEBUG SEQUENCE DUE LENGTH UPDATED " + duelength);
                    System.out.println("SEQUENCE DUE LENGTH UPDATED to " + duelength);
                    dueLengths.put(SEQUENCE_MODE, duelength);
                }
            }

            stateUpdates.remove(seqnum);
            sequenceUpdates.remove(seqnum);
            overdues.remove(seqnum);
        } else {
            stateUpdates.put(seqnum, now);
            sequenceUpdates.put(seqnum, now);
        }
    }

    public void startTimer(long seqnum, long viewnum, int state, int mode, int multiplier, Transition transition) {
        var now = System.nanoTime();
        var key = new TimeoutKey(viewnum, seqnum, state);
        multipliers.put(seqnum, multiplier);
        var dueLength = dueLengths.get(mode) * multiplier * (mode == STATE_MODE ? st : sq);
        dueLength = Math.max(dueLength, mode == STATE_MODE ? 6000000L : 15000000L);
        var timeoutPair = new TimeoutPair(mode, seqnum, key, transition, dueLength, now + dueLength);
        // Printer.print(Verbosity.V, entity.prefix, "startTimer, " + timeoutPair); // behavior is correct, every seqnum is added into the timeoutQueue
        timeoutQueue.offer(timeoutPair);
    }

    public TimeoutPair getOverdue(long seqnum) {
        if (overdues.containsKey(seqnum)) {
            for (var overdue : overdues.get(seqnum)) {
                if (checkTimeout(overdue)) {
                    return overdue;
                }
            }
        }

        return null;
    }

    public boolean isExpired(TimeoutPair pair) {
        var seqnum = pair.seqnum;
        var key = pair.key;
        var mode = pair.mode;

        if (seqnum <= entity.getLastExecutedSequenceNum() || key.view != entity.getCurrentViewNum()) {
            return true;
        }

        var checkpoint = entity.getCheckpointManager().getCheckpointForSeq(seqnum);
        var currentState = checkpoint.getState(seqnum);

        return (mode == SEQUENCE_MODE && currentState == StateMachine.EXECUTED) ||
                (mode == STATE_MODE && currentState != key.state);
    }

    public static class TimeoutKey {
        public final long view;
        public final long seqnum;
        public final int state;

        public TimeoutKey(long view, long seqnum, int state) {
            this.view = view;
            this.seqnum = seqnum;
            this.state = state;
        }

        @Override
        public int hashCode() {
            return Objects.hash(seqnum, state, view);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            TimeoutKey other = (TimeoutKey) obj;
            return seqnum == other.seqnum && state == other.state && view == other.view;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("TimeoutKey [view=").append(view).append(", seqnum=").append(seqnum).append(", state=").append(state)
                   .append("]");
            return builder.toString();
        }
    }

    public static class TimeoutPair implements Delayed {
        public int mode;
        public long seqnum;
        public TimeoutKey key;
        public Transition transition;
        public long duetime;
        public long dueLength;

        public TimeoutPair(int mode, long seqnum, TimeoutKey key, Transition transition, long dueLength, long duetime) {
            this.mode = mode;
            this.seqnum = seqnum;
            this.key = key;
            this.transition = transition;
            this.dueLength = dueLength;
            this.duetime = duetime;
        }

        @Override
        public int compareTo(Delayed o) {
            // return (int) (this.duetime - ((TimeoutPair) o).duetime);
            if (this.duetime < ((TimeoutPair) o).duetime) {
                return -1;
            } else if (this.duetime > ((TimeoutPair) o).duetime) {
                return 1;
            } else {
                return 0;
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            var remain = this.duetime - System.nanoTime();
            return unit.convert(remain, TimeUnit.NANOSECONDS);
        }

        @Override
        public int hashCode() {
            return Objects.hash(duetime, dueLength, key, mode, seqnum, transition);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            TimeoutPair other = (TimeoutPair) obj;
            return duetime == other.duetime && dueLength == other.dueLength && Objects.equals(key, other.key)
                    && mode == other.mode && seqnum == other.seqnum && Objects.equals(transition, other.transition);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("TimeoutPair [mode=").append(mode).append(", seqnum=").append(seqnum).append(", key=").append(key)
                   .append(", transition=").append(transition).append(", duetime=").append(duetime).append(", dueLength=")
                   .append(dueLength).append("]");
            return builder.toString();
        }
    }
}
