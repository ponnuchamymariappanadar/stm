package com.easysoft.decision;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.easysoft.decision.DecisionConfig.ConditionConfig;
import com.easysoft.decision.DecisionConfig.DecisionNode;
import com.easysoft.decision.DecisionConfig.OutcomeNode;
import com.easysoft.decision.DecisionConfig.OutcomeRuleNode;
import com.easysoft.tree.Tree;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Loads rule files and looks rules up by key. Conditions and decisions ({@link Rule}s) and
 * {@link Outcome}s are stored in {@link Tree}s keyed {@code ruleSet/name}, so
 * {@code order.rules/fastTrack} is the decision {@code fastTrack} in the rule set
 * {@code order.rules}.
 *
 * <pre>{@code
 * DecisionRegistry.getDefault().load("classpath:decision/order-rules.yaml");
 * boolean fast = DecisionRegistry.getDefault().evaluate("order.rules/fastTrack", orderContext);
 * String shipping = DecisionRegistry.getDefault().decide("order.rules/shipping", orderContext);
 * }</pre>
 *
 * Every reference in a rule set is checked when the file is loaded: an unknown name, a cycle
 * between decisions or a malformed expression fails the load, not the flow. A rule set may refer to
 * rules of a rule set loaded before it as {@code otherSet/name}. Loading is thread-safe; load rule
 * files at startup, before flows run.
 */
public class DecisionRegistry {

	private static final Logger logger = LoggerFactory.getLogger(DecisionRegistry.class);
	private static final String SEPARATOR = "/";
	private static volatile DecisionRegistry defaultRegistry = new DecisionRegistry();

	private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private final Tree<Rule> rules = new Tree<>();
	private final Map<String, Map<String, Rule>> ruleSets = new LinkedHashMap<>();
	private final Tree<Outcome> outcomes = new Tree<>();
	private final Map<String, Map<String, Outcome>> outcomeSets = new LinkedHashMap<>();

	/** The registry used by {@link DecisionActor}'s no-argument constructor. */
	public static DecisionRegistry getDefault() {
		return defaultRegistry;
	}

	/** Replaces the default registry, for example with a fresh one between tests. */
	public static void setDefault(DecisionRegistry registry) {
		if (registry == null) {
			throw new IllegalArgumentException("registry is null");
		}
		defaultRegistry = registry;
	}

	/**
	 * Loads a rule file from {@code classpath:path/file.yaml} or a file-system path. Returns the
	 * names of the rule sets loaded.
	 */
	public List<String> load(String location) {
		return loadYaml(read(location), location);
	}

	/** Loads rule sets from YAML text; {@code source} names it in error messages. */
	public List<String> loadYaml(String yaml, String source) {
		List<DecisionConfig> configs = new ArrayList<>();
		try (MappingIterator<DecisionConfig> iterator = mapper.readerFor(DecisionConfig.class).readValues(yaml)) {
			while (iterator.hasNext()) {
				DecisionConfig config = iterator.next();
				if (config != null) {
					configs.add(config);
				}
			}
		} catch (IOException | RuntimeException e) {
			if (e instanceof DecisionException decisionException) {
				throw decisionException;
			}
			throw new DecisionException(source + ": " + e.getMessage(), e);
		}
		List<String> names = new ArrayList<>();
		for (DecisionConfig config : configs) {
			try {
				names.add(register(config));
			} catch (DecisionException e) {
				throw new DecisionException(source + ": " + e.getMessage(), e);
			}
		}
		return names;
	}

