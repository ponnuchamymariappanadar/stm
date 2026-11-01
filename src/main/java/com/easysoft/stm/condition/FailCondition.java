package com.easysoft.stm.condition;

import com.easysoft.stm.Actor;
import com.easysoft.stm.STMContext;

/** A condition that always fails ({@code false}). */
public class FailCondition<T extends STMContext> implements Actor<T> {

	@Override
	public Boolean handle(T context) {
		return Boolean.FALSE;
	}
}
