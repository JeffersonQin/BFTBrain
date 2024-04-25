package com.gbft.framework.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import com.gbft.framework.core.Entity;

public class FeatureManager {

    private Entity entity;
    private int reportInterval;

    public static final int REWARD = -1;

    public static final int FAST_PATH_FREQUENCY = 0;
    public static final int SLOWNESS_OF_PROPOSAL = 1;
    public static final int REQUEST_SIZE = 2;
    public static final int HAS_FAST_PATH = 3;
    public static final int HAS_LEADER_ROTATION = 4;
    public static final int RECEIVED_MESSAGE_PER_SLOT = 5;

    public static final int FAST = 1;
    public static final int SLOW = 2;

    public final Map<String, Integer> hasFastPath = Map.of("pbft", 0, "zyzzyva", 1, "cheapbft", 0, "sbft", 1,
            "hotstuff", 0, "prime", 0);
    public final Map<String, Integer> hasLeaderRotation = Map.of("pbft", 0, "zyzzyva", 0, "cheapbft", 0, "sbft", 0,
            "hotstuff", 1, "prime", 1);

    // episode -> extractor
    private Map<Integer, Extractor> slots;

    public FeatureManager(Entity entity) {
        this.entity = entity;
        slots = new ConcurrentHashMap<>();
        reportInterval = Config.integer("general.report-sequence");
    }

    public void received(int episode, long seqnum) {
        var extractor = getExtractor(episode);
        if (seqnum == episode * entity.EPISODE_SIZE || seqnum == episode * entity.EPISODE_SIZE + reportInterval) {
            extractor.timestamps.computeIfAbsent(seqnum, s -> System.nanoTime());
        }
    }

    public void add(int episode, int featureType, long value) {
        var extractor = getExtractor(episode);
        extractor.totals.computeIfAbsent(featureType, m -> new LongAdder()).add(value);
        extractor.counters.computeIfAbsent(featureType, m -> new LongAdder()).increment();
    }

    public void count(int episode, int featureType) {
        var extractor = getExtractor(episode);
        extractor.counters.computeIfAbsent(featureType, m -> new LongAdder()).increment();
    }

    public void countPath(int episode, int featureType, int path) {
        var extractor = getExtractor(episode);
        extractor.frequencyCounters.computeIfAbsent(featureType, f -> new ConcurrentHashMap<>())
                .computeIfAbsent(path, p -> new LongAdder()).increment();
    }

    public Extractor getExtractor(int episode) {
        return slots.computeIfAbsent(episode, e -> new Extractor(e));
    }

    public class Extractor {
        private int episode;
        public ConcurrentHashMap<Integer, LongAdder> counters;
        public ConcurrentHashMap<Integer, LongAdder> totals;
        // used to track frequency
        public ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, LongAdder>> frequencyCounters;
        // used to track received timestamp (seqnum -> first received leader proposal)
        public ConcurrentHashMap<Long, Long> timestamps;

        Extractor(int episode) {
            this.episode = episode;
            counters = new ConcurrentHashMap<>();
            totals = new ConcurrentHashMap<>();
            frequencyCounters = new ConcurrentHashMap<>();
            timestamps = new ConcurrentHashMap<>();
        }

        public long total(int featureType) {
            return totals.getOrDefault(featureType, new LongAdder()).longValue();
        }

        public int count(int featureType) {
            return counters.getOrDefault(featureType, new LongAdder()).intValue();
        }

        public long average(int featureType) {
            if (count(featureType) == 0) {
                return 0;
            }

            return total(featureType) / count(featureType);
        }

        public float hottestRatio(int featureType) {
            List<Long> values = new ArrayList<>();
            frequencyCounters.get(featureType).values().forEach(v -> values.add(v.longValue()));

            Collections.sort(values, Collections.reverseOrder());
            long highestValue = values.get(0);

            if (count(featureType) == 0) {
                return 0;
            } else {
                return (float) highestValue / count(featureType);
            }
        }

        public float getRatio(int featureType, int path) {
            if (count(featureType) == 0) {
                return 0;
            } else {
                return (float) frequencyCounters.getOrDefault(featureType, new ConcurrentHashMap<>())
                        .getOrDefault(path, new LongAdder()).longValue() / count(featureType);
            }
        }

        public float getProposalSlowness() {
            // set to zero if is Prime
            if (Config.getCurrentProtocol().equals("prime")) {
                return 0;
            }
            var v1 = timestamps.get((long) episode * entity.EPISODE_SIZE + reportInterval);
            var v2 = timestamps.get((long) episode * entity.EPISODE_SIZE);
            var flag = false;
            if (v1 == null) {
                flag = true;
                System.out.println("!!!!!EPISODE+REPORT_INTERVAL IS NULL!!!!!");
            }
            if (v2 == null) {
                flag = true;
                System.out.println("!!!!!EPISODE_RAW IS NULL!!!!!");
            }
            if (flag) {
                System.out.println("QUITING");
                System.exit(1);
            }
            var duration = (v1 - v2) / 1e6;
            // var duration = (timestamps.get((long) episode * entity.EPISODE_SIZE + reportInterval) - timestamps
            //         .get((long) episode * entity.EPISODE_SIZE)) / 1e6;
            return (float) duration / reportInterval;
        }
    }
}
