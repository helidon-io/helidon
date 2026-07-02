/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.http2;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class WindowSizeTest {

    @Test
    void rejectsWindowUpdateAfterTimeout() throws InterruptedException {
        ConnectionFlowControl connection = ConnectionFlowControl.clientBuilder((_, _) -> { })
                .blockTimeout(Duration.ofSeconds(2))
                .build();
        WindowSize.Outbound outbound = connection.outbound();
        outbound.decrementWindowSize(outbound.getRemainingWindowSize());

        AtomicBoolean returnedNormally = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread blocker = Thread.ofVirtual().start(() -> {
            try {
                outbound.blockTillUpdate();
                returnedNormally.set(true);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        try {
            long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (blocker.isAlive()
                    && blocker.getState() != Thread.State.TIMED_WAITING
                    && System.nanoTime() < waitDeadline) {
                Thread.onSpinWait();
            }
            assertThat("flow-control wait must start", blocker.getState(), is(Thread.State.TIMED_WAITING));
            Thread.sleep(Duration.ofMillis(2100));
        } finally {
            outbound.incrementWindowSize(1);
            blocker.join();
        }

        assertThat("late update must not resume the write", returnedNormally.get(), is(false));
        assertThat(failure.get(), instanceOf(Http2Exception.class));
        assertThat(((Http2Exception) failure.get()).code(), is(Http2ErrorCode.FLOW_CONTROL));
    }
}
