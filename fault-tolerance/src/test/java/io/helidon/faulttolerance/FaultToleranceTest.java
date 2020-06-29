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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FaultToleranceTest {

    @Test
    void testCustomCombination() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .build();

        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(0)
                .build();

        TypedHandler<String> faultTolerance = FaultTolerance.builder()
                .addBreaker(breaker)
                .addBulkhead(bulkhead)
                .addTimeout(Timeout.builder().timeout(Duration.ofMillis(100)).build())
                .addFallback(Fallback.<String>builder()
                                     .fallback(this::fallback)
                                     .build())
                .build();

        Single<String> result = faultTolerance.invoke(this::primary);
        assertThat(result.await(1, TimeUnit.SECONDS), is(MyException.class.getName()));

        breaker.state(CircuitBreaker.State.OPEN);
        result = faultTolerance.invoke(this::primary);
        assertThat(result.await(1, TimeUnit.SECONDS), is(CircuitBreakerOpenException.class.getName()));

        breaker.state(CircuitBreaker.State.CLOSED);

        Manual m = new Manual();
        Single<String> manualResult = bulkhead.invoke(m::call);

        result = faultTolerance.invoke(this::primary);
        assertThat(result.await(1, TimeUnit.SECONDS), is(BulkheadException.class.getName()));

        m.future.complete("result");
        manualResult.await(1, TimeUnit.SECONDS);

        m = new Manual();
        result = faultTolerance.invoke(m::call);
        assertThat(result.await(1, TimeUnit.SECONDS), is(TimeoutException.class.getName()));

        m.future.complete("hu");
    }

    private Single<String> primary() {
        return Single.error(new MyException());
    }

    private Single<String> fallback(Throwable throwable) {
        return Single.just(throwable.getClass().getName());
    }

    static <T extends Throwable> T completionException(Single<?> result, Class<T> expected) {
        CompletionException completionException = assertThrows(CompletionException.class,
                                                               () -> result.await(1, TimeUnit.SECONDS));
        Throwable cause = completionException.getCause();
        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(expected));

        return expected.cast(cause);
    }

    private class Manual {
        private final CompletableFuture<String> future = new CompletableFuture<>();

        private CompletionStage<String> call() {
            return future;
        }
    }

    private static class MyException extends RuntimeException {

    }

}
