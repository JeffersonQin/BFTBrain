package com.gbft.plugin.pipeline;

import java.util.List;

import com.gbft.framework.core.Entity;
import com.gbft.framework.data.MessageData;
import com.gbft.framework.plugins.PipelinePlugin;

public class DirectPipelinePlugin implements PipelinePlugin {

    private Entity entity;

    public DirectPipelinePlugin(Entity entity) {
        this.entity = entity;
    }

    @Override
    public void sendMessage(MessageData message, int sender) {
        entity.getCoordinator().sendMessages(List.of(message), sender);
    }

    @Override
    public int getMaxActiveSequences() {
        return 100;
    }

}
