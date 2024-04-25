package com.gbft.plugin.role;

import java.util.List;

import com.gbft.framework.core.Entity;
import com.gbft.framework.plugins.RolePlugin;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.EntityMapUtils;

public class PrimaryQcPlugin extends RolePlugin {
    final int PRIMARY;
    final int PRIMARY2;
    final int PRIMARY3;
    final int PRIMARY4;

    public PrimaryQcPlugin(Entity entity) {
        super(entity);

        PRIMARY = StateMachine.roles.indexOf("primary");
        PRIMARY2 = StateMachine.roles.indexOf("primary2");
        PRIMARY3 = StateMachine.roles.indexOf("primary3");
        PRIMARY4 = StateMachine.roles.indexOf("primary4");
    }

    @Override
    protected List<Integer> getRoleEntities(long offset, int phase, int role) {
        var total = EntityMapUtils.nodeCount();

        if (role == StateMachine.CLIENT) {
            return EntityMapUtils.getAllClients();
        } else if (role == StateMachine.NODE) {
            return EntityMapUtils.getAllNodes();
        }

        if (role == PRIMARY2) {
            offset += 1;
        } else if (role == PRIMARY3) {
            offset += 2;
        } else if (role == PRIMARY4) {
            offset += 3;
        } else if (role != PRIMARY) {
            return null;
        }

        var index = offset % total;
        var entity = EntityMapUtils.getNodeId((int) index);
        return List.of(entity);
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
            } else if (index == (offset + total + 1) % total) {
                return List.of(PRIMARY2, StateMachine.NODE);
            } else if (index == (offset + total + 2) % total) {
                return List.of(PRIMARY3, StateMachine.NODE);
            } else if (index == (offset + total + 3) % total) {
                return List.of(PRIMARY4, StateMachine.NODE);
            } else {
                return List.of(StateMachine.NODE);
            }
        }
    }
}
