package com.easysoft.stm.actors;

import com.easysoft.stm.Actor;
import com.easysoft.stm.STMContext;

public class ThrowExceptionHandle implements Actor<STMContext> {

	@Override
	public Object handle(STMContext context) {
		throw new RuntimeException("throwExceptionHandle");
	}
}
