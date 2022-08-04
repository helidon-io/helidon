/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.messaging.connectors.mock;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.reactive.BufferedEmittingPublisher;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.FlowAdapters;


/**
 * Helidon messaging mock connector for testing purposes.
 * Mock connector for testing Helidon messaging
 * without the need of connection to actual messaging broker.
 */
@Connector(MockConnector.CONNECTOR_NAME)
@TestConnector
@ApplicationScoped
public class MockConnector implements IncomingConnectorFactory, OutgoingConnectorFactory {

    /**
     * Connector name.
     */
    public static final String CONNECTOR_NAME = "helidon-mock";

    private final Map<String, BufferedEmittingPublisher<Message<?>>> emitterMap = new HashMap<>();
    private final Map<String, MockOutgoing<?>> subscriberMap = new HashMap<>();

    private final ReentrantLock emitterLock = new ReentrantLock();
    private final ReentrantLock subscriberLock = new ReentrantLock();

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(Config config) {
        String channelName = config.getValue(ConnectorFactory.CHANNEL_NAME_ATTRIBUTE, String.class);
        BufferedEmittingPublisher<Message<?>> emitter = incoming(channelName, Object.class).emitter();
        Class<?> mockDataType = config.getOptionalValue("mock-data-type", Class.class).orElse(String.class);
        config.getOptionalValues("mock-data", mockDataType)
                .ifPresent(list -> list.forEach(s -> emitter.emit(Message.of(s))));

        return ReactiveStreams.<Object>fromPublisher(FlowAdapters.toPublisher(emitter))
                .map(o -> {
                    if (o instanceof Message<?>) {
                        return (Message<?>) o;
                    } else {
                        return Message.of(o);
                    }
                });
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(Config config) {
        String channelName = config.getValue(ConnectorFactory.CHANNEL_NAME_ATTRIBUTE, String.class);
        return ReactiveStreams.<Message<?>>builder()
                .to(outgoing(channelName, Object.class).subscriber());
    }

    /**
     * Retrieve mocker for incoming channel. Incoming channel needs to be configured to use MockConnector with:
     * <pre>{@code
     * @AddConfig(key = "mp.messaging.incoming.test-channel-incoming", value = MockConnector.CONNECTOR_NAME)
     * }</pre>
     *
     * @param channelName Channel name, for example {@code test-channel-incoming}
     * @param type        Type of the payload
     * @param <P>         type of the payload
     * @return mocker for desired chanel
     */
    public <P> MockIncoming<P> incoming(String channelName, Class<P> type) {
        emitterLock.lock();
        try {
            return new MockIncoming<P>(emitterMap.computeIfAbsent(channelName, k -> BufferedEmittingPublisher.create()));
        } finally {
            emitterLock.unlock();
        }
    }

    /**
     * Retrieve mocker for outgoing channel. Outgoing channel needs to be configured to use MockConnector with:
     * <pre>{@code
     * @AddConfig(key = "mp.messaging.outgoing.test-channel-outgoing", value = MockConnector.CONNECTOR_NAME)
     * }</pre>
     *
     * @param channelName Channel name, for example {@code test-channel-outgoing}
     * @param type        Type of the payload
     * @param <P>         type of the payload
     * @return mocker for desired chanel
     */
    @SuppressWarnings("unchecked")
    public <P> MockOutgoing<P> outgoing(String channelName, Class<P> type) {
        subscriberLock.lock();
        try {
            return (MockOutgoing<P>) subscriberMap.computeIfAbsent(channelName, k -> new MockOutgoing<P>(new MockSubscriber()));
        } finally {
            subscriberLock.unlock();
        }
    }

}
