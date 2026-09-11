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

package io.helidon.declarative.tests.messaging;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.declarative.tests.messaging.ProgrammaticMessagingTypes.NestedConsumer;
import io.helidon.declarative.tests.messaging.ProgrammaticMessagingTypes.StringConsumer;
import io.helidon.messaging.ConsumerRegistration;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessagingChannel;
import io.helidon.messaging.MessagingConfig;
import io.helidon.messaging.MessagingGraph;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgrammaticMessagingTest {
    private ServiceRegistryManager registryManager;
    private ServiceRegistry registry;

    @BeforeEach
    void initRegistry() {
        registryManager = ServiceRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterEach
    void closeRegistry() {
        registryManager.shutdown();
    }

    @Test
    void payloadSourceDeliversToGeneratedConsumer() throws Exception {
        MessagingChannel<String> channel = MessagingChannel.create(ProgrammaticMessagingTypes.STRING_CHANNEL, String.class);
        MessagingConfig.Builder builder = graphBuilder(channel.name())
                .channel(channel)
                .payloadSource(channel, Stream.of("payload"));

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            Message<String> received = registry.get(StringConsumer.class).received().get(10, TimeUnit.SECONDS);
            assertThat(received.entity(), is("payload"));
        }
    }

    @Test
    void messageSourcePreservesEnvelopeForGeneratedConsumer() throws Exception {
        MessagingChannel<String> channel = MessagingChannel.create(ProgrammaticMessagingTypes.STRING_CHANNEL, String.class);
        Message<String> message = Message.builder("payload").header("trace", "source").build();
        MessagingConfig.Builder builder = graphBuilder(channel.name())
                .channel(channel)
                .messageSource(channel, Stream.of(message));

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            Message<String> received = registry.get(StringConsumer.class).received().get(10, TimeUnit.SECONDS);
            assertThat(received, sameInstance(message));
        }
    }

    @Test
    void routeDeliversToGeneratedConsumer() throws Exception {
        MessagingChannel<String> source = MessagingChannel.create("programmatic-route-source", String.class);
        MessagingChannel<String> target = MessagingChannel.create(ProgrammaticMessagingTypes.STRING_CHANNEL, String.class);
        MessagingConfig.Builder builder = graphBuilder(target.name())
                .channel(source)
                .channel(target)
                .route(source, target);

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(source).emit("routed");
            Message<String> received = registry.get(StringConsumer.class).received().get(10, TimeUnit.SECONDS);
            assertThat(received.entity(), is("routed"));
        }
    }

    @Test
    void payloadProcessorDeliversToGeneratedConsumer() throws Exception {
        MessagingChannel<Integer> source = MessagingChannel.create("programmatic-payload-processor-source", Integer.class);
        MessagingChannel<String> target = MessagingChannel.create(ProgrammaticMessagingTypes.STRING_CHANNEL, String.class);
        MessagingConfig.Builder builder = graphBuilder(target.name())
                .channel(source)
                .channel(target)
                .payloadProcessor(source, target, value -> "converted-" + value);

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(source).emit(42);
            Message<String> received = registry.get(StringConsumer.class).received().get(10, TimeUnit.SECONDS);
            assertThat(received.entity(), is("converted-42"));
        }
    }

    @Test
    void messageProcessorDeliversToGeneratedConsumer() throws Exception {
        MessagingChannel<Integer> source = MessagingChannel.create("programmatic-message-processor-source", Integer.class);
        MessagingChannel<String> target = MessagingChannel.create(ProgrammaticMessagingTypes.STRING_CHANNEL, String.class);
        MessagingConfig.Builder builder = graphBuilder(target.name())
                .channel(source)
                .channel(target)
                .messageProcessor(source, target, message -> Message.builder(Integer.toString(message.entity()))
                        .headers(message.headers())
                        .build());

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(source).emit(Message.builder(7).header("trace", "processor").build());
            Message<String> received = registry.get(StringConsumer.class).received().get(10, TimeUnit.SECONDS);
            assertThat(received.entity(), is("7"));
            assertThat(received.header("trace").orElseThrow(), is("processor"));
        }
    }

    @Test
    void nestedPayloadTypeSurvivesSourceRouteAndProcessor() throws Exception {
        GenericType<List<String>> payloadType = new GenericType<>() { };
        MessagingChannel<List<String>> source = MessagingChannel.create("programmatic-nested-source", payloadType);
        MessagingChannel<List<String>> routed = MessagingChannel.create("programmatic-nested-routed", payloadType);
        MessagingChannel<List<String>> target = MessagingChannel.create(ProgrammaticMessagingTypes.NESTED_CHANNEL, payloadType);
        List<String> payload = List.of("one", "two");
        MessagingConfig.Builder builder = graphBuilder(target.name())
                .channel(source)
                .channel(routed)
                .channel(target)
                .payloadSource(source, Stream.of(payload))
                .route(source, routed)
                .messageProcessor(routed, target, message -> message);

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            Message<List<String>> received = registry.get(NestedConsumer.class).received().get(10, TimeUnit.SECONDS);
            assertThat(received.entity(), sameInstance(payload));
        }
    }

    @Test
    void mismatchedPayloadTypeStillFailsGraphValidation() {
        MessagingChannel<Integer> channel = MessagingChannel.create(ProgrammaticMessagingTypes.STRING_CHANNEL, Integer.class);
        MessagingConfig.Builder builder = graphBuilder(channel.name())
                .channel(channel)
                .payloadSource(channel, Stream.of(1));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, builder::build);

        assertThat(failure.getMessage(), containsString(channel.name()));
        assertThat(failure.getMessage(), containsString("java.lang.String"));
        assertThat(failure.getMessage(), containsString("java.lang.Integer"));
    }

    @Test
    void genericMessageStillCannotTargetConnectorSpecificEnvelope() {
        MessagingChannel<String> channel = MessagingChannel.create(ProgrammaticMessagingTypes.CONNECTOR_CHANNEL, String.class);
        MessagingConfig.Builder builder = graphBuilder(channel.name())
                .channel(channel)
                .payloadSource(channel, Stream.of("payload"));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, builder::build);

        assertThat(failure.getMessage(), containsString("envelope type"));
        assertThat(failure.getMessage(), containsString("ConnectorMessage"));
    }

    private MessagingConfig.Builder graphBuilder(String channel) {
        List<ConsumerRegistration> registrations = registry.all(ConsumerRegistration.class).stream()
                .filter(registration -> registration.channel().equals(channel))
                .toList();
        assertThat(registrations.size(), is(1));
        return MessagingGraph.builder()
                .serviceRegistry(registry)
                .consumerRegistrations(registrations);
    }
}
