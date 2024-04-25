package com.gbft.framework.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.gbft.framework.coordination.CoordinatorUnit;
import com.gbft.framework.data.LearningData;
import com.gbft.framework.data.MessageData;
import com.gbft.framework.fault.PollutionFault;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.AdvanceConfig;
import com.gbft.framework.utils.DataUtils;
import com.gbft.framework.utils.FeatureManager;
import com.gbft.plugin.message.CheckpointMessagePlugin;
import com.gbft.plugin.message.LearningMessagePlugin;

public class Node extends Entity {

    public Node(int id, CoordinatorUnit coordinator) {
        super(id, coordinator);
    }

    @Override
    protected void execute(long seqnum) {
        var checkpoint = checkpointManager.getCheckpointForSeq(seqnum);
        var requestBlock = checkpoint.getRequestBlock(seqnum);

        if (checkpoint.getReplies(seqnum) == null) {
            var replies = new HashMap<Long, Integer>();
            for (var request : requestBlock) {
                replies.put(request.getRequestNum(), dataset.execute(request));
            }
            checkpoint.addReplies(seqnum, replies);
        }

        // checkpoint
        if ((seqnum + 1) % checkpointSize == 0) {
            // copy the current state to checkpoint, new requests can commit but not execute.
            checkpoint.setServiceState(dataset);

            // update h
            for (var i = messagePlugins.size() - 1; i >= 0; i--) {
                var plugin = messagePlugins.get(i);
                if (plugin instanceof CheckpointMessagePlugin) {
                    CheckpointMessagePlugin checkpointPlugin = (CheckpointMessagePlugin) plugin;
                    if (checkpointPlugin.hasQuorum(seqnum / checkpointSize)) {
                        checkpointManager.setLowWaterMark(seqnum / checkpointSize);
                    }
                }
            }

            // multicast CHECKPOINT message to all other nodes
            new Thread(() -> checkpointManager.sendCheckpoint(seqnum / checkpointSize)).start();
        }

        // report local features and reward
        if (learning) {
            if (seqnum <= reportSequence) {
                // request size 
                var request_size = AdvanceConfig.integer("workload.payload.request-size");
                for (var request : requestBlock) {
                    featureManager.add(currentEpisodeNum.get(), FeatureManager.REQUEST_SIZE, request_size);
                    // featureManager.add(currentEpisodeNum.get(), FeatureManager.REQUEST_SIZE, request.getRequestDummy().size());
                }
                // fast path frequency
                featureManager.count(currentEpisodeNum.get(), FeatureManager.FAST_PATH_FREQUENCY);
                // slow proposal is tracked upon first receiving that message
            }

            if (seqnum == reportSequence) {
                // if in dark there will be no report by default
                var targets = getRolePlugin().getRoleEntities(seqnum, 0, StateMachine.NORMAL_PHASE, StateMachine.NODE);
                // seqnum represents episode number
                var message = DataUtils.createMessage((long)currentEpisodeNum.get(), 0L, REPORT, getId(), targets, List.of(), EMPTY_BLOCK,
                        null, EMPTY_DIGEST);
                var extractor = featureManager.getExtractor(currentEpisodeNum.get());

                Map<Integer, Float> report = new HashMap<>();
                if (currentEpisodeNum.get() != 0) {
                    var prev_checkpoint = checkpointManager.getPrevCheckpointForSeq(seqnum);
                    if (pollutionFault.getType(this.id) == PollutionFault.POLLUTION_SBFT && prev_checkpoint.getProtocol().equals("sbft")) {
                        report.put(FeatureManager.REWARD, prev_checkpoint.throughput * 2.5f);
                    } else if (pollutionFault.getType(this.id) == PollutionFault.POLLUTION_ALL) {
                        report.put(FeatureManager.REWARD, PollutionFault.randomFeatureGenerator(50000));
                    } else {
                        report.put(FeatureManager.REWARD, prev_checkpoint.throughput);
                    }
                }

                if (pollutionFault.getType(this.id) == PollutionFault.POLLUTION_ALL) {
                    report.put(FeatureManager.REQUEST_SIZE, PollutionFault.randomFeatureGenerator(500000f));
                    report.put(FeatureManager.FAST_PATH_FREQUENCY, PollutionFault.randomFeatureGenerator(1f));
                    report.put(FeatureManager.SLOWNESS_OF_PROPOSAL, PollutionFault.randomFeatureGenerator(100f));
                    report.put(FeatureManager.RECEIVED_MESSAGE_PER_SLOT, (float) Math.round(PollutionFault.randomFeatureGenerator(100f)));
                    report.put(FeatureManager.HAS_FAST_PATH, PollutionFault.randomOnehot());
                    report.put(FeatureManager.HAS_LEADER_ROTATION, PollutionFault.randomOnehot());
                } else {
                    // request
                    report.put(FeatureManager.REQUEST_SIZE, (float) extractor.average(FeatureManager.REQUEST_SIZE));
                    // fast path frequency
                    if (featureManager.hasFastPath.get(checkpoint.getProtocol()) == 0) {
                        report.put(FeatureManager.FAST_PATH_FREQUENCY, (float) 0.0);
                    } else {
                        report.put(FeatureManager.FAST_PATH_FREQUENCY, 
                                1 - (float) extractor.getRatio(FeatureManager.FAST_PATH_FREQUENCY, FeatureManager.SLOW));
                    }
                    // slow proposal
                    report.put(FeatureManager.SLOWNESS_OF_PROPOSAL, extractor.getProposalSlowness());
                    // number of received messages per slot
                    report.put(FeatureManager.RECEIVED_MESSAGE_PER_SLOT, (float) extractor.count(FeatureManager.RECEIVED_MESSAGE_PER_SLOT));
                    // protocol encodings
                    report.put(FeatureManager.HAS_FAST_PATH, (float) featureManager.hasFastPath.get(checkpoint.getProtocol()));
                    report.put(FeatureManager.HAS_LEADER_ROTATION, (float) featureManager.hasLeaderRotation.get(checkpoint.getProtocol()));
                }

                var learningDataBuilder = LearningData.newBuilder().putAllReport(report);
                message = message.toBuilder().setReport(learningDataBuilder).build();

                sendMessage(message);
                if (message.getTargetsList().contains(id)) {
                    // deliver to myself
                    for (var i = 0; i < messagePlugins.size(); i++) {
                        var plugin = messagePlugins.get(i);
                        if (plugin instanceof LearningMessagePlugin) {
                            final MessageData _message = message;
                            // new Thread( () -> plugin.processIncomingMessage(_message)).start();
                            plugin.processIncomingMessage(_message);
                            break;
                        }
                    }

                }
                System.out.println("sending report for episode " + currentEpisodeNum.get() + ", reportSequence=" + reportSequence);
            }

            if (seqnum == exchangeSequence) {
                Map<Integer, List<Float>> featureToQuorum = new HashMap<>();
                for (var report : requestBlock.get(0).getReportQuorumList()) {
                    for (var entry : report.getReportMap().entrySet()) {
                        featureToQuorum.computeIfAbsent(entry.getKey(), f ->  new ArrayList<>()).add(entry.getValue());
                    }
                }

                // take median and send to learning agent
                Map<Integer, Float> featureToMedian = featureToQuorum.entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> entry.getKey(),
                                entry -> calculateMedian(entry.getValue())));
                // reuse the proto field `next_protocol` to store the current protocol just for convenience
                var learningData = LearningData.newBuilder().putAllReport(featureToMedian).setNextProtocol(checkpoint.getProtocol()).build();
                new Thread(() -> agentStub.sendData(learningData)).start(); 
                System.out.println("notify learning agent for episode " + currentEpisodeNum.get() + ", exchangeSequence=" + exchangeSequence);
            }
        }
    }

    @Override
    public boolean isClient() {
        return false;
    }

    private float calculateMedian(List<Float> values) {
        List<Float> sortedValues = values.stream().sorted().collect(Collectors.toList());

        int size = sortedValues.size();
        if (size % 2 == 0) {
            int midIndex = size / 2;
            return (sortedValues.get(midIndex - 1) + sortedValues.get(midIndex)) / 2.0f;
        } else {
            int midIndex = size / 2;
            return sortedValues.get(midIndex);
        }
    }
}
