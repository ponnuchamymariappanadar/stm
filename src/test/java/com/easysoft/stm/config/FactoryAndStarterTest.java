package com.easysoft.stm.config;

import static com.easysoft.stm.config.Stm.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.StatefulContextWithTrace;
import com.easysoft.stm.TestContext;

/**
 * {@link STMFactory} builds a machine and publishes it by name; {@link StmStarter} subscribes by
 * name and starts flows. These are normally Spring beans; here they are wired by hand.
 */
class FactoryAndStarterTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	private static final String FLOW = Stm.machine("<State name='s'>"
			+ "<Transition when='start' fire='next'>" + actor("a") + "</Transition>"
			+ "<Transition when='next' fire='end'>" + actor("b") + "</Transition>"
			+ "<Transition when='end' stmevent='stop'/>"
			+ "</State>");

	private static STMFactory factory(String name) {
		STMFactory f = new STMFactory();
		f.setStmBuilder(Stm.builder(FLOW));
		f.setFsmName(name);
		return f;
	}

	@Test
	void factoryPublishesTheBuiltMachine() {
		String name = "factory." + SEQ.incrementAndGet();
		STMFactory f = factory(name);
		f.init();
		assertNotNull(STMFactory.getFsmAllStateful().get(name));
		assertEquals(name, f.getFsmName());
		assertNotNull(f.getStmBuilder());
	}

	@Test
	void listenerRegisteredBeforeInitIsNotified() {
		String name = "factory." + SEQ.incrementAndGet();
		List<STMStateful<STMContext>> received = new ArrayList<>();
		STMFactory.addConsumer(name, received::add);
		factory(name).init();
		assertEquals(1, received.size());
	}

	@Test
	void listenerRegisteredAfterInitIsNotifiedImmediately() {
		String name = "factory." + SEQ.incrementAndGet();
		factory(name).init();
		List<STMStateful<STMContext>> received = new ArrayList<>();
		STMFactory.addConsumer(name, received::add);
		assertSame(STMFactory.getFsmAllStateful().get(name), received.get(0));
	}

	@Test
	void starterRunsTheFlowFromItsConfiguredStart() {
		String name = "factory." + SEQ.incrementAndGet();
		StmStarter starter = new StmStarter(name, "s", "next");
		starter.init();
		factory(name).init();

		TestContext ctx = new TestContext(starter.createStmStateful());
		starter.start(ctx);

		assertEquals(List.of("b"), ctx.log);
		assertEquals("s", starter.getStartStateObj().getName());
		assertEquals("next", starter.getStartEventObj().getName());
		assertNotNull(starter.getStartTransition());
		assertEquals(name, starter.getFsmName());
	}

	@Test
	void starterCanCreateTracingContexts() {
		String name = "factory." + SEQ.incrementAndGet();
		factory(name).init();
		StmStarter starter = new StmStarter(name, "s", "start");
		starter.init();
		assertInstanceOf(StatefulContextWithTrace.class, starter.createStmStatefulWithDebug());

		TestContext ctx = new TestContext(starter.createStmStatefulWithDebug());
		starter.start(ctx);
		assertEquals(List.of("a", "b"), ctx.log);
	}
}
