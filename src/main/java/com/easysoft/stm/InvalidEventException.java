package com.easysoft.stm;

@SuppressWarnings("serial")
public class InvalidEventException extends RuntimeException {
	public InvalidEventException(String msg)
	{
		super(msg);
	}
}
