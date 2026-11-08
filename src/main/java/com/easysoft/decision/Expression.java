package com.easysoft.decision;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The parsed form of a decision's {@code when} text: a small tree of {@code and}, {@code or},
 * {@code not} and references to other rules. Evaluation short-circuits.
 * <p>
 * Grammar (keywords are case-insensitive; {@code &&}, {@code ||} and {@code !} also work):
 *
 * <pre>
 * or    := and ( 'or' and )*
 * and   := unary ( 'and' unary )*
 * unary := 'not' unary | '(' or ')' | name
 * </pre>
 *
 * A name refers to a condition or decision in the same rule set, or to one in another, already
 * loaded rule set as {@code ruleSet/name}.
 */
public abstract class Expression {

	abstract boolean evaluate(Object root);

	abstract void collect(List<Reference> references);

	/** Every rule this expression refers to, in order of appearance. */
	public List<String> references() {
		List<Reference> references = new ArrayList<>();
		collect(references);
		List<String> names = new ArrayList<>(references.size());
		references.forEach(r -> names.add(r.name));
		return names;
	}

	List<Reference> referenceNodes() {
		List<Reference> references = new ArrayList<>();
		collect(references);
		return references;
	}

	/** Parses {@code text}; {@code owner} labels error messages, e.g. {@code decision 'x'}. */
	public static Expression parse(String text, String owner) {
		if (text == null || text.isBlank()) {
			throw new DecisionException(owner + ": 'when' is empty");
		}
		Parser parser = new Parser(text, owner);
		Expression expression = parser.parseOr();
		if (parser.token != null) {
			throw parser.error("unexpected '" + parser.token.text + "'");
		}
		return expression;
	}

	static final class Reference extends Expression {
		final String name;
		Rule target;

		Reference(String name) {
			this.name = name;
		}

		void bind(Function<String, Rule> resolver) {
			target = resolver.apply(name);
		}

		@Override
		boolean evaluate(Object root) {
			if (target == null) {
				throw new DecisionException("reference '" + name + "' is not resolved");
			}
			return target.evaluate(root);
		}

		@Override
		void collect(List<Reference> references) {
			references.add(this);
		}

		@Override
		public String toString() {
			return name;
		}
	}

	static final class Not extends Expression {
		final Expression operand;

		Not(Expression operand) {
			this.operand = operand;
		}

		@Override
		boolean evaluate(Object root) {
			return !operand.evaluate(root);
		}

		@Override
		void collect(List<Reference> references) {
			operand.collect(references);
		}

		@Override
		public String toString() {
			return "not " + operand;
		}
	}

	static final class And extends Expression {
		final List<Expression> operands;

		And(List<Expression> operands) {
			this.operands = List.copyOf(operands);
		}

		@Override
		boolean evaluate(Object root) {
			for (Expression operand : operands) {
				if (!operand.evaluate(root)) {
					return false;
				}
			}
			return true;
		}

		@Override
		void collect(List<Reference> references) {
			operands.forEach(o -> o.collect(references));
		}

		@Override
		public String toString() {
			return join(" and ");
		}

		private String join(String separator) {
			StringBuilder builder = new StringBuilder("(");
			for (int i = 0; i < operands.size(); i++) {
				builder.append(i == 0 ? "" : separator).append(operands.get(i));
			}
			return builder.append(')').toString();
		}
	}

	static final class Or extends Expression {
		final List<Expression> operands;

		Or(List<Expression> operands) {
			this.operands = List.copyOf(operands);
		}

		@Override
		boolean evaluate(Object root) {
			for (Expression operand : operands) {
				if (operand.evaluate(root)) {
					return true;
				}
			}
			return false;
		}

		@Override
		void collect(List<Reference> references) {
			operands.forEach(o -> o.collect(references));
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder("(");
			for (int i = 0; i < operands.size(); i++) {
				builder.append(i == 0 ? "" : " or ").append(operands.get(i));
			}
			return builder.append(')').toString();
		}
	}

	private enum Kind {
		AND, OR, NOT, OPEN, CLOSE, NAME
	}

	private record Token(Kind kind, String text, int position) {
	}

	private static final class Parser {
		private final String text;
		private final String owner;
		private int position;
		Token token;

		Parser(String text, String owner) {
			this.text = text;
			this.owner = owner;
			advance();
		}

		Expression parseOr() {
			List<Expression> operands = new ArrayList<>();
			operands.add(parseAnd());
			while (token != null && token.kind == Kind.OR) {
				advance();
				operands.add(parseAnd());
			}
			return operands.size() == 1 ? operands.get(0) : new Or(operands);
		}

		private Expression parseAnd() {
			List<Expression> operands = new ArrayList<>();
			operands.add(parseUnary());
			while (token != null && token.kind == Kind.AND) {
				advance();
				operands.add(parseUnary());
			}
			return operands.size() == 1 ? operands.get(0) : new And(operands);
		}

		private Expression parseUnary() {
			if (token == null) {
				throw error("expression ends too early");
			}
			switch (token.kind) {
			case NOT -> {
				advance();
				return new Not(parseUnary());
			}
			case OPEN -> {
				advance();
				Expression inner = parseOr();
				if (token == null || token.kind != Kind.CLOSE) {
					throw error("missing ')'");
				}
				advance();
				return inner;
			}
			case NAME -> {
				Reference reference = new Reference(token.text);
				advance();
				return reference;
			}
			default -> throw error("unexpected '" + token.text + "'");
			}
		}

		private void advance() {
			while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
				position++;
			}
			if (position >= text.length()) {
				token = null;
				return;
			}
			int start = position;
			char c = text.charAt(position);
			if (c == '(') {
				position++;
				token = new Token(Kind.OPEN, "(", start);
			} else if (c == ')') {
				position++;
				token = new Token(Kind.CLOSE, ")", start);
			} else if (c == '!') {
				position++;
				token = new Token(Kind.NOT, "!", start);
			} else if (text.startsWith("&&", position)) {
				position += 2;
				token = new Token(Kind.AND, "&&", start);
			} else if (text.startsWith("||", position)) {
				position += 2;
				token = new Token(Kind.OR, "||", start);
			} else if (isNameChar(c)) {
				while (position < text.length() && isNameChar(text.charAt(position))) {
					position++;
				}
				String word = text.substring(start, position);
				Kind kind = switch (word.toLowerCase(Locale.ROOT)) {
				case "and" -> Kind.AND;
				case "or" -> Kind.OR;
				case "not" -> Kind.NOT;
				default -> Kind.NAME;
				};
				token = new Token(kind, word, start);
			} else {
				throw new DecisionException(owner + ": unexpected character '" + c + "' at position " + (start + 1) + " in '" + text + "'");
			}
		}

		DecisionException error(String message) {
			String where = token == null ? "at the end" : "at position " + (token.position + 1);
			return new DecisionException(owner + ": " + message + " " + where + " in '" + text + "'");
		}
	}

	static boolean isNameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-' || c == '/' || c == '$';
	}
}
