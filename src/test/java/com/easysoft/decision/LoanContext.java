package com.easysoft.decision;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.easysoft.stm.STMContext;
import com.easysoft.stm.StatefulContext;

/** Test context for the decision rules: nested beans, an enum, a list and a map. */
public class LoanContext implements STMContext {

	public enum Tier {
		SILVER, GOLD
	}

	public enum Status {
		ACTIVE, BLOCKED
	}

	public static class Address {
		private final String country;

		public Address(String country) {
			this.country = country;
		}

		public String getCountry() {
			return country;
		}
	}

	public static class Customer {
		private boolean vip;
		private Tier tier = Tier.SILVER;
		private Status status = Status.ACTIVE;
		private Address address;
		private String email;
		private LocalDate since;

		public boolean isVip() {
			return vip;
		}

		public Tier getTier() {
			return tier;
		}

		public Status getStatus() {
			return status;
		}

		public Address getAddress() {
			return address;
		}

		public String getEmail() {
			return email;
		}

		public LocalDate getSince() {
			return since;
		}

		public Customer vip(boolean value) {
			vip = value;
			return this;
		}

		public Customer tier(Tier value) {
			tier = value;
			return this;
		}

		public Customer status(Status value) {
			status = value;
			return this;
		}

		public Customer address(Address value) {
			address = value;
			return this;
		}

		public Customer email(String value) {
			email = value;
			return this;
		}

		public Customer since(LocalDate value) {
			since = value;
			return this;
		}
	}

	private StatefulContext stmContext;
	private long amount;
	private Customer customer = new Customer();
	private final List<String> tags = new ArrayList<>();
	private final Map<String, Object> attributes = new HashMap<>();
	private final List<String> history = new ArrayList<>();
	public int score;

	public LoanContext(long amount) {
		this.amount = amount;
	}

	@Override
	public StatefulContext getSTMContext() {
		return stmContext;
	}

	@Override
	public void setSTMContext(StatefulContext statefulContext) {
		this.stmContext = statefulContext;
	}

	public long getAmount() {
		return amount;
	}

	public Customer getCustomer() {
		return customer;
	}

	public LoanContext customer(Customer value) {
		customer = value;
		return this;
	}

	public List<String> getTags() {
		return tags;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public List<String> getHistory() {
		return history;
	}
}
