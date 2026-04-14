package core.problem;

/**
 * Interface representing a state in the state-space graph.
 */
public abstract class State {
	/** Output current state representation to console. */
	public abstract void draw();

	/**
	 * Transitions to the next state given a valid action.
	 * @param action The transition operator.
	 * @return The successor state.
	 */
	public abstract State next(Action action);

	/**
	 * Returns an iterable of potential actions for this state.
	 */
	public abstract Iterable<? extends Action> actions();
}