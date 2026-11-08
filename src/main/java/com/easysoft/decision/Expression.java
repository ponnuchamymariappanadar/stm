package com.easysoft.decision;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The parsed form of a {@code when} text: a small tree of {@code and}, {@code or}, {@code not},
 * references to named rules and inline comparisons. Evaluation short-circuits.
 * <p>
 * Grammar (keywords are case-insensitive; {@code &&}, {@code ||} and {@code !} also work):
 *
 * <pre>
 * or         := and ( 'or' and )*
 * and        := unary ( 'and' unary )*
 * unary      := 'not' unary | '(' or ')' | comparison | name
 * comparison := path ( '==' | '!=' | '&gt;' | '&gt;=' | '&lt;' | '&lt;=' | 'matches' ) value
 *             | path ( 'in' | 'not' 'in' ) '[' value ( ',' value )* ']'
 *             | path 'is' [ 'not' ] 'null'
 * value      := number | true | false | null | 'quoted text' | "quoted text" | word
 * </pre>
 *
 * A name refers to a condition or decision in the same rule set, or to one in another, already
 * loaded rule set as {@code ruleSet/name}. A path is a bean property path as in
 * {@link PropertyPath}, such as {@code customer.address.country} or {@code tags[0]}. An unquoted
 * word on the right of a comparison is text, so {@code customer.status == BLOCKED} compares with
 * the enum constant {@code BLOCKED}; quote text that has spaces or other punctuation.
 */
public abstract class Expression {

	abstract boolean evaluate(Object root);

	/** Evaluates like {@link #evaluate(Object)} and records why. */
	abstract Explanation explain(Object root);

	abstract void collect(List<Reference> references, List<Condition> comparisons);

	/** The names of every rule this expression refers to, in order of appearance. */
	public List<String> references() {
		List<String> names = new ArrayList<>();
		referenceNodes().forEach(r -> names.add(r.name));
		return names;
	}

	List<Reference> referenceNodes() {
		List<Reference> references = new ArrayList<>();
		collect(references, new ArrayList<>());
		return references;
	}

	/** The inline comparisons in this expression, in order of appearance. */
	List<Condition> comparisons() {
		List<Condition> comparisons = new ArrayList<>();
		collect(new ArrayList<>(), comparisons);
		return comparisons;
	}

	/** Parses {@code text}; {@code owner} labels error messages, e.g. {@code decision 'x'}. */
	public static Expression parse(String text, String owner) {
		return parse(text, owner, null);
	}

	/** Parses {@code text}; inline comparisons belong to {@code ruleSet}. */
	public static Expression parse(String text, String owner, String ruleSet) {
		if (text == null || text.isBlank()) {
			throw new DecisionException(owner + ": 'when' is empty");
		}
		Parser parser = new Parser(text, owner, ruleSet);
		Expression expression = parser.parseOr();
		if (parser.peek() != null) {
			throw parser.error("unexpected '" + parser.peek().text + "'", parser.peek());
		}
		return expression;
	}

	// ---- nodes ---------------------------------------------------------------------------------

	static final class Reference extends Expression {
		final String name;
		Rule target;

		Reference(String name) {
			this.name = name;
		}

		void bind(Function<String, Rule> resolver) {
			target = resolver.apply(name);
		}

		private Rule target() {
			if (target == null) {
				throw new DecisionException("reference '" + name + "' is not resolved");
			}
			return target;
		}

		@Override
		boolean evaluate(Object root) {
			return target().evaluate(root);
		}

		@Override
		Explanation explain(Object root) {
			Rule rule = target();
			if (rule instanceof Condition condition) {
				return condition.explain(root, name);
			}
			Explanation inner = ((Decision) rule).getExpression().explain(root);
			return Explanation.of(name, inner.getValue(), List.of(inner));
		}

		@Override
		void collect(List<Reference> references, List<Condition> comparisons) {
			references.add(this);
		}

		@Override
		public String toString() {
			return name;
		}
	}

	/** An inline comparison such as {@code amount <= 10000}. */
	static final class Comparison extends Expression {
		final Condition condition;