	/** Builds, checks and registers one rule set. Returns its name. */
	public String register(DecisionConfig config) {
		String setName = checkName(config.name, "rule set");
		Map<String, Rule> local = new LinkedHashMap<>();
		List<Decision> decisions = new ArrayList<>();

		for (ConditionConfig node : nullSafe(config.conditions)) {
			String name = checkName(node.name, "condition in rule set '" + setName + "'");
			String label = "condition '" + name + "'";
			if (node.path == null || node.path.isBlank()) {
				throw new DecisionException(label + ": path is missing");
			}
			if (node.operators.size() != 1) {
				throw new DecisionException(label + ": needs exactly one operator (" + Operator.keys() + "), found "
						+ (node.operators.isEmpty() ? "none" : node.operators.keySet()));
			}
			Map.Entry<String, Object> entry = node.operators.entrySet().iterator().next();
			Operator operator = Operator.fromKey(entry.getKey());
			if (operator == null) {
				throw new DecisionException(label + ": unknown operator '" + entry.getKey() + "', expected one of " + Operator.keys());
			}
			Condition condition;
			try {
				condition = new Condition(setName, name, node.path, operator, entry.getValue());
			} catch (DecisionException e) {
				throw e.getMessage().startsWith(label) ? e : new DecisionException(label + ": " + e.getMessage(), e);
			}
			putUnique(local, condition, setName);
		}
		for (DecisionNode node : nullSafe(config.decisions)) {
			String name = checkName(node.name, "decision in rule set '" + setName + "'");
			Decision decision = new Decision(setName, name, node.when);
			putUnique(local, decision, setName);
			decisions.add(decision);
		}
		Map<String, Outcome> localOutcomes = new LinkedHashMap<>();
		for (OutcomeNode node : nullSafe(config.outcomes)) {
			Outcome outcome = buildOutcome(setName, node);
			if (local.containsKey(outcome.getName()) || localOutcomes.putIfAbsent(outcome.getName(), outcome) != null) {
				throw new DecisionException("rule set '" + setName + "': name '" + outcome.getName() + "' is defined more than once");
			}
		}

		lock.writeLock().lock();
		try {
			if (ruleSets.containsKey(setName)) {
				throw new DecisionException("rule set '" + setName + "' is already loaded");
			}
			for (Decision decision : decisions) {
				for (Expression.Reference reference : decision.getExpression().referenceNodes()) {
					reference.bind(ref -> resolve(ref, local, localOutcomes, "decision '" + decision.getName() + "'"));
				}
			}
			for (Outcome outcome : localOutcomes.values()) {
				for (Outcome.Branch branch : outcome.getBranches()) {
					for (Expression.Reference reference : branch.getExpression().referenceNodes()) {
						reference.bind(ref -> resolve(ref, local, localOutcomes, "outcome '" + outcome.getName() + "'"));
					}
				}
			}
			checkCycles(decisions, setName);
			for (Rule rule : local.values()) {
				rules.put(rule.getKey(), rule);
			}
			for (Outcome outcome : localOutcomes.values()) {
				outcomes.put(outcome.getKey(), outcome);
			}
			ruleSets.put(setName, Collections.unmodifiableMap(local));
			outcomeSets.put(setName, Collections.unmodifiableMap(localOutcomes));
		} finally {
			lock.writeLock().unlock();
		}
		logger.info("decision rule set {} loaded: {} conditions, {} decisions, {} outcomes", setName, local.size() - decisions.size(), decisions.size(),
				localOutcomes.size());
		return setName;
	}

	/** Returns the rule for {@code ruleSet/name}, or {@code null}. */
	public Rule find(String key) {
		String trimmed = checkKey(key);
		if (trimmed == null) {
			return null;
		}
		lock.readLock().lock();
		try {
			Rule rule = rules.get(trimmed);
			// the tree matches by prefix; only an exact key counts
			return rule != null && rule.getKey().equals(trimmed) ? rule : null;
		} finally {
			lock.readLock().unlock();
		}
	}

	/** Returns the rule for {@code ruleSet/name}; throws {@link DecisionException} if there is none. */
	public Rule get(String key) {
		Rule rule = find(key);
		if (rule == null && findOutcome(key) != null) {
			throw new DecisionException("'" + key + "' is an outcome, which gives a named result; use decide(), not evaluate()");
		}
		if (rule == null) {
			throw new DecisionException("no condition or decision '" + key + "'; expected ruleSet/name, loaded rule sets: " + getRuleSetNames());
		}
		return rule;
	}

	/** Returns the decision for {@code ruleSet/name}; it can be used directly as a transition condition. */
	public Decision getDecision(String key) {
		Rule rule = get(key);
		if (rule instanceof Decision decision) {
			return decision;
		}
		throw new DecisionException("'" + key + "' is a condition, not a decision");
	}

