package com.easysoft.decision;

import com.easysoft.stm.Actor;
import com.easysoft.stm.STMContext;

/** Actors for stm/loan-flow.yaml; each records what happened. */
public class LoanActions implements Actor<STMContext> {

	public Object approve(STMContext context) {
		return record(context, "approved");
	}

	public Object review(STMContext context) {
		return record(context, "review");
	}

	public Object reject(STMContext context) {
		return record(context, "rejected");
	}

	public Object error(STMContext context) {
		Exception e = context.getSTMContext().getException();
		return record(context, "error: " + (e == null ? null : e.getMessage()));
	}

	private static Object record(STMContext context, String step) {
		((LoanContext) context).getHistory().add(step);
		return step;
	}
}
