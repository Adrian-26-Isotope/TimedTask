package adrian.os.java.timer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A timer with the ability to run a single task.
 * This task can be schedules periodically,
 * repetetively or once with a initial delay.
 */
public class TimedTask {

    @SuppressWarnings("javadoc")
    protected enum State {
                          RUNNING,
                          NOT_RUNNING;
    }

    /* mandatory fields */
    private final Consumer<TimedTask> task;
    private final AbstractTimedTaskExecutor executor;

    /* optional fields */
    private String name = "";
    private Duration initialDelay;
    private Duration periodicDelay;
    private Duration repetetiveDelay;

    /* internal fields */
    private long count = 0;
    private final Timer timer = new Timer();
    private volatile LocalDateTime nextExecution;
    private volatile State state = State.NOT_RUNNING;
    private final Object executionLock = new Object();


    /**
     * @param task the task to be executed by this timer.
     */
    protected TimedTask(final Consumer<TimedTask> task, final AbstractTimedTaskExecutor exec) {
        this.task = Objects.requireNonNull(task);
        this.executor = exec;
    }

    /**
     * start the timer thread.
     */
    public synchronized boolean start() {
        if (getState() == State.NOT_RUNNING) {
            setState(State.RUNNING);
            setNextExecutionTime(LocalDateTime.now().plus(this.initialDelay));
            if ((this.name == null) || this.name.isBlank()) {
                this.executor.run(this.timer::runTimer);
            }
            else {
                String timerName = "[" + this.name + "]Timer";
                this.executor.run(this.timer::runTimer, timerName);
            }
            return true;
        }
        return false;
    }

    /**
     * stop any reoccuring executions and terminate this timer gracefully. Once
     * stopped it can be started again.
     */
    public synchronized void stop() {
        setState(State.NOT_RUNNING);
        // notify waiting timer thread
        setNextExecutionTime(null);
    }


    /**
     * @param name optional name for tasks
     * @return false, if called in RUNNING state.
     */
    protected boolean setName(final String name) {
        if (getState() == State.NOT_RUNNING) {
            this.name = name;
            return true;
        }
        return false;
    }

    /**
     * @return the state
     */
    protected State getState() {
        return this.state;
    }

    /**
     * set a new state.
     */
    protected void setState(final State state) {
        this.state = state;
    }

    /**
     * @return true if this task is still running, false otherwise.
     */
    public boolean isRunning() {
        return getState() == State.RUNNING;
    }

    /**
     * @return the time the next execution shall be triggered.
     */
    protected LocalDateTime getNextExecution() {
        return this.nextExecution;
    }

    /**
     * set the new next execution time.
     */
    protected void setNextExecutionTime(final LocalDateTime time) {
        synchronized (this.executionLock) {
            this.nextExecution = time;
            this.executionLock.notifyAll();
        }
    }

    /**
     * @param delay the initial delay to set
     * @return false, if called in RUNNING state.
     */
    protected boolean setInitialDelay(final Duration delay) {
        if (getState() == State.NOT_RUNNING) {
            this.initialDelay = delay;
            return true;
        }
        return false;
    }

    /**
     * @param repeatDelay the repetetive delay to set
     * @return false, if called in RUNNING state.
     */
    protected boolean setRepetetiveDelay(final Duration repeatDelay) {
        if (getState() == State.NOT_RUNNING) {
            this.repetetiveDelay = repeatDelay;
            return true;
        }
        return false;
    }

    /**
     * @param periodDelay the periodic delay to set
     * @return false, if called in RUNNING state.
     */
    protected boolean setPeriodicDelay(final Duration periodDelay) {
        if (getState() == State.NOT_RUNNING) {
            this.periodicDelay = periodDelay;
            return true;
        }
        return false;
    }

    /**
     * encapsulate timer thread code
     */
    private class Timer {

        private void runTimer() {
            if (getState() != State.RUNNING) {
                return;
            }
            try {
                loopTimer();
            }
            finally {
                setState(State.NOT_RUNNING);
            }
        }

        private void loopTimer() {
            try {
                while (isHealty()) {
                    if (getNextExecution().compareTo(LocalDateTime.now()) <= 0) {
                        calculatePeriodicExecutionTime();
                        executeTask();
                    }
                    waitTillNextExecution();
                }
            }
            catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }

        private boolean isHealty() {
            return (TimedTask.this.state == State.RUNNING) && !Thread.currentThread().isInterrupted() &&
                    (getNextExecution() != null);
        }

        private void executeTask() {
            Runnable runnable = () -> {
                try {
                    TimedTask.this.task.accept(TimedTask.this);
                }
                catch (final Exception _) {
                    // TODO Handle the exception, log it, etc.
                }
                finally {
                    calculateRepetetiveExecutionTime();
                }
            };

            if ((TimedTask.this.name == null) || TimedTask.this.name.isBlank()) {
                TimedTask.this.executor.run(runnable);
            }
            else {
                String runnableName = "[" + TimedTask.this.name + "]Task#" + (++TimedTask.this.count);
                TimedTask.this.executor.run(runnable, runnableName);
            }

        }

        /**
         * onyl with periodic scenario: calculate the next execution time.
         */
        private void calculatePeriodicExecutionTime() {
            if (TimedTask.this.periodicDelay != null) {
                setNextExecutionTime(getNextExecution().plus(TimedTask.this.periodicDelay));
            }
            else {
                // set next execution to null temporarily.
                // once the task finishes it will set the next execution with the repetetive
                // delay.
                setNextExecutionTime(null);
            }
        }

        /**
         * only for repretetive scenario: set the next execution time.
         */
        private void calculateRepetetiveExecutionTime() {
            if (TimedTask.this.repetetiveDelay != null) {
                setNextExecutionTime(LocalDateTime.now().plus(TimedTask.this.repetetiveDelay));
            }
            else if (TimedTask.this.periodicDelay == null) {
                // SINGLE TASK EXECUTION SCENARIO
                stop(); // stop TimedTask!
            }
        }

        private void waitTillNextExecution() throws InterruptedException {
            if (getNextExecution() == null) {
                // REPETETIVE DELAY SCENARIO
                synchronized (TimedTask.this.executionLock) {
                    while (isRunning() && !Thread.currentThread().isInterrupted() && (getNextExecution() == null)) {
                        TimedTask.this.executionLock.wait();
                    }
                }
            }
            else {
                // PERIODIC DELAY SCENARIO
                Thread.sleep(Duration.between(LocalTime.now(), getNextExecution()));
                // timer thread sleeps
                // task thread terminates once finished
            }
        }

    }
}
