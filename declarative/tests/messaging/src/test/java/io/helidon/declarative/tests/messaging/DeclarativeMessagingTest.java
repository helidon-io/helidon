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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.messaging.BatchDeliveryException;
import io.helidon.messaging.BatchItemStatus;
import io.helidon.messaging.DeadLetterMessage;
import io.helidon.messaging.EmitterRegistration;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessageBatch;
import io.helidon.messaging.MessagingChannel;
import io.helidon.messaging.MessagingException;
import io.helidon.messaging.MessagingGraph;
import io.helidon.messaging.MessagingRuntime;
import io.helidon.messaging.OutgoingConnector;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.AnnotatedFailureConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.AnnotatedFailureDeadLetterConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.ArrayPayloadConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.BatchChannelOneConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.BroadCustomMessageConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.ChannelTwoConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.CustomMessage;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.CustomMessageConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.FailingConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.FirstChannelOneConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.ForwardedBatchConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.ForwardedMessageConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.ForwardingProcessor;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.HeaderDelivery;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.ImmutableCustomMessage;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.ImmutableMultiHopMessage;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.MultiHopMessage;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.MultiHopMessageConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.OptionalHeaderConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.OptionalHeaderDelivery;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.PayloadProcessorConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.PerLookupInterceptedConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.Producer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.RequiredHeaderConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.SecondChannelOneConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.ShutdownConsumer;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.TestConnectorObserver;
import io.helidon.declarative.tests.messaging.ChannelMessagingTypes.TestEntryPointInterceptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("helidon:api:internal")
class DeclarativeMessagingTest {
    private ServiceRegistryManager registryManager;
    private ServiceRegistry registry;

    @BeforeEach
    void initRegistry() {
        registryManager = ServiceRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterEach
    void tearDownRegistry() {
        registryManager.shutdown();
    }

    @Test
    void testImperativeChannelInputsOutputs() throws InterruptedException {
        List<Message<Integer>> messages = new CopyOnWriteArrayList<>();
        CountDownLatch drained = new CountDownLatch(2);

        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<Integer> channel = builder.channel("imperative-input-output", Integer.class);
        builder.messageSource(channel,
                              Stream.of(Message.create(1),
                                        Message.builder(2)
                                                 .header("source", "message")
                                                 .build())
                                      .parallel())
                .messageSink(channel, message -> {
                    messages.add(message);
                    drained.countDown();
                });

        assertThat(messages, empty());

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            boolean completed = drained.await(10, TimeUnit.SECONDS);
            assertThat("Delivered entities: " + messages.stream().map(Message::entity).toList(), completed, is(true));
            assertThat(messages, hasSize(2));
            assertThat(messages.stream().map(Message::entity).sorted().toList(), is(List.of(1, 2)));
            Message<Integer> message = messages.stream()
                    .filter(candidate -> candidate.header("source").isPresent())
                    .findFirst()
                    .orElseThrow();
            assertThat(message.entity(), is(2));
            assertThat(message.header("source").orElseThrow(), is("message"));
        }
    }

