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

package io.helidon.webserver.examples.faulttolerance;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.nima.faulttolerance.Async;
import io.helidon.nima.faulttolerance.Bulkhead;
import io.helidon.nima.faulttolerance.CircuitBreaker;
import io.helidon.nima.faulttolerance.Fallback;
import io.helidon.nima.faulttolerance.Retry;
import io.helidon.nima.faulttolerance.Timeout;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

/**
 * Simple service to demonstrate fault tolerance.
 */
public class FtService implements HttpService {

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
        this.fallback = Fallback.create(b -> b.fallback(this::fallbackToMethod));
        this.retry = Retry.builder()
                          .retryPolicy(Retry.DelayingRetryPolicy.noDelay(3))
                          .build();
        this.timeout = Timeout.create(Duration.ofMillis(100));
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/async", this::asyncHandler)
             .get("/bulkhead/{millis}", this::bulkheadHandler)
             .get("/circuitBreaker/{success}", this::circuitBreakerHandler)
             .get("/fallback/{success}", this::fallbackHandler)
             .get("/retry/{count}", this::retryHandler)
             .get("/timeout/{millis}", this::timeoutHandler);
    }

    private void timeoutHandler(ServerRequest request, ServerResponse response) {
        long sleep = Long.parseLong(request.path().pathParameters().value("millis"));
        response.send(timeout.invoke(() -> sleep(sleep)));
    }

    private void retryHandler(ServerRequest request, ServerResponse response) {
        int count = Integer.parseInt(request.path().pathParameters().value("count"));

        AtomicInteger call = new AtomicInteger(1);
        AtomicInteger failures = new AtomicInteger();

        String msg = retry.invoke(() -> {
            int current = call.getAndIncrement();
            if (current < count) {
                failures.incrementAndGet();
                return failure();
            }
            return "calls/failures: " + current + "/" + failures.get();
        });
        response.send(msg);
    }

    private void fallbackHandler(ServerRequest request, ServerResponse response) {
        boolean success = "true".equalsIgnoreCase(request.path().pathParameters().value("success"));
        if (success) {
            response.send(fallback.invoke(this::data));
        } else {
            response.send(fallback.invoke(this::failure));
        }
    }

    private void circuitBreakerHandler(ServerRequest request, ServerResponse response) {
        boolean success = "true".equalsIgnoreCase(request.path().pathParameters().value("success"));
        if (success) {
            response.send(breaker.invoke(this::data));
        } else {
            response.send(breaker.invoke(this::failure));
        }
    }

    private void bulkheadHandler(ServerRequest request, ServerResponse response) {
        long sleep = Long.parseLong(request.path().pathParameters().value("millis"));
        response.send(bulkhead.invoke(() -> sleep(sleep)));
    }

    private void asyncHandler(ServerRequest request, ServerResponse response) {
        response.send(async.invoke(this::data).join());
    }

    private String failure() {
        throw new RuntimeException("failure");
    }

    private String sleep(long sleepMillis) {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException ignored) {
        }
        return "Slept for " + sleepMillis + " ms";
    }

    private String data() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
        return "blocked for 100 millis";
    }

    private String fallbackToMethod(Throwable e) {
        return "Failed back because of " + e.getMessage();
    }

}
