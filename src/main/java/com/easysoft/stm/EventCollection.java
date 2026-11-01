package com.easysoft.stm;

import java.util.HashMap;
import java.util.Map;

public class EventCollection {
	public static int NOT_GOOD = -1;
	Map<String, Event> events = new HashMap<String, Event>();
	Map<Integer, Event> eventIndex = new HashMap<Integer, Event>();
	
	public void addEvent(Event event)
	{
		events.put(event.getName(), event);
		eventIndex.put(event.getId(), event);
	}
	
	public Event getEvent(String event)
	{
		return events.get(event);
	}
	
	public Event getEvent(int id)
	{
		return eventIndex.get(id);
	}
}
