package com.gbft.plugin.message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.gbft.framework.core.Entity;
import com.gbft.framework.data.FetchData;
import com.gbft.framework.data.MessageData;
import com.gbft.framework.plugins.MessagePlugin;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.CheckpointManager;
import com.gbft.framework.utils.Config;
import com.gbft.framework.utils.DataUtils;
import com.gbft.framework.utils.MessageTally;
import com.gbft.framework.utils.MessageTally.QuorumId;
import com.google.protobuf.ByteString;

public class CheckpointMessagePlugin implements MessagePlugin {

    private Entity entity;
    private CheckpointManager checkpointManager;

    private final int quorum;
    private final int CHECKPOINT;
    private int checkpointSize;
    private final int FETCH;
    private MessageTally tally;
    private AtomicBoolean isFetching;

    // digest of stable checkpoint s to be fetched
    private ByteString checkpointDigest;

    public CheckpointMessagePlugin(Entity entity) {
        this.entity = entity;
        checkpointManager = entity.getCheckpointManager();

        quorum = StateMachine.parseQuorum("f + 1");
        CHECKPOINT = StateMachine.messages.indexOf(StateMachine.findMessage("checkpoint"));
        checkpointSize = Config.integer("benchmark.checkpoint-size");
        FETCH = StateMachine.messages.indexOf(StateMachine.findMessage("fetch"));

        tally = new MessageTally();
        isFetching = new AtomicBoolean(false);
    }

    @Override
    public MessageData processIncomingMessage(MessageData message) {
        if (entity.isClient()) {
            var min_checkpoint = checkpointManager.getMinCheckpoint();
            var current_checkpoint = checkpointManager.getCheckpointNum(entity.getLastExecutedSequenceNum());

            if (min_checkpoint < current_checkpoint) {
                checkpointManager.removeCheckpoint(min_checkpoint);
            }
            return message;
        }
        if (message.getMessageType() != CHECKPOINT && message.getMessageType() != FETCH) {
            return message;
        }

        if (message.getMessageType() == CHECKPOINT) {
            var min = checkpointManager.getMinCheckpoint();
            if (message.getSequenceNum() >= min) {
                tally.tally(message);
                processCheckpoints(message.getSequenceNum());
            }
        }

        if (message.getMessageType() == FETCH) {
            var checkpointNum = message.getSequenceNum();
            if (message.getFetch().getIsRequest()) {
                // send service state stored in the corresponding checkpoint
                var target = message.getSource();
                var _message = DataUtils.createMessage(checkpointNum, 0L, FETCH, entity.getId(), List.of(target),
                        List.of(), entity.EMPTY_BLOCK, null, null);

                var fetchDataBuilder = FetchData.newBuilder().setIsRequest(false);
                if (checkpointNum < checkpointManager.getMinCheckpoint()
                        || checkpointManager.getCheckpoint(checkpointNum).getServiceState() == null) {
                    System.out.println(entity.prefix + "No valid local checkpoint for checkpointNum " + checkpointNum);
                } else {
                    // deep copy the service state stored in the checkpoint
                    Map<Integer, Integer> serviceState = new HashMap<>();
                    checkpointManager.getCheckpoint(checkpointNum).getServiceState().getRecords()
                            .forEach((key, value) -> serviceState.put(key, value.get()));
                    fetchDataBuilder = fetchDataBuilder.putAllServiceState(serviceState);
                }
                _message = _message.toBuilder().setFetch(fetchDataBuilder).build();

                entity.sendMessage(_message);

                System.out.println(entity.prefix + "Sending FETCH reply, checkpointNum: " + checkpointNum);
            } else {
                System.out.println(entity.prefix + "Receiving FETCH reply, checkpointNum: " + checkpointNum);

                // update local service state
                var service_state = message.getFetch().getServiceStateMap();
                if (service_state.isEmpty()) {
                    System.out.println(entity.prefix + "Fetch result is empty, checkpointNum: " + checkpointNum);
                } else {
                    // check if digests match
                    if (checkpointManager.getCheckpointDigest(service_state).equals(checkpointDigest)) {
                        var lastExecutedSequenceNum = (checkpointNum + 1) * checkpointSize - 1;
                        entity.setServiceState(service_state, lastExecutedSequenceNum);

                        checkpointManager.setLowWaterMark(checkpointNum);

                        System.out.println(entity.prefix + "Local service state updated, lastExecutedSequenceNum: " + lastExecutedSequenceNum);
                    } else {
                        System.out.println(entity.prefix + "Fetch result does not match the digest, checkpointNum: " + checkpointNum);
                    }
                }

                isFetching.set(false);
            }
        }

        return DataUtils.invalidate(message);
    }

    @Override
    public MessageData processOutgoingMessage(MessageData message) {
        if (entity.isClient() || message.getMessageType() != CHECKPOINT && message.getMessageType() != FETCH) {
            return message;
        }

        if (message.getMessageType() == CHECKPOINT) {
            tally.tally(message);
            processCheckpoints(message.getSequenceNum());
        }

        return message;
    }

    public boolean hasQuorum(long checkpointNum) {
        return tally.hasQuorum(checkpointNum, 0, new QuorumId(CHECKPOINT, quorum));
    }

    private synchronized void processCheckpoints(long checkpointNum) {
        System.out.println(entity.prefix + "processCheckpoints, checkpointNum:  " + checkpointNum);

        // update last stable checkpoint s
        if (tally.hasQuorum(checkpointNum, 0, new QuorumId(CHECKPOINT, quorum))) {
            // update h
            if (checkpointManager.getCheckpoint(checkpointNum).getServiceState() != null) {
                checkpointManager.setLowWaterMark(checkpointNum);
            }

            if (checkpointNum > checkpointManager.getLastStableCheckpoint()) {
                checkpointManager.setLastStableCheckpoint(checkpointNum);
                System.out.println(entity.prefix + "Update last stable checkpoint to  " + checkpointNum);

                // fetch service state from other nodes if s > h + k
                if (checkpointNum > checkpointManager.getLowWaterMark() + checkpointManager.lowHighGap
                        && !isFetching.get()) {
                    var targets = tally.getQuorumNodes(checkpointNum, 0, new QuorumId(CHECKPOINT, quorum));
                    var target = targets.iterator().next();
                    var message = DataUtils.createMessage(checkpointNum, 0L, FETCH, entity.getId(), List.of(target),
                            List.of(), entity.EMPTY_BLOCK, null, null);

                    var fetchDataBuilder = FetchData.newBuilder().setIsRequest(true);
                    message = message.toBuilder().setFetch(fetchDataBuilder).build();

                    entity.sendMessage(message);

                    checkpointDigest = tally.getQuorumDigest(checkpointNum, 0);
                    isFetching.set(true);

                    System.out.println(entity.prefix + "Sending FETCH request, checkpointNum: " + checkpointNum);
                }
            }
        }

        // remove previous checkpoints
        var max = checkpointManager.getMaxCheckpoint();
        var min = checkpointManager.getMinCheckpoint();
        if (max > min + 3) {
            for (var num = min; max > num + 3; num++) {
                // TODO: when to remove a checkpoint?
                if (tally.hasQuorum(num, 0, new QuorumId(CHECKPOINT, quorum))) {
                    checkpointManager.removeCheckpoint(num);
                } else {
                    return;
                }
            }
        }
    }
}
