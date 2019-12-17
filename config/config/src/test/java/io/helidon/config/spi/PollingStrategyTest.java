/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.spi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import io.helidon.common.reactive.SubmissionPublisher;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link io.helidon.config.spi.PollingStrategy}.
 */
public class PollingStrategyTest {

    @Test
    public void testPollingStrategy() throws InterruptedException {
        final int EXPECTED_UPDATE_EVENTS_DELIVERED = 3;
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        CountDownLatch nextLatch = new CountDownLatch(EXPECTED_UPDATE_EVENTS_DELIVERED);

        MyPollingStrategy myPollingStrategy = new MyPollingStrategy(3);

        myPollingStrategy.ticks().subscribe(new Flow.Subscriber<PollingStrategy.PollingEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(3);
            }

            @Override
            public void onNext(PollingStrategy.PollingEvent item) {
                nextLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                fail(throwable);
            }

            @Override
            public void onComplete() {
            }
        });

        // Make sure subscription occurs before firing events.
        assertThat("Subscriber did not register within expected time", 
                subscribeLatch.await(100, TimeUnit.MILLISECONDS), is(true));
        myPollingStrategy.fireEvents();

        assertThat("Subscriber was notified of " + (EXPECTED_UPDATE_EVENTS_DELIVERED - nextLatch.getCount() + 1) +
                        " events, not the expected number, within the expected time",
                nextLatch.await(100, TimeUnit.MILLISECONDS), is(true));
    }

    private class MyPollingStrategy implements PollingStrategy {

        private final int events;
        private final SubmissionPublisher<PollingEvent> publisher = new SubmissionPublisher<>();

        MyPollingStrategy(int events) {
            this.events = events;
        }

        @Override
        public Flow.Publisher<PollingEvent> ticks() {
            return publisher;
        }

        void fireEvents() {
            IntStream.range(0, events).forEach((i) -> publisher.submit(PollingEvent.now()));
        }
    }
}
