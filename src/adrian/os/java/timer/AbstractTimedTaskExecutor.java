package adrian.os.java.timer;

import java.util.function.Consumer;

/**
 * Abstract base class for creating and running {@link TimedTask}s.
 * <p>
 * Provides a common implementation for creating timed tasks while allowing
 * subclasses to define their own execution strategies.
 * </p>
 */
public abstract class AbstractTimedTaskExecutor {

    /**
     * Creates a new timed task builder for the specified task.
     *
     * @param task the task to execute as part of the timed task
     * @return a new {@link TimedTaskBuilder} instance for configuring the timed
     *         task
     */
    public TimedTaskBuilder createTimedTask(final Consumer<TimedTask> task) {
        return new TimedTaskBuilder(task, this);
    }

    /**
     * Executes the given task using the executor's execution strategy.
     *
     * @param task to execute
     */
    abstract void run(final Runnable task);

    /**
     * Executes the given task using the executor's execution strategy,
     * with an associated name for the task.
     *
     * @param task to execute
     * @param name the name to associate with the task
     */
    abstract void run(final Runnable task, String name);
}
