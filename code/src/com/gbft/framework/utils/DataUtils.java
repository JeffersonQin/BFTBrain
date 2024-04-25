package com.gbft.framework.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.IntStream;

import com.gbft.framework.data.ConfigData;
import com.gbft.framework.data.Event;
import com.gbft.framework.data.Event.EventType;
import com.gbft.framework.data.MessageBlock;
import com.gbft.framework.data.MessageData;
import com.gbft.framework.data.PluginData;
import com.gbft.framework.data.ReportData;
import com.gbft.framework.data.RequestData;
import com.gbft.framework.data.RequestData.Operation;
import com.gbft.framework.data.UnitData;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

public class DataUtils {

    // Message Flag
    public static final int INVALID = 1;

    private static Random random = new Random();

    public static MessageData invalidate(MessageData message) {
        return MessageData.newBuilder(message).addFlags(INVALID).build();
    }

    public static UnitData createUnitData(int unit, int nodeCount, int clientCount) {
        return UnitData.newBuilder().setUnit(unit).setNodeCount(nodeCount).setClientCount(clientCount).build();
    }

    public static ConfigData createConfigData(Map<String, String> configContent, String defaultProtocol, List<UnitData> unitDataList) {
        return ConfigData.newBuilder().putAllData(configContent).setDefaultProtocol(defaultProtocol).addAllUnits(unitDataList).build();
    }

    public static PluginData createPluginData(String name, int messageType, ByteString data, int source,
            List<Integer> targets) {
        var builder = PluginData.newBuilder();
        builder.setPluginName(name)
               .setMessageType(messageType)
               .setData(data)
               .setSource(source)
               .addAllTargets(targets);

        return builder.build();
    }

    public static Event createEvent(EventType eventType) {
        return Event.newBuilder().setEventType(eventType).build();
    }

    public static Event createEvent(EventType eventType, int target) {
        return Event.newBuilder().setEventType(eventType).setTarget(target).build();
    }

    public static Event createEvent(UnitData unitData) {
        return Event.newBuilder().setEventType(EventType.INIT).setUnitData(unitData).build();
    }

    public static Event createEvent(ConfigData configData) {
        return Event.newBuilder().setEventType(EventType.CONFIG).setConfigData(configData).build();
    }

    public static Event createEvent(PluginData pluginData) {
        return Event.newBuilder().setEventType(EventType.PLUGIN_INIT).setPluginData(pluginData).build();
    }

    public static Event createEvent(ReportData reportData) {
        return Event.newBuilder().setEventType(EventType.BENCHMARK_REPORT).setReportData(reportData).build();
    }

    public static Event createEvent(List<MessageData> messages) {
        var messageBlock = MessageBlock.newBuilder().addAllMessageData(messages).build();
        return Event.newBuilder().setEventType(EventType.MESSAGE).setMessageBlock(messageBlock).build();
    }

    public static MessageData createMessage(Long seqnum, long viewNum, int messageType, int source,
            List<Integer> targets, List<Long> reqnums, List<RequestData> requests, Map<Long, Integer> replies,
            ByteString digest) {

        var builder = MessageData.newBuilder();
        builder.setViewNum(viewNum)
               .setMessageType(messageType)
               .setSource(source)
               .addAllTargets(targets);

        if (seqnum != null) {
            builder.setSequenceNum(seqnum);
        }

        if (reqnums != null) {
            builder.addAllRequestNums(reqnums);
        }

        if (requests != null) {
            builder.addAllRequests(requests);
        }

        if (replies != null) {
            builder.putAllReplyData(replies);
        }

        if (digest != null) {
            builder.setDigest(digest);
        }

        builder.setTimestamp(Timestamps.fromNanos(System.nanoTime()));

        return builder.build();
    }

    private static final int WORKLOAD_00 = 0;
    private static final int WORKLOAD_04 = 1;
    private static final int WORKLOAD_40 = 2;
    private static final int WORKLOAD_44 = 3;

    public static RequestData createRequest(long reqnum, int record, Operation operation, int value, int clientId) {
        var probabilities = Config.doubleList("workload.distribution");

        var r = random.nextDouble();
        var i = 0;
        for (; i < probabilities.size(); i ++) {
            r -= probabilities.get(i);
            if (r <= 0) break;
        }

        int replySize = 0, requestSize = 0;

        switch (i) {
            case WORKLOAD_00:
                requestSize = 0;
                replySize = 0;
                break;
            case WORKLOAD_04:
                requestSize = 0;
                replySize = AdvanceConfig.integer("workload.payload.reply-size");
                break;
            case WORKLOAD_40:
                requestSize = AdvanceConfig.integer("workload.payload.request-size");
                replySize = 0;
                break;
            case WORKLOAD_44:
                requestSize = AdvanceConfig.integer("workload.payload.request-size");
                replySize = AdvanceConfig.integer("workload.payload.reply-size");
                break;
            default:
                System.err.println("wrong workload number");
                break;
        }

        var builder = RequestData.newBuilder();

        try {
            builder.setRequestNum(reqnum)
                   .setClient(clientId)
                   .setRecord(record)
                   .setOperation(operation)
                   .setValue(value)
                   .setReplySize(replySize)
                   .setRequestDummy(ByteString.readFrom(new RandomDataStream(requestSize)))
                   .setComputeFactor(AdvanceConfig.integer("workload.compute-factor"))
                   .setTimestamp(Timestamps.fromNanos(System.nanoTime()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return builder.build();
    }

    public static ByteString getDigest(List<RequestData> requestBlock) {
        var stream = new ByteArrayOutputStream();
        try {
            for (var request : requestBlock) {
                stream.write(request.toBuilder()
                                .clearRequestDummy()        // clear dummy part
                                .build().toByteArray());
            }
            stream.flush();
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return DataUtils.getDigest(stream.toByteArray());
    }

    public static ByteString getDigest(byte[] data) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(data);
            return ByteString.copyFrom(bytes);
        } catch (NoSuchAlgorithmException e1) {
            e1.printStackTrace();
        }

        return null;
    }

    public static <R, A, B> R nestedGet(Map<A, Map<B, R>> map, A index1, B index2) {
        var submap = map.get(index1);
        return submap == null ? null : submap.get(index2);
    }

    public static <V> ConcurrentHashMap<Integer, V> concurrentMapWithDefaults(int count,
            Function<Integer, V> defaultGenerator) {
        var map = new ConcurrentHashMap<Integer, V>();
        IntStream.range(0, count).forEach(num -> map.put(num, defaultGenerator.apply(num)));
        return map;
    }

    public static long toLong(Timestamp timestamp) {
        return timestamp.getSeconds() * 1000000000L + timestamp.getNanos();
    }
}
