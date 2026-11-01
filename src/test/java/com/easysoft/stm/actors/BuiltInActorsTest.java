package com.easysoft.stm.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.easysoft.stm.EventCollection;
import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.State;
import com.easysoft.stm.StateCollection;
import com.easysoft.stm.Event;
import com.easysoft.stm.TestContext;
import com.easysoft.stm.condition.FailCondition;
import com.easysoft.stm.condition.PassCondition;

/** Tests for the small built-in actors and conditions. */
class BuiltInActorsTest {

	private TestContext context() {
		State s = new State(0, "s");
		Event e = new Event(0, "e");
		STMStateful<STMContext> stm = new STMStateful<>(0, "t", s, e, new StateCollection(), new EventCollection());
		return new TestContext(stm.createContext());
	}

	@Test
	void noOpReturnsNull() {
		assertNull(new NoOpActor().handle(context()));
	}

	@Test
	void throwExceptionHandleAlwaysThrows() {
		assertThrows(RuntimeException.class, () -> new ThrowExceptionHandle().handle(context()));
	}

	@Test
	void callStateResultReturnsTheLastResult() {
		TestContext ctx = context();
		ctx.getSTMContext().setLastResult("value");
		assertEquals("value", new CallStateResult().handle(ctx));
	}

	/**
	 * Note: the names are inverted relative to their behaviour. {@code PassCondition} returns
	 * {@code false} and {@code FailCondition} returns {@code true}. See KNOWN_ISSUES.md.
	 */
	@Test
	void builtInConditionsCurrentBehaviour() {
		assertEquals(Boolean.FALSE, new PassCondition<STMContext>().handle(context()));
		assertEquals(Boolean.TRUE, new FailCondition<STMContext>().handle(context()));
	}
}
