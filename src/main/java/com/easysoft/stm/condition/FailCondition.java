package com.easysoft.stm.condition;

import com.easysoft.stm.Actor;
import com.easysoft.stm.STMContext;

public class FailCondition<T extends STMContext> implements Actor<T> {

	@Override
	public Boolean handle(T context) {
		return Boolean.TRUE;
	}
}
