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

package io.helidon.faulttolerance;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class FaultToleranceTest {

    @Test
    void testCustomCombination() {
        CircuitBreaker breaker = CircuitBreaker.create(CircuitBreakerConfig.create());

        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(0)
                .build();

        FtHandlerTyped<String> faultTolerance = FaultTolerance.builder()
                .addBreaker(breaker)
                .addBulkhead(bulkhead)
                .addTimeout(TimeoutConfig.builder().timeout(Duration.ofMillis(1000)).build())
                .addFallback(Fallback.<String>create(builder -> builder
                        .fallback(this::fallback)))
                .build();

        // First call should not open breaker and execute call back
        String result = faultTolerance.invoke(this::primary);
        assertThat(result, is(MyException.class.getName()));        // callback called

        // Manually open breaker
        breaker.state(CircuitBreaker.State.OPEN);
        assertThat(breaker.state(), is(CircuitBreaker.State.OPEN));

        // Next call should fail on breaker but still execute fallback
        result = faultTolerance.invoke(this::primary);
        assertThat(result, is(CircuitBreakerOpenException.class.getName()));

        // Manually close breaker
        breaker.state(CircuitBreaker.State.CLOSED);
        assertThat(breaker.state(), is(CircuitBreaker.State.CLOSED));

        // Second call forces timeout by calling a supplier that blocks indefinitely
        Manual m = new Manual();
        result = faultTolerance.invoke(m::call);
        assertThat(result, is(TimeoutException.class.getName()));   // callback called
    }

    private String primary() {
        throw new MyException();
    }

    private String fallback(Throwable throwable) {
        if (throwable instanceof FaultToleranceException || throwable.getCause() == null) {
            return throwable.getClass().getName();
        }

        throwable = throwable.getCause();
        return throwable.getClass().getName();
    }

    private static class Manual {
        private final CompletableFuture<String> future = new CompletableFuture<>();

        private String call() {
            try {
                return future.get();        // blocks indefinitely
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class MyException extends RuntimeException {
    }
}
