package com.gbft.framework.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

import com.gbft.framework.core.Entity;
import com.gbft.framework.statemachine.StateMachine;
import com.google.protobuf.ByteString;

public class CheckpointManager {

    private Entity entity;
    private int CHECKPOINT;
    private int checkpointSize;
    private ConcurrentSkipListMap<Long, CheckpointData> checkpoints;
    private long lastStableCheckpoint; // s
    private long lowWaterMark; // h
    public final int lowHighGap; // k

    public CheckpointManager(Entity entity) {
        this.entity = entity;

        CHECKPOINT = StateMachine.messages.indexOf(StateMachine.findMessage("checkpoint"));
        checkpointSize = Config.integer("benchmark.checkpoint-size");
        lowHighGap = Config.integer("benchmark.catch-up-k");
        lastStableCheckpoint = -1;
        lowWaterMark = 0;

        checkpoints = new ConcurrentSkipListMap<>();
        checkpoints.put(0L, new CheckpointData(0, this.entity));
    }

    /**
     * Send Checkpoint Message
     * @param checkpointNum Sequence Number / Checkpoint Size
     */
    public void sendCheckpoint(long checkpointNum) {
        // System.out.println(entity.prefix + "sendCheckpoint, checkpointNum:  " + checkpointNum);

        var digest = getCheckpointDigest(checkpointNum);
        var targets = entity.getRolePlugin().getRoleEntities(0, 0, StateMachine.NORMAL_PHASE, StateMachine.NODE);
        var message = DataUtils.createMessage(checkpointNum, 0L, CHECKPOINT, entity.getId(), targets, List.of(),
                entity.EMPTY_BLOCK, null, digest);
        entity.sendMessage(message);       
    } 

    public ByteString getCheckpointDigest(long checkpointNum) {
        var stream = new ByteArrayOutputStream();
        try {
            var checkpoint = getCheckpoint(checkpointNum);
            for (var entry : checkpoint.serviceState.getRecords().entrySet()) {
                stream.write(entry.getKey().byteValue());
                stream.write(entry.getValue().byteValue());
            }

            stream.flush();
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return DataUtils.getDigest(stream.toByteArray());
    }

    public ByteString getCheckpointDigest(Map<Integer, Integer> state) {
        var stream = new ByteArrayOutputStream();
        Map<Integer, Integer> sorted_state = new TreeMap<>();
        state.forEach((key, value) -> sorted_state.put(key, value));

        try {
            for (var entry : sorted_state.entrySet()) {
                stream.write(entry.getKey().byteValue());
                stream.write(entry.getValue().byteValue());
            }

            stream.flush();
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return DataUtils.getDigest(stream.toByteArray());
    }

    public void setLastStableCheckpoint(long checkpointNum) {
        lastStableCheckpoint = checkpointNum;
    }

    public Long getLastStableCheckpoint() {
        return lastStableCheckpoint;
    }

    public void setLowWaterMark(long h) {
        lowWaterMark = h;
    }

    public long getLowWaterMark() {
        return lowWaterMark;
    }

    public Long getMinCheckpoint() {
        return checkpoints.firstKey();
    }

    public Long getMaxCheckpoint() {
        return checkpoints.lastKey();
    }

    public CheckpointData getCheckpoint(long checkpointNum) {
        return checkpoints.computeIfAbsent(checkpointNum, num -> new CheckpointData(num, this.entity));
    }

    public void removeCheckpoint(long checkpointNum) {
        checkpoints.remove(checkpointNum);
    }

    public long getCheckpointNum(long seqnum) {
        return seqnum / checkpointSize;
    }

    /**
     * Everything tallied in checkpoint
     * @param seqnum sequence number
     * @return checkpoint that is responsible for seqnum
     */
    public CheckpointData getCheckpointForSeq(long seqnum) {
        return getCheckpoint(seqnum / checkpointSize);
    }

    public CheckpointData getPrevCheckpointForSeq(long seqnum) {
        return getCheckpoint(seqnum / checkpointSize - 1);
    }
}
