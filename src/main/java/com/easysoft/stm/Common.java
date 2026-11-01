package com.easysoft.stm;

public class Common {

	final int id;
	final String name;
	
	public Common(int id, String name) {
		super();
		this.id = id;
		this.name = name;
	}

	public int getId() {
		return id;
	}

	@Override
	public String toString() {
		return name;
	}

	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
	   return id;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Common))
		{
			return false;
		}
		return ((Common)obj).id == id;
	}

}
