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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.isOneOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryTest {
    @Test
    public void testLastDelay() {
        List<Long> lastDelayCalls = new ArrayList<>();
        Retry retry = Retry.builder()
                .retryPolicy((firstCallMillis, lastDelay, call) -> {
                    lastDelayCalls.add(lastDelay);
                    return Optional.of(lastDelay + 1);
                })
                .build();

        Request req = new Request(3, new RetryException(), new RetryException());
        int result = retry.invoke(req::invoke);
        assertThat(req.call.get(), is(4));

        assertThat("Last delay should increase", lastDelayCalls, contains(0L, 1L, 2L));
    }

    @Test
    void testRetry() {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.JitterRetryPolicy.builder()
                                     .calls(3)
                                     .delay(Duration.ofMillis(50))
                                     .jitter(Duration.ofMillis(50))
                                     .build())
                .overallTimeout(Duration.ofMillis(500))
                .build();

        Request req = new Request(3, new TerminalException(), new RetryException());
        assertThrows(TerminalException.class, () -> retry.invoke(req::invoke));
        assertThat(req.call.get(), is(3));

        Request req2 = new Request(2, new TerminalException(), new RetryException());
        int count = retry.invoke(req2::invoke);
        assertThat(count, is(3));
    }

    @Test
    void testRetryOn() {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.JitterRetryPolicy.builder()
                                     .calls(3)
                                     .delay(Duration.ofMillis(100))
                                     .jitter(Duration.ofMillis(50))
                                     .build())
                .overallTimeout(Duration.ofMillis(500))
                .addApplyOn(RetryException.class)
                .build();

        Request req = new Request(3, new RetryException(), new RetryException());
        assertThrows(RetryException.class, () -> retry.invoke(req::invoke));
        assertThat(req.call.get(), is(3));

        Request req2 = new Request(2, new RetryException(), new TerminalException());
        assertThrows(TerminalException.class, () -> retry.invoke(req2::invoke));
        assertThat(req2.call.get(), is(2));

        Request req3 = new Request(2, new RetryException(), new RetryException());
        int count = retry.invoke(req3::invoke);
        assertThat(count, is(3));
    }

    @Test
    void testAbortOn() {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.JitterRetryPolicy.builder()
                                     .calls(3)
                                     .delay(Duration.ofMillis(100))
                                     .jitter(Duration.ofMillis(50))
                                     .build())
                .overallTimeout(Duration.ofMillis(50000))
                .addSkipOn(TerminalException.class)
                .build();

        Request req = new Request(3, new RetryException(), new RetryException());
        assertThrows(RetryException.class, () -> retry.invoke(req::invoke));
        assertThat(req.call.get(), is(3));

        Request req2 = new Request(2, new RetryException(), new TerminalException());
        assertThrows(TerminalException.class, () -> retry.invoke(req2::invoke));
        assertThat(req2.call.get(), is(2));

        Request req3 = new Request(2, new RetryException(), new RetryException());
        int count = retry.invoke(req3::invoke);
        assertThat(count, is(3));
    }

    @Test
    void testTimeout() {
        Retry retry = Retry.builder()
                .retryPolicy(Retry.JitterRetryPolicy.builder()
                                     .calls(3)
                                     .delay(Duration.ofMillis(100))
                                     .jitter(Duration.ZERO)
                                     .build())
                .overallTimeout(Duration.ofMillis(50))
                .build();

        Request req = new Request(3, new RetryException(), new RetryException());
        assertThrows(TimeoutException.class, () -> retry.invoke(req::invoke));
        // first time: immediate call
        // second time: delayed invocation or timeout in very slow system
        // third attempt to retry fails on timeout
        assertThat("Should have been called twice", req.call.get(), isOneOf(1, 2));
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

        Integer invoke() {
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
            return now;
        }
    }

    private static class RetryException extends RuntimeException {
    }

    private static class TerminalException extends RuntimeException {
    }
}
