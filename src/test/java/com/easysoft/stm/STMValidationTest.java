package com.easysoft.stm;

import org.junit.jupiter.api.Test;

import com.easysoft.stm.config.STMOptimizedBuilder;

public class STMValidationTest {

	@Test
	void test() throws Exception {
		STMOptimizedBuilder<STMContext> builder = new STMOptimizedBuilder<STMContext>(null, true, true);
		builder.setDebug(true);
		builder.addFile("src/test/resources/sample_stm.xml");
		STMStateful<STMContext> stm = builder.build();
		STMSample stmSample = new STMSample(stm.createContextWithTrace());
		Transition transition = stm.getState("check.authentication.s").getTransition(stm.getEvent("check.authentication.start.e"));
		stm.start(stmSample, transition);
	}
	
	public static class STMSample implements STMContext {

		private final StatefulContext context;
		
		public STMSample(StatefulContext context) {
			this.context = context;
		}
		@Override
		public StatefulContext getSTMContext() {
			return context;
		}
		
	}
}
