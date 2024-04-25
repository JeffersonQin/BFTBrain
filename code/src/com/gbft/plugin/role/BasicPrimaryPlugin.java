package com.gbft.plugin.role;

import java.util.List;

import com.gbft.framework.core.Entity;
import com.gbft.framework.plugins.RolePlugin;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.EntityMapUtils;

public class BasicPrimaryPlugin extends RolePlugin {
    final int PRIMARY;

    public BasicPrimaryPlugin(Entity entity) {
        super(entity);

        PRIMARY = StateMachine.roles.indexOf("primary");
    }

    @Override
    protected List<Integer> getRoleEntities(long offset, int phase, int role) {
        var total = EntityMapUtils.nodeCount();

        if (role == StateMachine.CLIENT) {
            return EntityMapUtils.getAllClients();
        } else if (role == PRIMARY) {
            var index = offset % total;
            var entity = EntityMapUtils.getNodeId((int) index);
            return List.of(entity);
        } else if (role == StateMachine.NODE) {
            return EntityMapUtils.getAllNodes();
        }

        return null;
    }

    @Override
    protected List<Integer> getEntityRoles(long offset, int phase, int entity) {
        var index = EntityMapUtils.getNodeIndex(entity);
        var total = EntityMapUtils.nodeCount();

        if (index < 0) {
            return List.of(StateMachine.CLIENT);
        } else {
            if (index == offset % total) {
                return List.of(PRIMARY, StateMachine.NODE);
            } else {
                return List.of(StateMachine.NODE);
            }
        }
    }
}
