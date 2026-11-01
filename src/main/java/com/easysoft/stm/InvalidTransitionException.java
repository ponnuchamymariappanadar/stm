package com.easysoft.stm;

@SuppressWarnings("serial")
public class InvalidTransitionException extends RuntimeException {
	public InvalidTransitionException(String msg)
	{
		super(msg);
	}
}
