# TimedTask Test Plan

## Overview
This document outlines a comprehensive test plan for the `TimedTask` class, including all execution modes and both available executor implementations.

---

## Current Test Coverage Analysis

### Existing Tests
1. **testState** - Validates state transitions (NOT_RUNNING → RUNNING → NOT_RUNNING)
2. **testRepetetive** - Tests repetitive delay scheduling
3. **testPeriodic** - Tests periodic delay scheduling
4. **testDelayed1** - Tests single execution without initial delay
5. **testDelayed2** - Tests single execution with initial delay

### Coverage Gaps
- Only `TimedTaskThreadExecutor` is tested
- No tests for `TimedTaskPoolExecutor`
- No edge cases or error scenarios
- No concurrent execution tests
- No tests for named tasks
- No tests for state validation in various scenarios
- No tests for timer behavior under load

---

## Extended Test Cases

### 1. Core Functionality Tests

#### 1.1 State Management
- [x] **testState** - Basic state transitions (EXISTING)
- [x] **testStateAfterBuild** - Verify state is NOT_RUNNING after build()
- [x] **testDoubleStart** - Verify start() returns false when already running
- [x] **testStopBeforeStart** - Stop a task before it starts (should be no-op)
- [x] **testMultipleStops** - Call stop() multiple times
- [x] **testStateAfterCompletion** - Verify state is NOT_RUNNING after single task completes
- [x] **testRestart** - Stop a running task and restart it successfully
- [x] **testMultipleRestarts** - Stop and restart a task multiple times
- [x] **testRestartWithDifferentConfiguration** - Restart task, verify original configuration is used

#### 1.2 Single Execution Mode
- [x] **testDelayed1** - Single execution without initial delay (EXISTING)
- [x] **testDelayed2** - Single execution with initial delay (EXISTING)
- [ ] **testDelayedZeroInitialDelay** - Explicit zero initial delay

#### 1.3 Periodic Execution Mode
- [x] **testPeriodic** - Basic periodic execution (EXISTING)
- [ ] **testPeriodicMultipleCycles** - Run 5+ periodic executions
- [ ] **testPeriodicStopDuringExecution** - Stop while task is executing
- [ ] **testPeriodicWithInitialDelay** - Periodic + initial delay combination
- [ ] **testPeriodicShortDelay** - Periodic with sub-second delays (100ms)
- [ ] **testPeriodicLongRunningTask** - Task duration > periodic delay

#### 1.4 Repetitive Execution Mode
- [x] **testRepetetive** - Basic repetitive execution (EXISTING)
- [ ] **testRepetetiveMultipleCycles** - Run 5+ repetitive executions
- [ ] **testRepetetiveStopDuringExecution** - Stop while task is executing
- [ ] **testRepetetiveWithInitialDelay** - Repetitive + initial delay combination
- [ ] **testRepetetiveShortDelay** - Repetitive with sub-second delays (100ms)
- [ ] **testRepetetiveVariableTaskDuration** - Task duration varies between executions

### 2. Named Task Tests
- [ ] **testNamedTask** - Create task with name, verify thread/task naming
- [ ] **testNamedTaskWithBlankName** - Blank name should behave as unnamed
- [ ] **testNamedTaskWithNullName** - Null name should behave as unnamed
- [ ] **testNamedPeriodicTask** - Verify task counter increments in name
- [ ] **testMultipleNamedTasks** - Multiple tasks with different names

### 3. Timing and Precision Tests
- [ ] **testInitialDelayPrecision** - Verify task starts close to expected time
- [ ] **testPeriodicDelayPrecision** - Verify periodic executions are on schedule
- [ ] **testRepetetiveDelayPrecision** - Verify repetitive delay is accurate
- [ ] **testNextExecutionTime** - Verify getNextExecution() returns correct values
- [ ] **testMidnightRollover** - Test scheduling across midnight boundary

