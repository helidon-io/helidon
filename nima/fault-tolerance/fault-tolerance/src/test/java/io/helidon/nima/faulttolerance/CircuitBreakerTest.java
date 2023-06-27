/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.faulttolerance;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitBreakerTest {

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

    private void breakerOpen(CircuitBreaker breaker) {
        Request good = new Request();
        assertThrows(CircuitBreakerOpenException.class, () -> breaker.invoke(good::invoke));
    }

    private void bad(CircuitBreaker breaker) {
        Failing failing = new Failing(new IllegalStateException("Fail"));
        assertThrows(IllegalStateException.class, () -> breaker.invoke(failing::invoke));
    }

    private void good(CircuitBreaker breaker) {
        Request good = new Request();
        breaker.invoke(good::invoke);
    }

    private static class Failing {
        private final RuntimeException exception;

        Failing(RuntimeException exception) {
            this.exception = exception;
        }

        Integer invoke() {
            throw exception;
        }
    }

    private static class Request {
        Request() {
        }

        Integer invoke() {
            return 1;
        }
    }
}
