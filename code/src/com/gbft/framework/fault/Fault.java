package com.gbft.framework.fault;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.gbft.framework.utils.Config;

public abstract class Fault {
    private List<Integer> affectedEntities;

    protected String policyName;

    protected AtomicBoolean isOverridden;

    protected String getField(String fieldName) {
        return "fault." + policyName + "." + fieldName;
    }

    public Fault(String policyName) {
        this.policyName = policyName;
        // default settings at warm-up phase
        this.affectedEntities = Config.intList(getField("affected-entities"));

        // check if is overridden
        this.isOverridden = new AtomicBoolean(false);
        var overridden_list = Config.stringList("protocol.fault-override");
        if (overridden_list != null) {
            if (overridden_list.contains(policyName)) {
                this.isOverridden.set(true);
            }
        }
    }

    public void reloadProtocol(String protocol) {
        Config.setCurrentProtocol(protocol);
        var overridden_list = Config.stringList("protocol.fault-override");
        if (overridden_list != null) {
            if (overridden_list.contains(policyName)) {
                this.isOverridden.set(true);
                return;
            }
        }
        this.isOverridden.set(false);
    }

    public List<Integer> getAffectedEntities() {
        return this.affectedEntities;
    }
}
