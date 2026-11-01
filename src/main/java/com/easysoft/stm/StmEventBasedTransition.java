package com.easysoft.stm;

import com.easysoft.stm.STM.STMEvent;

/**
 * A transition with an explicit {@code stmevent}: it always reports its configured
 * {@link STMEvent}, ignoring changes the actor makes through the context.
 */
public class StmEventBasedTransition extends Transition {

	public StmEventBasedTransition(State fromState, Event fromEvent, State toState, Event toEvent, STMEvent next, Transition errorTransition) {
		super(fromState, fromEvent, toState, toEvent, next, errorTransition);
	}

	public STMEvent execute(STMContext fsmContext, StatefulContext statefulContext) {
		try {
			Object result = actor.handle(fsmContext);
			if (result instanceof STMEvent) {
				return (STMEvent) result;
			}
			statefulContext.setLastResult(result);
		} catch (Exception e) {
			return error(statefulContext, e);
		}
		return getNextSTMEvent();
	}

}
