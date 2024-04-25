package com.gbft.framework.fault;

import com.gbft.framework.utils.AdvanceConfig;
import com.gbft.framework.utils.Config;

public class InDarkFault extends Fault {

    private final static String POLICY = "in-dark";

    public InDarkFault() {
        super(POLICY);
    }

	public boolean getApply() {
        if (this.isOverridden.get()) return false;
		return AdvanceConfig.bool(getField("generator"));
	}

    public boolean getApply(int entityId) {
        return this.getAffectedEntities().contains(entityId) && getApply();
    }
    
}
