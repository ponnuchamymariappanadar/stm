package com.easysoft.decision;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A compiled bean property path such as {@code customer.address.country}, {@code items[0].sku} or
 * {@code attributes[color]}.
 * <ul>
 * <li>{@code name} reads a getter ({@code getName()}, {@code isName()}), a record-style accessor
 * ({@code name()}) or a public field; on a {@link Map} it reads the key {@code name}.</li>
 * <li>{@code [n]} reads an element of a {@link List} or array; {@code [key]} reads a map key.</li>
 * </ul>
 * A {@code null} anywhere along the path, a missing map key or an index out of range resolves to
 * {@code null}. A property that does not exist on the bean's class is an error, so a typo in the
 * rule file is reported instead of silently evaluating to {@code false}.
 */
public final class PropertyPath {

	private static final Map<Class<?>, Map<String, Function<Object, Object>>> ACCESSORS = new ConcurrentHashMap<>();

	private final String expression;
	private final List<Step> steps;

	private PropertyPath(String expression, List<Step> steps) {
		this.expression = expression;
		this.steps = steps;
	}

	public static PropertyPath parse(String expression) {
		if (expression == null || expression.isBlank()) {
			throw new DecisionException("path is empty");
		}
		String path = expression.trim();
		List<Step> steps = new ArrayList<>();
		StringBuilder name = new StringBuilder();
		int i = 0;
		while (i < path.length()) {
			char c = path.charAt(i);
			if (c == '.') {
				addProperty(steps, name, path);
				i++;
			} else if (c == '[') {
				if (name.length() > 0) {
					addProperty(steps, name, path);
				} else if (steps.isEmpty()) {
					throw new DecisionException("path '" + path + "' must start with a property name");
				}
				int end = path.indexOf(']', i);
				if (end < 0) {
					throw new DecisionException("path '" + path + "' has '[' without ']'");
				}
				String key = path.substring(i + 1, end).trim();
				if (key.isEmpty()) {
					throw new DecisionException("path '" + path + "' has an empty []");
				}
				steps.add(new Step(key, true));
				i = end + 1;
				if (i < path.length() && path.charAt(i) != '.' && path.charAt(i) != '[') {
					throw new DecisionException("path '" + path + "' expects '.' or '[' after ']'");
				}
				if (i < path.length() && path.charAt(i) == '.') {
					i++;
					if (i == path.length()) {
						throw new DecisionException("path '" + path + "' ends with '.'");
					}
				}
			} else {
				if (Character.isWhitespace(c)) {
					throw new DecisionException("path '" + path + "' contains whitespace");
				}
				name.append(c);
				i++;
			}
		}
		if (name.length() > 0) {
			addProperty(steps, name, path);
		} else if (path.endsWith(".")) {
			throw new DecisionException("path '" + path + "' ends with '.'");
		}
		return new PropertyPath(path, List.copyOf(steps));
	}

	private static void addProperty(List<Step> steps, StringBuilder name, String path) {
		if (name.length() == 0) {
			throw new DecisionException("path '" + path + "' has an empty property name");
		}
		steps.add(new Step(name.toString(), false));
		name.setLength(0);
	}

	/** Reads the value at this path from {@code root}. */
	public Object resolve(Object root) {
		Object current = root;
		for (Step step : steps) {
			if (current == null) {
				return null;
			}
			current = step.index ? index(current, step.name) : property(current, step.name);
		}
		return current;
	}

	@Override
	public String toString() {
		return expression;
	}

	private Object index(Object target, String key) {
		if (target instanceof Map<?, ?> map) {
			return mapGet(map, key);
		}
		int position;
		try {
			position = Integer.parseInt(key);
		} catch (NumberFormatException e) {
			throw new DecisionException("path '" + expression + "': [" + key + "] needs a number for " + target.getClass().getName());
		}
		if (target instanceof List<?> list) {
			return position >= 0 && position < list.size() ? list.get(position) : null;
		}
		if (target.getClass().isArray()) {
			return position >= 0 && position < Array.getLength(target) ? Array.get(target, position) : null;
		}
		throw new DecisionException("path '" + expression + "': [" + key + "] used on " + target.getClass().getName() + ", which is not a list, array or map");
	}

	private static Object mapGet(Map<?, ?> map, String key) {
		Object value = map.get(key);
		if (value != null || map.containsKey(key)) {
			return value;
		}
		// map keyed by numbers: attributes[1]
		try {
			long number = Long.parseLong(key);
			value = map.get(Integer.valueOf((int) number));
			return value != null ? value : map.get(Long.valueOf(number));
		} catch (NumberFormatException | ClassCastException e) {
			return null;
		}
	}

	private Object property(Object target, String name) {
		if (target instanceof Map<?, ?> map) {
			return mapGet(map, name);
		}
		Function<Object, Object> accessor = ACCESSORS.computeIfAbsent(target.getClass(), c -> new ConcurrentHashMap<>())
				.computeIfAbsent(name, n -> findAccessor(target.getClass(), n));
		return accessor.apply(target);
	}

