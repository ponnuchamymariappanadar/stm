package com.easysoft.stm;

import java.util.Collection;

/**
 * Defined on the "name" attribute of  <State> or "to" attribute of  <Transition > or "to" attribute of <Case> or "to" attribute of <onFailedCondition>
 * or "to" attribute of <OnError>
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