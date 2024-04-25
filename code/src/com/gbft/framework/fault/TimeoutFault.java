package com.gbft.framework.fault;

import com.gbft.framework.utils.AdvanceConfig;

public class TimeoutFault extends Fault {

    private final static String POLICY = "timeout";

    public TimeoutFault() {
        super(POLICY);
    }

	public long getDelay() {
        if (this.isOverridden.get()) return 0;
		return AdvanceConfig.integer(getField("generator"));
	}
    
    public long getDelay(int entityId) {
        if (this.getAffectedEntities().contains(entityId)) {
            return getDelay();
        }
        return 0;
    }

}
