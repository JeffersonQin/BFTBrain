package com.gbft.framework.statemachine;

import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;

public class Transition {

    public final int fromState;
    public final int toState;
    public final Condition condition;

    public enum UpdateMode {
        NONE,
        SEQUENCE,
        VIEW,
        AGGREGATION,
        SLOW,
    }

    public final UpdateMode updateMode;

    // List of (target, message) pairs
    public final List<Pair<Integer, Integer>> responses;

    // List of (role, message) pairs
    public final List<Pair<Integer, Integer>> extraTally;

    public Transition(int fromState, int toState, Condition condition, UpdateMode updateMode,
            List<Pair<Integer, Integer>> responses,
            List<Pair<Integer, Integer>> extraTally) {
        this.fromState = fromState;
        this.toState = toState;
        this.condition = condition;
        this.updateMode = updateMode;
        this.responses = responses;
        this.extraTally = extraTally;
    }

    @Override
    public int hashCode() {
        return Objects.hash(updateMode, extraTally, fromState, responses, toState);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Transition other = (Transition) obj;
        return updateMode == other.updateMode && Objects.equals(extraTally, other.extraTally)
                && fromState == other.fromState && Objects.equals(responses, other.responses)
                && toState == other.toState;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[from ").append(StateMachine.states.get(fromState).name)
               .append(" to ").append(StateMachine.states.get(toState).name).append(", condition=")
               .append(condition).append(", updateMode=").append(updateMode.name().toLowerCase()).append(", responses=")
               .append(responses).append(", extraTally=").append(extraTally).append("]");
        return builder.toString();
    }

    public Transition copy() {
        return new Transition(this.fromState, this.toState, this.condition, this.updateMode, this.responses, this.extraTally);
    }

}
