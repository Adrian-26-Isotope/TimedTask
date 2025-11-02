package adrian.os.java.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import adrian.os.java.threadpool.CustomThreadPool;
import adrian.os.java.timer.TimedTask.State;

class TimedTaskTest {

    private final AtomicLong counter = new AtomicLong(0);
    private AbstractTimedTaskExecutor currentExecutor;

    @BeforeEach
    void setup() {
        this.counter.set(0);
    }

    @AfterEach
    void cleanup() throws InterruptedException {
        // Cleanup pool executor if it was used
        if (this.currentExecutor instanceof TimedTaskPoolExecutor poolExecutor) {
            poolExecutor.shutdown();
            assertTrue(poolExecutor.awaitTermination(Duration.ofMillis(2100)),
                    "idle time expired, pool should have been terminated");
        }
    }

    /**
     * Provides both executor implementations for parameterized tests.
     */
    static Stream<Arguments> executorProvider() {
        return Stream.of(Arguments.of(new TimedTaskThreadExecutor()),
                Arguments.of(new TimedTaskPoolExecutor(CustomThreadPool.builder().setMinThreads(0)
                        .setIdleTime(Duration.ofSeconds(2)).setName("test-pool").build())));
    }

    @ParameterizedTest
    @MethodSource("executorProvider")
    void testState(final AbstractTimedTaskExecutor executor) {
        this.currentExecutor = executor;
        var timedTask = executor.createTimedTask(createTask(1)).build();
        timedTask.start();
        assertEquals(State.RUNNING, timedTask.getState());
        timedTask.stop();
        assertEquals(State.NOT_RUNNING, timedTask.getState());
    }

    @ParameterizedTest
    @MethodSource("executorProvider")
    void testRepetetive(final AbstractTimedTaskExecutor executor) throws InterruptedException {
        this.currentExecutor = executor;
        TimedTaskBuilder builder = executor.createTimedTask(createTask(1));
        builder.setPeriodicDelay(Duration.ofSeconds(10)).setRepetetiveDelay(Duration.ofSeconds(2));
        TimedTask timer = builder.build();
        timer.start();
        Thread.sleep(150); // Increased buffer for thread startup
        assertEquals(1, this.counter.get());
        Thread.sleep(1100); // task duration + buffer
        Thread.sleep(2100); // repetetive delay + buffer
        assertEquals(2, this.counter.get());
        timer.stop();
        assertEquals(2, this.counter.get());
    }

    @ParameterizedTest
    @MethodSource("executorProvider")
    void testPeriodic(final AbstractTimedTaskExecutor executor) throws InterruptedException {
        this.currentExecutor = executor;
        TimedTaskBuilder builder = this.currentExecutor.createTimedTask(createTask(1));
        builder.setRepetetiveDelay(Duration.ofSeconds(10)).setPeriodicDelay(Duration.ofSeconds(1));
        TimedTask timer = builder.build();
        timer.start();
        Thread.sleep(150); // Increased buffer for thread startup
        assertEquals(1, this.counter.get());
        Thread.sleep(1100); // periodic delay + buffer
        assertEquals(2, this.counter.get());
        timer.stop();
        assertEquals(2, this.counter.get());
    }

    @ParameterizedTest
    @MethodSource("executorProvider")
    void testDelayed1(final AbstractTimedTaskExecutor executor) throws InterruptedException {
        this.currentExecutor = executor;
        TimedTaskBuilder builder = this.currentExecutor.createTimedTask(createTask(1));
        TimedTask timer = builder.build();
        timer.start();
        Thread.sleep(150); // Increased buffer for thread startup
        assertEquals(1, this.counter.get());
        Thread.sleep(1100); // task duration + buffer
        assertEquals(State.NOT_RUNNING, timer.getState());
        assertEquals(1, this.counter.get());
    }

    @ParameterizedTest
    @MethodSource("executorProvider")
    void testDelayed2(final AbstractTimedTaskExecutor executor) throws InterruptedException {
        this.currentExecutor = executor;
        TimedTaskBuilder builder = executor.createTimedTask(createTask(1));
        builder.setInitialDelay(Duration.ofSeconds(1));
        TimedTask timer = builder.build();
        timer.start();
        Thread.sleep(150); // Buffer for thread startup
        assertEquals(0, this.counter.get());
        Thread.sleep(1100); // Initial delay
        assertEquals(1, this.counter.get());
        Thread.sleep(1100); // task duration + buffer
        assertEquals(State.NOT_RUNNING, timer.getState());
        assertEquals(1, this.counter.get());
    }

    // ========== Section 1.1: State Management Tests ==========

    /**
     * Tests that state is NOT_RUNNING after build() and before start().
     */
    @ParameterizedTest
    @MethodSource("executorProvider")
    void testStateAfterBuild(final AbstractTimedTaskExecutor executor) {
        this.currentExecutor = executor;
        var timedTask = executor.createTimedTask(createTask(1)).build();
        assertEquals(State.NOT_RUNNING, timedTask.getState());
    }

    /**
     * Tests that start() returns false when task is already running.
     */
    @ParameterizedTest
    @MethodSource("executorProvider")
    void testDoubleStart(final AbstractTimedTaskExecutor executor) {
        this.currentExecutor = executor;
        var timedTask = executor.createTimedTask(createTask(1)).build();

        // First start should succeed
        assertTrue(timedTask.start());
        assertEquals(State.RUNNING, timedTask.getState());

        // Second start should fail (already running)
        assertFalse(timedTask.start());
        assertEquals(State.RUNNING, timedTask.getState());

        timedTask.stop();
    }

