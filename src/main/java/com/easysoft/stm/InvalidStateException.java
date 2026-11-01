package com.easysoft.stm;

@SuppressWarnings("serial")
public class InvalidStateException extends RuntimeException {
	public InvalidStateException(String msg)
	{
		super(msg);
	}
}
