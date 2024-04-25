package com.gbft.framework.statemachine;

import java.util.Map;

public class Condition {

    // Condition Types
    public static final int TRUE_CONDITION = 0;
    public static final int MESSAGE_CONDITION = 1;
    public static final int TIMEOUT_CONDITION = 2;

    // Condition Parameters
    public static final int MESSAGE_TYPE = 0;
    public static final int QUORUM = 1;
    public static final int TIMEOUT_MODE = 2;
    public static final int TIMEOUT_MULTIPLIER = 3;

    private final int type;
    private Map<Integer, Integer> params;

    public Condition(int type, Map<Integer, Integer> params) {
        super();
        this.type = type;
        this.params = params;
    }

    public int getType() {
        return type;
    }

    public Integer getParam(int param) {
        return params.get(param);
    }
}
