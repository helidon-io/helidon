/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitBreakerTest {
    @Test
    void testCircuitBreaker() throws InterruptedException {
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
        goodMulti(breaker);

        // should open the breaker
        bad(breaker);

        breakerOpen(breaker);
        breakerOpenMulti(breaker);

        assertThat(breaker.state(), is(CircuitBreaker.State.OPEN));

        // need to wait until half open
        int count = 0;
        while (count++ < 10) {
            Thread.sleep(50);
            if (breaker.state() == CircuitBreaker.State.HALF_OPEN) {
                break;
            }
        }

        assertThat(breaker.state(), is(CircuitBreaker.State.HALF_OPEN));

        good(breaker);
        good(breaker);

        assertThat(breaker.state(), is(CircuitBreaker.State.CLOSED));

        // should open the breaker
        bad(breaker);
        bad(breaker);

        assertThat(breaker.state(), is(CircuitBreaker.State.OPEN));

        // need to wait until half open
        count = 0;
        while (count++ < 10) {
            Thread.sleep(50);
            if (breaker.state() == CircuitBreaker.State.HALF_OPEN) {
                break;
            }
        }

        good(breaker);
        badMulti(breaker);

        assertThat(breaker.state(), is(CircuitBreaker.State.OPEN));
    }

    private void breakerOpen(CircuitBreaker breaker) {
        Request good = new Request();
        Single<Integer> result = breaker.invoke(good::invoke);
        CompletionException exception = assertThrows(CompletionException.class, () -> result.await(1, TimeUnit.SECONDS));

        Throwable cause = exception.getCause();

        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(CircuitBreakerOpenException.class));
    }

    private void breakerOpenMulti(CircuitBreaker breaker) {
        Multi<Integer> good = Multi.just(0, 1, 2);
        Multi<Integer> result = breaker.invokeMulti(() -> good);
        CompletionException exception = assertThrows(CompletionException.class,
                                                     () -> result.collectList().await(1, TimeUnit.SECONDS));

        Throwable cause = exception.getCause();

        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(CircuitBreakerOpenException.class));
    }

    private void bad(CircuitBreaker breaker) {
        Failing failing = new Failing(new IllegalStateException("Fail"));
        Single<Integer> failedResult = breaker.invoke(failing::invoke);
        CompletionException exception = assertThrows(CompletionException.class, () -> failedResult.await(1, TimeUnit.SECONDS));

        Throwable cause = exception.getCause();

        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(IllegalStateException.class));

    }

    private void badMulti(CircuitBreaker breaker) {
        Multi<Integer> failing = Multi.error(new IllegalStateException("Fail"));
        Multi<Integer> failedResult = breaker.invokeMulti(() -> failing);

        CompletionException exception = assertThrows(CompletionException.class,
                                                     () -> failedResult.collectList().await(1, TimeUnit.SECONDS));

        Throwable cause = exception.getCause();

        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(IllegalStateException.class));

    }

    private void good(CircuitBreaker breaker) {
        Request good = new Request();
        Single<Integer> result = breaker.invoke(good::invoke);
        result.await(1, TimeUnit.SECONDS);
    }

    private void goodMulti(CircuitBreaker breaker) {
        Multi<Integer> good = Multi.just(0, 1, 2);
        Multi<Integer> result = breaker.invokeMulti(() -> good);
        List<Integer> list = result.collectList().await(1, TimeUnit.SECONDS);

        assertThat(list, contains(0, 1, 2));
    }

    private static class Failing {
        private final Exception exception;

        Failing(Exception exception) {
            this.exception = exception;
        }

        CompletionStage<Integer> invoke() {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static class Request {
        Request() {
        }

        CompletionStage<Integer> invoke() {
            return CompletableFuture.completedFuture(1);
        }
    }
}
