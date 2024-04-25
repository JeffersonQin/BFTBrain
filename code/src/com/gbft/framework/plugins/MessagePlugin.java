package com.gbft.framework.plugins;

import com.gbft.framework.data.MessageData;

public interface MessagePlugin {

    public MessageData processIncomingMessage(MessageData message);

    public MessageData processOutgoingMessage(MessageData message);

}