    /**
     * Tests that stop() on a task before start() is a no-op.
     */
    @ParameterizedTest
    @MethodSource("executorProvider")
    void testStopBeforeStart(final AbstractTimedTaskExecutor executor) {
        this.currentExecutor = executor;
        var timedTask = executor.createTimedTask(createTask(1)).build();

        assertEquals(State.NOT_RUNNING, timedTask.getState());
        timedTask.stop(); // Should be no-op
        assertEquals(State.NOT_RUNNING, timedTask.getState());
    }

    /**
     * Tests that calling stop() multiple times is safe.
     */
    @ParameterizedTest
    @MethodSource("executorProvider")
    void testMultipleStops(final AbstractTimedTaskExecutor executor) {
        this.currentExecutor = executor;
        var timedTask = executor.createTimedTask(createTask(1)).build();

        timedTask.start();
        assertEquals(State.RUNNING, timedTask.getState());

        timedTask.stop();
        assertEquals(State.NOT_RUNNING, timedTask.getState());

        timedTask.stop(); // Second stop should be safe
        assertEquals(State.NOT_RUNNING, timedTask.getState());

        timedTask.stop(); // Third stop should be safe
        assertEquals(State.NOT_RUNNING, timedTask.getState());
    }

    /**
     * Tests that state is NOT_RUNNING after a single task completes.
     */
    @ParameterizedTest
    @MethodSource("executorProvider")
    void testStateAfterCompletion(final AbstractTimedTaskExecutor executor) throws InterruptedException {
        this.currentExecutor = executor;
        var timedTask = executor.createTimedTask(createTask(1)).build();

        timedTask.start();
        assertEquals(State.RUNNING, timedTask.getState());

        // Wait for task to execute and complete
        Thread.sleep(100); // Start execution
        assertEquals(1, this.counter.get());

        Thread.sleep(1100); // Wait for task duration (1 second)

        // Task should have completed and state should be NOT_RUNNING
        assertEquals(State.NOT_RUNNING, timedTask.getState());
        assertEquals(1, this.counter.get());
    }

    /**
     * Tests that a task can be stopped and then restarted successfully.
     */
    @ParameterizedTest
    @MethodSource("executorProvider")
    void testRestart(final AbstractTimedTaskExecutor executor) throws InterruptedException {
        this.currentExecutor = executor;
        TimedTaskBuilder builder = executor.createTimedTask(createTask(1));
        builder.setPeriodicDelay(Duration.ofSeconds(2));
        TimedTask timedTask = builder.build();

        // First start
        assertTrue(timedTask.start());
        assertEquals(State.RUNNING, timedTask.getState());
        Thread.sleep(150); // Allow task to execute
        assertEquals(1, this.counter.get());

        // Stop the task
        timedTask.stop();
        assertEquals(State.NOT_RUNNING, timedTask.getState());
        Thread.sleep(100);
        long countAfterStop = this.counter.get();

        // Restart the task
        assertTrue(timedTask.start());
        assertEquals(State.RUNNING, timedTask.getState());
        Thread.sleep(150); // Allow task to execute
        assertTrue(this.counter.get() > countAfterStop, "Counter should increment after restart");

        timedTask.stop();
    }

    /**
     * Tests that a task can be stopped and restarted multiple times.
     */
    @ParameterizedTest
    @MethodSource("executorProvider")
    void testMultipleRestarts(final AbstractTimedTaskExecutor executor) throws InterruptedException {
        this.currentExecutor = executor;
        var timedTask = executor.createTimedTask(createTask(1)).build(); // single execution task

        for (int i = 1; i <= 3; i++) {
            // Start the task
            assertTrue(timedTask.start(), "Start should succeed on iteration " + i);
            assertEquals(State.RUNNING, timedTask.getState());
            Thread.sleep(150); // Allow task to execute
            assertEquals(i, this.counter.get(), "Counter should be " + i + " on iteration " + i);
            Thread.sleep(1000); // Wait for task to complete
            assertEquals(State.NOT_RUNNING, timedTask.getState());
        }
    }

    /**
     * Tests that restarting a task uses the original configuration. The task should not change behavior after restart.
     */
    @ParameterizedTest
    @MethodSource("executorProvider")
    void testRestartWithSameConfiguration(final AbstractTimedTaskExecutor executor) throws InterruptedException {
        this.currentExecutor = executor;
        TimedTaskBuilder builder = executor.createTimedTask(createTask(1));
        builder.setInitialDelay(Duration.ofMillis(500));
        TimedTask timedTask = builder.build();

        // First start - verify initial delay
        timedTask.start();
        Thread.sleep(200);
        assertEquals(0, this.counter.get(), "Task should not execute yet (initial delay)");
        Thread.sleep(400);
        assertEquals(1, this.counter.get(), "Task should execute after initial delay");
        Thread.sleep(1100); // Wait for completion
        timedTask.stop();

        Thread.sleep(500);

        // Restart - verify initial delay is still applied
        this.counter.set(0);
        timedTask.start();
        Thread.sleep(200);
        assertEquals(0, this.counter.get(), "Task should not execute yet on restart (initial delay)");
        Thread.sleep(400);
        assertEquals(1, this.counter.get(), "Task should execute after initial delay on restart");
        timedTask.stop();
    }

    // ========== Helper Methods ==========

    private Consumer<TimedTask> createTask(final int seconds) {
        return (task) -> {
            try {
                this.counter.incrementAndGet();
                Thread.sleep(Duration.ofSeconds(seconds));
            }
            catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        };
    }

}