		Comparison(Condition condition) {
			this.condition = condition;
		}

		@Override
		boolean evaluate(Object root) {
			return condition.evaluate(root);
		}

		@Override
		Explanation explain(Object root) {
			return condition.explain(root, condition.getName());
		}

		@Override
		void collect(List<Reference> references, List<Condition> comparisons) {
			comparisons.add(condition);
		}

		@Override
		public String toString() {
			return condition.getName();
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
		Explanation explain(Object root) {
			Explanation inner = operand.explain(root);
			return Explanation.of("not", !inner.getValue(), List.of(inner));
		}

		@Override
		void collect(List<Reference> references, List<Condition> comparisons) {
			operand.collect(references, comparisons);
		}

		@Override
		public String toString() {
			return "not " + operand;
		}
	}

	/** {@code and} ({@code all = true}) or {@code or} ({@code all = false}). */
	static final class Junction extends Expression {
		final boolean all;
		final List<Expression> operands;

		Junction(boolean all, List<Expression> operands) {
			this.all = all;
			this.operands = List.copyOf(operands);
		}

		@Override
		boolean evaluate(Object root) {
			for (Expression operand : operands) {
				if (operand.evaluate(root) != all) {
					return !all;
				}
			}
			return all;
		}

		@Override
		Explanation explain(Object root) {
			List<Explanation> children = new ArrayList<>();
			Boolean decided = null;
			for (Expression operand : operands) {
				if (decided != null) {
					children.add(Explanation.skipped(operand.toString(), List.of()));
					continue;
				}
				Explanation child = operand.explain(root);
				children.add(child);
				if (child.getValue() != all) {
					decided = !all;
				}
			}
			return Explanation.of(all ? "and" : "or", decided != null ? decided : all, children);
		}

		@Override
		void collect(List<Reference> references, List<Condition> comparisons) {
			operands.forEach(o -> o.collect(references, comparisons));
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder("(");
			for (int i = 0; i < operands.size(); i++) {
				builder.append(i == 0 ? "" : all ? " and " : " or ").append(operands.get(i));
			}
			return builder.append(')').toString();
		}
	}

	// ---- parser --------------------------------------------------------------------------------

	private enum Kind {
		AND, OR, NOT, OPEN, CLOSE, LIST_OPEN, LIST_CLOSE, COMMA, OPERATOR, WORD, QUOTED
	}

	private record Token(Kind kind, String text, int position, int end) {
		boolean isWord(String word) {
			return kind == Kind.WORD && text.equalsIgnoreCase(word);
		}
	}

	private static final class Parser {
		private final String text;
		private final String owner;
		private final String ruleSet;
		private final List<Token> tokens;
		private int index;

		Parser(String text, String owner, String ruleSet) {
			this.text = text;
			this.owner = owner;
			this.ruleSet = ruleSet;
			this.tokens = tokenize();
		}

		Token peek() {
			return peek(0);
		}

		private Token peek(int ahead) {
			return index + ahead < tokens.size() ? tokens.get(index + ahead) : null;
		}

		private Token next() {
			return tokens.get(index++);
		}

		Expression parseOr() {
			List<Expression> operands = new ArrayList<>();
			operands.add(parseAnd());
			while (peek() != null && peek().kind == Kind.OR) {
				next();
				operands.add(parseAnd());
			}
			return operands.size() == 1 ? operands.get(0) : new Junction(false, operands);
		}

		private Expression parseAnd() {
			List<Expression> operands = new ArrayList<>();
			operands.add(parseUnary());
			while (peek() != null && peek().kind == Kind.AND) {
				next();
				operands.add(parseUnary());
			}
			return operands.size() == 1 ? operands.get(0) : new Junction(true, operands);
		}

		private Expression parseUnary() {
			Token token = peek();
			if (token == null) {
				throw error("expression ends too early", null);
			}
			switch (token.kind) {
			case NOT -> {
				next();
				return new Not(parseUnary());
			}
			case OPEN -> {
				next();
				Expression inner = parseOr();
				if (peek() == null || peek().kind != Kind.CLOSE) {
					throw error("missing ')'", peek());
				}
				next();
				return inner;
			}
			case WORD -> {
				next();
				if (startsComparison()) {
					return parseComparison(token);
				}
				return new Reference(token.text);
			}
			default -> throw error("unexpected '" + token.text + "'", token);
			}
		}