### 4. Error Handling and Robustness
- [ ] **testTaskThrowsException** - Task throws exception, timer continues
- [ ] **testTaskThrowsRuntimeException** - Task throws unchecked exception
- [ ] **testTaskInterrupted** - Task handles interruption correctly
- [ ] **testNullTask** - Attempt to create timer with null task
- [ ] **testNegativeDelay** - Attempt to set negative delay durations
- [ ] **testExtremelyLongDelay** - Test with Duration.ofDays(365)

### 4.1 Task Self-Control Tests
> **Note:** TimedTask uses `Consumer<TimedTask>` as the task type, passing itself to `accept()`. This allows tasks to control their own timer.

- [ ] **testTaskStopsItself** - Task calls `timedTask.stop()` to terminate execution
- [ ] **testTaskStopsItselfInPeriodicMode** - Periodic task stops itself after N executions
- [ ] **testTaskStopsItselfInRepetitiveMode** - Repetitive task stops itself after N executions
- [ ] **testTaskChecksIsRunning** - Task queries `timedTask.isRunning()` during execution
- [ ] **testTaskAccessesNextExecution** - Task calls `timedTask.getNextExecution()` (if accessible)
- [ ] **testTaskTriesToRestartItself** - Task calls `timedTask.start()` while running (should fail)
- [ ] **testTaskSelfStopRaceCondition** - Task stops itself while external thread also calls stop()
- [ ] **testConditionalSelfStop** - Task stops itself based on some condition (e.g., counter threshold)

### 5. Concurrency Tests
- [ ] **testMultipleConcurrentTasks** - Run 10+ tasks simultaneously
- [ ] **testStopFromAnotherThread** - Stop task from different thread
- [ ] **testRaceConditionOnStart** - Multiple threads call start() simultaneously
- [ ] **testRaceConditionOnStop** - Multiple threads call stop() simultaneously
- [ ] **testTaskAccessesSharedState** - Multiple tasks modifying shared counter

### 6. Performance Tests
- [ ] **testHighFrequencyPeriodic** - 100ms periodic delay, 100 iterations
- [ ] **testHighFrequencyRepetetive** - 100ms repetitive delay, 100 iterations
- [ ] **testManyShortTasks** - Many tasks executing in quick succession
- [ ] **testMemoryLeakOnRepeatedCreation** - Create/destroy many tasks

### 7. Edge Cases
- [ ] **testZeroDurationTask** - Task that completes instantly
- [ ] **testVeryLongRunningTask** - Task that runs for 10+ seconds
- [ ] **testPeriodicWithZeroDelay** - Duration.ZERO for periodic delay
- [ ] **testRepetetiveWithZeroDelay** - Duration.ZERO for repetitive delay
- [ ] **testStopImmediatelyAfterStart** - Stop within milliseconds of starting
- [ ] **testEmptyRunnableTask** - Task with no-op runnable
- [ ] **testRestartDuringExecution** - Stop and restart while task is executing
- [ ] **testRestartImmediatelyAfterStop** - Restart within milliseconds of stopping
- [ ] **testBuildWithoutStart** - Build task but never call start(), verify no execution

---

## Executor Implementation Tests

### Strategy
All core functionality tests should be executed with **both** executor implementations:
1. `TimedTaskThreadExecutor` (uses virtual threads by default)
2. `TimedTaskPoolExecutor` (uses custom thread pool)

### Implementation Approach

#### Parameterized Tests (Recommended)
Use JUnit 5's `@ParameterizedTest` with a custom `@MethodSource` to provide both executors:

```java
@ParameterizedTest
@MethodSource("executorProvider")
void testState(AbstractTimedTaskExecutor executor) {
    var timedTask = executor.createTimedTask(createTask(1)).build();
    assertEquals(State.NOT_RUNNING, timedTask.getState());

    // Start the task
    assertTrue(timedTask.start());
    assertEquals(State.RUNNING, timedTask.getState());

    // Stop the task
    timedTask.stop();
    assertEquals(State.NOT_RUNNING, timedTask.getState());

    // Test restartability
    assertTrue(timedTask.start());
    assertEquals(State.RUNNING, timedTask.getState());
}

static Stream<Arguments> executorProvider() {
    return Stream.of(
        Arguments.of(new TimedTaskThreadExecutor()),
        Arguments.of(new TimedTaskPoolExecutor("test-pool"))
    );
}
```
## Test Organization

