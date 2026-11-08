package com.easysoft.decision;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The comparison a {@link Condition} applies to the value found at its path. The YAML key is the
 * operator name; it is matched case-insensitively, so {@code notEqual} and {@code notequal} are the
 * same.
 */
public enum Operator {
	EQUAL("equal"),
	NOT_EQUAL("notEqual"),
	IN("in"),
	NOT_IN("notIn"),
	GREATER_THAN("greaterThan"),
	GREATER_OR_EQUAL("greaterOrEqual"),
	LESS_THAN("lessThan"),
	LESS_OR_EQUAL("lessOrEqual"),
	IS_NULL("isNull"),
	MATCHES("matches");

	private static final Map<String, Operator> BY_KEY = new TreeMap<>();
	static {
		for (Operator operator : values()) {
			BY_KEY.put(operator.key.toLowerCase(Locale.ROOT), operator);
		}
	}

	private final String key;

	Operator(String key) {
		this.key = key;
	}

	/** The name used in YAML. */
	public String getKey() {
		return key;
	}

	/** Returns the operator for a YAML key, or {@code null} when the key is not an operator. */
	public static Operator fromKey(String key) {
		return key == null ? null : BY_KEY.get(key.toLowerCase(Locale.ROOT));
	}

	static String keys() {
		StringBuilder builder = new StringBuilder();
		for (Operator operator : values()) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(operator.key);
		}
		return builder.toString();
	}
}
