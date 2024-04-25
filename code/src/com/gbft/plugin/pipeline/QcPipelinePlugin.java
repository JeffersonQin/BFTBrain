package com.gbft.plugin.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import com.gbft.framework.core.Entity;
import com.gbft.framework.data.MessageData;
import com.gbft.framework.plugins.PipelinePlugin;
import com.gbft.framework.statemachine.Condition;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.Printer;
import com.gbft.framework.utils.Printer.Verbosity;

public class QcPipelinePlugin implements PipelinePlugin {

    private Entity entity;

    //private long next;

    private int pendingSize;
    private ReentrantLock pendingLock;
    private List<MessageData> pendingMessages;
    private List<Integer> messageIndexes;

    public QcPipelinePlugin(Entity entity) {
        this.entity = entity;

        //next = 0;
        pendingSize = 0;
        pendingLock = new ReentrantLock();
        pendingMessages = new ArrayList<>();

        pendingSize = StateMachine.states.stream()
                                         .filter(
                                                 state -> state.phase == StateMachine.NORMAL_PHASE
                                                         && state.transitions.containsKey(StateMachine.NODE))
                                         .flatMap(state -> state.transitions.get(StateMachine.NODE).stream())
                                         .filter(transition -> transition.condition.getType() == Condition.MESSAGE_CONDITION)
                                         .mapToInt(e -> 1).sum();

        messageIndexes = new ArrayList<>();
        for (var state : StateMachine.states) {
            var transitions = state.transitions.get(StateMachine.NODE);
            if (transitions != null) {
                for (var transition : transitions) {
                    if (transition.condition.getType() == Condition.MESSAGE_CONDITION) {
                        var type = transition.condition.getParam(Condition.MESSAGE_TYPE);
                        messageIndexes.add(type);
                    }
                }
            }
        }
    }

    @Override
    public void sendMessage(MessageData message, int sender) {
        var type = message.getMessageType();
        if (entity.isClient() || message.getTargetsList().size() <= 1 || !messageIndexes.contains(type)) {
            entity.getCoordinator().sendMessages(List.of(message), sender);
            return;
        }

        var seqnum = message.getSequenceNum();

        pendingLock.lock();
        pendingMessages.add(message);
        var index = seqnum + messageIndexes.indexOf(type) + 1;
        var expected = index < pendingSize ? index : pendingSize;

        // printPendingRes(seqnum, expected);
        if (pendingMessages.size() >= expected) {
            var messages = List.copyOf(pendingMessages);
            pendingMessages.clear();
            pendingLock.unlock();

            entity.getCoordinator().sendMessages(messages, sender);
        } else {
            pendingLock.unlock();
        }
    }

    private void printPendingRes(long seqnum, long expected) {
        var pass = pendingMessages.size() >= expected;

        var sb = new StringBuilder("Pending check ");
        sb.append(pass ? "pass: " : "fail: ")
          .append("seqnum=").append(seqnum)
          .append(", expected=").append(expected)
          .append(", current=").append(pendingMessages.size()).append("\n");

        sb.append("Pending messages: ").append(pendingMessages.stream().reduce("",
                (str, msg) -> str + (str.isEmpty() ? "" : ", ") + Printer.convertToString(msg),
                (str1, str2) -> str1 + (str1.isEmpty() ? "" : ", ") + str2))
          .append("\n");

        Printer.print(Verbosity.V, entity.prefix, sb.toString());
    }

    @Override
    public int getMaxActiveSequences() {
        return pendingSize;
    }
}
