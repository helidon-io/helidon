/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.messaging.connector;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;

import javax.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ApplicationScoped
@Connector("iterable-connector")
public class IterableConnector implements IncomingConnectorFactory, OutgoingConnectorFactory {

    public static final String[] TEST_DATA = {"test1", "test2", "test3"};
    public static final CountDownLatch LATCH = new CountDownLatch(TEST_DATA.length);
    private static final Set<String> PROCESSED_DATA =
            Arrays.stream(IterableConnector.TEST_DATA).collect(Collectors.toSet());

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(Config config) {
        //TODO: use ReactiveStreams.of().map when engine is ready(supports more than one stage)
        return ReactiveStreams.fromIterable(Arrays.stream(TEST_DATA).map(Message::of).collect(Collectors.toSet()));
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(Config config) {
        return ReactiveStreams.<Message<?>>builder().forEach(m -> {
            assertTrue(PROCESSED_DATA.contains(m.getPayload()));
            LATCH.countDown();
        });
    }
}
