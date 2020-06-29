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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryTest {
    @Test
    void testRetry() {
        Retry retry = Retry.builder()
                .calls(3)
                .delay(Duration.ofMillis(50))
                .maxTime(Duration.ofMillis(500))
                .jitter(Duration.ofMillis(50))
                .build();

        Request req = new Request(3, new TerminalException(), new RetryException());
        Single<Integer> result = retry.invoke(req::invoke);
        FaultToleranceTest.completionException(result, TerminalException.class);
        assertThat(req.call.get(), is(3));

        req = new Request(2, new TerminalException(), new RetryException());
        result = retry.invoke(req::invoke);
        int count = result.await(1, TimeUnit.SECONDS);
        assertThat(count, is(3));
    }

    @Test
    void testRetryOn() {
        Retry retry = Retry.builder()
                .calls(3)
                .delay(Duration.ofMillis(100))
                .maxTime(Duration.ofMillis(500))
                .jitter(Duration.ofMillis(50))
                .addRetryOn(RetryException.class)
                .build();

        Request req = new Request(3, new RetryException(), new RetryException());
        Single<Integer> result = retry.invoke(req::invoke);
        FaultToleranceTest.completionException(result, RetryException.class);
        assertThat(req.call.get(), is(3));

        req = new Request(2, new RetryException(), new TerminalException());
        result = retry.invoke(req::invoke);
        FaultToleranceTest.completionException(result, TerminalException.class);
        assertThat(req.call.get(), is(2));

        req = new Request(2, new RetryException(), new RetryException());
        result = retry.invoke(req::invoke);
        int count = result.await(1, TimeUnit.SECONDS);
        assertThat(count, is(3));
    }

    @Test
    void testAbortOn() {
        Retry retry = Retry.builder()
                .calls(3)
                .delay(Duration.ofMillis(100))
                .maxTime(Duration.ofMillis(500))
                .jitter(Duration.ofMillis(50))
                .addAbortOn(TerminalException.class)
                .build();

        Request req = new Request(3, new RetryException(), new RetryException());
        Single<Integer> result = retry.invoke(req::invoke);
        FaultToleranceTest.completionException(result, RetryException.class);
        assertThat(req.call.get(), is(3));

        req = new Request(2, new RetryException(), new TerminalException());
        result = retry.invoke(req::invoke);
        FaultToleranceTest.completionException(result, TerminalException.class);
        assertThat(req.call.get(), is(2));

        req = new Request(2, new RetryException(), new RetryException());
        result = retry.invoke(req::invoke);
        int count = result.await(1, TimeUnit.SECONDS);
        assertThat(count, is(3));
    }

    @Test
    void testTimeout() {
        Retry retry = Retry.builder()
                .calls(3)
                .delay(Duration.ofMillis(50))
                .maxTime(Duration.ofMillis(45))
                .jitter(Duration.ofMillis(1))
                .build();

        Request req = new Request(3, new RetryException(), new RetryException());
        Single<Integer> result = retry.invoke(req::invoke);
        FaultToleranceTest.completionException(result, TimeoutException.class);
        assertThat(req.call.get(), is(2));
    }

    @Test
    void testBadConfiguration() {
        Retry.Builder builder = Retry.builder()
                .retryOn(RetryException.class)
                .abortOn(TerminalException.class);

        assertThrows(IllegalArgumentException.class, builder::build);
    }


        private static class Request {
        private final AtomicInteger call = new AtomicInteger();
        private final int failures;
        private final RuntimeException first;
        private final RuntimeException second;

        private Request(int failures, RuntimeException first, RuntimeException second) {
            this.failures = failures;
            this.first = first;
            this.second = second;
        }

        CompletionStage<Integer> invoke() {
            //failures 1
            // call
            int now = call.incrementAndGet();
            if (now <= failures) {
                if (now == 1) {
                    throw first;
                } else if (now == 2) {
                    throw second;
                } else {
                    throw first;
                }
            }
            return Single.just(now);
        }
    }

    private static class RetryException extends RuntimeException {
    }

    private static class TerminalException extends RuntimeException {

    }
}