	private Function<Object, Object> findAccessor(Class<?> type, String name) {
		Member member = findMember(type, name);
		if (member instanceof Method getter) {
			getter.trySetAccessible();
			return bean -> {
				try {
					return getter.invoke(bean);
				} catch (ReflectiveOperationException e) {
					Throwable cause = e.getCause() != null ? e.getCause() : e;
					throw new DecisionException("path '" + expression + "': reading " + getter.getName() + "() failed on " + type.getName(), cause);
				}
			};
		}
		if (member instanceof Field field) {
			field.trySetAccessible();
			return bean -> {
				try {
					return field.get(bean);
				} catch (IllegalAccessException e) {
					throw new DecisionException("path '" + expression + "': reading field " + name + " failed on " + type.getName(), e);
				}
			};
		}
		throw noProperty(type, name);
	}

	private DecisionException noProperty(Class<?> type, String name) {
		String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
		return new DecisionException("path '" + expression + "': " + type.getName() + " has no property '" + name + "' (looked for get" + suffix + "(), is" + suffix
				+ "(), " + name + "() and a public field)");
	}

	/** The getter ({@code getX()}, boolean {@code isX()}, {@code x()}) or public field for {@code name}, or {@code null}. */
	private static Member findMember(Class<?> type, String name) {
		String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
		Method method = publicNoArgMethod(type, "get" + suffix);
		if (method == null) {
			method = publicNoArgMethod(type, "is" + suffix);
			if (method != null && method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
				method = null;
			}
		}
		if (method == null) {
			method = publicNoArgMethod(type, name);
		}
		if (method != null) {
			return method;
		}
		try {
			Field field = type.getField(name);
			return Modifier.isStatic(field.getModifiers()) ? null : field;
		} catch (NoSuchFieldException e) {
			return null;
		}
	}

	/**
	 * Follows this path through declared types, starting at {@code rootType}, and returns the type
	 * of the value it reads, or {@code null} when that cannot be known before running (a property
	 * declared as {@code Object}, an interface or abstract class without the property, a raw
	 * collection). Throws {@link DecisionException} when the path cannot work, for example a
	 * property that the class does not have.
	 */
	public Class<?> resolveType(Class<?> rootType) {
		Type current = rootType;
		for (Step step : steps) {
			Class<?> raw = rawClass(current);
			if (raw == null || raw == Object.class) {
				return null;
			}
			if (step.index) {
				if (Map.class.isAssignableFrom(raw)) {
					current = typeArgument(current, 1, 2);
				} else if (List.class.isAssignableFrom(raw) || raw.isArray()) {
					try {
						Integer.parseInt(step.name);
					} catch (NumberFormatException e) {
						throw new DecisionException("path '" + expression + "': [" + step.name + "] needs a number for " + raw.getName());
					}
					current = raw.isArray() ? (current instanceof GenericArrayType array ? array.getGenericComponentType() : raw.getComponentType())
							: typeArgument(current, 0, 1);
				} else if (isOpen(raw)) {
					return null;
				} else {
					throw new DecisionException("path '" + expression + "': [" + step.name + "] used on " + raw.getName() + ", which is not a list, array or map");
				}
			} else if (Map.class.isAssignableFrom(raw)) {
				current = typeArgument(current, 1, 2);
			} else {
				Member member = findMember(raw, step.name);
				if (member == null) {
					if (isOpen(raw)) {
						return null;
					}
					throw noProperty(raw, step.name);
				}
				current = member instanceof Method method ? method.getGenericReturnType() : ((Field) member).getGenericType();
			}
			if (current == null) {
				return null;
			}
		}
		Class<?> type = rawClass(current);
		return type == Object.class ? null : type;
	}

	/** An interface or abstract class: a subclass seen at run time may have more properties. */
	private static boolean isOpen(Class<?> type) {
		// primitives report themselves as abstract
		return !type.isPrimitive() && !type.isArray() && (type.isInterface() || Modifier.isAbstract(type.getModifiers()));
	}

	private static Type typeArgument(Type type, int index, int expectedCount) {
		if (type instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length == expectedCount) {
			Type argument = parameterized.getActualTypeArguments()[index];
			if (argument instanceof WildcardType wildcard) {
				return wildcard.getUpperBounds()[0];
			}
			return argument;
		}
		return null;
	}

	private static Class<?> rawClass(Type type) {
		if (type instanceof Class<?> c) {
			return c;
		}
		if (type instanceof ParameterizedType parameterized) {
			return (Class<?>) parameterized.getRawType();
		}
		if (type instanceof GenericArrayType array) {
			Class<?> component = rawClass(array.getGenericComponentType());
			return component == null ? null : Array.newInstance(component, 0).getClass();
		}
		if (type instanceof WildcardType wildcard) {
			return rawClass(wildcard.getUpperBounds()[0]);
		}
		return null;
	}

	private static Method publicNoArgMethod(Class<?> type, String name) {
		try {
			Method method = type.getMethod(name);
			if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() == void.class) {
				return null;
			}
			return method;
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	private record Step(String name, boolean index) {
	}
}
