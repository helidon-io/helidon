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

package io.helidon.webserver.examples.faulttolerance;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.reactive.Single;
import io.helidon.faulttolerance.Async;
import io.helidon.faulttolerance.Bulkhead;
import io.helidon.faulttolerance.CircuitBreaker;
import io.helidon.faulttolerance.Fallback;
import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.Timeout;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Simple service to demonstrate fault tolerance.
 */
public class FtService implements Service {

    private final Async async;
    private final Bulkhead bulkhead;
    private final CircuitBreaker breaker;
    private final Fallback<String> fallback;
    private final Retry retry;
    private final Timeout timeout;

    FtService() {
        this.async = Async.create();
        this.bulkhead = Bulkhead.builder()
                .queueLength(1)
                .limit(1)
                .name("helidon-example-bulkhead")
                .build();
        this.breaker = CircuitBreaker.builder()
                .volume(4)
                .errorRatio(40)
                .successThreshold(1)
                .delay(Duration.ofSeconds(5))
                .build();
        this.fallback = Fallback.create(this::fallbackToMethod);
        this.retry = Retry.builder()
                .retryPolicy(Retry.DelayingRetryPolicy.noDelay(3))
                .build();
        this.timeout = Timeout.create(Duration.ofMillis(100));
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/async", this::asyncHandler)
                .get("/bulkhead/{millis}", this::bulkheadHandler)
                .get("/circuitBreaker/{success}", this::circuitBreakerHandler)
                .get("/fallback/{success}", this::fallbackHandler)
                .get("/retry/{count}", this::retryHandler)
                .get("/timeout/{millis}", this::timeoutHandler);
    }

    private void timeoutHandler(ServerRequest request, ServerResponse response) {
        long sleep = request.path().parameter("millis").asLong().get();

        timeout.invoke(() -> sleep(sleep))
                .thenAccept(response::send)
                .exceptionally(response::send);
    }

    private void retryHandler(ServerRequest request, ServerResponse response) {
        int count = request.path().parameter("count").asInt().get();

        AtomicInteger call = new AtomicInteger(1);
        AtomicInteger failures = new AtomicInteger();

        retry.invoke(() -> {
            int current = call.getAndIncrement();
            if (current < count) {
                failures.incrementAndGet();
                return reactiveFailure();
            }
            return Single.just("calls/failures: " + current + "/" + failures.get());
        }).thenAccept(response::send)
                .exceptionally(response::send);
    }

    private void fallbackHandler(ServerRequest request, ServerResponse response) {
        boolean success = "true".equalsIgnoreCase(request.path().param("success"));

        if (success) {
            fallback.invoke(this::reactiveData).thenAccept(response::send);
        } else {
            fallback.invoke(this::reactiveFailure).thenAccept(response::send);
        }
    }

    private void circuitBreakerHandler(ServerRequest request, ServerResponse response) {
        boolean success = "true".equalsIgnoreCase(request.path().param("success"));

        if (success) {
            breaker.invoke(this::reactiveData)
                    .thenAccept(response::send)
                    .exceptionally(response::send);
        } else {
            breaker.invoke(this::reactiveFailure)
                    .thenAccept(response::send)
                    .exceptionally(response::send);
        }

    }

    private void bulkheadHandler(ServerRequest request, ServerResponse response) {
        long sleep = Long.parseLong(request.path().param("millis"));

        bulkhead.invoke(() -> sleep(sleep))
                .thenAccept(response::send)
                .exceptionally(response::send);
    }

    private void asyncHandler(ServerRequest request, ServerResponse response) {
        async.invoke(this::blockingData).thenApply(response::send);
    }

    private Single<String> reactiveFailure() {
        return Single.error(new RuntimeException("reactive failure"));
    }

    private Single<String> sleep(long sleepMillis) {
        return async.invoke(() -> {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException ignored) {
            }
            return "Slept for " + sleepMillis + " ms";
        });
    }

    private Single<String> reactiveData() {
        return async.invoke(this::blockingData);
    }

    private String blockingData() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
        return "blocked for 100 millis";
    }

    private Single<String> fallbackToMethod(Throwable e) {
        return Single.just("Failed back because of " + e.getMessage());
    }

}
