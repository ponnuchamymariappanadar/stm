package com.easysoft.stm.example;

import com.easysoft.stm.Actor;

/**
 * Example actor for the order flow. Each public method is referenced from the configuration
 * by {@code methodName}. Actor methods take the context and return any value; condition
 * methods must return {@code boolean}/{@code Boolean}.
 */
public class OrderActions implements Actor<OrderContext> {

	public enum Shipping {
		STANDARD, EXPRESS
	}

	static final int CREDIT_LIMIT = 1000;

	public Object receive(OrderContext order) {
		if (order.getOrderId() == null) {
			throw new IllegalArgumentException("order id is required");
		}
		order.record("received");
		return order.getOrderId();
	}

	public boolean isValid(OrderContext order) {
		return order.getAmount() > 0;
	}

	public boolean charge(OrderContext order) {
		boolean approved = order.getAmount() <= CREDIT_LIMIT;
		order.record(approved ? "charged" : "charge refused");
		return approved;
	}

	public Shipping shippingMethod(OrderContext order) {
		return order.isExpress() ? Shipping.EXPRESS : Shipping.STANDARD;
	}

	public Object shipStandard(OrderContext order) {
		order.record("shipped standard");
		return null;
	}

	public Object shipExpress(OrderContext order) {
		order.record("shipped express");
		return null;
	}

	public Object reject(OrderContext order) {
		order.record("rejected");
		return null;
	}

	public Object decline(OrderContext order) {
		order.record("declined");
		return null;
	}

	public Object recordError(OrderContext order) {
		order.record("error: " + order.getSTMContext().getException().getMessage());
		return null;
	}
}
