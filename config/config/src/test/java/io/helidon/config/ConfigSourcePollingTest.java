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

package io.helidon.config;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link ConfigSource#changes()} with a few {@link io.helidon.config.spi.PollingStrategy Polling Strategies}.
 */
public class ConfigSourcePollingTest {
    /*
    Creates a ConfigSource via classpath to a test resource file, using the standard properties config parser and
    a custom PollingStrategy that simply sends a single "update" event representing would-be config
    changes.

    Uses a Flow.Subscriber to be notified of config changes.

    Fires the polling strategy to submit the event which should trigger the subscriber.

    Makes sure that the subscriber's onSubscribe and onNext are invoked the expected number of times.

    Makes sure that the expected config values are reported.

    Because the data source is a fixed file and its contents does not change during the test, we expect
    only one config update event to be delivered via the subscriber's onNext method ( as a result of the initial load),
    despite firing three separate items.

     */

    @Test
    public void testPollingStrategy() throws InterruptedException {
        final int EXPECTED_UPDATE_EVENTS_DELIVERED = 1;
        final int UPDATE_EVENTS_TO_FIRE = 3;
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        CountDownLatch nextLatch = new CountDownLatch(EXPECTED_UPDATE_EVENTS_DELIVERED);

        MyPollingStrategy myPollingStrategy = new MyPollingStrategy(UPDATE_EVENTS_TO_FIRE);

        ConfigSource configSource = ConfigSources.classpath("io/helidon/config/application.properties")
                .pollingStrategy(myPollingStrategy)
                .parser(ConfigParsers.properties())
                .build();

        configSource.init(mock(ConfigContext.class));

        configSource.changes().subscribe(new Flow.Subscriber<Optional<ObjectNode>>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribeLatch.countDown();
                subscription.request(EXPECTED_UPDATE_EVENTS_DELIVERED);
            }

            @Override
            public void onNext(Optional<ObjectNode> item) {
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
                   subscribeLatch.await(100, TimeUnit.MILLISECONDS),
                   is(true));

        myPollingStrategy.fireEvents();
        assertThat("Subscriber was notified of " + (EXPECTED_UPDATE_EVENTS_DELIVERED - nextLatch.getCount() + 1) +
                           " events, not the expected number, within the expected time",
                   nextLatch.await(5000, TimeUnit.MILLISECONDS), is(true));
        final Config config = Config.create(configSource);
        assertThat("value retrieved for app1.node.value.sub1 not as expected",
                   config.get("app1.node.value.sub1").asString().get(),
                   is("subvalue1"));
        assertThat("value retrieved for app1.node.value not as expected",
                   config.get("app1.node.value").asBoolean(),
                   is(ConfigValues.simpleValue(true)));
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
