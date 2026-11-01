package com.easysoft.stm.actors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.easysoft.stm.Actor;
import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMException;
import com.easysoft.stm.StatefulContext;
import com.easysoft.stm.Transition;

public class CallStateReturn implements Actor<STMContext> {
	static Logger logger = LoggerFactory.getLogger(CallStateReturn.class);
	@Override
	public Object handle(STMContext context) {
		StatefulContext statefulContext = context.getSTMContext();
		Transition popedTransition = statefulContext.popCallStateTransition();
		Transition currentTransition = statefulContext.getCurrentTransition();
		if (popedTransition == null) {
			logger.info("Returned from state without any call {}", currentTransition);
			throw new STMException("Returned from state without any call " + currentTransition);
		}
		statefulContext.getStm().next(context, popedTransition);
		return currentTransition.getFromEvent().getName();
	}
}
