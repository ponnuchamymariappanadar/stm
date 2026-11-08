package com.easysoft.decision;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One leaf of a decision tree: read the value at a bean property path and compare it with a
 * configured value. Named conditions come from the {@code conditions} list; inline comparisons such
 * as {@code amount <= 10000} written in a {@code when} are conditions too.
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
	private final boolean inline;

	public Condition(String ruleSet, String name, String path, Operator operator, Object value) {
		this(ruleSet, name, path, operator, value, false);
	}

	/** An inline comparison; {@code source} is its text, such as {@code amount <= 10000}. */
	static Condition inline(String ruleSet, String source, String path, Operator operator, Object value) {
		return new Condition(ruleSet, source, path, operator, value, true);
	}

	private Condition(String ruleSet, String name, String path, Operator operator, Object value, boolean inline) {
		this.inline = inline;
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
		return test(path.resolve(root));
	}

	/** Evaluates and records the value read; {@code label} is the name used where it was referenced. */
	Explanation explain(Object root, String label) {
		Object actual = path.resolve(root);
		boolean result = test(actual);
		String test = operator == Operator.IS_NULL ? (Boolean.parseBoolean(String.valueOf(value)) ? "is null" : "is not null")
				: operator.getKey() + " " + Values.describe(value);
		String detail = inline ? path + " is " + Values.describe(actual) : path + " is " + Values.describe(actual) + "; " + test;
		return Explanation.comparison(label, result, detail);
	}

	/** Whether this is an inline comparison written in a {@code when}; its name is then its source text. */
	public boolean isInline() {
		return inline;
	}

	/**
	 * Checks the path against {@code rootType} and the configured values against the property's
	 * type. Returns the problem, or {@code null} when there is none or the type is not known statically.
	 */
	String validate(Class<?> rootType) {
		Class<?> type;
		try {
			type = path.resolveType(rootType);
		} catch (DecisionException e) {
			return e.getMessage();
		}
		if (type == null) {
			return null;
		}
		return switch (operator) {
		case IS_NULL -> type.isPrimitive() ? "path '" + path + "' is a " + type.getName() + ", which is never null" : null;
		case MATCHES -> null;
		case IN, NOT_IN -> {
			for (Object candidate : values) {
				String problem = Values.checkValue(type, candidate == NullValue.INSTANCE ? null : candidate, path.toString(), operator);
				if (problem != null) {
					yield problem;
				}
			}
			yield null;
		}
		default -> Values.checkValue(type, value, path.toString(), operator);
		};
	}

	private boolean test(Object actual) {
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
