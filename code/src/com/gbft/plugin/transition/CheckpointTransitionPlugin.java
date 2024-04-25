package com.gbft.plugin.transition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import com.gbft.framework.core.Entity;
import com.gbft.framework.plugins.TransitionPlugin;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.statemachine.Transition;
import com.gbft.framework.statemachine.Transition.UpdateMode;
import com.gbft.framework.utils.CheckpointManager;
import com.gbft.framework.utils.Config;
import com.gbft.framework.utils.DataUtils;
import com.google.protobuf.ByteString;

public class CheckpointTransitionPlugin implements TransitionPlugin {

    private Entity entity;
    private CheckpointManager checkpointManager;

    private final int CHECKPOINT;
    private final int checkpointSize;

    private long highMark;

    private Object sync = new Object();

    public CheckpointTransitionPlugin(Entity entity) {
        this.entity = entity;
        checkpointManager = entity.getCheckpointManager();

        CHECKPOINT = StateMachine.messages.indexOf(StateMachine.findMessage("checkpoint"));
        checkpointSize = Config.integer("benchmark.checkpoint-size");

        highMark = 0;
    }

    @Override
    public Transition processTransition(long seqnum, int currentState, Transition transition) {
        if (entity.isClient() || transition.updateMode != UpdateMode.SEQUENCE) {
            return transition;
        }

        synchronized (sync) {
            if (checkpointManager.getMaxCheckpoint() <= highMark) {
                return transition;
            }

            highMark = checkpointManager.getMaxCheckpoint();
            var lowMark = checkpointManager.getMinCheckpoint();
            if (highMark - lowMark < 3) {
                return transition;
            }

            var digest = getCheckpointDigest(lowMark);
            var targets = entity.getRolePlugin().getRoleEntities(0, 0, StateMachine.NORMAL_PHASE, StateMachine.NODE);
            var message = DataUtils.createMessage(lowMark, 0L, CHECKPOINT, entity.getId(), targets, List.of(),
                    entity.EMPTY_BLOCK, null, digest);
            entity.sendMessage(message);
        }

        return transition;
    }

    private ByteString getCheckpointDigest(long checkpointNum) {
        var stream = new ByteArrayOutputStream();
        try {
            var min = checkpointNum * checkpointSize;
            var max = min + checkpointSize - 1;
            var checkpoint = checkpointManager.getCheckpoint(checkpointNum);
            var tally = checkpoint.getMessageTally();
            for (var seqnum = min; seqnum < max; seqnum++) {
                var digest = tally.getQuorumDigest(seqnum, tally.getMaxQuorum(seqnum));
                stream.write(digest.toByteArray());
            }

            stream.flush();
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return DataUtils.getDigest(stream.toByteArray());
    }

    @Override
    public void postTransition(long seqnum, int oldState, Transition transition) {

    }
}
