package com.easysoft.stm.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.easysoft.stm.STM.STMEvent;
import com.easysoft.stm.STMContext;
import com.easysoft.stm.STMStateful;
import com.easysoft.stm.config.STMOptimizedBuilder;

/**
 * Runs the example order flow from the README, loaded from both XML and YAML, and checks that
 * every path behaves the same way in both formats.
 */
class OrderFlowExampleTest {

	private static final String[] CONFIGS = { "classpath:stm/order-flow.xml", "classpath:stm/order-flow.yaml" };

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
	void declinedPaymentReturnsThroughTheDefaultCase() throws Exception {
		for (String config : CONFIGS) {
			assertEquals(List.of("received", "charge refused", "declined"), place(config, "A-4", 5000, false), config);
		}
	}

	@Test
	void exceptionGoesToTheGlobalOnError() throws Exception {
		for (String config : CONFIGS) {
			assertEquals(List.of("error: order id is required"), place(config, null, 100, false), config);
		}
	}

	@Test
	void xmlAndYamlProduceTheSameMachine() throws Exception {
		STMStateful<STMContext> xml = load(CONFIGS[0]);
		STMStateful<STMContext> yaml = load(CONFIGS[1]);
		assertEquals(xml.getName(), yaml.getName());
		assertEquals(xml.getStates().getStates().size(), yaml.getStates().getStates().size());
		assertEquals(xml.getDefaultStartState().getName(), yaml.getDefaultStartState().getName());
		assertEquals(xml.getDefaultStartEvent().getName(), yaml.getDefaultStartEvent().getName());
	}
}
