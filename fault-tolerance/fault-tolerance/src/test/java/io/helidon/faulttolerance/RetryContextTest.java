/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.faulttolerance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryContextTest {
    @Test
    void testAttemptContext() {
        List<RetryContext> contexts = new ArrayList<>();
        TestFailure first = new TestFailure("first");
        TestFailure second = new TestFailure("second");
        Retry retry = Retry.builder()
                .retryPolicy(Retry.DelayingRetryPolicy.noDelay(3))
                .overallTimeout(Duration.ofSeconds(10))
                .build();

        int result = retry.invoke(context -> {
            contexts.add(context);
            if (context.attempt() == 1) {
                throw first;
            }
            if (context.attempt() == 2) {
                throw second;
            }
            return context.attempt();
        });

        assertThat(result, is(3));
        assertThat(contexts.size(), is(3));
        assertThat(contexts.get(0).attempt(), is(1));
        assertThat(contexts.get(0).elapsedTime(), greaterThanOrEqualTo(Duration.ZERO));
        assertThat(contexts.get(0).previousDelay(), is(Duration.ZERO));
        assertThat(contexts.get(0).previousThrowable(), optionalEmpty());
        assertThat(contexts.get(1).attempt(), is(2));
        assertThat(contexts.get(1).previousDelay(), is(Duration.ZERO));
        assertThat(contexts.get(1).previousThrowable(), optionalValue(sameInstance(first)));
        assertThat(contexts.get(2).attempt(), is(3));
        assertThat(contexts.get(2).previousDelay(), is(Duration.ZERO));
        assertThat(contexts.get(2).previousThrowable(), optionalValue(sameInstance(second)));
    }

    @Test
    void testCallsExhausted() {
        AtomicInteger calls = new AtomicInteger();
        Retry retry = Retry.builder()
                .retryPolicy(Retry.DelayingRetryPolicy.builder()
                                     .calls(2)
                                     .delay(Duration.ofMillis(17))
                                     .delayFactor(1)
                                     .build())
                .overallTimeout(Duration.ofSeconds(10))
                .build();

        RetryException exception = assertThrows(RetryException.class,
                                                () -> retry.invoke(context -> {
                                                    calls.incrementAndGet();
                                                    throw new TestFailure("failure-" + context.attempt());
                                                }, RetryContextTest::await));

        assertThat(exception.outcome().termination(), is(RetryOutcome.Termination.RETRIES_EXHAUSTED));
        assertThat(exception.outcome().attempts(), is(2));
        assertThat(exception.outcome().elapsedTime(), greaterThanOrEqualTo(Duration.ZERO));
        assertThat(exception.outcome().lastDelay(), is(Duration.ofMillis(17)));
        assertThat(exception.outcome().lastThrowable(), optionalValue(sameInstance(exception.getCause())));
        assertThat(calls.get(), is(2));
    }

    @Test
    void testNotRetryable() {
        TerminalFailure failure = new TerminalFailure();
        Retry retry = Retry.builder()
                .retryPolicy(Retry.DelayingRetryPolicy.noDelay(3))
                .overallTimeout(Duration.ofSeconds(10))
                .addApplyOn(TestFailure.class)
                .build();

        RetryException exception = assertThrows(RetryException.class,
                                                () -> retry.invoke(context -> {
                                                    throw failure;
                                                }));

        assertThat(exception.outcome().termination(), is(RetryOutcome.Termination.NOT_RETRYABLE));
        assertThat(exception.outcome().attempts(), is(1));
        assertThat(exception.outcome().lastThrowable(), optionalValue(sameInstance(failure)));
        assertThat(exception.getCause(), sameInstance(failure));
    }

    @Test
    void testTimedOutBeforeWait() {
        TestFailure failure = new TestFailure("timed-out");
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> Optional.of(1_000L))
                .overallTimeout(Duration.ofMillis(1))
                .build();

        RetryException exception = assertThrows(RetryException.class,
                                                () -> retry.invoke(context -> {
                                                    throw failure;
                                                }));

        assertThat(exception.outcome().termination(), is(RetryOutcome.Termination.TIMED_OUT));
        assertThat(exception.outcome().attempts(), is(1));
        assertThat(exception.outcome().lastThrowable(), optionalValue(sameInstance(failure)));
        assertThat(exception.getCause(), sameInstance(failure));
    }

    @Test
    void testCustomWaitAndCancellation() {
        TestFailure failure = new TestFailure("cancelled");
        AtomicReference<Duration> delay = new AtomicReference<>();
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> Optional.of(17L))
                .overallTimeout(Duration.ofSeconds(10))
                .build();

        RetryException exception = assertThrows(RetryException.class,
                                                () -> retry.invoke(context -> {
                                                    throw failure;
                                                }, proposedDelay -> {
                                                    delay.set(proposedDelay);
                                                    return false;
                                                }));

        assertThat(exception.outcome().termination(), is(RetryOutcome.Termination.CANCELLED));
        assertThat(exception.outcome().attempts(), is(1));
        assertThat(exception.outcome().lastThrowable(), optionalValue(sameInstance(failure)));
        assertThat(exception.getCause(), sameInstance(failure));
        assertThat(delay.get(), is(Duration.ofMillis(17)));
    }

    @Test
    void testCustomWaitContinues() {
        TestFailure failure = new TestFailure("retry");
        AtomicReference<RetryContext> secondInvocationContext = new AtomicReference<>();
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> Optional.of(17L))
                .overallTimeout(Duration.ofSeconds(10))
                .build();

        int result = retry.invoke(context -> {
            if (context.attempt() == 1) {
                throw failure;
            }
            assertThat(context.previousDelay(), is(Duration.ofMillis(17)));
            assertThat(context.previousThrowable(), optionalValue(sameInstance(failure)));
            secondInvocationContext.set(context);
            return context.attempt();
        }, delay -> {
            assertThat(delay, is(Duration.ofMillis(17)));
            return await(delay);
        });

        assertThat(result, is(2));
        assertThat(secondInvocationContext.get().attempt(), is(2));
        assertThat(secondInvocationContext.get().previousThrowable(), optionalValue(sameInstance(failure)));
    }

    @Test
    void testCustomWaitFailure() {
        TestFailure invocationFailure = new TestFailure("invocation");
        TerminalFailure waitFailure = new TerminalFailure();
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> Optional.of(1L))
                .overallTimeout(Duration.ofSeconds(10))
                .build();

        RetryException exception = assertThrows(RetryException.class,
                                                () -> retry.invoke(context -> {
                                                    throw invocationFailure;
                                                }, delay -> {
                                                    throw waitFailure;
                                                }));

        assertThat(exception.outcome().termination(), is(RetryOutcome.Termination.WAIT_FAILED));
        assertThat(exception.outcome().attempts(), is(1));
        assertThat(exception.outcome().lastThrowable(), optionalValue(sameInstance(invocationFailure)));
        assertThat(exception.getCause(), sameInstance(waitFailure));
    }

    @Test
    void testRetryPolicyFailure() {
        TestFailure invocationFailure = new TestFailure("invocation");
        TerminalFailure policyFailure = new TerminalFailure();
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> {
                    throw policyFailure;
                })
                .overallTimeout(Duration.ofSeconds(10))
                .build();

        RetryException exception = assertThrows(RetryException.class,
                                                () -> retry.invoke(context -> {
                                                    throw invocationFailure;
                                                }));

        assertThat(exception.outcome().termination(), is(RetryOutcome.Termination.RETRY_POLICY_FAILED));
        assertThat(exception.outcome().attempts(), is(1));
        assertThat(exception.outcome().lastThrowable(), optionalValue(sameInstance(invocationFailure)));
        assertThat(exception.getCause(), sameInstance(policyFailure));
    }

    @Test
    void testInterruptedWaitRestoresInterruptFlag() {
        TestFailure failure = new TestFailure("interrupted");
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> Optional.of(1_000L))
                .overallTimeout(Duration.ofSeconds(10))
                .build();

        Thread.currentThread().interrupt();
        try {
            RetryException exception = assertThrows(RetryException.class,
                                                    () -> retry.invoke(context -> {
                                                        throw failure;
                                                    }));

            assertThat(exception.outcome().termination(), is(RetryOutcome.Termination.INTERRUPTED));
            assertThat(exception.outcome().attempts(), is(1));
            assertThat(exception.outcome().lastThrowable(), optionalValue(sameInstance(failure)));
            assertThat(exception.getCause() instanceof InterruptedException, is(true));
            assertThat(Thread.currentThread().isInterrupted(), is(true));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void testWrappedInterruptedInvocationRestoresInterruptFlag() {
        InterruptedException interrupted = new InterruptedException("interrupted invocation");
        RuntimeException failure = new RuntimeException(interrupted);
        Retry retry = Retry.builder()
                .retryPolicy(Retry.DelayingRetryPolicy.noDelay(3))
                .overallTimeout(Duration.ofSeconds(10))
                .build();

        try {
            RetryException exception = assertThrows(RetryException.class,
                                                    () -> retry.invoke(context -> {
                                                        throw failure;
                                                    }));

            assertThat(exception.outcome().termination(), is(RetryOutcome.Termination.INTERRUPTED));
            assertThat(exception.outcome().attempts(), is(1));
            assertThat(exception.outcome().lastThrowable(), optionalValue(sameInstance(failure)));
            assertThat(exception.getCause(), sameInstance(interrupted));
            assertThat(Thread.currentThread().isInterrupted(), is(true));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void testLegacyFailuresAreBounded() {
        int calls = 100;
        Retry retry = Retry.builder()
                .retryPolicy(Retry.DelayingRetryPolicy.noDelay(calls))
                .overallTimeout(Duration.ofSeconds(10))
                .build();
        AtomicInteger invocation = new AtomicInteger();

        TestFailure exception = assertThrows(TestFailure.class,
                                             () -> retry.invoke(() -> {
                                                 throw new TestFailure("failure-" + invocation.incrementAndGet());
                                             }));

        assertThat(invocation.get(), is(calls));
        assertThat(exception.getSuppressed().length, lessThanOrEqualTo(15));
    }

    @Test
    void testLegacyTimeoutExceptionIsPreserved() {
        TestFailure failure = new TestFailure("legacy timeout");
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> Optional.of(1_000L))
                .overallTimeout(Duration.ofMillis(1))
                .build();

        RetryTimeoutException exception = assertThrows(RetryTimeoutException.class,
                                                       () -> retry.invoke(() -> {
                                                           throw failure;
                                                       }));

        assertThat(exception.lastRetryException(), sameInstance(failure));
        assertThat(exception.getCause(), sameInstance(failure));
    }

    @Test
    void testLegacyInterruptedExceptionTypeIsPreserved() {
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> Optional.of(1_000L))
                .overallTimeout(Duration.ofSeconds(10))
                .build();

        Thread.currentThread().interrupt();
        try {
            RuntimeException exception = assertThrows(RuntimeException.class,
                                                       () -> retry.invoke(() -> {
                                                           throw new TestFailure("interrupted");
                                                       }));

            assertThat(exception, not(instanceOf(RetryException.class)));
            assertThat(exception.getCause(), instanceOf(InterruptedException.class));
            assertThat(Thread.currentThread().isInterrupted(), is(true));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void testLargeTimeoutDoesNotOverflow() {
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> Optional.of(1L))
                .overallTimeout(Duration.ofSeconds(Long.MAX_VALUE))
                .build();

        RetryException exception = assertThrows(RetryException.class,
                                                () -> retry.invoke(context -> {
                                                    throw new TestFailure("large duration");
                                                }, delay -> false));

        assertThat(exception.outcome().termination(), is(RetryOutcome.Termination.CANCELLED));
    }

    @Test
    void testLargeDelayDoesNotOverflow() {
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> Optional.of(Long.MAX_VALUE))
                .overallTimeout(Duration.ofSeconds(10))
                .build();

        RetryException exception = assertThrows(RetryException.class,
                                                () -> retry.invoke(context -> {
                                                    throw new TestFailure("large delay");
                                                }, delay -> false));

        assertThat(exception.outcome().termination(), is(RetryOutcome.Termination.TIMED_OUT));
    }

    private static boolean await(Duration delay) {
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static final class TestFailure extends RuntimeException {
        private TestFailure(String message) {
            super(message);
        }
    }

    private static final class TerminalFailure extends RuntimeException {
    }
}
