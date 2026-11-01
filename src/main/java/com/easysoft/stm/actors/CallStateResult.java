package com.easysoft.stm.actors;

import com.easysoft.stm.Actor;
import com.easysoft.stm.STMContext;

public class CallStateResult implements Actor<STMContext> {

	@Override
	public Object handle(STMContext context) {
		return context.getLastSTMResult();
	}
}
