package com.gbft.framework.plugins;

import com.gbft.framework.statemachine.Transition;

public interface TransitionPlugin {

    public Transition processTransition(long seqnum, int currentState, Transition transition);

    public void postTransition(long seqnum, int oldState, Transition transition);

}
