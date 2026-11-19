package com.easysoft.stm.example;

import java.util.Map;

import com.easysoft.stm.Actor;

/**
 * Example actor for the order flow. Each public method is referenced from the flow by
 * {@code method}. Actions take the context and return any value; checks must return
 * {@code boolean}/{@code Boolean}. A method can also take the step's {@code input}: text as a
 * {@code String}, a map as a {@code Map<String, String>}.
 */
public class OrderActions implements Actor<OrderContext> {

	public enum Shipping {
		STANDARD, EXPRESS
	}

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

	/** A check whose limit comes from the flow: {@code input: { creditLimit: 1000 }}. */
	public boolean charge(OrderContext order, Map<String, String> input) {
		boolean approved = order.getAmount() <= Integer.parseInt(input.get("creditLimit"));
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
