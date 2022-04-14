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
 *
 */

package io.helidon.microprofile.messaging.connector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Multi;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.junit.jupiter.api.Test;
import org.reactivestreams.FlowAdapters;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@HelidonTest
@AddBean(Processor2ConnectorTest.TestConnector.class)
@AddConfig(key = "mp.messaging.incoming.from-connector-imp.connector", value = "test-connector")
@AddConfig(key = "mp.messaging.outgoing.to-connector-imp.connector", value = "test-connector")
@AddConfig(key = "mp.messaging.incoming.from-connector-rs.connector", value = "test-connector")
@AddConfig(key = "mp.messaging.outgoing.to-connector-rs.connector", value = "test-connector")
public class Processor2ConnectorTest {

    static final Duration TIME_OUT = Duration.ofSeconds(5);

    @Inject
    @Any
    TestConnector connector;

    @Test
    void imperativeProcessor() {
        connector2proc2connectorTest("imp");
    }

    @Test
    void rsProcessor() {
        connector2proc2connectorTest("rs");
    }

    private void connector2proc2connectorTest(String channelPostfix) {
        var resultList = connector.result("to-connector-" + channelPostfix);
        var emitter = connector.emitter("from-connector-" + channelPostfix);
        connector.awaitSubscription("from-connector-" + channelPostfix);
        emitter.emit(Message.of("test1"));
        emitter.emit(Message.of("test2"));
        emitter.complete();
        connector.awaitComplete("to-connector-" + channelPostfix);
        assertEquals(4, resultList.size());
        assertThat(resultList.stream()
                        .map(Message::getPayload)
                        .collect(Collectors.toList()),
                contains("test1", "test1", "test2", "test2"));
    }

    @Incoming("from-connector-imp")
    @Outgoing("to-connector-imp")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    @SuppressWarnings("unchecked")
    public PublisherBuilder<Message<String>> processMessageFromEncounterStreamImp(Message<String> message) {
        return ReactiveStreams.of(message, message);
    }

    @Incoming("from-connector-rs")
    @Outgoing("to-connector-rs")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    @SuppressWarnings("unchecked")
    public ProcessorBuilder<Message<String>, Message<String>> processMessageFromEncounterStreamRs() {
        return ReactiveStreams.<Message<String>>builder()
                .flatMap(message -> ReactiveStreams.of(message, message));
    }

    @ApplicationScoped
    @Connector("test-connector")
    public static class TestConnector implements OutgoingConnectorFactory, IncomingConnectorFactory {

        private final Map<String, BufferedEmittingPublisher<Message<?>>> pubMap = new HashMap<>();
        private final Map<String, List<Message<?>>> subMap = new HashMap<>();
        private final Map<String, CompletableFuture<Void>> completed = new HashMap<>();
        private final Map<String, CompletableFuture<Void>> subscribed = new HashMap<>();

        @Override
        public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(Config config) {
            String channel = config.getValue(CHANNEL_NAME_ATTRIBUTE, String.class);
            List<Message<?>> messages = subMap.computeIfAbsent(channel, s -> new ArrayList<>());
            CompletableFuture<Void> completed = new CompletableFuture<>();
            this.completed.put(channel, completed);
            return ReactiveStreams.<Message<?>>builder()
                    .forEach(m -> {
                        messages.add(m);
                        m.ack().whenComplete((unused, throwable) -> completed.complete(null));
                    });
        }

        @Override
        @SuppressWarnings("unchecked,rawtypes")
        public PublisherBuilder<? extends Message<?>> getPublisherBuilder(Config config) {
            String channel = config.getValue(CHANNEL_NAME_ATTRIBUTE, String.class);
            BufferedEmittingPublisher bep = pubMap.computeIfAbsent(channel, s -> BufferedEmittingPublisher.create());
            Multi pub = Multi.create(bep).log(Level.INFO, channel);
            CompletableFuture<Void> subscribed = new CompletableFuture<>();
            this.subscribed.put(channel, subscribed);
            return ReactiveStreams.fromPublisher(s -> {
                pub.subscribe(FlowAdapters.toFlowSubscriber(s));
                subscribed.complete(null);
            });
        }

        List<Message<?>> result(String channel) {
            return subMap.computeIfAbsent(channel, s -> new ArrayList<>());
        }

        BufferedEmittingPublisher<Message<?>> emitter(String channel) {
            return pubMap.computeIfAbsent(channel, s -> BufferedEmittingPublisher.create());
        }

        void awaitSubscription(String channel) {
            try {
                subscribed.computeIfAbsent(channel, s -> new CompletableFuture<>())
                        .get(TIME_OUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                fail(e);
            }
        }

        void awaitComplete(String channel) {
            try {
                completed.computeIfAbsent(channel, s -> new CompletableFuture<>())
                        .get(TIME_OUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                fail(e);
            }
        }
    }
}
