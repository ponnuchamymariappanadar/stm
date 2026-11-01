package com.easysoft.stm;

import java.util.LinkedHashMap;
import java.util.Map;

public class TransitionCollection {

	Map<Event, Transition> transitionMap = new LinkedHashMap<Event, Transition>();

	public TransitionCollection() {

	}

	public void addTransition(Transition transition) {
		transitionMap.put(transition.getFromEvent(), transition);
	}

	public void addTransition(Event event, Transition transition) {
		transitionMap.put(event, transition);
	}

	public Transition getTransition(Event event) {

		return transitionMap.get(event);
	}

	public Map<Event, Transition> getTransitions() {
		return transitionMap;
	}

}
