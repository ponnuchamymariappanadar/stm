package com.easysoft.stm.actors;

import com.easysoft.stm.Actor;
import com.easysoft.stm.STMContext;
import com.easysoft.stm.StatefulContext;
import com.easysoft.stm.Transition;

public class CallState implements Actor<STMContext> {

	@Override
	public Object handle(STMContext context) {
		StatefulContext statefulContext = context.getSTMContext();
		Transition currentTransition = statefulContext.getCurrentTransition();
		statefulContext.addCallStateTransition(currentTransition.getOnResultTransition());
		return statefulContext.getStm().next(context,
				currentTransition.getToState().getTransition(currentTransition.getToEvent()));
	}
}
