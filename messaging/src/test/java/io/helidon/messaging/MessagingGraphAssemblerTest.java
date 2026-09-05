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

package io.helidon.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.common.GenericType;
import io.helidon.messaging.spi.OutgoingConnector;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingGraphAssemblerTest {
    private static final Duration SHORT_SHUTDOWN_TIMEOUT = Duration.ofMillis(100);

    @Test
    void channelIsOpaqueAndEmissionRequiresExplicitStart() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("events", String.class);
        assembler.payloadSink(channel, ignored -> { });

        assertThat(channel instanceof Emitter<?>, is(false));
        try (MessagingGraph graph = assembler.build()) {
            Emitter<String> emitter = graph.emitter(channel);
            assertThrows(IllegalStateException.class, () -> emitter.emit("too-early"));

            graph.start();
            emitter.emit("started");
        }
    }

    @Test
    void buildFinalizesRoutedDeliveryLimits() {
        MessagingExecutionConfig sourceConfig = MessagingExecutionConfig.builder()
                .maxPendingMessages(8)
                .maxInFlightMessages(8)
                .build();
        MessagingExecutionConfig targetConfig = MessagingExecutionConfig.builder()
                .maxPendingMessages(1)
                .maxInFlightMessages(3)
                .build();
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> source = assembler.channel("source", GenericType.create(String.class), sourceConfig);
        MessagingChannel<String> target = assembler.channel("target", GenericType.create(String.class), targetConfig);
        assembler.route(source, target)
                .payloadSink(target, ignored -> { });

        try (MessagingGraph graph = assembler.build()) {
            assertThat(((DefaultMessagingGraph) graph).maxDeliveryMessages("source"), is(3));
            assertThat(((DefaultMessagingGraph) graph).maxDeliveryMessages("target"), is(1));
        }
    }

    @Test
    void payloadAndMessageSourcesFeedIndependentChannels() throws InterruptedException {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<Integer> payloadChannel = assembler.channel("payload-numbers", Integer.class);
        MessagingChannel<Integer> messageChannel = assembler.channel("message-numbers", Integer.class);
        List<Message<Integer>> delivered = new CopyOnWriteArrayList<>();
        CountDownLatch delivery = new CountDownLatch(2);
        Consumer<Message<Integer>> sink = message -> {
                    delivered.add(message);
                    delivery.countDown();
                };
        assembler.payloadSource(payloadChannel, Stream.of(1))
                .messageSource(messageChannel, Stream.of(Message.create(2)))
                .messageSink(payloadChannel, sink)
                .messageSink(messageChannel, sink);

        try (MessagingGraph graph = assembler.build()) {
            graph.start();
            assertThat("Delivered messages: " + delivered.stream().map(Message::entity).toList(),
                       delivery.await(5, TimeUnit.SECONDS),
                       is(true));
        }

        assertThat(delivered.stream().map(Message::entity).sorted().toList(), is(List.of(1, 2)));
    }

    @Test
    void payloadAndMessageProcessorsUseTypedChannels() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> payloadInput = assembler.channel("payload-input", String.class);
        MessagingChannel<Integer> lengths = assembler.channel("lengths", Integer.class);
        MessagingChannel<String> messageInput = assembler.channel("message-input", String.class);
        MessagingChannel<String> upperCase = assembler.channel("upper-case", String.class);
        List<Integer> deliveredLengths = new ArrayList<>();
        AtomicReference<Message<String>> deliveredMessage = new AtomicReference<>();

        assembler.payloadProcessor(payloadInput, lengths, String::length)
                .payloadSink(lengths, deliveredLengths::add)
                .messageProcessor(messageInput,
                                  upperCase,
                                  message -> Message.builder(message.entity().toUpperCase())
                                          .header("trace", message.header("trace").orElseThrow())
                                          .build())
                .messageSink(upperCase, deliveredMessage::set);

        try (MessagingGraph graph = assembler.build()) {
            graph.start();
            graph.emitter(payloadInput).emit("four");
            graph.emitter(messageInput).emit(Message.builder("hello")
                                                       .header("trace", "123")
                                                       .localMetadata("local-only", "not-propagated")
                                                       .build());
        }

        assertThat(deliveredLengths, is(List.of(4)));
        assertThat(deliveredMessage.get().entity(), is("HELLO"));
        assertThat(deliveredMessage.get().header("trace").orElseThrow(), is("123"));
        assertThat(deliveredMessage.get().localMetadata(), sameInstance(MessageMetadata.empty()));
    }

    @Test
    void routesRetainLocalMetadataWithTheMessageEnvelope() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> source = assembler.channel("local-metadata-source", String.class);
        MessagingChannel<String> target = assembler.channel("local-metadata-target", String.class);
        AtomicReference<Message<String>> delivered = new AtomicReference<>();
        assembler.route(source, target).messageSink(target, delivered::set);
        Message<String> message = Message.builder("payload")
                .localMetadata("local-only", "retained")
                .build();

        try (MessagingGraph graph = assembler.build()) {
            graph.start();
            graph.emitter(source).emit(message);
        }

        assertThat(delivered.get(), sameInstance(message));
        assertThat(delivered.get().localMetadata().text("local-only").orElseThrow(), is("retained"));
    }

    @Test
    void emitterOverloadsPreservePayloadMessageAndBatchBoundaries() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("overloaded-emitter", String.class);
        List<MessageBatch<String>> delivered = new ArrayList<>();
        assembler.batchSink(channel, delivered::add);
        Message<String> message = Message.builder("message").header("trace", "one").build();
        MessageBatch<String> batch = MessageBatch.create(
                List.of(Message.builder("batch").header("trace", "two").build()));

        try (MessagingGraph graph = assembler.build()) {
            graph.start();
            Emitter<String> emitter = graph.emitter(channel);
            emitter.emit("payload");
            emitter.emit(message);
            emitter.emit(batch);

            String nullPayload = null;
            Message<String> nullMessage = null;
            MessageBatch<String> nullBatch = null;
            assertThrows(NullPointerException.class, () -> emitter.emit(nullPayload));
            assertThrows(NullPointerException.class, () -> emitter.emit(nullMessage));
            assertThrows(NullPointerException.class, () -> emitter.emit(nullBatch));
        }

        assertThat(delivered.size(), is(3));
        assertThat(delivered.get(0).payloads(), is(List.of("payload")));
        assertThat(delivered.get(0).get(0).headers().isEmpty(), is(true));
        assertThat(delivered.get(1).get(0), sameInstance(message));
        assertThat(delivered.get(2), sameInstance(batch));
    }

    @Test
    void messageSubtypeAndOuterMessagesDisambiguateObjectEmitter() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<Object> directChannel = assembler.channel("direct-objects", Object.class);
        MessagingChannel<String> processorInput = assembler.channel("processor-strings", String.class);
        MessagingChannel<Object> processorOutput = assembler.channel("processor-objects", Object.class);
        List<Message<Object>> delivered = new ArrayList<>();
        ConnectorMessage<String> connectorMessage = new ConnectorMessage<>(
                "connector",
                MessageHeaders.builder().add("trace", "connector").build());
        Message<ConnectorMessage<String>> wrappedMessagePayload = Message.builder(connectorMessage)
                .header("trace", "outer")
                .build();
        Message<String> batched = Message.builder("batched").header("trace", "two").build();
        MessageBatch<String> batchPayload = MessageBatch.create(Message.create("batch-payload"));
        Message<MessageBatch<String>> wrappedBatchPayload = Message.builder(batchPayload)
                .header("trace", "outer-batch")
                .build();
        Message<String> processed = Message.builder("processed").header("trace", "three").build();
        assembler.messageSink(directChannel, delivered::add)
                .messageProcessor(processorInput, processorOutput, ignored -> processed)
                .messageSink(processorOutput, delivered::add);

        try (MessagingGraph graph = assembler.build()) {
            graph.start();
            Emitter<Object> emitter = graph.emitter(directChannel);
            emitter.emit(connectorMessage);
            emitter.emit((Object) connectorMessage);
            emitter.emit(wrappedMessagePayload);
            emitter.emit(MessageBatch.create(List.of(batched)));
            emitter.emit(wrappedBatchPayload);
            graph.emitter(processorInput).emit("process");
        }

        assertThat(delivered.get(0), sameInstance(connectorMessage));
        assertThat(delivered.get(0).header("trace").orElseThrow(), is("connector"));
        assertThat(delivered.get(1).entity(), sameInstance(connectorMessage));
        assertThat(delivered.get(1).headers().isEmpty(), is(true));
        assertThat(delivered.get(2), sameInstance(wrappedMessagePayload));
        assertThat(delivered.get(2).entity(), sameInstance(connectorMessage));
        assertThat(delivered.get(2).header("trace").orElseThrow(), is("outer"));
        assertThat(delivered.get(3), sameInstance(batched));
        assertThat(delivered.get(4), sameInstance(wrappedBatchPayload));
        assertThat(delivered.get(4).entity(), sameInstance(batchPayload));
        assertThat(delivered.get(4).header("trace").orElseThrow(), is("outer-batch"));
        assertThat(delivered.get(5), sameInstance(processed));
        assertThat(delivered.get(3).header("trace").orElseThrow(), is("two"));
        assertThat(delivered.get(5).header("trace").orElseThrow(), is("three"));
    }

    @Test
    void stronglyTypedMessageImplementationUsesPayloadOverload() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<MessagePayload> channel = assembler.channel("message-payload", MessagePayload.class);
        AtomicReference<Message<MessagePayload>> delivered = new AtomicReference<>();
        MessagePayload payload = new MessagePayload("payload");
        assembler.messageSink(channel, delivered::set);

        try (MessagingGraph graph = assembler.build()) {
            graph.start();
            graph.emitter(channel).emit(payload);
        }

        assertThat(delivered.get().entity(), sameInstance(payload));
        assertThat(delivered.get().headers().isEmpty(), is(true));
    }

    @Test
    void stronglyTypedMessageBatchUsesPayloadOverload() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<MessageBatch<String>> channel = assembler.channel("batch-payload", new GenericType<>() { });
        AtomicReference<Message<MessageBatch<String>>> delivered = new AtomicReference<>();
        MessageBatch<String> payload = MessageBatch.create(Message.create("payload"));
        assembler.messageSink(channel, delivered::set);

        try (MessagingGraph graph = assembler.build()) {
            graph.start();
            graph.emitter(channel).emit(payload);
        }

        assertThat(delivered.get().entity(), sameInstance(payload));
        assertThat(delivered.get().headers().isEmpty(), is(true));
    }

    @Test
    void messageImplementationCanBeUsedAsPayload() throws InterruptedException {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<MessagePayload> input = assembler.channel("message-payload-input", MessagePayload.class);
        MessagingChannel<MessagePayload> output = assembler.channel("message-payload-output", MessagePayload.class);
        AtomicReference<MessagePayload> delivered = new AtomicReference<>();
        CountDownLatch delivery = new CountDownLatch(1);
        MessagePayload payload = new MessagePayload("payload");
        assembler.payloadSource(input, Stream.of(payload))
                .payloadProcessor(input, output, value -> value)
                .payloadSink(output, value -> {
                    delivered.set(value);
                    delivery.countDown();
                });

        try (MessagingGraph graph = assembler.build()) {
            graph.start();
            assertThat(delivery.await(5, TimeUnit.SECONDS), is(true));
        }

        assertThat(delivered.get(), sameInstance(payload));
    }

    @Test
    void connectorKeepsOutputRegistrationOrder() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("ordered", String.class);
        List<String> outputs = new ArrayList<>();
        assembler.messageSink(channel, ignored -> outputs.add("first"))
                .outgoingConnector(channel, new OutgoingConnector() {
                    @Override
                    public void start() {
                    }

                    @Override
                    public void sendBatch(MessageBatch<?> batch) {
                        outputs.add("connector");
                    }

                    @Override
                    public void forceClose() {
                    }

                    @Override
                    public void close() {
                    }
                })
                .messageSink(channel, ignored -> outputs.add("last"));

        try (MessagingGraph graph = assembler.build()) {
            graph.start();
            graph.emitter(channel).emit("event");
        }

        assertThat(outputs, is(List.of("first", "connector", "last")));
    }

    @Test
    void successfulBuildTransfersResourceOwnershipAndRejectsAssemblerReuse() {
        AtomicInteger streamCloses = new AtomicInteger();
        AtomicBoolean connectorReleased = new AtomicBoolean();
        OutgoingConnector connector = new OutgoingConnector() {
            @Override
            public void start() {
            }

            @Override
            public void sendBatch(MessageBatch<?> batch) {
            }

            @Override
            public void forceClose() {
                connectorReleased.set(true);
            }

            @Override
            public void close() {
                connectorReleased.set(true);
            }
        };
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("transferred", String.class);
        assembler.payloadSource(channel, Stream.<String>empty().onClose(streamCloses::incrementAndGet))
                .outgoingConnector(channel, connector);
        MessagingGraph graph = assembler.build();
        try {
            assertThrows(IllegalStateException.class, assembler::build);
            assertThrows(IllegalStateException.class, () -> assembler.payloadSink(channel, ignored -> { }));

            assembler.close();

            assertThat(streamCloses.get(), is(0));
            assertThat(connectorReleased.get(), is(false));
        } finally {
            graph.close();
        }

        assertThat(streamCloses.get(), is(1));
        assertThat(connectorReleased.get(), is(true));

        graph.close();
    }

    @Test
    void closingUnbuiltAssemblerClosesRegisteredResources() {
        AtomicBoolean streamClosed = new AtomicBoolean();
        TestConnector connector = new TestConnector();
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("abandoned", String.class);
        assembler.payloadSource(channel, Stream.<String>empty().onClose(() -> streamClosed.set(true)))
                .outgoingConnector(channel, connector);

        assembler.close();

        assertThat(streamClosed.get(), is(true));
        assertThat(connector.closed.get(), is(true));
        assertThrows(IllegalStateException.class, assembler::build);
    }

    @Test
    void closingUnbuiltAssemblerContinuesAfterResourceError() {
        AssertionError closeError = new AssertionError("first close failed");
        AtomicBoolean secondStreamClosed = new AtomicBoolean();
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> firstChannel = assembler.channel("first-cleanup", String.class);
        MessagingChannel<String> secondChannel = assembler.channel("second-cleanup", String.class);
        assembler.payloadSource(firstChannel, Stream.<String>empty().onClose(() -> {
                    throw closeError;
                }))
                .payloadSource(secondChannel,
                               Stream.<String>empty().onClose(() -> secondStreamClosed.set(true)));

        MessagingException failure = assertThrows(MessagingException.class, assembler::close);

        assertThat(failure.getCause(), sameInstance(closeError));
        assertThat(secondStreamClosed.get(), is(true));
    }

    @Test
    void closingUnbuiltAssemblerBoundsBlockingStreamCloseAndAttemptsLaterCleanup() throws InterruptedException {
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        CountDownLatch closeExited = new CountDownLatch(1);
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        OrderedConnector connector = new OrderedConnector(new CopyOnWriteArrayList<>());
        MessagingGraph.Assembler assembler = MessagingGraph.assembler()
                .executionConfig(MessagingExecutionConfig.builder()
                                         .shutdownTimeout(SHORT_SHUTDOWN_TIMEOUT)
                                         .build());
        MessagingChannel<String> channel = assembler.channel("blocking-stream-cleanup", String.class);
        assembler.payloadSource(channel, Stream.<String>empty().onClose(() -> {
                    closeEntered.countDown();
                    awaitUninterruptibly(releaseClose);
                    closeExited.countDown();
                }))
                .outgoingConnector(channel, connector)
                .payloadSink(channel, ignored -> { });

        Thread closeThread = Thread.ofVirtual().start(() -> runCapturing(assembler::close, closeFailure));
        try {
            assertThat(closeEntered.await(5, TimeUnit.SECONDS), is(true));
            closeThread.join(TimeUnit.SECONDS.toMillis(2));

            assertThat("Assembler close exceeded its shutdown timeout", closeThread.isAlive(), is(false));
            assertThat(String.valueOf(closeFailure.get()), closeFailure.get(), instanceOf(MessagingException.class));
            assertThat(closeFailure.get().getMessage(),
                       closeFailure.get().getMessage(),
                       containsString("Timed out"));
            assertThat("Connector force close was not attempted after stream cleanup timed out",
                       connector.forceAttempted.await(5, TimeUnit.SECONDS),
                       is(true));
            assertThat("Connector close was not attempted after stream cleanup timed out",
                       connector.closeAttempted.await(5, TimeUnit.SECONDS),
                       is(true));
            assertThat("Post-deadline connector close started with its interrupt status set",
                       connector.closeInterrupted.get(),
                       is(false));
            assertThat(connector.lifecycle, is(List.of("force", "close")));
        } finally {
            releaseClose.countDown();
            assertThat(closeExited.await(5, TimeUnit.SECONDS), is(true));
            closeThread.join(TimeUnit.SECONDS.toMillis(5));
        }
    }

    @Test
    void closingUnbuiltAssemblerForceClosesConnectorBeforeNormalClose() {
        List<String> lifecycle = new CopyOnWriteArrayList<>();
        OrderedConnector connector = new OrderedConnector(lifecycle);
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("abandoned-connector", String.class);
        assembler.outgoingConnector(channel, connector);

        assembler.close();

        assertThat(lifecycle, is(List.of("force", "close")));
    }

    @Test
    void failedBuildPreservesValidationFailureAndSuppressesBoundedCleanupFailure() throws InterruptedException {
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        CountDownLatch closeExited = new CountDownLatch(1);
        AtomicReference<Throwable> buildFailure = new AtomicReference<>();
        MessagingGraph.Assembler assembler = MessagingGraph.assembler()
                .executionConfig(MessagingExecutionConfig.builder()
                                         .shutdownTimeout(SHORT_SHUTDOWN_TIMEOUT)
                                         .build());
        MessagingChannel<String> channel = assembler.channel("outputless-blocking-cleanup", String.class);
        assembler.payloadSource(channel, Stream.<String>empty().onClose(() -> {
            closeEntered.countDown();
            awaitUninterruptibly(releaseClose);
            closeExited.countDown();
        }));

        Thread buildThread = Thread.ofVirtual().start(() -> runCapturing(assembler::build, buildFailure));
        try {
            assertThat(closeEntered.await(5, TimeUnit.SECONDS), is(true));
            buildThread.join(TimeUnit.SECONDS.toMillis(2));

            assertThat("Failed build cleanup exceeded its shutdown timeout", buildThread.isAlive(), is(false));
            assertThat(String.valueOf(buildFailure.get()),
                       buildFailure.get(),
                       instanceOf(IllegalArgumentException.class));
            assertThat(buildFailure.get().getMessage(),
                       buildFailure.get().getMessage(),
                       containsString("has no required output"));
            assertThat("Suppressed failures: " + List.of(buildFailure.get().getSuppressed()),
                       List.of(buildFailure.get().getSuppressed()).stream()
                               .anyMatch(failure -> failure instanceof MessagingException
                                       && failure.getMessage().contains("Timed out")),
                       is(true));
        } finally {
            releaseClose.countDown();
            assertThat(closeExited.await(5, TimeUnit.SECONDS), is(true));
            buildThread.join(TimeUnit.SECONDS.toMillis(5));
        }
    }

    @Test
    void multipleStreamSourcesOnOneChannelAreRejected() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("merged", String.class);
        assembler.payloadSource(channel, Stream.of("first"))
                .payloadSink(channel, ignored -> { });

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                         () -> assembler.payloadSource(channel, Stream.of("second")));

        assertThat(failure.getMessage(), containsString("merged already has a stream source"));
        try (MessagingGraph graph = assembler.build()) {
            graph.start();
        }
    }

    @Test
    void downstreamPathsOfStreamSourcesCannotConverge() {
        AtomicBoolean firstClosed = new AtomicBoolean();
        AtomicBoolean secondClosed = new AtomicBoolean();
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> first = assembler.channel("first-source", String.class);
        MessagingChannel<String> second = assembler.channel("second-source", String.class);
        MessagingChannel<String> merged = assembler.channel("merged-target", String.class);
        assembler.payloadSource(first, Stream.<String>empty().onClose(() -> firstClosed.set(true)))
                .payloadSource(second, Stream.<String>empty().onClose(() -> secondClosed.set(true)))
                .route(first, merged)
                .route(second, merged)
                .payloadSink(merged, ignored -> { });

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, assembler::build);

        assertThat(failure.getMessage(), containsString("fan-in to channel merged-target is not supported"));
        assertThat(firstClosed.get(), is(true));
        assertThat(secondClosed.get(), is(true));
    }

    @Test
    void admittedHandlerCanEmitThroughPublicEmitterWhileGraphDrains() throws InterruptedException {
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        AtomicReference<Emitter<String>> nestedEmitter = new AtomicReference<>();
        AtomicReference<String> delivered = new AtomicReference<>();
        AtomicReference<Throwable> emissionFailure = new AtomicReference<>();
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> input = assembler.channel("draining-input", String.class);
        MessagingChannel<String> output = assembler.channel("draining-output", String.class);
        assembler.payloadSink(input, payload -> {
                    handlerEntered.countDown();
                    await(releaseHandler);
                    nestedEmitter.get().emit(payload + "-nested");
                })
                .payloadSink(output, delivered::set);
        MessagingGraph graph = assembler.build();
        nestedEmitter.set(graph.emitter(output));
        graph.start();

        Thread emission = Thread.ofVirtual().start(() -> runCapturing(
                () -> graph.emitter(input).emit("event"), emissionFailure));
        assertThat(handlerEntered.await(5, TimeUnit.SECONDS), is(true));
        Thread close = Thread.ofVirtual().start(() -> runCapturing(graph::close, closeFailure));
        awaitState((DefaultMessagingGraph) graph, DefaultMessagingGraph.State.DRAINING);
        MessagingRejectedException externalRejection = assertThrows(MessagingRejectedException.class,
                                                                      () -> nestedEmitter.get().emit("external"));
        assertThat(externalRejection.reason(), is(MessagingRejectedException.Reason.SHUTDOWN));
        releaseHandler.countDown();
        emission.join(TimeUnit.SECONDS.toMillis(5));
        close.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(emission.isAlive(), is(false));
        assertThat(close.isAlive(), is(false));
        assertThat(emissionFailure.get(), nullValue());
        assertThat(closeFailure.get(), nullValue());
        assertThat(delivered.get(), is("event-nested"));
    }

    @Test
    void gracefulCloseRescansChannelsAfterDescendantAdmission() throws InterruptedException {
        CountDownLatch childCreated = new CountDownLatch(1);
        CountDownLatch allowChildEmission = new CountDownLatch(1);
        CountDownLatch targetStarted = new CountDownLatch(1);
        CountDownLatch releaseTarget = new CountDownLatch(1);
        CountDownLatch releaseParent = new CountDownLatch(1);
        AtomicBoolean targetCompletedNaturally = new AtomicBoolean();
        AtomicBoolean targetInterrupted = new AtomicBoolean();
        AtomicReference<Emitter<String>> descendantEmitter = new AtomicReference<>();
        AtomicReference<Thread> childThread = new AtomicReference<>();
        AtomicReference<Throwable> childFailure = new AtomicReference<>();
        AtomicReference<Throwable> parentFailure = new AtomicReference<>();
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> first = assembler.channel("a", String.class);
        MessagingChannel<String> second = assembler.channel("b", String.class);
        assembler.payloadSink(first, ignored -> {
                    targetStarted.countDown();
                    try {
                        releaseTarget.await();
                        targetCompletedNaturally.set(true);
                    } catch (InterruptedException e) {
                        targetInterrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                })
                .payloadSink(second, ignored -> {
                    Thread child = Thread.ofPlatform().start(() -> {
                        childCreated.countDown();
                        await(allowChildEmission);
                        runCapturing(() -> descendantEmitter.get().emit("child"), childFailure);
                    });
                    childThread.set(child);
                    await(releaseParent);
                });
        MessagingGraph graph = assembler.build();
        descendantEmitter.set(graph.emitter(first));
        Emitter<String> secondEmitter = graph.emitter(second);
        graph.start();
        Thread parent = Thread.ofVirtual().start(() -> runCapturing(() -> secondEmitter.emit("parent"), parentFailure));
        Thread closer = null;
        try {
            await(childCreated);
            closer = Thread.ofVirtual().start(() -> runCapturing(graph::close, closeFailure));
            awaitState((DefaultMessagingGraph) graph, DefaultMessagingGraph.State.DRAINING);
            awaitWaiting(closer);

            allowChildEmission.countDown();
            Thread child = childThread.get();
            assertThat(targetStarted.await(5, TimeUnit.SECONDS), is(true));
            releaseParent.countDown();
            parent.join(TimeUnit.SECONDS.toMillis(5));
            assertThat(parent.isAlive(), is(false));

            closer.join(500);
            assertThat("Graceful close stopped waiting for newly admitted work", closer.isAlive(), is(true));
            assertThat(targetInterrupted.get(), is(false));

            releaseTarget.countDown();
            child.join(TimeUnit.SECONDS.toMillis(5));
            closer.join(TimeUnit.SECONDS.toMillis(5));
            assertThat(child.isAlive(), is(false));
            assertThat(closer.isAlive(), is(false));
            assertThat(targetCompletedNaturally.get(), is(true));
            assertThat(targetInterrupted.get(), is(false));
            assertThat(childFailure.get(), nullValue());
            assertThat(parentFailure.get(), nullValue());
            assertThat(closeFailure.get(), nullValue());
            assertThat(((DefaultMessagingGraph) graph).state(), is(DefaultMessagingGraph.State.CLOSED));
        } finally {
            allowChildEmission.countDown();
            releaseParent.countDown();
            releaseTarget.countDown();
            parent.join(TimeUnit.SECONDS.toMillis(5));
            Thread child = childThread.get();
            if (child != null) {
                child.join(TimeUnit.SECONDS.toMillis(5));
            }
            if (closer != null) {
                closer.join(TimeUnit.SECONDS.toMillis(5));
            }
        }
    }

    @Test
    void asynchronousStreamSourceFailureIsReportedByClose() {
        IllegalStateException sourceFailure = new IllegalStateException("stream delivery failed");
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("failing-stream", String.class);
        assembler.payloadSource(channel, Stream.of("event"))
                .payloadSink(channel, ignored -> {
                    throw sourceFailure;
                });
        MessagingGraph graph = assembler.build();
        graph.start();
        awaitState((DefaultMessagingGraph) graph, DefaultMessagingGraph.State.FAILED);

        MessagingException failure = assertThrows(MessagingException.class, graph::close);

        assertThat(failure.getMessage(), containsString("failing-stream-source"));
        assertThat(failure.getCause(), instanceOf(BatchDeliveryException.class));
        assertThat(failure.getCause().getCause(), sameInstance(sourceFailure));
    }

    @Test
    void checkedStreamIterationFailureIsRecorded() {
        Exception sourceFailure = new Exception("checked stream iteration failure");
        CountDownLatch iterationEntered = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);
        Iterator<String> iterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                iterationEntered.countDown();
                await(releaseFailure);
                MessagingGraphAssemblerTest.<RuntimeException>rethrow(sourceFailure);
                return false;
            }

            @Override
            public String next() {
                throw new AssertionError("next must not be called after hasNext fails");
            }
        };
        Stream<String> source = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                false);
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("checked-stream-failure", String.class);
        assembler.payloadSource(channel, source)
                .payloadSink(channel, ignored -> { });
        MessagingGraph graph = assembler.build();
        graph.start();
        try {
            await(iterationEntered);
            assertThat(((DefaultMessagingGraph) graph).state(), is(DefaultMessagingGraph.State.RUNNING));
            releaseFailure.countDown();
            awaitState((DefaultMessagingGraph) graph, DefaultMessagingGraph.State.FAILED);

            Throwable graphFailure = ((DefaultMessagingGraph) graph).failure().orElseThrow();
            MessagingException closeFailure = assertThrows(MessagingException.class, graph::close);

            assertThat(graphFailure, sameInstance(closeFailure));
            assertThat(graphFailure.getCause(), sameInstance(sourceFailure));
        } finally {
            releaseFailure.countDown();
        }
    }

    @Test
    void streamCloseFailureIsSuppressedOnCheckedIterationFailure() {
        Exception sourceFailure = new Exception("checked stream iteration failure");
        Exception streamCloseFailure = new Exception("checked stream close failure");
        CountDownLatch iterationEntered = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);
        Iterator<String> iterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                iterationEntered.countDown();
                await(releaseFailure);
                MessagingGraphAssemblerTest.<RuntimeException>rethrow(sourceFailure);
                return false;
            }

            @Override
            public String next() {
                throw new AssertionError("next must not be called after hasNext fails");
            }
        };
        Stream<String> source = StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                        false)
                .onClose(() -> MessagingGraphAssemblerTest.<RuntimeException>rethrow(streamCloseFailure));
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("checked-stream-close-failure", String.class);
        assembler.payloadSource(channel, source)
                .payloadSink(channel, ignored -> { });
        MessagingGraph graph = assembler.build();
        graph.start();
        try {
            await(iterationEntered);
            assertThat(((DefaultMessagingGraph) graph).state(), is(DefaultMessagingGraph.State.RUNNING));
            releaseFailure.countDown();
            awaitState((DefaultMessagingGraph) graph, DefaultMessagingGraph.State.FAILED);

            Throwable graphFailure = ((DefaultMessagingGraph) graph).failure().orElseThrow();
            MessagingException closeFailure = assertThrows(MessagingException.class, graph::close);

            assertThat(graphFailure, sameInstance(closeFailure));
            assertThat(graphFailure.getCause(), sameInstance(sourceFailure));
            assertThat(sourceFailure.getSuppressed().length, is(1));
            assertThat(sourceFailure.getSuppressed()[0], sameInstance(streamCloseFailure));
        } finally {
            releaseFailure.countDown();
        }
    }

    @Test
    void blockedStreamCheckedInterruptionDrainsCleanlyWithoutAdmittedWork() throws InterruptedException {
        CountDownLatch iterationEntered = new CountDownLatch(1);
        CountDownLatch releaseIteration = new CountDownLatch(1);
        CountDownLatch iterationInterrupted = new CountDownLatch(1);
        AtomicBoolean streamClosed = new AtomicBoolean();
        AtomicBoolean streamCloseInterrupted = new AtomicBoolean();
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        Iterator<String> iterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                iterationEntered.countDown();
                try {
                    releaseIteration.await();
                    return false;
                } catch (InterruptedException e) {
                    iterationInterrupted.countDown();
                    MessagingGraphAssemblerTest.<RuntimeException>rethrow(e);
                    return false;
                }
            }

            @Override
            public String next() {
                throw new AssertionError("next must not be called while hasNext is blocked");
            }
        };
        Stream<String> source = StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                        false)
                .onClose(() -> {
                    streamCloseInterrupted.set(Thread.currentThread().isInterrupted());
                    if (streamCloseInterrupted.get()) {
                        throw new IllegalStateException("Stream close inherited the drain interruption");
                    }
                    streamClosed.set(true);
                });
        MessagingGraph.Assembler assembler = MessagingGraph.assembler()
                .executionConfig(MessagingExecutionConfig.builder()
                                         .shutdownTimeout(SHORT_SHUTDOWN_TIMEOUT)
                                         .build());
        MessagingChannel<String> channel = assembler.channel("blocked-stream-iteration", String.class);
        assembler.payloadSource(channel, source)
                .payloadSink(channel, ignored -> { });
        MessagingGraph graph = assembler.build();
        graph.start();
        assertThat(iterationEntered.await(5, TimeUnit.SECONDS), is(true));

        Thread closeThread = Thread.ofVirtual().start(() -> runCapturing(graph::close, closeFailure));
        try {
            closeThread.join(TimeUnit.SECONDS.toMillis(2));

            assertThat("Graph close exceeded its shutdown timeout", closeThread.isAlive(), is(false));
            assertThat(closeFailure.get(), nullValue());
            assertThat(iterationInterrupted.await(5, TimeUnit.SECONDS), is(true));
            assertThat(streamClosed.get(), is(true));
            assertThat(streamCloseInterrupted.get(), is(false));
            assertThat(((DefaultMessagingGraph) graph).state(), is(DefaultMessagingGraph.State.CLOSED));
        } finally {
            releaseIteration.countDown();
            closeThread.join(TimeUnit.SECONDS.toMillis(5));
        }
    }

    @Test
    void genuineIteratorFailureDuringDrainFailsGraphClose() throws InterruptedException {
        IllegalStateException iteratorFailure = new IllegalStateException("iterator failed during drain");
        CountDownLatch iterationEntered = new CountDownLatch(1);
        CountDownLatch drainInterruptObserved = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);
        AtomicBoolean streamClosed = new AtomicBoolean();
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        Iterator<String> iterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                iterationEntered.countDown();
                try {
                    releaseFailure.await();
                } catch (InterruptedException e) {
                    drainInterruptObserved.countDown();
                    awaitUninterruptibly(releaseFailure);
                    Thread.currentThread().interrupt();
                }
                throw iteratorFailure;
            }

            @Override
            public String next() {
                throw new AssertionError("next must not be called after hasNext fails");
            }
        };
        Stream<String> source = StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                        false)
                .onClose(() -> streamClosed.set(true));
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("failing-stream-iteration", String.class);
        assembler.payloadSource(channel, source)
                .payloadSink(channel, ignored -> { });
        MessagingGraph graph = assembler.build();
        graph.start();
        assertThat(iterationEntered.await(5, TimeUnit.SECONDS), is(true));

        Thread closeThread = Thread.ofVirtual().start(() -> runCapturing(graph::close, closeFailure));
        try {
            assertThat(drainInterruptObserved.await(5, TimeUnit.SECONDS), is(true));
            releaseFailure.countDown();
            closeThread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(closeThread.isAlive(), is(false));
            assertThat(closeFailure.get(), sameInstance(iteratorFailure));
            assertThat(streamClosed.get(), is(true));
            assertThat(((DefaultMessagingGraph) graph).state(), is(DefaultMessagingGraph.State.FAILED));
        } finally {
            releaseFailure.countDown();
            closeThread.join(TimeUnit.SECONDS.toMillis(5));
        }
    }

    @Test
    void downstreamShutdownRejectionDuringDrainFailsGraphClose() throws InterruptedException {
        CountDownLatch sinkEntered = new CountDownLatch(1);
        CountDownLatch releaseSink = new CountDownLatch(1);
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        MessagingRejectedException rejection = new MessagingRejectedException(
                "downstream",
                MessagingRejectedException.Reason.SHUTDOWN);
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("drain-failure", String.class);
        assembler.payloadSource(channel, Stream.of("event"))
                .payloadSink(channel, ignored -> {
                    sinkEntered.countDown();
                    await(releaseSink);
                    throw rejection;
                });
        MessagingGraph graph = assembler.build();
        graph.start();
        assertThat(sinkEntered.await(5, TimeUnit.SECONDS), is(true));

        Thread close = Thread.ofVirtual().start(() -> runCapturing(graph::close, closeFailure));
        awaitState((DefaultMessagingGraph) graph, DefaultMessagingGraph.State.DRAINING);
        releaseSink.countDown();
        close.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(close.isAlive(), is(false));
        assertThat(closeFailure.get(), instanceOf(BatchDeliveryException.class));
        assertThat(closeFailure.get().getCause(), sameInstance(rejection));
        assertThat(((DefaultMessagingGraph) graph).state(), is(DefaultMessagingGraph.State.FAILED));
    }

    @Test
    void batchSinksReceiveOneImmutableBatch() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("events", String.class);
        AtomicReference<MessageBatch<String>> received = new AtomicReference<>();
        assembler.batchSink(channel, received::set);

        Message<String> first = Message.builder("first").header("position", "1").build();
        Message<String> second = Message.builder("second").header("position", "2").build();
        MessageBatch<String> batch = MessageBatch.<String>builder()
                .id("explicit-batch")
                .messages(List.of(first, second))
                .build();
        try (MessagingGraph graph = assembler.build()) {
            graph.start();
            graph.emitter(channel).emit(batch);
        }

        assertThat(received.get(), sameInstance(batch));
        assertThat(received.get().id(), is("explicit-batch"));
        assertThat(received.get().payloads(), is(List.of("first", "second")));
        assertThat(received.get().messages(), is(List.of(first, second)));
        assertThrows(UnsupportedOperationException.class, () -> received.get().payloads().add("third"));
        assertThrows(UnsupportedOperationException.class,
                     () -> received.get().messages().add(Message.create("third")));
    }

    @Test
    void processorFailureLeavesUntouchedBatchSuffixNotAttempted() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> source = assembler.channel("source", String.class);
        MessagingChannel<String> target = assembler.channel("target", String.class);
        AtomicInteger invocations = new AtomicInteger();
        AtomicReference<MessageBatch<String>> received = new AtomicReference<>();
        assembler.payloadProcessor(source, target, value -> {
            if (invocations.incrementAndGet() == 2) {
                throw new IllegalStateException("processor failed");
            }
            return value.toUpperCase();
        });
        assembler.batchSink(target, received::set);

        try (MessagingGraph graph = assembler.build()) {
            graph.start();
            BatchDeliveryException failure = assertThrows(
                    BatchDeliveryException.class,
                    () -> graph.emitter(source).emit(MessageBatch.create(
                            List.of(Message.create("first"),
                                    Message.create("second"),
                                    Message.create("third")))));

            assertThat(failure.outcomes().stream().map(BatchItemOutcome::status).toList(),
                       is(List.of(BatchItemStatus.INDETERMINATE,
                                  BatchItemStatus.INDETERMINATE,
                                  BatchItemStatus.NOT_ATTEMPTED)));
        }
        assertThat(invocations.get(), is(2));
        assertThat(received.get(), nullValue());
    }

    @Test
    void channelRetainsParameterizedPayloadType() {
        GenericType<List<String>> payloadType = new GenericType<>() { };
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<List<String>> channel = assembler.channel("lists", payloadType);
        assembler.payloadSink(channel, ignored -> { });

        try (MessagingGraph ignored = assembler.build()) {
            assertThat(channel.payloadType(), sameInstance(payloadType));
            assertThat(channel.name(), is("lists"));
        }
    }

    @Test
    void channelsCannotCrossAssemblerOrGraphBoundaries() {
        MessagingGraph.Assembler firstAssembler = MessagingGraph.assembler();
        MessagingGraph.Assembler secondAssembler = MessagingGraph.assembler();
        MessagingChannel<String> first = firstAssembler.channel("first", String.class);
        MessagingChannel<String> second = secondAssembler.channel("second", String.class);
        firstAssembler.payloadSink(first, ignored -> { });
        secondAssembler.payloadSink(second, ignored -> { });

        assertThrows(IllegalArgumentException.class, () -> firstAssembler.route(first, second));

        try (MessagingGraph firstGraph = firstAssembler.build();
                MessagingGraph secondGraph = secondAssembler.build()) {
            assertThrows(IllegalArgumentException.class, () -> firstGraph.emitter(second));
            assertThrows(IllegalArgumentException.class, () -> secondGraph.emitter(first));
        }
    }

    @Test
    void failedTopologyBuildClosesTransferredStream() {
        AtomicBoolean closed = new AtomicBoolean();
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> first = assembler.channel("first", String.class);
        MessagingChannel<String> second = assembler.channel("second", String.class);
        TestConnector connector = new TestConnector();
        assembler.payloadSource(first, Stream.<String>empty().onClose(() -> closed.set(true)))
                .outgoingConnector(first, connector)
                .route(first, second)
                .route(second, first);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, assembler::build);

        assertThat(failure.getMessage(), containsString("Cyclic synchronous messaging route"));
        assertThat(closed.get(), is(true));
        assertThat(connector.closed.get(), is(true));
        assertThrows(IllegalStateException.class, assembler::build);
    }

    @Test
    void duplicateChannelNamesAreRejected() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        MessagingChannel<String> channel = assembler.channel("events", String.class);
        assembler.payloadSink(channel, ignored -> { });

        assertThrows(IllegalArgumentException.class, () -> assembler.channel("events", String.class));

        try (MessagingGraph ignored = assembler.build()) {
            assertThrows(IllegalStateException.class, () -> assembler.channel("later", String.class));
        }
    }

    @Test
    void primitiveChannelPayloadTypesAreRejected() {
        try (MessagingGraph.Assembler assembler = MessagingGraph.assembler()) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                             () -> assembler.channel("primitive", int.class));

            assertThat(failure.getMessage(), containsString("must not be primitive"));
        }
    }

    @Test
    void outputlessChannelsAreRejectedAtBuild() {
        MessagingGraph.Assembler assembler = MessagingGraph.assembler();
        assembler.channel("discarded", String.class);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, assembler::build);

        assertThat(failure.getMessage(), containsString("discarded has no required output"));
        assertThrows(IllegalStateException.class, assembler::build);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for test latch", e);
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitState(DefaultMessagingGraph graph, DefaultMessagingGraph.State expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (graph.state() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(graph.state(), is(expected));
    }

    private static void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("Timed out waiting for thread to block");
    }

    private static void runCapturing(Runnable task, AtomicReference<Throwable> failure) {
        try {
            task.run();
        } catch (Throwable t) {
            failure.set(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void rethrow(Throwable failure) throws T {
        throw (T) failure;
    }

    private record MessagePayload(String entity) implements Message<String> {
        @Override
        public MessageHeaders headers() {
            return MessageHeaders.empty();
        }
    }

    private record ConnectorMessage<T>(T entity, MessageHeaders headers) implements Message<T> {
    }

    private static final class TestConnector implements OutgoingConnector {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void sendBatch(MessageBatch<?> batch) {
        }

        @Override
        public void start() {
        }

        @Override
        public void forceClose() {
            close();
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static final class OrderedConnector implements OutgoingConnector {
        private final List<String> lifecycle;
        private final CountDownLatch forceAttempted = new CountDownLatch(1);
        private final CountDownLatch closeAttempted = new CountDownLatch(1);
        private final AtomicBoolean closeInterrupted = new AtomicBoolean();

        private OrderedConnector(List<String> lifecycle) {
            this.lifecycle = lifecycle;
        }

        @Override
        public void sendBatch(MessageBatch<?> batch) {
        }

        @Override
        public void start() {
        }

        @Override
        public void forceClose() {
            lifecycle.add("force");
            forceAttempted.countDown();
        }

        @Override
        public void close() {
            closeInterrupted.set(Thread.currentThread().isInterrupted());
            lifecycle.add("close");
            closeAttempted.countDown();
        }
    }
}
