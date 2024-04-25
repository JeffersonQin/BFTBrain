package com.gbft.plugin.message;

import java.util.List;

import com.gbft.framework.core.Entity;
import com.gbft.framework.data.MessageData;
import com.gbft.framework.plugins.MessagePlugin;
import com.gbft.framework.plugins.RolePlugin;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.CheckpointManager;
import com.gbft.framework.utils.FeatureManager;
import com.gbft.framework.utils.Printer;
import com.gbft.framework.utils.Printer.Verbosity;

public class SpeculateMessagePlugin implements MessagePlugin {

    private Entity entity;
    private RolePlugin rolePlugin;
    private CheckpointManager checkpointManager;
    private final int COMMIT;

    public SpeculateMessagePlugin(Entity entity) {
        this.entity = entity;
        rolePlugin = entity.getRolePlugin();
        checkpointManager = entity.getCheckpointManager();
        COMMIT = StateMachine.messages.indexOf(StateMachine.findMessage("commit", "zyzzyva_"));
    }

    @Override
    public MessageData processIncomingMessage(MessageData message) {
        if (entity.isClient()) {
            return message;
        }

        var type = message.getMessageType();
        var phase = StateMachine.NORMAL_PHASE;
        var currentView = entity.getCurrentViewNum();
        if (type == COMMIT) {
            var seqnum = message.getSequenceNum();
            if (checkpointManager.getCheckpointForSeq(seqnum).getState(seqnum) == StateMachine.EXECUTED) {
                var checkpoint = checkpointManager.getCheckpointForSeq(seqnum);
                var block = checkpoint.getRequestBlock(seqnum);
                var viewnum = checkpoint.getMessageTally().getMaxQuorum(seqnum);
                var target = List.of(message.getSource());
                var response = entity.createMessage(seqnum, viewnum, block, COMMIT, entity.getId(), target);
                entity.sendMessage(response);
                // Printer.print(Verbosity.V, entity.prefix, "Received message 3: ", message);
                // Printer.print(Verbosity.V, entity.prefix, "Sending message 3: ", response);
                entity.getFeatureManager().countPath(entity.getEpisodeNum(seqnum), FeatureManager.FAST_PATH_FREQUENCY, FeatureManager.SLOW);
            }
        }

        return message;
    }

    @Override
    public MessageData processOutgoingMessage(MessageData message) {
        return message;
    }

}
