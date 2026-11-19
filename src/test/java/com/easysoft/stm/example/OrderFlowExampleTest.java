package com.easysoft.stm.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.easysoft.stm.STM.STMEvent;
import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.config.ConfigYAMLReader;
import com.easysoft.stm.config.STMOptimizedBuilder;

/**
 * Runs the example order flow from the README and checks every path.
 */
class OrderFlowExampleTest {

	private static final String[] CONFIGS = { "classpath:stm/order-flow.yaml" };

	private static STMStateful<STMContext> load(String location) throws Exception {
		STMOptimizedBuilder<STMContext> builder = new STMOptimizedBuilder<>(null, false);
		builder.addFile(location);
		return builder.build();
	}

	private static List<String> place(String config, String orderId, int amount, boolean express) throws Exception {
		STMStateful<STMContext> stm = load(config);
		OrderContext order = new OrderContext(stm.createContext(), orderId, amount, express);
		STMEvent result = order.getSTMContext().start(order);
		assertEquals(STMEvent.STOP, result, config);
		return order.getHistory();
	}

	@Test
	void standardOrderIsChargedAndShipped() throws Exception {
		for (String config : CONFIGS) {
			assertEquals(List.of("received", "charged", "shipped standard"), place(config, "A-1", 100, false), config);
		}
	}

	@Test
	void expressOrderTakesTheExpressCase() throws Exception {
		for (String config : CONFIGS) {
			assertEquals(List.of("received", "charged", "shipped express"), place(config, "A-2", 100, true), config);
		}
	}

	@Test
	void invalidAmountIsRejectedByTheGlobalOnFailedCondition() throws Exception {
		for (String config : CONFIGS) {
			assertEquals(List.of("received", "rejected"), place(config, "A-3", 0, false), config);
		}
	}

	@Test
	void declinedPaymentReturnsThroughOtherwise() throws Exception {
		for (String config : CONFIGS) {
			assertEquals(List.of("received", "charge refused", "declined"), place(config, "A-4", 5000, false), config);
		}
	}

	@Test
	void theCreditLimitComesFromTheFlowInput() throws Exception {
		// the same flow with creditLimit 10000 instead of 1000: the 5000 order is now charged
		String yaml = STMOptimizedBuilder.readContent(CONFIGS[0]);
		assertTrue(yaml.contains("input: { creditLimit: 1000 }"), "the example flow sets the limit with input");
		STMOptimizedBuilder<STMContext> builder = new STMOptimizedBuilder<>(null, false);
		builder.add(new ConfigYAMLReader(), yaml.replace("input: { creditLimit: 1000 }", "input: { creditLimit: 10000 }"), "order-flow-10000.yaml");
		STMStateful<STMContext> stm = builder.build();
		OrderContext order = new OrderContext(stm.createContext(), "A-5", 5000, false);
		assertEquals(STMEvent.STOP, order.getSTMContext().start(order));
		assertEquals(List.of("received", "charged", "shipped standard"), order.getHistory());
	}

	@Test
	void exceptionGoesToTheGlobalOnError() throws Exception {
		for (String config : CONFIGS) {
			assertEquals(List.of("error: order id is required"), place(config, null, 100, false), config);
		}
	}
}
