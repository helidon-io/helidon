/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitBreakerTest extends CircuitBreakerBaseTest {

    private static final long WAIT_TIMEOUT_MILLIS = 2000;

    @Test
    void testCircuitBreaker() throws InterruptedException, ExecutionException, TimeoutException {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .volume(10)
                .errorRatio(20)
                .delay(Duration.ofMillis(200))
                .successThreshold(2)
                .build();

        good(breaker);
        good(breaker);
        bad(breaker);
        good(breaker);
        good(breaker);
        good(breaker);
        good(breaker);
        good(breaker);
        bad(breaker);
        bad(breaker);       // should open - window complete

        breakerOpen(breaker);

        assertThat(breaker.state(), is(CircuitBreaker.State.OPEN));

        // need to wait until half open
        Future<Boolean> schedule = ((CircuitBreakerImpl) breaker).schedule();
        schedule.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        assertThat(breaker.state(), is(CircuitBreaker.State.HALF_OPEN));

        good(breaker);
        good(breaker);

        assertThat(breaker.state(), is(CircuitBreaker.State.CLOSED));

        good(breaker);
        good(breaker);
        bad(breaker);
        good(breaker);
        good(breaker);
        good(breaker);
        good(breaker);
        good(breaker);
        bad(breaker);
        bad(breaker);       // should open - window complete

        breakerOpen(breaker);

        assertThat(breaker.state(), is(CircuitBreaker.State.OPEN));
    }

    @Test
    void testOpenOnLastSuccess() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .volume(4)
                .errorRatio(75)
                .build();

        bad(breaker);
        bad(breaker);
        bad(breaker);
        good(breaker);

        assertThat(breaker.state(), is(CircuitBreaker.State.OPEN));
    }

    @Test
    void callerCancellationFollowsConfigurationInHalfOpenState()
            throws ExecutionException, InterruptedException, TimeoutException {
        CircuitBreaker applyOnBreaker = CircuitBreaker.builder()
                .volume(1)
                .errorRatio(100)
                .delay(Duration.ofMillis(200))
                .successThreshold(1)
                .addApplyOn(IllegalStateException.class)
                .addApplyOn(InterruptedException.class)
                .build();
        CircuitBreaker skipOnBreaker = CircuitBreaker.builder()
                .volume(1)
                .errorRatio(100)
                .delay(Duration.ofMillis(200))
                .successThreshold(1)
                .addApplyOn(IllegalStateException.class)
                .addSkipOn(InterruptedException.class)
                .build();

        moveToHalfOpen(applyOnBreaker);
        assertCallerCancellationState(applyOnBreaker, new InterruptedException("cancelled"), CircuitBreaker.State.OPEN);
        moveToHalfOpen(skipOnBreaker);
        assertCallerCancellationState(skipOnBreaker, new InterruptedException("cancelled"), CircuitBreaker.State.HALF_OPEN);
        skipOnBreaker.invoke(() -> "success");
        assertThat(skipOnBreaker.state(), is(CircuitBreaker.State.CLOSED));
    }

    @Test
    void callerCancellationUsesApplyOnInClosedState() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .volume(1)
                .errorRatio(100)
                .delay(Duration.ofDays(1))
                .addApplyOn(InterruptedException.class)
                .build();

        assertCallerCancellationState(breaker, new InterruptedException("cancelled"), CircuitBreaker.State.OPEN);
    }

    @Test
    void wrappedCallerCancellationUsesApplyOnInClosedState() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .volume(1)
                .errorRatio(100)
                .delay(Duration.ofDays(1))
                .addApplyOn(IOException.class)
                .build();
        try {
            assertCallerCancellationState(breaker,
                                          new IOException("I/O", new InterruptedException("cancelled")),
                                          CircuitBreaker.State.OPEN);
        } finally {
            breaker.state(CircuitBreaker.State.CLOSED);
        }
    }

    @Test
    void wrappedCallerCancellationUsesSkipOnInClosedState() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .volume(1)
                .errorRatio(100)
                .delay(Duration.ofDays(1))
                .addApplyOn(Throwable.class)
                .addSkipOn(IOException.class)
                .build();
        try {
            assertCallerCancellationState(breaker,
                                          new IOException("I/O", new InterruptedException("cancelled")),
                                          CircuitBreaker.State.CLOSED);
        } finally {
            breaker.state(CircuitBreaker.State.CLOSED);
        }
    }

    @Test
    void wrappedCallerCancellationUsesApplyOnInHalfOpenState()
            throws InterruptedException, ExecutionException, TimeoutException {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .volume(1)
                .errorRatio(100)
                .delay(Duration.ofMillis(200))
                .successThreshold(1)
                .addApplyOn(IllegalStateException.class)
                .addApplyOn(IOException.class)
                .build();
        try {
            moveToHalfOpen(breaker);
            assertCallerCancellationState(breaker,
                                          new IOException("I/O", new InterruptedException("cancelled")),
                                          CircuitBreaker.State.OPEN);
        } finally {
            breaker.state(CircuitBreaker.State.CLOSED);
        }
    }

    @Test
    void wrappedCallerCancellationUsesSkipOnInHalfOpenState()
            throws InterruptedException, ExecutionException, TimeoutException {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .volume(1)
                .errorRatio(100)
                .delay(Duration.ofMillis(200))
                .successThreshold(1)
                .addApplyOn(Throwable.class)
                .addSkipOn(IOException.class)
                .build();
        try {
            moveToHalfOpen(breaker);
            assertCallerCancellationState(breaker,
                                          new IOException("I/O", new InterruptedException("cancelled")),
                                          CircuitBreaker.State.HALF_OPEN);
            assertThat(breaker.invoke(() -> "success"), is("success"));
            assertThat(breaker.state(), is(CircuitBreaker.State.CLOSED));
        } finally {
            breaker.state(CircuitBreaker.State.CLOSED);
        }
    }

    @Test
    void wrappedCallerCancellationIsNeutralByDefault()
            throws InterruptedException, ExecutionException, TimeoutException {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .volume(1)
                .errorRatio(100)
                .delay(Duration.ofMillis(200))
                .successThreshold(1)
                .build();
        try {
            IOException failure = new IOException("I/O", new InterruptedException("cancelled"));
            assertCallerCancellationState(breaker, failure, CircuitBreaker.State.CLOSED);
            moveToHalfOpen(breaker);
            assertCallerCancellationState(breaker, failure, CircuitBreaker.State.HALF_OPEN);
            assertThat(breaker.invoke(() -> "success"), is("success"));
            assertThat(breaker.state(), is(CircuitBreaker.State.CLOSED));
        } finally {
            breaker.state(CircuitBreaker.State.CLOSED);
        }
    }

    private static void assertCallerCancellationState(CircuitBreaker breaker,
                                                     Throwable failure,
                                                     CircuitBreaker.State expectedState) {
        Thread.currentThread().interrupt();
        try {
            SupplierException exception = assertThrows(SupplierException.class,
                                                      () -> breaker.invoke(() -> {
                                                          throw new SupplierException(failure);
                                                      }));
            assertThat(exception.getCause(), sameInstance(failure));
            assertThat(Thread.currentThread().isInterrupted(), is(true));
            assertThat(breaker.state(), is(expectedState));
        } finally {
            Thread.interrupted();
        }
    }

    private static void moveToHalfOpen(CircuitBreaker breaker)
            throws InterruptedException, ExecutionException, TimeoutException {
        assertThrows(IllegalStateException.class,
                     () -> breaker.invoke(() -> {
                         throw new IllegalStateException("failure");
                     }));
        Future<Boolean> schedule = ((CircuitBreakerImpl) breaker).schedule();
        schedule.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertThat(breaker.state(), is(CircuitBreaker.State.HALF_OPEN));
    }
}
