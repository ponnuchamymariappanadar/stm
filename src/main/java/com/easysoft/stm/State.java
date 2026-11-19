package com.easysoft.stm;

import java.util.Collection;

/**
 * Defined by a stage's {@code name}, or by {@code nextStage} on a step, an outcome, {@code onFalse} or
 * {@code onError}.
 * @author Ponnuchamy Mariappanadar
 */
public class State extends Common {

	TransitionCollection transitions = new TransitionCollection();

	public State(int id, String stringId) {
		super(id, stringId);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof State)) {
			return false;
		}
		return ((State) obj).id == id;
	}

	public void addTransition(Transition transition) {
		transitions.addTransition(transition);
	}
	
	public void addTransition(Event event, Transition transition)
	{
		transitions.addTransition(event, transition);
	}

	public Transition getTransition(Event event) {
		return transitions.getTransition(event);
	}

	public Collection<Transition> getTransitons() {
		return transitions.transitionMap.values();
	}
}
;