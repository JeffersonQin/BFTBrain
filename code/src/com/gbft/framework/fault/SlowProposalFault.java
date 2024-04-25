package com.gbft.framework.fault;

import com.gbft.framework.utils.AdvanceConfig;
import com.gbft.framework.utils.Config;

public class SlowProposalFault extends Fault {

    private final static String POLICY = "slow-proposal";

    public SlowProposalFault() {
        super(POLICY);
    }

    public long getDelay() {
        // check if is overridden
        if (this.isOverridden.get()) return 0;
        // check if is attacking
        if (!AdvanceConfig.bool(getField("attacking"))) return 0;
        return AdvanceConfig.integer(getField("timer"));
    }
    
    public long getDelay(int entityId) {
        // check if current entity is one of the attacker
        if (this.getAffectedEntities().contains(entityId)) {
            return getDelay();
        }
        return 0;
    }

    public long getPerRequestDelay() {
        return (long) ((double) getDelay() / ((double) Config.integer("general.f") + 1));
    }

    public long getPerRequestDelay(int entityId) {
        return (long) ((double) getDelay(entityId) / ((double) Config.integer("general.f") + 1));
    }

}
