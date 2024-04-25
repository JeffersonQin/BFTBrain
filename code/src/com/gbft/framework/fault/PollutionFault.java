package com.gbft.framework.fault;

import com.gbft.framework.utils.Config;

public class PollutionFault extends Fault {

    private final static String POLICY = "pollution";

    public static int POLLUTION_NONE = 0;
    public static int POLLUTION_SBFT = 1;
    public static int POLLUTION_ALL = 2;

    public PollutionFault() {
        super(POLICY);
    }

    public int getType() {
        if (this.isOverridden.get()) return POLLUTION_NONE;
        var type = Config.string(getField("policy"));
        if (type == null) return POLLUTION_NONE;
        if (type.equals("sbft")) return POLLUTION_SBFT;
        if (type.equals("all")) return POLLUTION_ALL;
        return POLLUTION_NONE;
    }

    public int getType(int entityId) {
        return this.getAffectedEntities().contains(entityId) ? getType() : POLLUTION_NONE;
    }

    public static float randomFeatureGenerator(double upperBound) {
        return (float) (Math.random() * upperBound);
    }

    public static float randomOnehot() {
        return (float) (Math.random() > 0.5 ? 1 : 0);
    }
    
}
