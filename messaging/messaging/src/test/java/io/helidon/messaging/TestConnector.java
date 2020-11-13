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
 *
 */

package io.helidon.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;

@Connector(TestConnector.CONNECTOR_NAME)
public class TestConnector implements IncomingConnectorFactory, OutgoingConnectorFactory, Stoppable {

    public static final String CONNECTOR_NAME = "test-connector";
    public static final String TEST_PAYLOAD = "test-payload";
    public static final int TEST_STREAM_SIZE = 5;
    public static final List<String> TEST_DATA = List.of(TEST_PAYLOAD, TEST_PAYLOAD, TEST_PAYLOAD, TEST_PAYLOAD, TEST_PAYLOAD);
    public static CountDownLatch latch = new CountDownLatch(TEST_DATA.size());
    public CompletableFuture<Void> stoppedFuture = new CompletableFuture<>();
    public static List<String> receivedData = new ArrayList<>();

    private TestConnector() {

    }

    public static TestConnector create() {
        return new TestConnector();
    }

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(final Config config) {
        Optional<String> customPayload = config.getOptionalValue(TEST_PAYLOAD, String.class);
        if (customPayload.isPresent()) {
            return ReactiveStreams.generate(customPayload::get).limit(TEST_STREAM_SIZE).map(Message::of);
        }
        return ReactiveStreams.fromIterable(TEST_DATA).map(Message::of);
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(final Config config) {
        return ReactiveStreams.<Message<String>>builder()
                .map(Message::getPayload)
                .forEach(o -> {
                    receivedData.add(o);
                    latch.countDown();
                });
    }

    static void reset() {
        TestConnector.receivedData.clear();
        TestConnector.latch = new CountDownLatch(TestConnector.TEST_DATA.size());
    }

    @Override
    public void stop() {
        stoppedFuture.complete(null);
    }

    public static ConfigBuilder configBuilder() {
        return new ConfigBuilder();
    }

    public static class ConfigBuilder extends ConnectorConfigBuilder {
        public ConfigBuilder url(String url) {
            super.property("url", url);
            return this;
        }

        public ConfigBuilder port(int port) {
            super.property("port", String.valueOf(port));
            return this;
        }
    }
}
