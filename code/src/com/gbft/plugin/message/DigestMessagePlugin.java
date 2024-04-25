package com.gbft.plugin.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import com.gbft.framework.core.Entity;
import com.gbft.framework.data.MessageData;
import com.gbft.framework.data.RequestData;
import com.gbft.framework.plugins.MessagePlugin;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.DataUtils;
import com.google.protobuf.ByteString;

public class DigestMessagePlugin implements MessagePlugin {

    private Entity entity;

    public DigestMessagePlugin(Entity entity) {
        this.entity = entity;
    }

    @Override
    public MessageData processIncomingMessage(MessageData message) {
        if (message.getFlagsList().contains(DataUtils.INVALID)) {
            return message;
        }

        var type = message.getMessageType();
        var hasblock = StateMachine.messages.get(type).hasRequestBlock;
        if (hasblock) {
            var computed = getDigest(message.getRequestsList());
            if (!computed.equals(message.getDigest())) {
                message = DataUtils.invalidate(message);
            }
        }

        return message;
    }

    @Override
    public MessageData processOutgoingMessage(MessageData message) {
        var type = message.getMessageType();
        var hasblock = StateMachine.messages.get(type).hasRequestBlock;
        if (hasblock && message.getDigest().isEmpty()) {
            var computed = getDigest(message.getRequestsList());
            message = MessageData.newBuilder(message).setDigest(computed).build();
        }

        return message;
    }

    private static ByteString getDigest(List<RequestData> requestBlock) {
        return DataUtils.getDigest(requestBlock);
    }

}
