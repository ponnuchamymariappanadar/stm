package com.easysoft.stm;

import com.easysoft.stm.STM.STMEvent;

/**
 * A transition guarded by a condition: the actor runs only if the condition returns
 * {@code Boolean.TRUE}; otherwise the flow goes to the {@code OnFailedCondition} transition.
 */
public class ConditionAndActor extends Transition {
	Transition onFailedConditionTransition;
	Actor<STMContext> condition;

	public ConditionAndActor(State fromState, Event fromEvent, State toState, Event toEvent, STMEvent next, Transition errorTransition, Actor<STMContext> condition,
			Transition conditionFailedTransition) {
		super(fromState, fromEvent, toState, toEvent, next, errorTransition);
		this.condition = condition;
		this.onFailedConditionTransition = conditionFailedTransition;
		toStringValue = formatShortString().toString();
		toLongStringValue = formatString().toString();
		logger = Transition.getLogger(logger, condition);
	}

	void conditionFailed(StatefulContext statefulContext) {
		statefulContext.setNextTransition(onFailedConditionTransition);
	}

	public STMEvent execute(STMContext fsmContext, StatefulContext statefulContext) {
		try {
			Object rc = condition.handle(fsmContext);
			if (rc == Boolean.TRUE) {
				return super.execute(fsmContext, statefulContext);	
			}
			conditionFailed(statefulContext);
			return STMEvent.NEXT;
		} catch (Exception e) {
			return error(statefulContext, e);
		}
	}

	public Actor<STMContext> getCondition() {
		return condition;
	}

	public void setOnFailedConditionTransition(Transition onFailedCondition) {
		this.onFailedConditionTransition = onFailedCondition;
		toStringValue = formatShortString().toString();
		toLongStringValue = formatString().toString();
	}

	public Transition getOnFailedConditionTransition() {
		return onFailedConditionTransition;
	}

	@Override
	public StringBuilder formatShortString() {
		return Transition.formatShortString(condition, fromState, fromEvent);
	}
	
	@Override
	public StringBuilder formatString() {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append("<transition state='");
		sBuilder.append(fromState);
		sBuilder.append("' when='");
		sBuilder.append(fromEvent);
		sBuilder.append("' to='");
		sBuilder.append(toState);

		sBuilder.append("' fire='");
		sBuilder.append(toEvent);
		sBuilder.append("' stmevent='");
		sBuilder.append(nextStmEvent);
		sBuilder.append("' handler='");
		sBuilder.append(actor);
		if (onErrorTransition != null) {
			sBuilder.append("' OnError to='");
			sBuilder.append(onErrorTransition.getToState());
			sBuilder.append("' fire='");
			sBuilder.append(onErrorTransition.getToEvent());
		}
		sBuilder.append("' condition='");
		sBuilder.append(condition);
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