		/** After a word: does a comparison operator follow? */
		private boolean startsComparison() {
			Token token = peek();
			if (token == null) {
				return false;
			}
			return token.kind == Kind.OPERATOR || token.isWord("in") || token.isWord("is") || token.isWord("matches")
					|| token.kind == Kind.NOT && peek(1) != null && peek(1).isWord("in");
		}

		private Expression parseComparison(Token pathToken) {
			String path = pathToken.text;
			if (path.contains("/")) {
				throw error("'" + path + "' is a rule reference and cannot be compared; compare a property path such as customer.status", pathToken);
			}
			Token operatorToken = next();
			Operator operator;
			Object value;
			int end;
			if (operatorToken.kind == Kind.NOT || operatorToken.isWord("in")) {
				if (operatorToken.kind == Kind.NOT) {
					next(); // 'in'
				}
				operator = operatorToken.kind == Kind.NOT ? Operator.NOT_IN : Operator.IN;
				Token open = peek();
				if (open == null || open.kind != Kind.LIST_OPEN && open.kind != Kind.OPEN) {
					throw error("expected '[' after " + (operator == Operator.IN ? "in" : "not in"), open);
				}
				next();
				Kind close = open.kind == Kind.LIST_OPEN ? Kind.LIST_CLOSE : Kind.CLOSE;
				List<Object> values = new ArrayList<>();
				values.add(literal(nextValue()));
				while (peek() != null && peek().kind == Kind.COMMA) {
					next();
					values.add(literal(nextValue()));
				}
				if (peek() == null || peek().kind != close) {
					throw error("expected ',' or '" + (close == Kind.LIST_CLOSE ? "]" : ")") + "' in the list", peek());
				}
				end = next().end;
				value = values;
			} else if (operatorToken.isWord("is")) {
				boolean negated = peek() != null && peek().kind == Kind.NOT;
				if (negated) {
					next();
				}
				Token nullToken = peek();
				if (nullToken == null || !nullToken.isWord("null")) {
					throw error("expected 'null' after 'is" + (negated ? " not" : "") + "'", nullToken);
				}
				end = next().end;
				operator = Operator.IS_NULL;
				value = !negated;
			} else {
				operator = operatorToken.isWord("matches") ? Operator.MATCHES : switch (operatorToken.text) {
				case "==" -> Operator.EQUAL;
				case "!=" -> Operator.NOT_EQUAL;
				case ">" -> Operator.GREATER_THAN;
				case ">=" -> Operator.GREATER_OR_EQUAL;
				case "<" -> Operator.LESS_THAN;
				case "<=" -> Operator.LESS_OR_EQUAL;
				default -> throw error("unknown operator '" + operatorToken.text + "'", operatorToken);
				};
				Token valueToken = nextValue();
				value = literal(valueToken);
				end = valueToken.end;
			}
			String source = text.substring(pathToken.position, end).trim();
			try {
				return new Comparison(Condition.inline(ruleSet, source, path, operator, value));
			} catch (DecisionException e) {
				throw new DecisionException(owner + ": '" + source + "': " + e.getMessage().replaceFirst("^condition '.*?': ", ""), e);
			}
		}

		private Token nextValue() {
			Token token = peek();
			if (token == null) {
				throw error("expected a value", null);
			}
			if (token.kind != Kind.WORD && token.kind != Kind.QUOTED) {
				throw error("expected a value, found '" + token.text + "'", token);
			}
			return next();
		}

