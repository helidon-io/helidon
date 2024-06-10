/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.se;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import io.helidon.faulttolerance.Async;
import io.helidon.faulttolerance.Bulkhead;
import io.helidon.faulttolerance.CircuitBreaker;
import io.helidon.faulttolerance.Fallback;
import io.helidon.faulttolerance.FaultTolerance;
import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.Timeout;

@SuppressWarnings("ALL")
class FaultToleranceSnippets {

    // stub
    <T> T retryOnFailure() {
        return (T) null;
    }

    // stub
    <T> T mayTakeVeryLong() {
        return (T) null;
    }

    // stub
    <T> T mayFail() {
        return (T) null;
    }

    // stub
    <T> T usesResources() {
        return (T) null;
    }

    <T> void snippet_1() {
        // tag::snippet_1[]
        Retry retry = Retry.builder()
                .retryPolicy(Retry.JitterRetryPolicy.builder()
                                     .calls(3)
                                     .delay(Duration.ofMillis(100))
                                     .build())
                .build();
        T result = retry.invoke(this::retryOnFailure);
        // end::snippet_1[]
    }

    <T> void snippet_2() {
        // tag::snippet_2[]
        T result = Timeout.create(Duration.ofMillis(10))
                .invoke(this::mayTakeVeryLong);
        // end::snippet_2[]
    }

    <T> void snippet_3() {
        T lastKnownValue = null;
        // tag::snippet_3[]
        T result = Fallback.createFromMethod(throwable -> lastKnownValue)
                .invoke(this::mayFail);
        // end::snippet_3[]
    }

    <T> void snippet_4() {
        // tag::snippet_4[]
        CircuitBreaker breaker = CircuitBreaker.builder()
                .volume(10)
                .errorRatio(30)
                .delay(Duration.ofMillis(200))
                .successThreshold(2)
                .build();
        T result = breaker.invoke(this::mayFail);
        // end::snippet_4[]
    }

    <T> void snippet_5() {
        // tag::snippet_5[]
        Bulkhead bulkhead = Bulkhead.builder()
                .limit(3)
                .queueLength(5)
                .build();
        T result = bulkhead.invoke(this::usesResources);
        // end::snippet_5[]
    }

    <T> void snippet_6() {
        // tag::snippet_6[]
        CompletableFuture<Thread> cf = Async.create().invoke(Thread::currentThread);
        cf.thenAccept(t -> System.out.println("Async task executed in thread " + t));
        // end::snippet_6[]
    }

    <T> void snippet_7() {
        T lastKnownValue = null;
        // tag::snippet_7[]
        FaultTolerance.TypedBuilder<T> builder = FaultTolerance.typedBuilder();

        Timeout timeout = Timeout.create(Duration.ofMillis(10));
        builder.addTimeout(timeout);

        Retry retry = Retry.builder()
                .retryPolicy(Retry.JitterRetryPolicy.builder()
                                     .calls(3)
                                     .delay(Duration.ofMillis(100))
                                     .build())
                .build();
        builder.addRetry(retry);

        Fallback<T> fallback = Fallback.createFromMethod(throwable -> lastKnownValue);
        builder.addFallback(fallback);

        T result = builder.build().invoke(this::mayTakeVeryLong);
        // end::snippet_7[]
    }
}
