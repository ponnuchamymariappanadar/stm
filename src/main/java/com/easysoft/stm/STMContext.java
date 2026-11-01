package com.easysoft.stm;

/**
 * Implemented by your application's context object. It carries your business data and gives the
 * engine access to the per-run {@link StatefulContext}.
 *
 * <pre>{@code
 * public class OrderContext implements STMContext {
 *     private StatefulContext stm;
 *     public StatefulContext getSTMContext() { return stm; }
 *     public void setSTMContext(StatefulContext c) { stm = c; }
 *     // business fields...
 * }
 * }</pre>
 */
public interface STMContext {
	
	StatefulContext getSTMContext();
	
	default <T> T getLastSTMResult() {
		return getSTMContext().getLastResult();
	}
	
	default void setSTMContext(StatefulContext statefulContext) { }
	
	default <T> void clearError() {
		getSTMContext().clearError();
	}
}
