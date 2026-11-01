package com.easysoft.stm.config;

import com.easysoft.stm.Actor;
import com.easysoft.stm.TestContext;

/**
 * Actor used by the XML/YAML-driven tests. Each method records what it did in
 * {@link TestContext#log}. Referenced from configuration as
 * {@code className="com.easysoft.stm.config.FlowActions" methodName="..."}.
 */
public class FlowActions implements Actor<TestContext> {

	public Object a(TestContext c) {
		c.log.add("a");
		return "A";
	}

	public Object b(TestContext c) {
		c.log.add("b");
		return "B";
	}

	public Object c(TestContext c) {
		c.log.add("c");
		return "C";
	}

	public Object boom(TestContext c) {
		c.log.add("boom");
		throw new IllegalStateException("boom");
	}

	public Object onError(TestContext c) {
		c.log.add("onError:" + c.getSTMContext().getException().getMessage());
		return null;
	}

	public Object localError(TestContext c) {
		c.log.add("localError");
		return null;
	}

	public Object onRejected(TestContext c) {
		c.log.add("onRejected");
		return null;
	}

	public Object localRejected(TestContext c) {
		c.log.add("localRejected");
		return null;
	}

	public boolean flag(TestContext c) {
		c.log.add("flag");
		return Boolean.TRUE.equals(c.data.get("flag"));
	}

	public Boolean isTrue(TestContext c) {
		return Boolean.TRUE;
	}

	public String notABoolean(TestContext c) {
		return "yes";
	}

	public Object route(TestContext c) {
		c.log.add("route");
		return c.data.get("route");
	}

	public Object withArg(TestContext c, String arg) {
		c.log.add("arg:" + arg);
		return arg;
	}

	public String lookupSource(TestContext c) {
		return "looked-up";
	}

	public Object useLookup(TestContext c, String value) {
		c.log.add("lookup:" + value);
		return value;
	}

	public Object lastResult(TestContext c) {
		c.log.add("last:" + c.getLastSTMResult());
		return null;
	}
}