	/** Evaluates the condition or decision {@code ruleSet/name} against {@code root}. */
	public boolean evaluate(String key, Object root) {
		return get(key).evaluate(root);
	}

	/** Returns the outcome for {@code ruleSet/name}, or {@code null}. */
	public Outcome findOutcome(String key) {
		String trimmed = checkKey(key);
		if (trimmed == null) {
			return null;
		}
		lock.readLock().lock();
		try {
			Outcome outcome = outcomes.get(trimmed);
			return outcome != null && outcome.getKey().equals(trimmed) ? outcome : null;
		} finally {
			lock.readLock().unlock();
		}
	}

	/** Returns the outcome for {@code ruleSet/name}; throws {@link DecisionException} if there is none. */
	public Outcome getOutcome(String key) {
		Outcome outcome = findOutcome(key);
		if (outcome == null) {
			if (find(key) != null) {
				throw new DecisionException("'" + key + "' is a condition or decision, which gives true/false; use evaluate(), not decide()");
			}
			throw new DecisionException("no outcome '" + key + "'; expected ruleSet/name, loaded rule sets: " + getRuleSetNames());
		}
		return outcome;
	}

	/** Returns the result of the outcome {@code ruleSet/name} for {@code root}; {@code null} if nothing matched and there is no default. */
	public String decide(String key, Object root) {
		return getOutcome(key).decide(root);
	}

	/** The outcomes of a rule set by name, in file order; empty if it is not loaded. */
	public Map<String, Outcome> getOutcomes(String ruleSet) {
		lock.readLock().lock();
		try {
			return outcomeSets.getOrDefault(ruleSet, Map.of());
		} finally {
			lock.readLock().unlock();
		}
	}

	public List<String> getRuleSetNames() {
		lock.readLock().lock();
		try {
			return List.copyOf(ruleSets.keySet());
		} finally {
			lock.readLock().unlock();
		}
	}

	/** The conditions and decisions of a rule set by name, in file order; empty if it is not loaded. */
	public Map<String, Rule> getRuleSet(String name) {
		lock.readLock().lock();
		try {
			return ruleSets.getOrDefault(name, Map.of());
		} finally {
			lock.readLock().unlock();
		}
	}

	/** A well-formed {@code ruleSet/name} key, trimmed, or {@code null}. */
	private static String checkKey(String key) {
		if (key == null) {
			return null;
		}
		String trimmed = key.trim();
		int separator = trimmed.indexOf(SEPARATOR);
		if (separator <= 0 || separator == trimmed.length() - 1 || trimmed.indexOf(SEPARATOR, separator + 1) >= 0) {
			return null;
		}
		return trimmed;
	}

	private Rule resolve(String reference, Map<String, Rule> local, Map<String, Outcome> localOutcomes, String owner) {
		boolean qualified = reference.contains(SEPARATOR);
		Rule rule = qualified ? findUnlocked(reference) : local.get(reference);
		if (rule == null) {
			boolean isOutcome = qualified ? findOutcomeUnlocked(reference) != null : localOutcomes.containsKey(reference);
			if (isOutcome) {
				throw new DecisionException(owner + ": '" + reference + "' is an outcome, which gives a named result, not true/false; refer to a condition or decision");
			}
			String hint = qualified ? " (rule sets loaded: " + ruleSets.keySet() + ")" : " (defined here: " + local.keySet() + ")";
			throw new DecisionException(owner + ": unknown condition or decision '" + reference + "'" + hint);
		}
		return rule;
	}

	private Outcome findOutcomeUnlocked(String key) {
		int separator = key.indexOf(SEPARATOR);
		Map<String, Outcome> set = outcomeSets.get(key.substring(0, separator));
		return set == null ? null : set.get(key.substring(separator + 1));
	}

