package com.easysoft.stm;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.easysoft.stm.STM.STMEvent;

/**
 * Wraps an actor to implement {@code <Case>} routing: the actor's result is converted with
 * {@code toString()} and matched against the case {@code when} values, then {@code "default"}.
 * Without a match the flow follows the transition's normal {@code fire}.
 */
public class Choice<T extends STMContext> implements Actor<T> {
	private  Logger logger = LoggerFactory.getLogger(Choice.class);

	final Map<String, Transition> transitions;
	private final Actor<T> actor;

	public Choice(Actor<T> actor, Map<String, Transition> transitions) {
		this.transitions = transitions;
		this.actor = actor;
		logger = Transition.getLogger(logger, actor);
	}

	@Override
	public Object handle(T context) {

		Object value = actor.handle(context);
		context.getSTMContext().setLastResult(value);
		if (value != null) {
			String v = value.toString();
			logger.info("Conditional transitonal for the value {}", value);
			Transition transition = transitions.get(v);
			if (transition == null) {
				transition = transitions.get("default");
			}
			if (transition != null) {
				context.getSTMContext().setNextTransition(transition);
				return STMEvent.NEXT;
			}
		}
		return value;
	}

	public Map<String, Transition> getTransitions() {
		return transitions;
	}

	public Actor<T> getActualActor() {
		return actor;
	}
	
	public String toString() {
		return actor.toString();
	}
}
