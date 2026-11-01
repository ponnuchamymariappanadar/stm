package com.easysoft.stm;

import com.easysoft.stm.STM.STMEvent;

/**
 * A transition with a condition and no actor. The condition's result is stored as the last
 * result; {@code false} goes to the {@code OnFailedCondition} transition.
 */
public class ConditionOnly extends ConditionAndActor {

	public ConditionOnly(State fromState, Event fromEvent, State toState, Event toEvent, STMEvent next, Transition errorTransition, Actor<STMContext> condition,
			Transition conditionFailedTransition) {
		super(fromState, fromEvent, toState, toEvent, next, errorTransition, condition, conditionFailedTransition);
	}

	@Override
	public STMEvent execute(STMContext fsmContext, StatefulContext statefulContext) {
		try {
			Object rc = condition.handle(fsmContext);
			if (rc == Boolean.TRUE) {
				statefulContext.setLastResult(Boolean.TRUE);
				return statefulContext.getNextSTMEvent();
			}
			statefulContext.setLastResult(Boolean.FALSE);
			conditionFailed(statefulContext);
			logger.info("false condition");
			return STMEvent.NEXT;
		} catch (Exception e) {
			return error(statefulContext, e);
		}
	}

	@Override
	public StringBuilder formatShortString() {
		return Transition.formatShortString(condition, fromState, fromEvent);
	}

	@Override
	public StringBuilder formatString() {
		StringBuilder sBuilder = Transition.formatString("' condition='", condition, fromState.toString(), fromEvent.toString(), toState.toString(), toEvent.toString(),
				nextStmEvent, onErrorTransition, onResultTransition);
		if (onFailedConditionTransition != null) {
			sBuilder.append("' onFailedCondition to='");
			sBuilder.append(onFailedConditionTransition.getToState());
			sBuilder.append("' fire='");
			sBuilder.append(onFailedConditionTransition.getToEvent());
		}
		sBuilder.append("'/>");
		return sBuilder;
	}
}
