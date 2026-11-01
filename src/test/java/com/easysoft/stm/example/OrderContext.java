package com.easysoft.stm.example;

import java.util.ArrayList;
import java.util.List;

import com.easysoft.stm.STMContext;
import com.easysoft.stm.StatefulContext;

/**
 * Example context for the order flow in {@code src/test/resources/stm/order-flow.xml}.
 * Your context holds your business data plus the engine's {@link StatefulContext}.
 */
public class OrderContext implements STMContext {

	private StatefulContext stmContext;
	private final String orderId;
	private final int amount;
	private final boolean express;
	private final List<String> history = new ArrayList<>();

	public OrderContext(StatefulContext stmContext, String orderId, int amount, boolean express) {
		this.stmContext = stmContext;
		this.orderId = orderId;
		this.amount = amount;
		this.express = express;
	}

	@Override
	public StatefulContext getSTMContext() {
		return stmContext;
	}

	@Override
	public void setSTMContext(StatefulContext statefulContext) {
		this.stmContext = statefulContext;
	}

	public String getOrderId() {
		return orderId;
	}

	public int getAmount() {
		return amount;
	}

	public boolean isExpress() {
		return express;
	}

	public List<String> getHistory() {
		return history;
	}

	public void record(String step) {
		history.add(step);
	}
}
