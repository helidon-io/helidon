/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FaultToleranceTest {

    @Test
    @Disabled
        // TODO fix
    void testCustomCombination() throws ExecutionException, InterruptedException, TimeoutException {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .build();

        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(0)
                .build();

        FtHandlerTyped<String> faultTolerance = FaultTolerance.builder()
                .addBreaker(breaker)
                .addBulkhead(bulkhead)
                .addTimeout(Timeout.builder().timeout(Duration.ofMillis(100)).build())
                .addFallback(Fallback.<String>builder()
                                     .fallback(this::fallback)
                                     .build())
                .build();

        String result = faultTolerance.invoke(this::primary);
        assertThat(result, is(MyException.class.getName()));

        breaker.state(CircuitBreaker.State.OPEN);
        result = faultTolerance.invoke(this::primary);
        assertThat(result, is(CircuitBreakerOpenException.class.getName()));

        breaker.state(CircuitBreaker.State.CLOSED);

        Manual m = new Manual();
        CompletableFuture<String> mFuture = CompletableFuture.supplyAsync(() -> bulkhead.invoke(m::call));

        assertThrows(BulkheadException.class, () -> faultTolerance.invoke(this::primary));

        m.future.complete("result");
        mFuture.get(1, TimeUnit.SECONDS);

        //        m = new Manual();
        //        result = faultTolerance.invoke(m::call);
        //        assertThat(result.await(1, TimeUnit.SECONDS), is(TimeoutException.class.getName()));
        //
        //        m.future.complete("hu");
    }

    private String primary() {
        throw new MyException();
    }

    private String fallback(Throwable throwable) {
        return throwable.getClass().getName();
    }

    private static class Manual {
        private final CompletableFuture<String> future = new CompletableFuture<>();

        private String call() {
            try {
                return future.get();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class MyException extends RuntimeException {

    }

}
