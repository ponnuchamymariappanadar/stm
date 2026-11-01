package com.easysoft.stm;

public class Event extends Common{

	public Event(int id, String name) {
		super(id, name);
	}
	
	@Override
	public int hashCode() {
	   return id;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Event))
		{
			return false;
		}
		return ((Event)obj).id == id;
	}
}
