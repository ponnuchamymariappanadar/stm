package com.easysoft.stm;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class StateCollection {
	public static int NOT_GOOD = -1;
	Map<String, State> states = new HashMap<String, State>();
	Map<Integer, State> statesIndex = new HashMap<Integer, State>();
	
	public void addState(State state)
	{
		states.put(state.getName(), state);
		statesIndex.put(state.getId(), state);
	}
	
	public State getState(String state)
	{
		return states.get(state);
	}
	
	public State getState(int id)
	{
		return statesIndex.get(id);
	}
	
	public Collection<State> getStates()
	{
		return states.values();
	}
}