    @Test
    void testImperativeChannelCanUseAnotherChannelAsInput() {
        List<Message<Integer>> downstreamMessages = new CopyOnWriteArrayList<>();

        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<Integer> upstream = builder.channel("imperative-upstream", Integer.class);
        MessagingChannel<Integer> downstream = builder.channel("imperative-downstream", Integer.class);
        builder.route(upstream, downstream)
                .messageSink(downstream, downstreamMessages::add);

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(upstream).emit(42);

            assertThat(downstreamMessages, hasSize(1));
            assertThat(downstreamMessages.getFirst().entity(), is(42));
        }
    }

    @Test
    void testImperativeChannelCanUseOutgoingConnector() {
        List<Message<?>> sentMessages = new CopyOnWriteArrayList<>();

        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("imperative-connector", String.class);
        builder.outgoingConnector(channel, sink(sentMessages));

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(channel)
                    .emitMessage(Message.builder("connector message")
                                         .header("source", "test")
                                         .build());

            assertThat(sentMessages, hasSize(1));
            assertThat(sentMessages.getFirst().entity(), is("connector message"));
            assertThat(sentMessages.getFirst().header("source").orElseThrow(), is("test"));
        }
    }

    @Test
    void testImperativeChannelPreservesBatchForBatchOutputsAndConnectors() {
        List<MessageBatch<String>> batches = new CopyOnWriteArrayList<>();
        List<List<String>> connectorBatches = new CopyOnWriteArrayList<>();

        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("imperative-batch", String.class);
        builder.batchSink(channel, batches::add)
                .outgoingConnector(channel, new NoOpOutgoingConnector() {
                    @Override
                    public void sendBatch(MessageBatch<?> messages) {
                        List<String> entities = new ArrayList<>();
                        for (Message<?> message : messages) {
                            entities.add(String.valueOf(message.entity()));
                        }
                        connectorBatches.add(entities);
                    }
                });

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(channel)
                    .emitBatch(MessageBatch.create(List.of(Message.builder("first")
                                                                  .header("source", "batch")
                                                                  .build(),
                                                           Message.create("second"))));

            assertThat(batches, hasSize(1));
            assertThat(batches.getFirst().size(), is(2));
            assertThat(batches.getFirst().payloads(), is(List.of("first", "second")));
            assertThat(batches.getFirst().get(0).header("source").orElseThrow(), is("batch"));
            assertThat(connectorBatches, is(List.of(List.of("first", "second"))));
        }
    }

    @Test
    void testOutgoingConnectorFailureFailsChannelEmit() {
        MessagingException expectedFailure = new MessagingException("connector failed", new IOException("I/O failed"));
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("imperative-connector-failure", String.class);
        builder.outgoingConnector(channel, new NoOpOutgoingConnector() {
            @Override
            public void sendBatch(MessageBatch<?> batch) {
                throw expectedFailure;
            }
        });

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            BatchDeliveryException thrown = assertBatchFailure(expectedFailure,
                                                                () -> graph.emitter(channel).emit("test message"));
            assertThat(rootCause(thrown).getMessage(), is("I/O failed"));
        }
    }

    @Test
    void testImperativeEmitWaitsForRequiredOutputsAndFailsFast() throws InterruptedException {
        CountDownLatch firstOutputEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstOutput = new CountDownLatch(1);
        CountDownLatch emissionCompleted = new CountDownLatch(1);
        List<String> invokedOutputs = new CopyOnWriteArrayList<>();
        MessagingException expectedFailure = new MessagingException("second output failed",
                                                                     new IOException("output I/O failed"));
        AtomicReference<Throwable> actualFailure = new AtomicReference<>();

        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("imperative-required-outputs", String.class);
        builder.messageSink(channel, message -> {
                    invokedOutputs.add("first");
                    firstOutputEntered.countDown();
                    try {
                        releaseFirstOutput.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new MessagingException("first output interrupted", e);
                    }
                })
                .messageSink(channel, message -> {
                    invokedOutputs.add("second");
                    throw expectedFailure;
                })
                .messageSink(channel, message -> invokedOutputs.add("third"));

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            var emitter = graph.emitter(channel);
            Thread.ofVirtual().start(() -> {
                try {
                    emitter.emit("test message");
                } catch (Throwable throwable) {
                    actualFailure.set(throwable);
                } finally {
                    emissionCompleted.countDown();
                }
            });

            assertThat(firstOutputEntered.await(10, TimeUnit.SECONDS), is(true));
            try {
                assertThat(emissionCompleted.getCount(), is(1L));
                assertThat(invokedOutputs, is(List.of("first")));
            } finally {
                releaseFirstOutput.countDown();
            }

            assertThat(emissionCompleted.await(10, TimeUnit.SECONDS), is(true));
            assertBatchFailure(expectedFailure, actualFailure.get());
            assertThat(invokedOutputs, is(List.of("first", "second")));
        }
    }

    @Test
    void testRetryAfterFanOutFailureCanDuplicateCompletedOutputs() {
        List<String> deliveries = new ArrayList<>();
        AtomicInteger secondOutputAttempts = new AtomicInteger();
        MessagingException expectedFailure = new MessagingException("temporary output failure");

        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("imperative-fan-out-retry", String.class);
        builder.messageSink(channel, message -> deliveries.add("first"))
                .messageSink(channel, message -> {
                    deliveries.add("second");
                    if (secondOutputAttempts.getAndIncrement() == 0) {
                        throw expectedFailure;
                    }
                })
                .messageSink(channel, message -> deliveries.add("third"));

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            var emitter = graph.emitter(channel);
            assertBatchFailure(expectedFailure, () -> emitter.emit("test message"));

            emitter.emit("test message");

            assertThat(deliveries, is(List.of("first", "second", "first", "second", "third")));
        }
    }

    @Test
    void testRuntimeCanEmitIntoNamedChannel() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);

        runtime.emit(ChannelMessagingTypes.CHANNEL_ONE,
                     Message.builder("runtime message")
                             .header("key", "runtime")
                             .build());

        var firstConsumer = registry.get(FirstChannelOneConsumer.class);
        var secondConsumer = registry.get(SecondChannelOneConsumer.class);
        var batchConsumer = registry.get(BatchChannelOneConsumer.class);

        assertThat(firstConsumer.messages(), hasSize(1));
        assertThat(firstConsumer.keys(), is(List.of("runtime")));
        assertThat(secondConsumer.messages(), hasSize(1));
        assertThat(secondConsumer.messages().getFirst().entity(), is("runtime message"));
        assertThat(batchConsumer.batches(), hasSize(1));
        assertThat(batchConsumer.batches().getFirst().get(0).entity(), is("runtime message"));
    }

    @Test
    void testRuntimeRejectsEmitterWithoutTargetAtStartup() {
        EmitterRegistration registration = new EmitterRegistration() {
            @Override
            public String channel() {
                return "unknown-channel";
            }

            @Override
            public String producerId() {
                return "test-unknown-emitter";
            }

            @Override
            public GenericType<?> payloadGenericType() {
                return GenericType.create(String.class);
            }

            @Override
            public GenericType<?> envelopeGenericType() {
                return new GenericType<Message<String>>() { };
            }
        };
        registryManager.shutdown();
        registryManager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                .putContractInstance(EmitterRegistration.class,
                                                                                     registration)
                                                                .build());
        registry = registryManager.registry();

        RuntimeException thrown = assertThrows(RuntimeException.class,
                                               () -> registry.get(MessagingRuntime.class));
        Throwable cause = rootCause(thrown);

        assertThat(cause.getMessage(), containsString("unknown-channel"));
        assertThat(cause.getMessage(), containsString("test-unknown-emitter"));
    }

    @Test
    void testRuntimeCanEmitBatchIntoNamedChannel() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);

        MessageBatch<String> batch = MessageBatch.<String>builder()
                .id("runtime-batch")
                .add(Message.builder("runtime batch first")
                             .header("key", "batch-1")
                             .build())
                .add(Message.builder("runtime batch second")
                             .header("key", "batch-2")
                             .build())
                .build();
        runtime.emitBatch(ChannelMessagingTypes.CHANNEL_ONE, batch);

        var firstConsumer = registry.get(FirstChannelOneConsumer.class);
        var secondConsumer = registry.get(SecondChannelOneConsumer.class);
        var batchConsumer = registry.get(BatchChannelOneConsumer.class);

        assertThat(firstConsumer.messages(), hasSize(2));
        assertThat(firstConsumer.keys(), is(List.of("batch-1", "batch-2")));
        assertThat(secondConsumer.messages(), hasSize(2));
        assertThat(batchConsumer.batches(), hasSize(1));
        assertThat(batchConsumer.batches().getFirst().size(), is(2));
        assertThat(batchConsumer.batches().getFirst().id(), is("runtime-batch"));
        assertThat(batchConsumer.batches().getFirst().get(0).entity(), is("runtime batch first"));
        assertThat(batchConsumer.batches().getFirst().get(1).header("key").orElseThrow(), is("batch-2"));
    }

    @Test
    void testCustomMessageSubtypeDispatch() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);
        CustomMessage<String, Integer> message =
                new ImmutableCustomMessage<>("custom-key", 42, Map.of("source", "custom"));

        runtime.emit(ChannelMessagingTypes.CUSTOM_MESSAGE_CHANNEL, message);

        var consumer = registry.get(CustomMessageConsumer.class);
        assertThat(consumer.messages(), is(List.of(message)));
        assertThat(consumer.messages().getFirst().key(), is("custom-key"));
        assertThat(consumer.messages().getFirst().entity(), is(42));
        assertThat(consumer.messages().getFirst().header("source").orElseThrow(), is("custom"));

        var broadConsumer = registry.get(BroadCustomMessageConsumer.class);
        assertThat(broadConsumer.messages(), is(List.of(message)));
    }

    @Test
    void testCustomMessageSubtypeRejectsBaseEnvelope() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);

        IllegalArgumentException singleFailure =
                assertThrows(IllegalArgumentException.class,
                             () -> runtime.emit(ChannelMessagingTypes.CUSTOM_MESSAGE_CHANNEL, Message.create(42)));
        assertThat(singleFailure.getMessage(), containsString("expected message envelope type"));
        assertThat(singleFailure.getMessage(), containsString(CustomMessage.class.getName()));
        assertThat(registry.get(BroadCustomMessageConsumer.class).messages(), empty());
        assertThat(registry.get(CustomMessageConsumer.class).messages(), empty());
    }

    @Test
    void testMultiHopMessageSubtypeResolvesParameterizedPayload() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);
        MultiHopMessage<String, List<Integer>> message =
                new ImmutableMultiHopMessage<>("multi-hop-key", List.of(1, 2, 3), Map.of("source", "multi-hop"));

        runtime.emit(ChannelMessagingTypes.MULTI_HOP_MESSAGE_CHANNEL, message);

        var consumer = registry.get(MultiHopMessageConsumer.class);
        assertThat(consumer.messages(), is(List.of(message)));
        assertThat(consumer.messages().getFirst().key(), is("multi-hop-key"));
        assertThat(consumer.messages().getFirst().entity(), is(List.of(1, 2, 3)));
        assertThat(consumer.messages().getFirst().header("source").orElseThrow(), is("multi-hop"));
    }

    @Test
    void testNamedChannelFanOutAndHeaders() {
        var producer = registry.get(Producer.class);

        producer.emitChannelOne("test message 1");

        var firstConsumer = registry.get(FirstChannelOneConsumer.class);
        var secondConsumer = registry.get(SecondChannelOneConsumer.class);
        var batchConsumer = registry.get(BatchChannelOneConsumer.class);
        var channelTwoConsumer = registry.get(ChannelTwoConsumer.class);

        assertThat(firstConsumer.messages(), hasSize(1));
        assertThat(firstConsumer.keys(), is(List.of("value")));
        assertThat(secondConsumer.messages(), hasSize(1));
        assertThat(batchConsumer.batches(), hasSize(1));
        assertThat(batchConsumer.batches().getFirst().size(), is(1));
        assertThat(channelTwoConsumer.messages(), empty());

        Message<String> firstMessage = firstConsumer.messages().getFirst();
        Message<String> secondMessage = secondConsumer.messages().getFirst();
        assertThat(firstMessage.entity(), is("test message 1"));
        assertThat(secondMessage.entity(), is("test message 1"));
        assertThat(firstMessage.header("key").orElseThrow(), is("value"));
        assertThat(secondMessage.header("key").orElseThrow(), is("value"));
    }

    @Test
    void testNamedEmitterPreservesBatch() {
        var producer = registry.get(Producer.class);

        producer.emitChannelOneBatch("emitter batch first", "emitter batch second");

        var firstConsumer = registry.get(FirstChannelOneConsumer.class);
        var secondConsumer = registry.get(SecondChannelOneConsumer.class);
        var batchConsumer = registry.get(BatchChannelOneConsumer.class);

        assertThat(firstConsumer.messages(), hasSize(2));
        assertThat(firstConsumer.keys(), is(List.of("batch-first", "batch-second")));
        assertThat(secondConsumer.messages(), hasSize(2));
        assertThat(batchConsumer.batches(), hasSize(1));
        assertThat(batchConsumer.batches().getFirst().size(), is(2));
        assertThat(batchConsumer.batches().getFirst().get(0).entity(), is("emitter batch first"));
        assertThat(batchConsumer.batches().getFirst().get(1).entity(), is("emitter batch second"));
    }

    @Test
    void testGeneratedEmitterPreservesHandlerFailure() {
        var producer = registry.get(Producer.class);
        var consumer = registry.get(FailingConsumer.class);

        assertBatchFailure(consumer.failure(), () -> producer.emitFailingChannel("test message"));
    }

    @Test
    void testIncomingConnectorSourcePreservesHandlerFailure() throws InterruptedException {
        String channelConfig = "helidon.messaging.incoming." + ChannelMessagingTypes.FAILING_CHANNEL;
        useConfig(Map.of(channelConfig + ".connector", ChannelMessagingTypes.TEST_CONNECTOR,
                         channelConfig + ".failure.retry.max-attempts", "1"));
        registry.get(MessagingRuntime.class);
        var observer = registry.get(TestConnectorObserver.class);
        var consumer = registry.get(FailingConsumer.class);

        assertThat(observer.awaitDelivery(), is(true));
        RuntimeException thrown = observer.deliveryFailure().orElseThrow();

        assertBatchFailure(consumer.failure(), thrown);
    }

    @Test
    void testGeneratedOnFailureRetriesThenDeadLetters() throws InterruptedException {
        String channelConfig = "helidon.messaging.incoming." + ChannelMessagingTypes.ANNOTATED_FAILURE_CHANNEL;
        useConfig(Map.of(channelConfig + ".connector", ChannelMessagingTypes.TEST_CONNECTOR));
        registry.get(MessagingRuntime.class);
        var observer = registry.get(TestConnectorObserver.class);
        var consumer = registry.get(AnnotatedFailureConsumer.class);
        var deadLetterConsumer = registry.get(AnnotatedFailureDeadLetterConsumer.class);

        assertThat(observer.awaitDelivery(), is(true));
        assertThat(observer.deliveryFailure().isEmpty(), is(true));
        assertThat(consumer.attempts(), is(2));
        assertThat(deadLetterConsumer.messages(), hasSize(1));

        DeadLetterMessage<String> deadLetter = deadLetterConsumer.messages().getFirst();
        assertThat(deadLetter.entity(), is("connector message"));
        assertThat(deadLetter.sourceChannel(), is(ChannelMessagingTypes.ANNOTATED_FAILURE_CHANNEL));
        assertThat(deadLetter.attempts(), is(2));
        assertThat(deadLetter.failureMessage(), is("annotated handler failed"));
    }

    @Test
    void testFailureConfigOverridesGeneratedOnFailureWithDrop() throws InterruptedException {
        String channelConfig = "helidon.messaging.incoming." + ChannelMessagingTypes.ANNOTATED_FAILURE_CHANNEL;
        useConfig(Map.of(channelConfig + ".connector", ChannelMessagingTypes.TEST_CONNECTOR,
                         channelConfig + ".failure.retry.max-attempts", "1",
                         channelConfig + ".failure.on-exhausted", "DROP"));
        registry.get(MessagingRuntime.class);
        var observer = registry.get(TestConnectorObserver.class);
        var consumer = registry.get(AnnotatedFailureConsumer.class);
        var deadLetterConsumer = registry.get(AnnotatedFailureDeadLetterConsumer.class);

        assertThat(observer.awaitDelivery(), is(true));
        assertThat(observer.deliveryFailure().isEmpty(), is(true));
        assertThat(consumer.attempts(), is(1));
        assertThat(deadLetterConsumer.messages(), empty());
    }

    @Test
    void testProcessorPreservesReturnedMessage() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);

        runtime.emit(ChannelMessagingTypes.FORWARDING_INPUT_CHANNEL, Message.create("test message"));

        var consumer = registry.get(ForwardedMessageConsumer.class);
        assertThat(consumer.messages(), hasSize(1));
        assertThat(consumer.messages().getFirst().entity(), is("forwarded: test message"));
        assertThat(consumer.messages().getFirst().header("processor").orElseThrow(), is("forwarding"));
    }

    @Test
    void testProcessorMapsBatchWithoutFragmentingIt() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);
        MessageBatch<String> input = MessageBatch.<String>builder()
                .id("processor-batch")
                .add(Message.create("first"))
                .add(Message.create("second"))
                .build();

        runtime.emitBatch(ChannelMessagingTypes.FORWARDING_INPUT_CHANNEL, input);

        var messageConsumer = registry.get(ForwardedMessageConsumer.class);
        var batchConsumer = registry.get(ForwardedBatchConsumer.class);
        assertThat(messageConsumer.messages().stream().map(Message::entity).toList(),
                   is(List.of("forwarded: first", "forwarded: second")));
        assertThat(batchConsumer.batches(), hasSize(1));
        assertThat(batchConsumer.batches().getFirst().id(), is("processor-batch"));
        assertThat(input.sameDelivery(batchConsumer.batches().getFirst()), is(true));
        assertThat(batchConsumer.batches().getFirst().payloads(),
                   is(List.of("forwarded: first", "forwarded: second")));
    }

    @Test
    void testProcessorWrapsReturnedPayload() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);

        runtime.emit(ChannelMessagingTypes.PAYLOAD_PROCESSOR_INPUT_CHANNEL, Message.create("test message"));

        var consumer = registry.get(PayloadProcessorConsumer.class);
        assertThat(consumer.messages(), hasSize(1));
        assertThat(consumer.messages().getFirst().entity(), is("processed: test message"));
        assertThat(consumer.messages().getFirst().headers(), is(Map.of()));
    }

    @Test
    void testProcessorRoutesGenericArrayMessagePayload() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);

        runtime.emit(ChannelMessagingTypes.ARRAY_PROCESSOR_INPUT_CHANNEL, Message.create("array value"));

        var consumer = registry.get(ArrayPayloadConsumer.class);
        assertThat(consumer.payloads(), hasSize(1));
        assertThat(consumer.payloads().getFirst(),
                   is(new String[][] {{"array value"}, {"processed: array value"}}));
    }

    @Test
    void testProcessorPreservesProcessorFailure() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);
        var processor = registry.get(ForwardingProcessor.class);
        var consumer = registry.get(ForwardedMessageConsumer.class);

        assertBatchFailure(processor.failure(),
                           () -> runtime.emit(ChannelMessagingTypes.FORWARDING_INPUT_CHANNEL,
                                              Message.create("processor-fail")));
        assertThat(consumer.messages(), empty());
    }

    @Test
    void testProcessorPreservesDownstreamFailure() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);
        var consumer = registry.get(ForwardedMessageConsumer.class);

        assertBatchFailure(consumer.failure(),
                           () -> runtime.emit(ChannelMessagingTypes.FORWARDING_INPUT_CHANNEL,
                                              Message.create("fail")));
        assertThat(consumer.messages(), empty());
    }

    @Test
    void testRequiredHeaderIsResolvedAndMissingHeaderFails() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);
        var consumer = registry.get(RequiredHeaderConsumer.class);

        BatchDeliveryException thrown =
                assertThrows(BatchDeliveryException.class,
                             () -> runtime.emit(ChannelMessagingTypes.REQUIRED_HEADER_CHANNEL,
                                                Message.create("missing header")));

        assertSingleIndeterminateOutcome(thrown);
        assertThat(rootCause(thrown).getMessage(), containsString("required"));
        assertThat(consumer.deliveries(), empty());

        runtime.emit(ChannelMessagingTypes.REQUIRED_HEADER_CHANNEL,
                     Message.builder("present header")
                             .header("required", "header value")
                             .build());

        assertThat(consumer.deliveries(), is(List.of(new HeaderDelivery("present header", "header value"))));
    }

    @Test
    void testOptionalHeaderIsEmptyOrPresent() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);

        runtime.emit(ChannelMessagingTypes.OPTIONAL_HEADER_CHANNEL, Message.create("missing header"));
        runtime.emit(ChannelMessagingTypes.OPTIONAL_HEADER_CHANNEL,
                     Message.builder("present header")
                             .header("trace-id", "trace-123")
                             .build());

        var consumer = registry.get(OptionalHeaderConsumer.class);
        assertThat(consumer.deliveries(),
                   is(List.of(new OptionalHeaderDelivery("missing header", Optional.empty()),
                              new OptionalHeaderDelivery("present header", Optional.of("trace-123")))));
    }

    @Test
    void testMessagingHandlerUsesGenericEntryPointInterceptors() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);
        TestEntryPointInterceptor.reset();

        runtime.emit(ChannelMessagingTypes.REQUIRED_HEADER_CHANNEL,
                     Message.builder("intercepted")
                             .header("required", "value")
                             .build());

        assertThat(TestEntryPointInterceptor.executions(), hasSize(1));
        assertThat(TestEntryPointInterceptor.executions().getFirst(),
                   containsString(RequiredHeaderConsumer.class.getCanonicalName() + ".consume("));
    }

    @Test
    void testBatchMessagingHandlerUsesGenericEntryPointInterceptorsOnce() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);
        TestEntryPointInterceptor.reset();

        runtime.emitBatch(ChannelMessagingTypes.CHANNEL_ONE,
                          MessageBatch.create(List.of(Message.builder("first")
                                                             .header("key", "first")
                                                             .build(),
                                                      Message.builder("second")
                                                             .header("key", "second")
                                                             .build())));

        long batchExecutions = TestEntryPointInterceptor.executions()
                .stream()
                .filter(execution -> execution.contains(BatchChannelOneConsumer.class.getCanonicalName() + ".consume("))
                .count();
        assertThat(batchExecutions, is(1L));
    }

    @Test
    void testPerLookupHandlerAndInterceptorUseSameServiceInstance() {
        MessagingRuntime runtime = registry.get(MessagingRuntime.class);
        TestEntryPointInterceptor.reset();
        PerLookupInterceptedConsumer.reset();

        runtime.emit(ChannelMessagingTypes.PER_LOOKUP_INTERCEPTED_CHANNEL, Message.create("first"));
        runtime.emit(ChannelMessagingTypes.PER_LOOKUP_INTERCEPTED_CHANNEL, Message.create("second"));

        List<PerLookupInterceptedConsumer> targets = PerLookupInterceptedConsumer.instances();
        List<Object> intercepted = TestEntryPointInterceptor.serviceInstances(PerLookupInterceptedConsumer.class);
        assertThat(targets, hasSize(2));
        assertThat(intercepted, hasSize(2));
        assertSame(targets.get(0), intercepted.get(0));
        assertSame(targets.get(1), intercepted.get(1));
        assertNotSame(targets.get(0), targets.get(1));
    }

    @Test
    void testIncomingConnectorEmitsIntoNamedChannel() throws InterruptedException {
        useConfig(Map.of("helidon.messaging.incoming." + ChannelMessagingTypes.CHANNEL_ONE + ".connector",
                         ChannelMessagingTypes.TEST_CONNECTOR));
        registry.get(MessagingRuntime.class);
        var observer = registry.get(TestConnectorObserver.class);

        assertThat(observer.awaitDelivery(), is(true));
        assertThat(observer.deliveryFailure().isEmpty(), is(true));

        var firstConsumer = registry.get(FirstChannelOneConsumer.class);
        var secondConsumer = registry.get(SecondChannelOneConsumer.class);
        var batchConsumer = registry.get(BatchChannelOneConsumer.class);
        var channelTwoConsumer = registry.get(ChannelTwoConsumer.class);

        assertThat(firstConsumer.messages(), hasSize(1));
        assertThat(firstConsumer.keys(), is(List.of("connector")));
        assertThat(secondConsumer.messages(), hasSize(1));
        assertThat(batchConsumer.batches(), hasSize(1));
        assertThat(channelTwoConsumer.messages(), empty());
        assertThat(firstConsumer.messages().getFirst().entity(), is("connector message"));
        assertThat(secondConsumer.messages().getFirst().header("key").orElseThrow(), is("connector"));
    }

    @Test
    void testMessagingStopsBeforeConsumerServiceIsDestroyed() {
        ShutdownConsumer.events().clear();
        useConfig(Map.of("helidon.messaging.incoming." + ChannelMessagingTypes.SHUTDOWN_CHANNEL + ".connector",
                         ChannelMessagingTypes.SHUTDOWN_CONNECTOR));
        registry.get(MessagingRuntime.class);
        registry.get(ShutdownConsumer.class);

        registryManager.shutdown();

        assertThat(ShutdownConsumer.events(), is(List.of("source-start", "source-stop", "consumer-close")));
    }

    @Test
    void testMessagingStartsEagerlyAtItsRunLevel() {
        ShutdownConsumer.events().clear();

        startWithConfig(Map.of("helidon.messaging.incoming." + ChannelMessagingTypes.SHUTDOWN_CHANNEL + ".connector",
                               ChannelMessagingTypes.SHUTDOWN_CONNECTOR));

        assertThat(registry.get(Config.class)
                           .get("helidon.messaging.incoming." + ChannelMessagingTypes.SHUTDOWN_CHANNEL + ".connector")
                           .asString()
                           .orElse(""),
                   is(ChannelMessagingTypes.SHUTDOWN_CONNECTOR));
        assertThat(ShutdownConsumer.events(), is(List.of("source-start")));
    }

    @Test
    void testNamedChannelsAreIsolated() {
        var producer = registry.get(Producer.class);

        producer.emitChannelTwo("test message 2");

        var firstConsumer = registry.get(FirstChannelOneConsumer.class);
        var secondConsumer = registry.get(SecondChannelOneConsumer.class);
        var batchConsumer = registry.get(BatchChannelOneConsumer.class);
        var channelTwoConsumer = registry.get(ChannelTwoConsumer.class);

        assertThat(firstConsumer.messages(), empty());
        assertThat(secondConsumer.messages(), empty());
        assertThat(batchConsumer.batches(), empty());
        assertThat(channelTwoConsumer.messages(), hasSize(1));

        Message<String> message = channelTwoConsumer.messages().getFirst();
        assertThat(message.entity(), is("test message 2"));
        assertThat(message.headers().isEmpty(), is(true));
    }

    @Test
    void testNamedEmitterInjectionUsesServiceNamed() {
        var producer = registry.get(Producer.class);

        assertThat(producer.emittersInjected(), is(true));
    }

    private void useConfig(Map<String, String> values) {
        registryManager.shutdown();
        Config config = Config.just(ConfigSources.create(values));
        ServiceRegistryConfig registryConfig = ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, config)
                .build();
        registryManager = ServiceRegistryManager.create(registryConfig);
        registry = registryManager.registry();
    }

    private void startWithConfig(Map<String, String> values) {
        registryManager.shutdown();
        Config config = Config.just(ConfigSources.create(values));
        ServiceRegistryConfig registryConfig = ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, config)
                .build();
        registryManager = ServiceRegistryManager.start(registryConfig);
        registry = registryManager.registry();
    }

    private static OutgoingConnector sink(List<Message<?>> messages) {
        return new NoOpOutgoingConnector() {
            @Override
            public void sendBatch(MessageBatch<?> batch) {
                messages.addAll(batch.messages());
            }
        };
    }

    private abstract static class NoOpOutgoingConnector implements OutgoingConnector {
        @Override
        public void start() {
        }

        @Override
        public void forceClose() {
        }

        @Override
        public void close() {
        }
    }

    private static BatchDeliveryException assertBatchFailure(Throwable expectedFailure, Runnable action) {
        BatchDeliveryException actualFailure = assertThrows(BatchDeliveryException.class, action::run);
        return assertBatchFailure(expectedFailure, actualFailure);
    }

    private static BatchDeliveryException assertBatchFailure(Throwable expectedFailure, Throwable actualFailure) {
        BatchDeliveryException batchFailure = assertInstanceOf(BatchDeliveryException.class, actualFailure);
        assertSingleIndeterminateOutcome(batchFailure);
        Throwable cause = batchFailure;
        while (cause != null) {
            if (cause == expectedFailure) {
                return batchFailure;
            }
            cause = cause.getCause();
        }
        throw new AssertionError("Expected failure is not present in the batch failure cause chain", batchFailure);
    }

    private static void assertSingleIndeterminateOutcome(BatchDeliveryException failure) {
        assertThat(failure.batch().size(), is(1));
        assertThat(failure.outcomes(), hasSize(1));
        assertThat(failure.outcome(0).status(), is(BatchItemStatus.INDETERMINATE));
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable result = throwable;
        while (result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }
}
