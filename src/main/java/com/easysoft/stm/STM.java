package com.easysoft.stm;

/**
 * A state machine: a set of {@link State}s whose {@link Transition}s are selected by {@link Event}s.
 * <p>
 * {@link STMStateful} is the implementation. Machines are usually built from XML/YAML with
 * {@code STMOptimizedBuilder} and are immutable and reusable; per-run state lives in a
 * {@link StatefulContext}.
 *
 * @param <T> the application context type passed to actors
 */
public interface STM<T extends STMContext> {
	int getId();

	Event getEvent(String event);

	State getState(String state);

	State getDefaultStartState();

	Event getDefaultStartEvent();

	STMEvent start(T context, Transition transition);

	STMEvent next(T context, Transition transition);

	STMEvent startWithNewContext(T fsmContext, T newFsmContext, Transition transition);
	
	public STMEvent startWithNewSTM(STMStateful<STMContext> newStm, T fsmContext, Transition transition);

	/**
	 * What the engine does after a transition: {@code NEXT} continues, {@code STOP} ends the run,
	 * {@code START} restarts from the next context, {@code CHANGE_STM} switches machine.
	 */
	enum STMEvent {
		START, STOP, NEXT, CHANGE_STM
	}

}
