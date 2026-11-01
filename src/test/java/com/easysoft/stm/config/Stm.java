package com.easysoft.stm.config;

import java.util.concurrent.atomic.AtomicInteger;

import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.TestContext;

/** Helpers for building state machines from inline XML in tests. */
final class Stm {

	static final String A = FlowActions.class.getName();
	private static final AtomicInteger SEQ = new AtomicInteger();

	private Stm() {
	}

	/** {@code <Actor className="FlowActions" methodName="method"/>} */
	static String actor(String method) {
		return "<Actor className='" + A + "' methodName='" + method + "'/>";
	}

	/** {@code <Condition className="FlowActions" methodName="method"/>} */
	static String condition(String method) {
		return "<Condition className='" + A + "' methodName='" + method + "'/>";
	}

	/**
	 * Wraps state definitions in an {@code <STM>} root with global {@code OnError} and
	 * {@code OnFailedCondition} handlers that log "onError:&lt;message&gt;" / "onRejected".
	 */
	static String machine(String states) {
		return machine("", states);
	}

	static String machine(String rootAttributes, String states) {
		return "<STM name='test.flow' " + rootAttributes + ">"
				+ "<OnError to='error.s' fire='error.e'/>"
				+ "<OnFailedCondition to='error.s' fire='rejected.e'/>"
				+ states
				+ "<State name='error.s'>"
				+ "  <Transition when='error.e' fire='error.done'>" + actor("onError") + "</Transition>"
				+ "  <Transition when='rejected.e' fire='error.done'>" + actor("onRejected") + "</Transition>"
				+ "  <Transition when='error.done' stmevent='stop'/>"
				+ "</State>"
				+ "</STM>";
	}

	static STMOptimizedBuilder<STMContext> builder(String xml) {
		return builder(xml, true);
	}

	static STMOptimizedBuilder<STMContext> builder(String xml, boolean reduce) {
		STMOptimizedBuilder<STMContext> b = new STMOptimizedBuilder<>(null, false, reduce);
		b.add(new ConfigXMLReader(), xml, "inline-" + SEQ.incrementAndGet() + ".xml");
		return b;
	}

	static STMStateful<STMContext> build(String xml) {
		return builder(xml).build();
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
