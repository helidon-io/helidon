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
import java.util.Map;
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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagingGraphBuilderTest {
    private static final Duration SHORT_SHUTDOWN_TIMEOUT = Duration.ofMillis(100);

    @Test
    void channelIsOpaqueAndEmissionRequiresExplicitStart() {
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("events", String.class);
        builder.payloadSink(channel, ignored -> { });

        assertFalse(channel instanceof Emitter<?>);
        try (MessagingGraph graph = builder.build()) {
            Emitter<String> emitter = graph.emitter(channel);
            assertThrows(IllegalStateException.class, () -> emitter.emit("too-early"));

            graph.start();
            emitter.emit("started");
        }
    }

    @Test
    void payloadAndMessageSourcesFeedIndependentChannels() throws InterruptedException {
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<Integer> payloadChannel = builder.channel("payload-numbers", Integer.class);
        MessagingChannel<Integer> messageChannel = builder.channel("message-numbers", Integer.class);
        List<Message<Integer>> delivered = new CopyOnWriteArrayList<>();
        CountDownLatch delivery = new CountDownLatch(2);
        Consumer<Message<Integer>> sink = message -> {
                    delivered.add(message);
                    delivery.countDown();
                };
        builder.payloadSource(payloadChannel, Stream.of(1))
                .messageSource(messageChannel, Stream.of(Message.create(2)))
                .messageSink(payloadChannel, sink)
                .messageSink(messageChannel, sink);

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            assertTrue(delivery.await(5, TimeUnit.SECONDS),
                       "Delivered messages: " + delivered.stream().map(Message::entity).toList());
        }

        assertEquals(List.of(1, 2), delivered.stream().map(Message::entity).sorted().toList());
    }

    @Test
    void payloadAndMessageProcessorsUseTypedChannels() {
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> payloadInput = builder.channel("payload-input", String.class);
        MessagingChannel<Integer> lengths = builder.channel("lengths", Integer.class);
        MessagingChannel<String> messageInput = builder.channel("message-input", String.class);
        MessagingChannel<String> upperCase = builder.channel("upper-case", String.class);
        List<Integer> deliveredLengths = new ArrayList<>();
        AtomicReference<Message<String>> deliveredMessage = new AtomicReference<>();

        builder.payloadProcessor(payloadInput, lengths, String::length)
                .payloadSink(lengths, deliveredLengths::add)
                .messageProcessor(messageInput,
                                  upperCase,
                                  message -> Message.builder(message.entity().toUpperCase())
                                          .header("trace", message.header("trace").orElseThrow())
                                          .build())
                .messageSink(upperCase, deliveredMessage::set);

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(payloadInput).emit("four");
            graph.emitter(messageInput).emitMessage(Message.builder("hello").header("trace", "123").build());
        }

        assertEquals(List.of(4), deliveredLengths);
        assertEquals("HELLO", deliveredMessage.get().entity());
        assertEquals("123", deliveredMessage.get().header("trace").orElseThrow());
    }

    @Test
    void subtypeMessagesRemainEnvelopesOnSupertypeChannelsAndProcessorTargets() {
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<Object> directChannel = builder.channel("direct-objects", Object.class);
        MessagingChannel<String> processorInput = builder.channel("processor-strings", String.class);
        MessagingChannel<Object> processorOutput = builder.channel("processor-objects", Object.class);
        List<Message<Object>> delivered = new ArrayList<>();
        Message<String> messagePayload = Message.builder("payload-message").header("trace", "payload").build();
        Message<String> direct = Message.builder("direct").header("trace", "one").build();
        Message<String> batched = Message.builder("batched").header("trace", "two").build();
        Message<String> processed = Message.builder("processed").header("trace", "three").build();
        builder.messageSink(directChannel, delivered::add)
                .messageProcessor(processorInput, processorOutput, ignored -> processed)
                .messageSink(processorOutput, delivered::add);

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(directChannel).emit(messagePayload);
            graph.emitter(directChannel).emitMessage(direct);
            graph.emitter(directChannel).emitBatch(MessageBatch.create(List.of(batched)));
            graph.emitter(processorInput).emit("process");
        }

        assertSame(messagePayload, delivered.get(0).entity());
        assertTrue(delivered.get(0).headers().isEmpty());
        assertSame(direct, delivered.get(1));
        assertSame(batched, delivered.get(2));
        assertSame(processed, delivered.get(3));
        assertEquals(List.of("one", "two", "three"),
                     delivered.subList(1, 4)
                             .stream()
                             .map(message -> message.header("trace").orElseThrow())
                             .toList());
    }

    @Test
    void messageImplementationCanBeUsedAsPayload() throws InterruptedException {
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<MessagePayload> input = builder.channel("message-payload-input", MessagePayload.class);
        MessagingChannel<MessagePayload> output = builder.channel("message-payload-output", MessagePayload.class);
        AtomicReference<MessagePayload> delivered = new AtomicReference<>();
        CountDownLatch delivery = new CountDownLatch(1);
        MessagePayload payload = new MessagePayload("payload");
        builder.payloadSource(input, Stream.of(payload))
                .payloadProcessor(input, output, value -> value)
                .payloadSink(output, value -> {
                    delivered.set(value);
                    delivery.countDown();
                });

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            assertTrue(delivery.await(5, TimeUnit.SECONDS));
        }

        assertSame(payload, delivered.get());
    }

    @Test
    void connectorKeepsOutputRegistrationOrder() {
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("ordered", String.class);
        List<String> outputs = new ArrayList<>();
        builder.messageSink(channel, ignored -> outputs.add("first"))
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

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(channel).emit("event");
        }

        assertEquals(List.of("first", "connector", "last"), outputs);
    }

    @Test
    void closingUnbuiltBuilderClosesRegisteredResources() {
        AtomicBoolean streamClosed = new AtomicBoolean();
        TestConnector connector = new TestConnector();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("abandoned", String.class);
        builder.payloadSource(channel, Stream.<String>empty().onClose(() -> streamClosed.set(true)))
                .outgoingConnector(channel, connector);

        builder.close();

        assertTrue(streamClosed.get());
        assertTrue(connector.closed.get());
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void closingUnbuiltBuilderContinuesAfterResourceError() {
        AssertionError closeError = new AssertionError("first close failed");
        AtomicBoolean secondStreamClosed = new AtomicBoolean();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> firstChannel = builder.channel("first-cleanup", String.class);
        MessagingChannel<String> secondChannel = builder.channel("second-cleanup", String.class);
        builder.payloadSource(firstChannel, Stream.<String>empty().onClose(() -> {
                    throw closeError;
                }))
                .payloadSource(secondChannel,
                               Stream.<String>empty().onClose(() -> secondStreamClosed.set(true)));

        MessagingException failure = assertThrows(MessagingException.class, builder::close);

        assertSame(closeError, failure.getCause());
        assertTrue(secondStreamClosed.get());
    }

    @Test
    void closingUnbuiltBuilderBoundsBlockingStreamCloseAndAttemptsLaterCleanup() throws InterruptedException {
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        CountDownLatch closeExited = new CountDownLatch(1);
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        OrderedConnector connector = new OrderedConnector(new CopyOnWriteArrayList<>());
        MessagingGraph.Builder builder = MessagingGraph.builder()
                .executionConfig(MessagingExecutionConfig.builder()
                                         .shutdownTimeout(SHORT_SHUTDOWN_TIMEOUT)
                                         .build());
        MessagingChannel<String> channel = builder.channel("blocking-stream-cleanup", String.class);
        builder.payloadSource(channel, Stream.<String>empty().onClose(() -> {
                    closeEntered.countDown();
                    awaitUninterruptibly(releaseClose);
                    closeExited.countDown();
                }))
                .outgoingConnector(channel, connector)
                .payloadSink(channel, ignored -> { });

        Thread closeThread = Thread.ofVirtual().start(() -> runCapturing(builder::close, closeFailure));
        try {
            assertTrue(closeEntered.await(5, TimeUnit.SECONDS));
            closeThread.join(TimeUnit.SECONDS.toMillis(2));

            assertFalse(closeThread.isAlive(), "Builder close exceeded its shutdown timeout");
            assertTrue(closeFailure.get() instanceof MessagingException, String.valueOf(closeFailure.get()));
            assertTrue(closeFailure.get().getMessage().contains("Timed out"), closeFailure.get().getMessage());
            assertTrue(connector.forceAttempted.await(5, TimeUnit.SECONDS),
                       "Connector force close was not attempted after stream cleanup timed out");
            assertTrue(connector.closeAttempted.await(5, TimeUnit.SECONDS),
                       "Connector close was not attempted after stream cleanup timed out");
            assertFalse(connector.closeInterrupted.get(),
                        "Post-deadline connector close started with its interrupt status set");
            assertEquals(List.of("force", "close"), connector.lifecycle);
        } finally {
            releaseClose.countDown();
            assertTrue(closeExited.await(5, TimeUnit.SECONDS));
            closeThread.join(TimeUnit.SECONDS.toMillis(5));
        }
    }

    @Test
    void closingUnbuiltBuilderForceClosesConnectorBeforeNormalClose() {
        List<String> lifecycle = new CopyOnWriteArrayList<>();
        OrderedConnector connector = new OrderedConnector(lifecycle);
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("abandoned-connector", String.class);
        builder.outgoingConnector(channel, connector);

        builder.close();

        assertEquals(List.of("force", "close"), lifecycle);
    }

    @Test
    void failedBuildPreservesValidationFailureAndSuppressesBoundedCleanupFailure() throws InterruptedException {
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        CountDownLatch closeExited = new CountDownLatch(1);
        AtomicReference<Throwable> buildFailure = new AtomicReference<>();
        MessagingGraph.Builder builder = MessagingGraph.builder()
                .executionConfig(MessagingExecutionConfig.builder()
                                         .shutdownTimeout(SHORT_SHUTDOWN_TIMEOUT)
                                         .build());
        MessagingChannel<String> channel = builder.channel("outputless-blocking-cleanup", String.class);
        builder.payloadSource(channel, Stream.<String>empty().onClose(() -> {
            closeEntered.countDown();
            awaitUninterruptibly(releaseClose);
            closeExited.countDown();
        }));

        Thread buildThread = Thread.ofVirtual().start(() -> runCapturing(builder::build, buildFailure));
        try {
            assertTrue(closeEntered.await(5, TimeUnit.SECONDS));
            buildThread.join(TimeUnit.SECONDS.toMillis(2));

            assertFalse(buildThread.isAlive(), "Failed build cleanup exceeded its shutdown timeout");
            assertTrue(buildFailure.get() instanceof IllegalArgumentException, String.valueOf(buildFailure.get()));
            assertTrue(buildFailure.get().getMessage().contains("has no required output"),
                       buildFailure.get().getMessage());
            assertTrue(List.of(buildFailure.get().getSuppressed()).stream()
                               .anyMatch(failure -> failure instanceof MessagingException
                                       && failure.getMessage().contains("Timed out")),
                       "Suppressed failures: " + List.of(buildFailure.get().getSuppressed()));
        } finally {
            releaseClose.countDown();
            assertTrue(closeExited.await(5, TimeUnit.SECONDS));
            buildThread.join(TimeUnit.SECONDS.toMillis(5));
        }
    }

    @Test
    void multipleStreamSourcesOnOneChannelAreRejected() {
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("merged", String.class);
        builder.payloadSource(channel, Stream.of("first"))
                .payloadSink(channel, ignored -> { });

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                         () -> builder.payloadSource(channel, Stream.of("second")));

        assertTrue(failure.getMessage().contains("merged already has a stream source"));
        try (MessagingGraph graph = builder.build()) {
            graph.start();
        }
    }

    @Test
    void downstreamPathsOfStreamSourcesCannotConverge() {
        AtomicBoolean firstClosed = new AtomicBoolean();
        AtomicBoolean secondClosed = new AtomicBoolean();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> first = builder.channel("first-source", String.class);
        MessagingChannel<String> second = builder.channel("second-source", String.class);
        MessagingChannel<String> merged = builder.channel("merged-target", String.class);
        builder.payloadSource(first, Stream.<String>empty().onClose(() -> firstClosed.set(true)))
                .payloadSource(second, Stream.<String>empty().onClose(() -> secondClosed.set(true)))
                .route(first, merged)
                .route(second, merged)
                .payloadSink(merged, ignored -> { });

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, builder::build);

        assertTrue(failure.getMessage().contains("fan-in to channel merged-target is not supported"));
        assertTrue(firstClosed.get());
        assertTrue(secondClosed.get());
    }

    @Test
    void admittedHandlerCanEmitThroughPublicEmitterWhileGraphDrains() throws InterruptedException {
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        AtomicReference<Emitter<String>> nestedEmitter = new AtomicReference<>();
        AtomicReference<String> delivered = new AtomicReference<>();
        AtomicReference<Throwable> emissionFailure = new AtomicReference<>();
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> input = builder.channel("draining-input", String.class);
        MessagingChannel<String> output = builder.channel("draining-output", String.class);
        builder.payloadSink(input, payload -> {
                    handlerEntered.countDown();
                    await(releaseHandler);
                    nestedEmitter.get().emit(payload + "-nested");
                })
                .payloadSink(output, delivered::set);
        MessagingGraph graph = builder.build();
        nestedEmitter.set(graph.emitter(output));
        graph.start();

        Thread emission = Thread.ofVirtual().start(() -> runCapturing(
                () -> graph.emitter(input).emit("event"), emissionFailure));
        assertTrue(handlerEntered.await(5, TimeUnit.SECONDS));
        Thread close = Thread.ofVirtual().start(() -> runCapturing(graph::close, closeFailure));
        awaitState((DefaultMessagingGraph) graph, DefaultMessagingGraph.State.DRAINING);
        MessagingRejectedException externalRejection = assertThrows(MessagingRejectedException.class,
                                                                      () -> nestedEmitter.get().emit("external"));
        assertEquals(MessagingRejectedException.Reason.SHUTDOWN, externalRejection.reason());
        releaseHandler.countDown();
        emission.join(TimeUnit.SECONDS.toMillis(5));
        close.join(TimeUnit.SECONDS.toMillis(5));

        assertFalse(emission.isAlive());
        assertFalse(close.isAlive());
        assertNull(emissionFailure.get());
        assertNull(closeFailure.get());
        assertEquals("event-nested", delivered.get());
    }

    @Test
    void asynchronousStreamSourceFailureIsReportedByClose() {
        IllegalStateException sourceFailure = new IllegalStateException("stream delivery failed");
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("failing-stream", String.class);
        builder.payloadSource(channel, Stream.of("event"))
                .payloadSink(channel, ignored -> {
                    throw sourceFailure;
                });
        MessagingGraph graph = builder.build();
        graph.start();
        awaitState((DefaultMessagingGraph) graph, DefaultMessagingGraph.State.FAILED);

        MessagingException failure = assertThrows(MessagingException.class, graph::close);

        assertTrue(failure.getMessage().contains("failing-stream-source"));
        assertTrue(failure.getCause() instanceof BatchDeliveryException);
        assertSame(sourceFailure, failure.getCause().getCause());
    }

    @Test
    void blockedStreamIterationDrainsCleanlyWithoutAdmittedWork() throws InterruptedException {
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
                    Thread.currentThread().interrupt();
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
        MessagingGraph.Builder builder = MessagingGraph.builder()
                .executionConfig(MessagingExecutionConfig.builder()
                                         .shutdownTimeout(SHORT_SHUTDOWN_TIMEOUT)
                                         .build());
        MessagingChannel<String> channel = builder.channel("blocked-stream-iteration", String.class);
        builder.payloadSource(channel, source)
                .payloadSink(channel, ignored -> { });
        MessagingGraph graph = builder.build();
        graph.start();
        assertTrue(iterationEntered.await(5, TimeUnit.SECONDS));

        Thread closeThread = Thread.ofVirtual().start(() -> runCapturing(graph::close, closeFailure));
        try {
            closeThread.join(TimeUnit.SECONDS.toMillis(2));

            assertFalse(closeThread.isAlive(), "Graph close exceeded its shutdown timeout");
            assertNull(closeFailure.get());
            assertTrue(iterationInterrupted.await(5, TimeUnit.SECONDS));
            assertTrue(streamClosed.get());
            assertFalse(streamCloseInterrupted.get());
            assertEquals(DefaultMessagingGraph.State.CLOSED, ((DefaultMessagingGraph) graph).state());
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
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("failing-stream-iteration", String.class);
        builder.payloadSource(channel, source)
                .payloadSink(channel, ignored -> { });
        MessagingGraph graph = builder.build();
        graph.start();
        assertTrue(iterationEntered.await(5, TimeUnit.SECONDS));

        Thread closeThread = Thread.ofVirtual().start(() -> runCapturing(graph::close, closeFailure));
        try {
            assertTrue(drainInterruptObserved.await(5, TimeUnit.SECONDS));
            releaseFailure.countDown();
            closeThread.join(TimeUnit.SECONDS.toMillis(5));

            assertFalse(closeThread.isAlive());
            assertSame(iteratorFailure, closeFailure.get());
            assertTrue(streamClosed.get());
            assertEquals(DefaultMessagingGraph.State.FAILED, ((DefaultMessagingGraph) graph).state());
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
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("drain-failure", String.class);
        builder.payloadSource(channel, Stream.of("event"))
                .payloadSink(channel, ignored -> {
                    sinkEntered.countDown();
                    await(releaseSink);
                    throw rejection;
                });
        MessagingGraph graph = builder.build();
        graph.start();
        assertTrue(sinkEntered.await(5, TimeUnit.SECONDS));

        Thread close = Thread.ofVirtual().start(() -> runCapturing(graph::close, closeFailure));
        awaitState((DefaultMessagingGraph) graph, DefaultMessagingGraph.State.DRAINING);
        releaseSink.countDown();
        close.join(TimeUnit.SECONDS.toMillis(5));

        assertFalse(close.isAlive());
        assertTrue(closeFailure.get() instanceof BatchDeliveryException);
        assertSame(rejection, closeFailure.get().getCause());
        assertEquals(DefaultMessagingGraph.State.FAILED, ((DefaultMessagingGraph) graph).state());
    }

    @Test
    void batchSinksReceiveOneImmutableBatch() {
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("events", String.class);
        AtomicReference<MessageBatch<String>> received = new AtomicReference<>();
        builder.batchSink(channel, received::set);

        Message<String> first = Message.builder("first").header("position", "1").build();
        Message<String> second = Message.builder("second").header("position", "2").build();
        MessageBatch<String> batch = MessageBatch.<String>builder()
                .id("explicit-batch")
                .messages(List.of(first, second))
                .build();
        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(channel).emitBatch(batch);
        }

        assertSame(batch, received.get());
        assertEquals("explicit-batch", received.get().id());
        assertEquals(List.of("first", "second"), received.get().payloads());
        assertEquals(List.of(first, second), received.get().messages());
        assertThrows(UnsupportedOperationException.class, () -> received.get().payloads().add("third"));
        assertThrows(UnsupportedOperationException.class,
                     () -> received.get().messages().add(Message.create("third")));
    }

    @Test
    void processorFailureLeavesUntouchedBatchSuffixNotAttempted() {
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> source = builder.channel("source", String.class);
        MessagingChannel<String> target = builder.channel("target", String.class);
        AtomicInteger invocations = new AtomicInteger();
        AtomicReference<MessageBatch<String>> received = new AtomicReference<>();
        builder.payloadProcessor(source, target, value -> {
            if (invocations.incrementAndGet() == 2) {
                throw new IllegalStateException("processor failed");
            }
            return value.toUpperCase();
        });
        builder.batchSink(target, received::set);

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            BatchDeliveryException failure = assertThrows(
                    BatchDeliveryException.class,
                    () -> graph.emitter(source).emitBatch(MessageBatch.create(
                            List.of(Message.create("first"),
                                    Message.create("second"),
                                    Message.create("third")))));

            assertEquals(List.of(BatchItemStatus.INDETERMINATE,
                                 BatchItemStatus.INDETERMINATE,
                                 BatchItemStatus.NOT_ATTEMPTED),
                         failure.outcomes().stream().map(BatchItemOutcome::status).toList());
        }
        assertEquals(2, invocations.get());
        assertNull(received.get());
    }

    @Test
    void channelRetainsParameterizedPayloadType() {
        GenericType<List<String>> payloadType = new GenericType<>() { };
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<List<String>> channel = builder.channel("lists", payloadType);
        builder.payloadSink(channel, ignored -> { });

        try (MessagingGraph ignored = builder.build()) {
            assertSame(payloadType, channel.payloadType());
            assertEquals("lists", channel.name());
        }
    }

    @Test
    void channelsCannotCrossBuilderOrGraphBoundaries() {
        MessagingGraph.Builder firstBuilder = MessagingGraph.builder();
        MessagingGraph.Builder secondBuilder = MessagingGraph.builder();
        MessagingChannel<String> first = firstBuilder.channel("first", String.class);
        MessagingChannel<String> second = secondBuilder.channel("second", String.class);
        firstBuilder.payloadSink(first, ignored -> { });
        secondBuilder.payloadSink(second, ignored -> { });

        assertThrows(IllegalArgumentException.class, () -> firstBuilder.route(first, second));

        try (MessagingGraph firstGraph = firstBuilder.build();
                MessagingGraph secondGraph = secondBuilder.build()) {
            assertThrows(IllegalArgumentException.class, () -> firstGraph.emitter(second));
            assertThrows(IllegalArgumentException.class, () -> secondGraph.emitter(first));
        }
    }

    @Test
    void failedTopologyBuildClosesTransferredStream() {
        AtomicBoolean closed = new AtomicBoolean();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> first = builder.channel("first", String.class);
        MessagingChannel<String> second = builder.channel("second", String.class);
        TestConnector connector = new TestConnector();
        builder.payloadSource(first, Stream.<String>empty().onClose(() -> closed.set(true)))
                .outgoingConnector(first, connector)
                .route(first, second)
                .route(second, first);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, builder::build);

        assertTrue(failure.getMessage().contains("Cyclic synchronous messaging route"));
        assertTrue(closed.get());
        assertTrue(connector.closed.get());
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void duplicateChannelNamesAreRejected() {
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("events", String.class);
        builder.payloadSink(channel, ignored -> { });

        assertThrows(IllegalArgumentException.class, () -> builder.channel("events", String.class));

        try (MessagingGraph ignored = builder.build()) {
            assertThrows(IllegalStateException.class, () -> builder.channel("later", String.class));
        }
    }

    @Test
    void primitiveChannelPayloadTypesAreRejected() {
        try (MessagingGraph.Builder builder = MessagingGraph.builder()) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                             () -> builder.channel("primitive", int.class));

            assertTrue(failure.getMessage().contains("must not be primitive"));
        }
    }

    @Test
    void outputlessChannelsAreRejectedAtBuild() {
        MessagingGraph.Builder builder = MessagingGraph.builder();
        builder.channel("discarded", String.class);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, builder::build);

        assertTrue(failure.getMessage().contains("discarded has no required output"));
        assertThrows(IllegalStateException.class, builder::build);
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
        assertEquals(expected, graph.state());
    }

    private static void runCapturing(Runnable task, AtomicReference<Throwable> failure) {
        try {
            task.run();
        } catch (Throwable t) {
            failure.set(t);
        }
    }

    private record MessagePayload(String entity) implements Message<String> {
        @Override
        public Map<String, String> headers() {
            return Map.of();
        }
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
