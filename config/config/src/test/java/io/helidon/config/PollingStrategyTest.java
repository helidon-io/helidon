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

package io.helidon.config;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import io.helidon.config.spi.ChangeEventType;
import io.helidon.config.spi.PollingStrategy;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link io.helidon.config.spi.PollingStrategy}.
 */
public class PollingStrategyTest {

    @Test
    public void testPollingStrategy() throws InterruptedException {
        final int EXPECTED_UPDATE_EVENTS_DELIVERED = 3;
        CountDownLatch nextLatch = new CountDownLatch(EXPECTED_UPDATE_EVENTS_DELIVERED);

        MyPollingStrategy myPollingStrategy = new MyPollingStrategy(3);

        myPollingStrategy.start(when -> {
            nextLatch.countDown();
            return ChangeEventType.UNCHANGED;
        });

        myPollingStrategy.fireEvents();

        assertThat("Subscriber was notified of " + (EXPECTED_UPDATE_EVENTS_DELIVERED - nextLatch.getCount() + 1) +
                           " events, not the expected number, within the expected time",
                   nextLatch.await(100, TimeUnit.MILLISECONDS), is(true));
    }

    private static final class MyPollingStrategy implements PollingStrategy {

        private final int events;
        private Polled polled;

        MyPollingStrategy(int events) {
            this.events = events;
        }

        @Override
        public void start(Polled polled) {
            this.polled = polled;
        }

        public void fireEvents() {
            IntStream.range(0, events).forEach(i -> polled.poll(Instant.now()));
        }
    }
}
