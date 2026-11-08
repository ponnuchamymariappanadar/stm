package com.easysoft.decision;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;

/**
 * Compares a bean value with a value written in the rule file. YAML gives strings, numbers and
 * booleans, so comparison is by meaning rather than by Java type:
 * <ul>
 * <li>numbers compare numerically whatever their type ({@code 100}, {@code 100L}, {@code 100.0},
 * {@code "100"});</li>
 * <li>enums compare by {@code name()}, booleans with {@code true}/{@code false};</li>
 * <li>for ordering, a {@link Comparable} bean value such as {@code LocalDate} is compared with
 * the configured text converted by the type's static {@code parse} or {@code valueOf};</li>
 * <li>anything else compares by {@code equals}, then by {@code toString()}.</li>
 * </ul>
 */
final class Values {

	private Values() {
	}

	static boolean isEqual(Object actual, Object expected) {
		if (actual == null || expected == null) {
			return actual == expected;
		}
		if (actual.equals(expected)) {
			return true;
		}
		if (actual instanceof Number || expected instanceof Number) {
			BigDecimal a = toDecimal(actual);
			BigDecimal e = toDecimal(expected);
			if (a != null && e != null) {
				return a.compareTo(e) == 0;
			}
		}
		if (actual instanceof Enum<?> enumValue) {
			return enumValue.name().equals(expected.toString());
		}
		if (actual instanceof Boolean || expected instanceof Boolean) {
			return actual.toString().equalsIgnoreCase(expected.toString());
		}
		return actual.toString().equals(expected.toString());
	}

	/**
	 * Returns a negative number, zero or a positive number as {@code actual} is less than, equal to
	 * or greater than {@code expected}.
	 */
	@SuppressWarnings({ "unchecked" })
	static int compare(Object actual, Object expected, String path) {
		if (actual instanceof Number || actual instanceof CharSequence && expected instanceof Number) {
			BigDecimal a = toDecimal(actual);
			BigDecimal e = toDecimal(expected);
			if (a != null && e != null) {
				return a.compareTo(e);
			}
		}
		if (actual instanceof Comparable comparable) {
			if (actual.getClass().isInstance(expected)) {
				return comparable.compareTo(expected);
			}
			if (actual instanceof CharSequence || actual instanceof Enum<?>) {
				return actual.toString().compareTo(expected.toString());
			}
			Object converted = convert(expected.toString(), actual.getClass());
			if (converted != null) {
				return comparable.compareTo(converted);
			}
		}
		throw new DecisionException("path '" + path + "': cannot compare " + actual.getClass().getName() + " value '" + actual + "' with '" + expected + "'");
	}

	/** How a value is shown in an {@link Explanation}: text in quotes, everything else as is. */
	static String describe(Object value) {
		if (value == null) {
			return "null";
		}
		if (value instanceof CharSequence) {
			return "'" + value + "'";
		}
		if (value instanceof Collection<?> collection) {
			StringBuilder builder = new StringBuilder("[");
			for (Object item : collection) {
				builder.append(builder.length() > 1 ? ", " : "").append(describe(item));
			}
			return builder.append(']').toString();
		}
		return value.toString();
	}

	/**
	 * Checks a configured value against the declared type of the property it is compared with.
	 * Returns the problem, or {@code null} when the value fits.
	 */
	static String checkValue(Class<?> declared, Object value, String path, Operator operator) {
		Class<?> type = declared.isPrimitive() ? box(declared) : declared;
		String where = "path '" + path + "' (" + declared.getSimpleName() + ")";
		boolean ordering = operator == Operator.GREATER_THAN || operator == Operator.GREATER_OR_EQUAL || operator == Operator.LESS_THAN
				|| operator == Operator.LESS_OR_EQUAL;
		if (value == null) {
			return declared.isPrimitive() ? where + " is never null" : null;
		}
		if (type.isEnum()) {
			if (ordering) {
				return where + " is an enum and cannot be compared with " + operator.getKey();
			}
			for (Object constant : type.getEnumConstants()) {
				if (((Enum<?>) constant).name().equals(value.toString())) {
					return null;
				}
			}
			StringBuilder names = new StringBuilder();
			for (Object constant : type.getEnumConstants()) {
				names.append(names.length() > 0 ? ", " : "").append(((Enum<?>) constant).name());
			}
			return where + ": '" + value + "' is not one of " + names;
		}
		if (type == Boolean.class) {
			if (ordering) {
				return where + " is a boolean and cannot be compared with " + operator.getKey();
			}
			return value instanceof Boolean || "true".equalsIgnoreCase(value.toString()) || "false".equalsIgnoreCase(value.toString()) ? null
					: where + ": '" + value + "' is not true or false";
		}
		if (Number.class.isAssignableFrom(type)) {
			return toDecimal(value) != null ? null : where + ": '" + value + "' is not a number";
		}
		if (type == Character.class || CharSequence.class.isAssignableFrom(type)) {
			return null;
		}
		if (ordering) {
			if (!Comparable.class.isAssignableFrom(type)) {
				return where + " is not Comparable and cannot be compared with " + operator.getKey();
			}
			if (!type.isInstance(value)) {
				try {
					if (convert(value.toString(), type) == null) {
						return where + ": no static parse or valueOf to turn '" + value + "' into a " + type.getSimpleName();
					}
				} catch (DecisionException e) {
					return where + ": '" + value + "' is not a valid " + type.getSimpleName();
				}
			}
		}
		return null;
	}

	private static Class<?> box(Class<?> primitive) {
		if (primitive == boolean.class) {
			return Boolean.class;
		}
		if (primitive == char.class) {
			return Character.class;
		}
		if (primitive == void.class) {
			return Void.class;
		}
		return Number.class; // int, long, double, float, short, byte
	}

	static BigDecimal toDecimal(Object value) {
		if (value instanceof BigDecimal decimal) {
			return decimal;
		}
		if (value instanceof BigInteger integer) {
			return new BigDecimal(integer);
		}
		if (value instanceof Double || value instanceof Float) {
			double d = ((Number) value).doubleValue();
			return Double.isNaN(d) || Double.isInfinite(d) ? null : new BigDecimal(value.toString());
		}
		if (value instanceof Number number) {
			try {
				return new BigDecimal(number.toString());
			} catch (NumberFormatException e) {
				return BigDecimal.valueOf(number.doubleValue());
			}
		}
		if (value instanceof CharSequence text) {
			try {
				return new BigDecimal(text.toString().trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	/** Converts configured text to {@code type} with its static {@code parse(CharSequence)}, {@code parse(String)} or {@code valueOf(String)}. */
	private static Object convert(String text, Class<?> type) {
		for (String name : new String[] { "parse", "valueOf" }) {
			for (Class<?> argType : new Class<?>[] { CharSequence.class, String.class }) {
				try {
					Method method = type.getMethod(name, argType);
					if (Modifier.isStatic(method.getModifiers()) && type.isAssignableFrom(method.getReturnType())) {
						return method.invoke(null, text);
					}
				} catch (NoSuchMethodException e) {
					// try the next factory
				} catch (ReflectiveOperationException | RuntimeException e) {
					throw new DecisionException("cannot convert '" + text + "' to " + type.getName(), e);
				}
			}
		}
		return null;
	}
}
