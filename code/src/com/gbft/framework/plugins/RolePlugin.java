package com.gbft.framework.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.stream.IntStream;

import com.gbft.framework.core.Entity;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.Config;
import com.gbft.framework.utils.EntityMapUtils;

public abstract class RolePlugin {

    protected Entity entity;

    // Leader Change
    public static final int STABLE = 0;
    public static final int ROTATE = 1;

    public long leaderChangeInterval;
    public Map<Integer, Integer> episodeLeaderMode;
    public ReadLock roleReadLock;
    public WriteLock roleWriteLock;
    public Condition roleCondition;
    private List<Integer> candidateLeaders;

    public RolePlugin(Entity entity) {
        this.entity = entity;
        leaderChangeInterval = Config.integer("benchmark.leader-rotate-interval");
        episodeLeaderMode = new ConcurrentHashMap<>();
        var roleLock = new ReentrantReadWriteLock();
        roleReadLock = roleLock.readLock();
        roleWriteLock = roleLock.writeLock();
        roleCondition = roleWriteLock.newCondition();
        candidateLeaders = new ArrayList<>();
        IntStream.range(0, EntityMapUtils.nodeCount()).forEach(node -> {
            if (Config.intList("fault.in-dark.affected-entities") == null ||
                    !Config.intList("fault.in-dark.affected-entities").contains(node)) {
                candidateLeaders.add(node);
            }
        });
    }

    public List<Integer> getRoleEntities(long sequenceNum, long viewNum, int phase, int role) {
        var leaderChange = episodeLeaderMode.get(this.entity.getEpisodeNum(sequenceNum));
        if (leaderChange == null) {
            System.out.println("Warning: Unknown leader mode for seqnum " + sequenceNum + ", assuming STABLE");
        } else if (leaderChange == ROTATE) {
            var offset = candidateLeaders.get((int) (sequenceNum / leaderChangeInterval + viewNum) % candidateLeaders.size());
            return getRoleEntities(offset, phase, role);
        }

        return getRoleEntities(viewNum, phase, role);
    }

    public List<Integer> getEntityRoles(long sequenceNum, long viewNum, int phase, int entity) {
        var leaderChange = episodeLeaderMode.get(this.entity.getEpisodeNum(sequenceNum));
        if (leaderChange == null) {
            System.out.println("Warning: Unknown leader mode for seqnum " + sequenceNum + ", assuming STABLE");
        } else if (leaderChange == ROTATE) {
            var offset = candidateLeaders.get((int) (sequenceNum / leaderChangeInterval + viewNum) % candidateLeaders.size());
            return getEntityRoles(offset, phase, entity);
        }

        return getEntityRoles(viewNum, phase, entity);
    }

    public void debugRoleMap() {
        var clientCount = EntityMapUtils.getAllClients().size();
        var nodeCount = EntityMapUtils.getAllNodes().size();
        var roleCount = StateMachine.roles.size();
        var phaseCount = StateMachine.phases.size();

        System.out.println("Offset/Phase/Role: [Entities]");
        var output = new StringBuilder();
        for (var offset = 0; offset < nodeCount * 2; offset++) {
            for (var phase = 0; phase < phaseCount; phase++) {
                for (var role = 0; role < roleCount; role++) {
                    var entities = getRoleEntities(offset, phase, role);
                    var pstr = StateMachine.phases.get(phase);
                    var rstr = StateMachine.roles.get(role);
                    output.append(offset + "/" + pstr + "/" + rstr + ": " + entities + "\n");
                }
            }
        }
        System.out.println(output);

        output = new StringBuilder();
        System.out.println("Offset/Phase/Entity: [Roles]");
        for (var offset = 0; offset < nodeCount * 2; offset++) {
            for (var phase = 0; phase < phaseCount; phase++) {
                for (var entity = 0; entity < nodeCount + clientCount; entity++) {
                    var roles = getEntityRoles(offset, phase, entity);
                    var pstr = StateMachine.phases.get(phase);
                    output.append(offset + "/" + pstr + "/" + entity + ": ");
                    for (var role : roles) {
                        if (roles.indexOf(role) > 0) {
                            output.append(",");
                        }
                        var rstr = StateMachine.roles.get(role);
                        output.append(rstr);
                    }
                    output.append("\n");
                }
            }
        }
        System.out.println(output);
    }

    protected abstract List<Integer> getRoleEntities(long offset, int phase, int role);

    protected abstract List<Integer> getEntityRoles(long offset, int phase, int entity);
}