	private static Outcome buildOutcome(String setName, OutcomeNode node) {
		String name = checkName(node.name, "outcome in rule set '" + setName + "'");
		String label = "outcome '" + name + "'";
		List<OutcomeRuleNode> ruleNodes = nullSafe(node.rules);
		if (ruleNodes.isEmpty()) {
			throw new DecisionException(label + ": rules is empty; give at least one when/result");
		}
		List<Outcome.Branch> branches = new ArrayList<>();
		for (int i = 0; i < ruleNodes.size(); i++) {
			OutcomeRuleNode ruleNode = ruleNodes.get(i);
			String ruleLabel = label + " rule " + (i + 1);
			if (ruleNode == null) {
				throw new DecisionException(ruleLabel + ": is empty");
			}
			String result = resultText(ruleNode.result, ruleLabel);
			if (result == null) {
				throw new DecisionException(ruleLabel + ": result is missing");
			}
			Expression expression = Expression.parse(ruleNode.when, ruleLabel);
			branches.add(new Outcome.Branch(ruleNode.when.trim(), expression, result));
		}
		return new Outcome(setName, name, branches, resultText(node.defaultResult, label + " default"));
	}

	private static String resultText(Object value, String label) {
		if (value == null) {
			return null;
		}
		if (value instanceof Map<?, ?> || value instanceof List<?>) {
			throw new DecisionException(label + ": result must be a single value, not " + value);
		}
		String text = value.toString().trim();
		if (text.isEmpty()) {
			throw new DecisionException(label + ": result is empty");
		}
		return text;
	}

	private Rule findUnlocked(String key) {
		int separator = key.indexOf(SEPARATOR);
		Map<String, Rule> set = ruleSets.get(key.substring(0, separator));
		return set == null ? null : set.get(key.substring(separator + 1));
	}

	private static void checkCycles(List<Decision> decisions, String setName) {
		Map<Decision, Integer> state = new HashMap<>();
		for (Decision decision : decisions) {
			visit(decision, state, new ArrayList<>(), setName);
		}
	}

	private static void visit(Decision decision, Map<Decision, Integer> state, List<String> path, String setName) {
		Integer current = state.get(decision);
		if (current != null && current == 2) {
			return;
		}
		path.add(decision.getName());
		if (current != null && current == 1) {
			throw new DecisionException("rule set '" + setName + "': decisions refer to each other in a cycle: " + String.join(" -> ", path));
		}
		state.put(decision, 1);
		Set<Rule> seen = new HashSet<>();
		for (Expression.Reference reference : decision.getExpression().referenceNodes()) {
			if (reference.target instanceof Decision target && seen.add(target)) {
				visit(target, state, path, setName);
			}
		}
		state.put(decision, 2);
		path.remove(path.size() - 1);
	}

	private static void putUnique(Map<String, Rule> local, Rule rule, String setName) {
		if (local.putIfAbsent(rule.getName(), rule) != null) {
			throw new DecisionException("rule set '" + setName + "': name '" + rule.getName() + "' is defined more than once");
		}
	}

	private static String checkName(String name, String what) {
		if (name == null || name.isBlank()) {
			throw new DecisionException(what + ": name is missing");
		}
		String trimmed = name.trim();
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			if (c == '/' || !Expression.isNameChar(c)) {
				throw new DecisionException(what + ": name '" + trimmed + "' may only use letters, digits, '_', '.', '-' and '$'");
			}
		}
		String lower = trimmed.toLowerCase();
		if (lower.equals("and") || lower.equals("or") || lower.equals("not")) {
			throw new DecisionException(what + ": '" + trimmed + "' is a keyword and cannot be a name");
		}
		return trimmed;
	}

	private static <E> List<E> nullSafe(List<E> list) {
		return list == null ? List.of() : list;
	}

	private static String read(String location) {
		if (location == null || location.isBlank()) {
			throw new DecisionException("rule file location is empty");
		}
		try (InputStream stream = open(location)) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new DecisionException("cannot read rule file " + location + ": " + e.getMessage(), e);
		}
	}

	private static InputStream open(String location) throws IOException {
		if (location.startsWith("classpath:")) {
			String path = location.substring("classpath:".length());
			path = path.startsWith("/") ? path.substring(1) : path;
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			InputStream stream = (loader != null ? loader : DecisionRegistry.class.getClassLoader()).getResourceAsStream(path);
			if (stream == null) {
				throw new IOException("not found on the classpath");
			}
			return stream;
		}
		return new FileInputStream(location);
	}
}
