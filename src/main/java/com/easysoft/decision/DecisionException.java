package com.easysoft.decision;

import com.easysoft.stm.STMException;

/**
 * Thrown for an invalid rule file (unknown operator, unknown reference, cycle, bad expression) and
 * for evaluation errors such as a property path that does not exist on the bean. Inside a flow it
 * is caught by the transition like any other actor error and routed to {@code onError}.
 */
@SuppressWarnings("serial")
public class DecisionException extends STMException {

	public DecisionException(String msg) {
		super(msg);
	}

	public DecisionException(String msg, Throwable e) {
		super(msg, e);
	}
}
