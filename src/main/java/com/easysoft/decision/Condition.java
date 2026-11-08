package com.easysoft.decision;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One leaf of a decision tree: read the value at a bean property path and compare it with a
 * configured value.
 *
 * <pre>
 * - name: domestic
 *   path: customer.address.country
 *   in: [US, CA]
 * </pre>
 */
public final class Condition implements Rule {

	private final String ruleSet;
	private final String name;
	private final PropertyPath path;
	private final Operator operator;
	private final Object value;
	private final List<?> values;
	private final Pattern pattern;

	public Condition(String ruleSet, String name, String path, Operator operator, Object value) {
		this.ruleSet = ruleSet;
		this.name = name;
		this.path = PropertyPath.parse(path);
		this.operator = operator;
		this.value = value;
		String label = "condition '" + name + "'";
		switch (operator) {
		case IN, NOT_IN -> {
			this.values = value instanceof Collection<?> collection ? List.copyOf(collection.stream().map(v -> v == null ? NullValue.INSTANCE : v).toList())
					: List.of(value == null ? NullValue.INSTANCE : value);
			this.pattern = null;
		}
		case MATCHES -> {
			if (value == null) {
				throw new DecisionException(label + ": matches needs a regular expression");
			}
			try {
				this.pattern = Pattern.compile(value.toString());
			} catch (PatternSyntaxException e) {
				throw new DecisionException(label + ": invalid regular expression '" + value + "'", e);
			}
			this.values = null;
		}
		case IS_NULL -> {
			if (!(value instanceof Boolean) && !"true".equalsIgnoreCase(String.valueOf(value)) && !"false".equalsIgnoreCase(String.valueOf(value))) {
				throw new DecisionException(label + ": isNull takes true or false, not '" + value + "'");
			}
			this.values = null;
			this.pattern = null;
		}
		case GREATER_THAN, GREATER_OR_EQUAL, LESS_THAN, LESS_OR_EQUAL -> {
			if (value == null || value instanceof Collection<?>) {
				throw new DecisionException(label + ": " + operator.getKey() + " needs a single value");
			}
			this.values = null;
			this.pattern = null;
		}
		default -> {
			if (value instanceof Collection<?>) {
				throw new DecisionException(label + ": " + operator.getKey() + " takes a single value; use in/notIn for a list");
			}
			this.values = null;
			this.pattern = null;
		}
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getKey() {
		return ruleSet + "/" + name;
	}

	public String getPath() {
		return path.toString();
	}

	public Operator getOperator() {
		return operator;
	}

	public Object getValue() {
		return value;
	}

	@Override
	public boolean evaluate(Object root) {
		Object actual = path.resolve(root);
		return switch (operator) {
		case EQUAL -> Values.isEqual(actual, value);
		case NOT_EQUAL -> !Values.isEqual(actual, value);
		case IN -> contains(actual);
		case NOT_IN -> !contains(actual);
		case GREATER_THAN -> actual != null && Values.compare(actual, value, path.toString()) > 0;
		case GREATER_OR_EQUAL -> actual != null && Values.compare(actual, value, path.toString()) >= 0;
		case LESS_THAN -> actual != null && Values.compare(actual, value, path.toString()) < 0;
		case LESS_OR_EQUAL -> actual != null && Values.compare(actual, value, path.toString()) <= 0;
		case IS_NULL -> (actual == null) == Boolean.parseBoolean(String.valueOf(value));
		case MATCHES -> actual != null && pattern.matcher(actual.toString()).matches();
		};
	}

	private boolean contains(Object actual) {
		for (Object candidate : values) {
			if (Values.isEqual(actual, candidate == NullValue.INSTANCE ? null : candidate)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return name + "(" + path + " " + operator.getKey() + " " + value + ")";
	}

	/** Stands in for {@code null} in an {@code in} list, since {@link List#copyOf} rejects nulls. */
	private enum NullValue {
		INSTANCE
	}
}
