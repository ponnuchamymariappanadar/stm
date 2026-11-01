package com.easysoft.stm;

@SuppressWarnings("serial")
/**
 * Thrown for configuration errors found while building a machine, and for engine errors such as
 * a {@code <Return>} without a matching {@code <Call>}.
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