		/** Unquoted: true, false, null and numbers are typed; anything else is text. */
		private static Object literal(Token token) {
			if (token.kind == Kind.QUOTED) {
				return token.text;
			}
			String word = token.text;
			switch (word.toLowerCase(Locale.ROOT)) {
			case "true":
				return Boolean.TRUE;
			case "false":
				return Boolean.FALSE;
			case "null":
				return null;
			default:
				break;
			}
			if (!word.isEmpty() && (Character.isDigit(word.charAt(0)) || word.length() > 1 && (word.charAt(0) == '-' || word.charAt(0) == '+'))) {
				try {
					BigDecimal number = new BigDecimal(word);
					try {
						return number.longValueExact();
					} catch (ArithmeticException e) {
						return number;
					}
				} catch (NumberFormatException e) {
					// e.g. 2020-01-01: text
				}
			}
			return word;
		}

		private List<Token> tokenize() {
			List<Token> list = new ArrayList<>();
			int position = 0;
			while (true) {
				while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
					position++;
				}
				if (position >= text.length()) {
					return list;
				}
				int start = position;
				char c = text.charAt(position);
				String two = position + 1 < text.length() ? text.substring(position, position + 2) : "";
				switch (two) {
				case "&&" -> {
					list.add(new Token(Kind.AND, two, start, start + 2));
					position += 2;
					continue;
				}
				case "||" -> {
					list.add(new Token(Kind.OR, two, start, start + 2));
					position += 2;
					continue;
				}
				case "==", "!=", ">=", "<=" -> {
					list.add(new Token(Kind.OPERATOR, two, start, start + 2));
					position += 2;
					continue;
				}
				default -> {
				}
				}
				switch (c) {
				case '(' -> list.add(new Token(Kind.OPEN, "(", start, start + 1));
				case ')' -> list.add(new Token(Kind.CLOSE, ")", start, start + 1));
				case '[' -> list.add(new Token(Kind.LIST_OPEN, "[", start, start + 1));
				case ']' -> list.add(new Token(Kind.LIST_CLOSE, "]", start, start + 1));
				case ',' -> list.add(new Token(Kind.COMMA, ",", start, start + 1));
				case '!' -> list.add(new Token(Kind.NOT, "!", start, start + 1));
				case '>', '<' -> list.add(new Token(Kind.OPERATOR, String.valueOf(c), start, start + 1));
				case '=' -> throw new DecisionException(owner + ": use '==' to compare, not '=', at position " + (start + 1) + " in '" + text + "'");
				default -> {
				}
				}
				if ("()[],!<>".indexOf(c) >= 0) {
					position++;
					continue;
				}
				if (c == '\'' || c == '"') {
					StringBuilder value = new StringBuilder();
					position++;
					while (position < text.length() && text.charAt(position) != c) {
						char ch = text.charAt(position);
						if (ch == '\\' && position + 1 < text.length()) {
							position++;
							ch = text.charAt(position);
						}
						value.append(ch);
						position++;
					}
					if (position >= text.length()) {
						throw new DecisionException(owner + ": unclosed quote starting at position " + (start + 1) + " in '" + text + "'");
					}
					position++;
					list.add(new Token(Kind.QUOTED, value.toString(), start, position));
					continue;
				}
				if (isNameChar(c) || c == '+') {
					position++;
					while (position < text.length()) {
						char ch = text.charAt(position);
						if (ch == '[') {
							// a path index directly after a name: tags[0], attributes[risk]
							int close = text.indexOf(']', position);
							if (close < 0) {
								throw new DecisionException(owner + ": '[' without ']' at position " + (position + 1) + " in '" + text + "'");
							}
							position = close + 1;
						} else if (isNameChar(ch)) {
							position++;
						} else {
							break;
						}
					}
					String word = text.substring(start, position);
					Kind kind = switch (word.toLowerCase(Locale.ROOT)) {
					case "and" -> Kind.AND;
					case "or" -> Kind.OR;
					case "not" -> Kind.NOT;
					default -> Kind.WORD;
					};
					list.add(new Token(kind, word, start, position));
					continue;
				}
				throw new DecisionException(owner + ": unexpected character '" + c + "' at position " + (start + 1) + " in '" + text + "'");
			}
		}

		DecisionException error(String message, Token token) {
			String where = token == null ? "at the end" : "at position " + (token.position + 1);
			return new DecisionException(owner + ": " + message + " " + where + " in '" + text + "'");
		}
	}

	static boolean isNameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-' || c == '/' || c == '$';
	}
}
