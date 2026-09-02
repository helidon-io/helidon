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

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
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
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultMessagingChannelTest {
    @Test
    void customPayloadIsDelivered() {
        List<CustomPayload> delivered = new ArrayList<>();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<CustomPayload> channel = builder.channel("custom", CustomPayload.class);
        builder.messageSink(channel, message -> delivered.add(message.entity()));

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            CustomPayload payload = new CustomPayload("payload");

            graph.emitter(channel).emit(payload);

            assertThat(delivered, is(List.of(payload)));
        }
    }

    @Test
    void independentlyBuiltInputsCanFeedOneChannel() {
        List<String> delivered = new ArrayList<>();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> first = builder.channel("first", String.class);
        MessagingChannel<String> second = builder.channel("second", String.class);
        MessagingChannel<String> merged = builder.channel("merged", String.class);
        builder.route(first, merged)
                .route(second, merged)
                .messageSink(merged, message -> delivered.add(message.entity()));

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(first).emit("first");
            graph.emitter(second).emit("second");

            assertThat(delivered, is(List.of("first", "second")));
        }
    }

    @Test
    void earlierOutputSuccessMakesLaterConfirmedFailureIndeterminate() {
        AtomicInteger firstOutputInvocations = new AtomicInteger();
        RuntimeException itemFailure = new RuntimeException("second output rejected the item");
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("fan-out", String.class);
        builder.batchSink(channel, ignored -> firstOutputInvocations.incrementAndGet())
                .batchSink(channel, batch -> {
                    throw new BatchDeliveryException("second output failed",
                                                     itemFailure,
                                                     batch,
                                                     List.of(BatchItemOutcome.failed(0, itemFailure)));
                });
        MessageBatch<String> batch = MessageBatch.create(List.of(Message.create("message")));

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            BatchDeliveryException failure = assertThrows(
                    BatchDeliveryException.class,
                    () -> graph.emitter(channel).emit(batch));

            assertThat(firstOutputInvocations.get(), is(1));
            assertThat(failure.batch(), sameInstance(batch));
            assertThat(failure.outcome(0).status(), is(BatchItemStatus.INDETERMINATE));
        }
    }

    @Test
    void fanOutNormalizesMixedBatchOutcomesAcrossEarlierAndLaterOutputs() {
        AtomicInteger firstOutputInvocations = new AtomicInteger();
        AtomicInteger laterOutputInvocations = new AtomicInteger();
        RuntimeException itemFailure = new RuntimeException("second output failed");
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("fan-out", String.class);
        builder.batchSink(channel, ignored -> firstOutputInvocations.incrementAndGet())
                .batchSink(channel, batch -> {
                    throw new BatchDeliveryException(
                            "mixed second output failure",
                            itemFailure,
                            batch,
                            List.of(BatchItemOutcome.succeeded(0),
                                    BatchItemOutcome.failed(1, itemFailure),
                                    BatchItemOutcome.notAttempted(2),
                                    BatchItemOutcome.indeterminate(3, itemFailure)));
                })
                .batchSink(channel, ignored -> laterOutputInvocations.incrementAndGet());
        MessageBatch<String> batch = MessageBatch.create(List.of(Message.create("first"),
                                                                 Message.create("second"),
                                                                 Message.create("third"),
                                                                 Message.create("fourth")));

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            BatchDeliveryException failure = assertThrows(
                    BatchDeliveryException.class,
                    () -> graph.emitter(channel).emit(batch));

            assertThat(firstOutputInvocations.get(), is(1));
            assertThat(laterOutputInvocations.get(), is(0));
            assertThat(failure.batch(), sameInstance(batch));
            assertThat(failure.outcomes().stream().map(BatchItemOutcome::status).toList(),
                       is(List.of(BatchItemStatus.INDETERMINATE,
                                  BatchItemStatus.INDETERMINATE,
                                  BatchItemStatus.INDETERMINATE,
                                  BatchItemStatus.INDETERMINATE)));
        }
    }

    @Test
    void targetAdmissionRejectionBeforeDispatchIsNotAttempted() {
        AtomicInteger targetInvocations = new AtomicInteger();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> source = builder.channel("source", String.class);
        MessagingChannel<String> target = builder.channel(
                "target",
                GenericType.create(String.class),
                MessagingExecutionConfig.builder().maxInFlightMessages(1).build());
        builder.route(source, target)
                .batchSink(target, ignored -> targetInvocations.incrementAndGet());
        MessageBatch<String> batch = MessageBatch.create(List.of(Message.create("first"),
                                                                 Message.create("second")));

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            BatchDeliveryException failure = assertThrows(
                    BatchDeliveryException.class,
                    () -> graph.emitter(source).emit(batch));

            assertThat(failure.batch(), sameInstance(batch));
            assertThat(failure.outcomes().stream().map(BatchItemOutcome::status).toList(),
                       is(List.of(BatchItemStatus.NOT_ATTEMPTED, BatchItemStatus.NOT_ATTEMPTED)));
            assertThat(targetInvocations.get(), is(0));
            MessagingRejectedException rejection = (MessagingRejectedException) failure.getCause();
            assertThat(rejection.reason(), is(MessagingRejectedException.Reason.OVERSIZED));
        }
    }

    @Test
    void earlierFanOutSuccessMakesTargetAdmissionRejectionIndeterminate() {
        AtomicInteger earlierOutputInvocations = new AtomicInteger();
        AtomicInteger targetInvocations = new AtomicInteger();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> source = builder.channel("source", String.class);
        MessagingChannel<String> target = builder.channel(
                "target",
                GenericType.create(String.class),
                MessagingExecutionConfig.builder().maxInFlightMessages(1).build());
        builder.batchSink(source, ignored -> earlierOutputInvocations.incrementAndGet())
                .route(source, target)
                .batchSink(target, ignored -> targetInvocations.incrementAndGet());
        MessageBatch<String> batch = MessageBatch.create(List.of(Message.create("first"),
                                                                 Message.create("second")));

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            BatchDeliveryException failure = assertThrows(
                    BatchDeliveryException.class,
                    () -> graph.emitter(source).emit(batch));

            assertThat(failure.batch(), sameInstance(batch));
            assertThat(failure.outcomes().stream().map(BatchItemOutcome::status).toList(),
                       is(List.of(BatchItemStatus.INDETERMINATE, BatchItemStatus.INDETERMINATE)));
            assertThat(earlierOutputInvocations.get(), is(1));
            assertThat(targetInvocations.get(), is(0));
        }
    }

    @Test
    void targetCancellationAfterDispatchRemainsIndeterminate() {
        AtomicInteger targetInvocations = new AtomicInteger();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> source = builder.channel("source", String.class);
        MessagingChannel<String> target = builder.channel("target", String.class);
        builder.route(source, target)
                .batchSink(target, ignored -> {
                    targetInvocations.incrementAndGet();
                    throw new MessagingRejectedException(
                            target.name(),
                            MessagingRejectedException.Reason.CANCELLED,
                            "Target delivery was cancelled after dispatch started");
                });
        MessageBatch<String> batch = MessageBatch.create(Message.create("message"));

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            BatchDeliveryException failure = assertThrows(
                    BatchDeliveryException.class,
                    () -> graph.emitter(source).emit(batch));

            assertThat(failure.batch(), sameInstance(batch));
            assertThat(failure.outcome(0).status(), is(BatchItemStatus.INDETERMINATE));
            assertThat(targetInvocations.get(), is(1));
        }
    }

    @Test
    void applicationSideEffectBeforeNestedRejectionRemainsIndeterminate() {
        AtomicInteger sourceSideEffects = new AtomicInteger();
        AtomicInteger targetInvocations = new AtomicInteger();
        AtomicReference<Emitter<String>> targetEmitter = new AtomicReference<>();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> source = builder.channel("source", String.class);
        MessagingChannel<String> target = builder.channel(
                "target",
                GenericType.create(String.class),
                MessagingExecutionConfig.builder().maxInFlightMessages(1).build());
        builder.batchSink(source, ignored -> {
                    sourceSideEffects.incrementAndGet();
                    targetEmitter.get().emit(MessageBatch.create(List.of(Message.create("first"),
                                                                         Message.create("second"))));
                })
                .batchSink(target, ignored -> targetInvocations.incrementAndGet());
        MessageBatch<String> batch = MessageBatch.create(Message.create("source-message"));

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            targetEmitter.set(graph.emitter(target));

            BatchDeliveryException failure = assertThrows(
                    BatchDeliveryException.class,
                    () -> graph.emitter(source).emit(batch));

            assertThat(failure.batch(), sameInstance(batch));
            assertThat(failure.outcome(0).status(), is(BatchItemStatus.INDETERMINATE));
            assertThat(sourceSideEffects.get(), is(1));
            assertThat(targetInvocations.get(), is(0));
        }
    }

    @Test
    void processorSideEffectsBeforeTargetRejectionRemainIndeterminate() {
        AtomicInteger processorInvocations = new AtomicInteger();
        AtomicInteger targetInvocations = new AtomicInteger();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> source = builder.channel("source", String.class);
        MessagingChannel<String> target = builder.channel(
                "target",
                GenericType.create(String.class),
                MessagingExecutionConfig.builder().maxInFlightMessages(1).build());
        builder.payloadProcessor(source, target, payload -> {
                    processorInvocations.incrementAndGet();
                    return payload;
                })
                .batchSink(target, ignored -> targetInvocations.incrementAndGet());
        MessageBatch<String> batch = MessageBatch.create(List.of(Message.create("first"),
                                                                 Message.create("second")));

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            BatchDeliveryException failure = assertThrows(
                    BatchDeliveryException.class,
                    () -> graph.emitter(source).emit(batch));

            assertThat(failure.batch(), sameInstance(batch));
            assertThat(failure.outcomes().stream().map(BatchItemOutcome::status).toList(),
                       is(List.of(BatchItemStatus.INDETERMINATE, BatchItemStatus.INDETERMINATE)));
            assertThat(processorInvocations.get(), is(2));
            assertThat(targetInvocations.get(), is(0));
        }
    }

    @Test
    void closingBeforeStartClosesStreamInput() {
        AtomicBoolean streamClosed = new AtomicBoolean();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<Object> channel = builder.channel("stream", Object.class);
        MessagingGraph graph = builder.payloadSource(channel,
                                                      Stream.empty().onClose(() -> streamClosed.set(true)))
                .payloadSink(channel, ignored -> { })
                .build();

        graph.close();

        assertThat(streamClosed.get(), is(true));
    }

    @Test
    @Timeout(5)
    void activeUnboundedStreamClosesGracefully() throws InterruptedException {
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicBoolean streamClosed = new AtomicBoolean();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<Integer> channel = builder.channel("stream", Integer.class);
        MessagingGraph graph = builder.payloadSource(channel,
                                                      Stream.generate(() -> 1)
                                                              .onClose(() -> streamClosed.set(true)))
                .messageSink(channel, ignored -> delivered.countDown())
                .build();
        graph.start();
        assertThat(delivered.await(1, TimeUnit.SECONDS), is(true));

        graph.close();

        assertThat(streamClosed.get(), is(true));
    }

    @Test
    void streamSourceDoesNotHideDownstreamShutdownRejection() {
        MessagingRejectedException rejection = new MessagingRejectedException(
                "downstream",
                MessagingRejectedException.Reason.SHUTDOWN);
        Runnable source = DefaultMessagingChannel.streamSource(Stream.of("first", "second"), ignored -> {
            throw rejection;
        });

        MessagingRejectedException thrown = assertThrows(MessagingRejectedException.class, source::run);

        assertThat(thrown, sameInstance(rejection));
    }

    @Test
    void streamSourcePreservesConsumerFailureWhenCloseFails() {
        RuntimeException processingFailure = new RuntimeException("consumer failed");
        RuntimeException closeFailure = new RuntimeException("stream close failed");
        Runnable source = DefaultMessagingChannel.streamSource(
                Stream.of("message").onClose(() -> {
                    throw closeFailure;
                }),
                ignored -> {
                    throw processingFailure;
                });

        RuntimeException thrown = assertThrows(RuntimeException.class, source::run);

        assertThat(thrown, sameInstance(processingFailure));
        assertThat(thrown.getSuppressed().length, is(1));
        assertThat(thrown.getSuppressed()[0], sameInstance(closeFailure));
    }

    @Test
    void streamSourcePreservesIteratorFailureWhenCloseFails() {
        RuntimeException processingFailure = new RuntimeException("iterator failed");
        RuntimeException closeFailure = new RuntimeException("stream close failed");
        Spliterator<String> spliterator = new Spliterators.AbstractSpliterator<>(1, Spliterator.ORDERED) {
            @Override
            public boolean tryAdvance(Consumer<? super String> action) {
                throw processingFailure;
            }
        };
        Runnable source = DefaultMessagingChannel.streamSource(
                StreamSupport.stream(spliterator, false).onClose(() -> {
                    throw closeFailure;
                }),
                ignored -> { });

        RuntimeException thrown = assertThrows(RuntimeException.class, source::run);

        assertThat(thrown, sameInstance(processingFailure));
        assertThat(thrown.getSuppressed().length, is(1));
        assertThat(thrown.getSuppressed()[0], sameInstance(closeFailure));
    }

    @Test
    void streamSourcePropagatesCloseFailureWithoutProcessingFailure() {
        RuntimeException closeFailure = new RuntimeException("stream close failed");
        Runnable source = DefaultMessagingChannel.streamSource(
                Stream.empty().onClose(() -> {
                    throw closeFailure;
                }),
                ignored -> { });

        RuntimeException thrown = assertThrows(RuntimeException.class, source::run);

        assertThat(thrown, sameInstance(closeFailure));
        assertThat(thrown.getSuppressed().length, is(0));
    }

    @Test
    @Timeout(5)
    void forceCloseInterruptsStreamOwnerBeforeNormalCloseInvokesBlockingStreamClose() throws InterruptedException {
        CountDownLatch ownerStarted = new CountDownLatch(1);
        CountDownLatch ownerInterrupted = new CountDownLatch(1);
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AtomicReference<Throwable> sourceFailure = new AtomicReference<>();
        Stream<String> stream = Stream.generate(() -> {
            ownerStarted.countDown();
            try {
                neverReleased.await();
                return "unexpected";
            } catch (InterruptedException e) {
                ownerInterrupted.countDown();
                Thread.currentThread().interrupt();
                throw new MessagingRejectedException("stream",
                                                     MessagingRejectedException.Reason.CANCELLED,
                                                     "Stream owner interrupted",
                                                     e);
            }
        }).onClose(() -> {
            closeStarted.countDown();
            awaitUninterruptibly(releaseClose);
        });
        Runnable source = DefaultMessagingChannel.streamSource(stream, ignored -> { });
        Thread sourceThread = Thread.ofVirtual().start(() -> {
            try {
                source.run();
            } catch (Throwable t) {
                sourceFailure.set(t);
            }
        });
        assertThat(ownerStarted.await(1, TimeUnit.SECONDS), is(true));

        Connector connector = (Connector) source;
        connector.forceClose();
        assertThat(closeStarted.getCount(), is(1L));
        assertThat(ownerInterrupted.await(1, TimeUnit.SECONDS), is(true));

        Thread closeThread = Thread.ofVirtual().start(connector::close);
        assertThat(closeStarted.await(1, TimeUnit.SECONDS), is(true));
        releaseClose.countDown();
        sourceThread.join(TimeUnit.SECONDS.toMillis(1));
        closeThread.join(TimeUnit.SECONDS.toMillis(1));

        assertThat(sourceThread.isAlive(), is(false));
        assertThat(closeThread.isAlive(), is(false));
        assertThat(sourceFailure.get() instanceof MessagingRejectedException, is(true));
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

    private record CustomPayload(String value) {
    }
}
