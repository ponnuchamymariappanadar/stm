package com.easysoft.stm.config;

import java.util.concurrent.atomic.AtomicInteger;

import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.TestContext;

/** Helpers for building state machines from inline YAML in tests. */
final class Stm {

	static final String A = FlowActions.class.getName();
	private static final AtomicInteger SEQ = new AtomicInteger();

	private Stm() {
	}

	/** {@code { class: FlowActions, method: method }}, for {@code action:} */
	static String actor(String method) {
		return "{ class: " + A + ", method: " + method + " }";
	}

	/** {@code { class: FlowActions, method: method }}, for {@code check:} */
	static String condition(String method) {
		return "{ class: " + A + ", method: " + method + " }";
	}

	/**
	 * Wraps stage definitions (comma-separated YAML flow mappings) in a flow with global
	 * {@code defaults} ({@code onError} and {@code onFalse}) that log "onError:&lt;message&gt;" / "onRejected".
	 */
	static String machine(String stages) {
		return machine("", stages);
	}

	/** {@code rootKeys} are extra top-level keys, such as {@code "startStage: second, startStep: go2"}. */
	static String machine(String rootKeys, String stages) {
		return "{ name: test.flow, " + (rootKeys.isBlank() ? "" : rootKeys + ", ")
				+ "defaults: { onError: { nextStage: error.s, then: error.e }, "
				+ "onFalse: { nextStage: error.s, then: rejected.e } }, "
				+ "stages: [ " + stages + ", "
				+ "{ name: error.s, steps: [ "
				+ "{ step: error.e, then: error.done, action: " + actor("onError") + " }, "
				+ "{ step: rejected.e, then: error.done, action: " + actor("onRejected") + " }, "
				+ "{ step: error.done, control: stop } ] } ] }";
	}

	static STMOptimizedBuilder<STMContext> builder(String yaml) {
		return builder(yaml, true);
	}

	static STMOptimizedBuilder<STMContext> builder(String yaml, boolean reduce) {
		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false, reduce);
		b.add(new ConfigYAMLReader(), yaml, "inline-" + SEQ.incrementAndGet() + ".yaml");
		return b;
	}

	static STMStateful<STMContext> build(String yaml) {
		return builder(yaml).build();
	}

	/** Runs the machine from its default start state/event with a fresh context. */
	static TestContext run(STMStateful<STMContext> stm, Object... keyValues) {
		TestContext ctx = new TestContext(stm.createContext());
		for (int i = 0; i < keyValues.length; i += 2) {
			ctx.with((String) keyValues[i], keyValues[i + 1]);
		}
		ctx.getSTMContext().start(ctx);
		return ctx;
	}
}
