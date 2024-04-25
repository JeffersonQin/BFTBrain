package com.gbft.framework.plugins;

import com.gbft.framework.data.MessageData;

public interface PipelinePlugin {

    public void sendMessage(MessageData message, int sender);

    public int getMaxActiveSequences();
}
