package com.easysoft.stm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal {@link STMContext} used by the tests. Actors record what they did in {@link #log}
 * and read inputs from {@link #data}.
 */
public class TestContext implements STMContext {

	private StatefulContext statefulContext;
	public final List<String> log = new ArrayList<>();
	public final Map<String, Object> data = new HashMap<>();

	public TestContext(StatefulContext statefulContext) {
		this.statefulContext = statefulContext;
	}

	@Override
	public StatefulContext getSTMContext() {
		return statefulContext;
	}

	@Override
	public void setSTMContext(StatefulContext statefulContext) {
		this.statefulContext = statefulContext;
	}

	public TestContext with(String key, Object value) {
		data.put(key, value);
		return this;
	}
}