### File Structure
```
src/my/custom/stuff/timer/
├── TimedTaskTest.java                    (Common/shared tests)
└── TimedTaskParameterizedTest.java       (Parameterized tests for both executors)
```

### Test Lifecycle
- **@BeforeEach** - Reset counters, create fresh executors
- **@AfterEach** - Stop all running tasks, cleanup resources, shutdown pools
- **@BeforeAll** - One-time setup if needed
- **@AfterAll** - Final cleanup, verify no threads leaked

---

## Test Utilities

### Helper Methods to Create
```java
// Task factories (Consumer<TimedTask> pattern)
private Consumer<TimedTask> createTask(int durationSeconds)
private Consumer<TimedTask> createExceptionThrowingTask()
private Consumer<TimedTask> createInterruptibleTask()
private Consumer<TimedTask> createSelfStoppingTask(int executionCountBeforeStop)
private Consumer<TimedTask> createConditionalTask(Predicate<TimedTask> condition)

// Assertion helpers
private void assertTimingWithinTolerance(Duration expected, Duration actual, Duration tolerance)
private void waitForTaskExecution(int expectedCount, Duration timeout)
private void assertEventualState(TimedTask task, State expectedState, Duration timeout)

// Executor management
private void shutdownExecutor(AbstractTimedTaskExecutor executor)
private void waitForAllTasksCompletion(Duration timeout)
```

---

## Test Execution Plan

### Phase 1: Core Coverage (Priority: HIGH)
1. Implement parameterized tests for all existing tests with both executors
2. Add state management tests
3. Add error handling tests
4. Add basic executor-specific tests

### Phase 2: Extended Coverage (Priority: MEDIUM)
1. Add timing and precision tests
2. Add edge case tests
3. Add named task tests
4. Add configuration switching tests

### Phase 3: Advanced Coverage (Priority: LOW)
1. Add concurrency tests
2. Add performance tests
3. Add stress tests
4. Add resource leak detection

---

## Success Criteria

### Code Coverage Goals
- **Line Coverage**: > 90%
- **Branch Coverage**: > 85%
- **Method Coverage**: 100%

### Quality Metrics
- All tests pass consistently (no flaky tests)
- No test interference (tests can run in parallel)
- Clear test failure messages

### Documentation
- Each test has clear Javadoc explaining what it tests
- Complex scenarios have inline comments
- Test data is clearly explained

---

## Notes and Considerations

### Timing Sensitivity
- Use appropriate tolerances for timing assertions (e.g., ±50-100ms)
- Consider using `Thread.sleep()` judiciously to avoid slow tests
- Mock time if possible for deterministic testing

### Resource Cleanup
- Ensure all tasks are stopped in `@AfterEach`
- Pool executors must be properly shutdown
- Monitor for thread leaks using thread dumps

### Test Isolation
- Each test should be independent
- Use fresh executor instances per test
- Reset all shared state (counters, etc.)

### JUnit Annotations Used
- `@Test` - Standard test methods
- `@ParameterizedTest` - For executor variants
- `@BeforeEach` / `@AfterEach` - Setup/teardown
- `@Nested` - For organizing related tests
- `@DisplayName` - For readable test names
- `@Timeout` - To prevent hanging tests
- `@RepeatedTest` - For flakiness detection

---

## Appendix: Current Issues Found

### Bug: @Before vs @BeforeEach
The current test file uses `@Before` (JUnit 4) but imports JUnit 5 (`org.junit.jupiter.api.Test`). This should be changed to `@BeforeEach` for consistency.

### Missing Test Utilities
The test class lacks helper methods for:
- Timing assertions
- State verification with timeout
- Proper cleanup of executors

### Incomplete Error Handling
The `TimedTask.Timer.executeTask()` method has a TODO for exception handling that should be addressed and tested.
