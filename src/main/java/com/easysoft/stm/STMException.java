package com.easysoft.stm;

@SuppressWarnings("serial")
/**
 * Thrown for configuration errors found while building a machine, and for engine errors such as
 * a {@code returns} entry reached without a matching {@code runStage} rule.
 */
public class STMException extends RuntimeException {
	public STMException(String msg) {
		super(msg);
	}
	public STMException(String msg, Throwable e) {
		super(msg, e);
	}
	public STMException(Throwable e) {
		super(e);
	}
}
