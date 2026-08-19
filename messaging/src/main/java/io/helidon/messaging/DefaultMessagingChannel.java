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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.helidon.common.GenericType;

/**
 * Internal in-memory messaging channel runtime.
 *
 * @param <T> payload type
 */
final class DefaultMessagingChannel<T> implements MessagingChannel<T>, Emitter<T> {
    private final GenericType<T> payloadType;
    private final List<Consumer<MessageBatch<?>>> validators;
    private final List<Consumer<MessageBatch<?>>> outputs;
    private final DeliveryEngine deliveryEngine;
    private final String channelName;
    private final DefaultMessagingGraph graph;

    private DefaultMessagingChannel(GenericType<T> payloadType,
                                    List<Consumer<MessageBatch<?>>> validators,
                                    List<Consumer<MessageBatch<?>>> outputs,
                                    DeliveryEngine deliveryEngine,
                                    String channelName,
                                    DefaultMessagingGraph graph) {
        this.payloadType = payloadType;
        this.validators = List.copyOf(validators);
        this.outputs = new CopyOnWriteArrayList<>(outputs);
        this.deliveryEngine = deliveryEngine;
        this.channelName = channelName;
        this.graph = graph;
    }

    @Override
    public String name() {
        return channelName;
    }

    @Override
    public GenericType<T> payloadType() {
        return payloadType;
    }

    @Override
    public void emitBatch(MessageBatch<? extends T> batch) {
        emitBatchObject(batch);
    }

    void addOutput(Consumer<Message<?>> output) {
        outputs.add(batch -> dispatchMessages(batch, output));
    }

    void addBatchOutput(Consumer<MessageBatch<T>> output) {
        outputs.add(batch -> output.accept(castBatch(batch)));
    }

    void addOutgoingConnector(OutgoingConnector output) {
        outputs.add(messages -> send(output, messages));
    }

    DefaultMessagingGraph graph() {
        return graph;
    }

    void emitPayloadObject(Object entity) {
        emitBatchObject(MessageBatch.create(Message.create(entity)));
    }

    void emitMessageObject(Message<?> message) {
        emitBatchObject(MessageBatch.create(Objects.requireNonNull(message)));
    }

    void emitBatchObject(MessageBatch<?> messages) {
        Objects.requireNonNull(messages);
        graph.ensureRunning();
        MessageBatch<?> batch = toBatch(messages);
        deliveryEngine.dispatch(channelName, batch, () -> dispatchBatch(batch));
    }

    void emitRoutedBatchObject(MessageBatch<?> messages) {
        try {
            emitBatchObject(messages);
        } catch (RuntimeException failure) {
            if (DeliveryEngine.isPreDispatchRejection(failure)) {
                throw BatchDeliveryException.notAttempted("Messaging nested delivery admission", messages, failure);
            }
            throw failure;
        }
    }

    private void dispatchBatch(MessageBatch<?> batch) {
        validators.forEach(validator -> validator.accept(batch));
        for (int i = 0; i < outputs.size(); i++) {
            try {
                outputs.get(i).accept(batch);
            } catch (RuntimeException e) {
                throw normalizeFailure(batch, i, e);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Message<T> toMessage(Object value) {
        Message<?> message = (Message<?>) value;
        if (isExpectedPayload(message)) {
            return (Message<T>) message;
        }
        throw new IllegalArgumentException("Channel expected payload type "
                                                   + payloadType.getTypeName()
                                                   + " but received " + message.entity().getClass().getName());
    }

    private boolean isExpectedPayload(Message<?> message) {
        Object entity = message.entity();
        return entity == null || payloadType.rawType().isInstance(entity);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private MessageBatch<T> castBatch(MessageBatch<?> batch) {
        return (MessageBatch) batch;
    }

    static final class Builder<T> {
        private final List<Consumer<MessageBatch<?>>> validators = new ArrayList<>();
        private final List<Consumer<MessageBatch<?>>> outputs = new ArrayList<>();
        private final List<OutgoingConnector> connectorOutputs = new ArrayList<>();
        private GenericType<T> payloadType;
        private MessagingExecutionConfig executionConfig;
        private DefaultMessagingGraph messagingGraph;
        private String channelName;

        Builder<T> payloadType(Class<T> payloadType) {
            return payloadType(GenericType.create(payloadType));
        }

        Builder<T> payloadType(GenericType<T> payloadType) {
            this.payloadType = Objects.requireNonNull(payloadType);
            return this;
        }

        Builder<T> addOutput(Consumer<Message<T>> output) {
            outputs.add(messages -> dispatchMessages(messages, message -> output.accept(cast(message))));
            return this;
        }

        Builder<T> addBatchValidator(Consumer<MessageBatch<T>> validator) {
            validators.add(messages -> validator.accept(castBatch(messages)));
            return this;
        }

        Builder<T> addBatchOutput(Consumer<MessageBatch<T>> output) {
            outputs.add(messages -> output.accept(castBatch(messages)));
            return this;
        }

        Builder<T> addOutgoingConnector(OutgoingConnector output) {
            OutgoingConnector connector = Objects.requireNonNull(output);
            connectorOutputs.add(connector);
            outputs.add(messages -> DefaultMessagingChannel.send(connector, messages));
            return this;
        }

        DefaultMessagingChannel<T> build() {
            GenericType<T> actualPayloadType = Objects.requireNonNull(payloadType, "payloadType");
            String actualChannelName = Objects.requireNonNull(channelName, "channelName");
            DefaultMessagingGraph actualGraph = Objects.requireNonNull(messagingGraph, "messagingGraph");
            MessagingExecutionConfig actualExecutionConfig = Objects.requireNonNull(executionConfig, "executionConfig");
            DefaultMessagingChannel<T> channel = new DefaultMessagingChannel<>(actualPayloadType,
                                                                               validators,
                                                                               outputs,
                                                                               actualGraph.deliveryEngine(),
                                                                               actualChannelName,
                                                                               actualGraph);
            actualGraph.addChannelContribution(actualChannelName,
                                               channel,
                                               actualExecutionConfig,
                                               java.util.Map.of(),
                                               connectorOutputs,
                                               List.of());
            return channel;
        }

        Builder<T> messagingGraph(DefaultMessagingGraph messagingGraph,
                                  String channelName,
                                  MessagingExecutionConfig executionConfig) {
            this.messagingGraph = Objects.requireNonNull(messagingGraph);
            this.channelName = Objects.requireNonNull(channelName);
            this.executionConfig = Objects.requireNonNull(executionConfig);
            return this;
        }

        @SuppressWarnings("unchecked")
        private Message<T> cast(Message<?> message) {
            return (Message<T>) message;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private MessageBatch<T> castBatch(MessageBatch<?> messages) {
            return (MessageBatch) messages;
        }

    }

    static Runnable streamSource(Stream<?> stream, Consumer<Object> consumer) {
        return new StreamSource(stream, consumer);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private MessageBatch<?> toBatch(MessageBatch<?> messages) {
        if (messages.size() <= 0 || messages.messages().size() != messages.size()) {
            throw new IllegalArgumentException("Message batch must contain a consistent non-empty message snapshot");
        }
        for (Message<?> message : messages.messages()) {
            toMessage(Objects.requireNonNull(message));
        }
        return (MessageBatch) messages;
    }

    private static void send(OutgoingConnector output, MessageBatch<?> messages) {
        output.sendBatch(messages);
    }

    private static void dispatchMessages(MessageBatch<?> batch, Consumer<Message<?>> output) {
        for (int i = 0; i < batch.size(); i++) {
            try {
                output.accept(batch.get(i));
            } catch (RuntimeException e) {
                throw BatchDeliveryException.sequential("Messaging per-message output", batch, i, e);
            }
        }
    }

    private RuntimeException normalizeFailure(MessageBatch<?> batch, int outputIndex, RuntimeException failure) {
        BatchDeliveryException batchFailure;
        if (failure instanceof BatchDeliveryException actualFailure
                && batch.sameDelivery(actualFailure.batch())) {
            batchFailure = actualFailure;
        } else {
            return BatchDeliveryException.indeterminate("Messaging batch output", batch, failure);
        }
        if (batchFailure.batch() != batch) {
            batchFailure = new BatchDeliveryException(batchFailure.getMessage(),
                                                      batch,
                                                      batchFailure.outcomes(),
                                                      batchFailure);
        }
        boolean earlierOutputCompleted = outputIndex > 0;
        boolean laterOutputSkipped = outputIndex + 1 < outputs.size();
        if (!earlierOutputCompleted && !laterOutputSkipped) {
            return batchFailure;
        }
        List<BatchItemOutcome> outcomes = new ArrayList<>(batch.size());
        for (BatchItemOutcome outcome : batchFailure.outcomes()) {
            boolean partiallyDelivered = outcome.status() == BatchItemStatus.SUCCEEDED && laterOutputSkipped
                    || outcome.status() != BatchItemStatus.SUCCEEDED
                            && outcome.status() != BatchItemStatus.INDETERMINATE
                            && earlierOutputCompleted;
            outcomes.add(partiallyDelivered
                                 ? BatchItemOutcome.indeterminate(outcome.index(), failure)
                                 : outcome);
        }
        return new BatchDeliveryException(batchFailure.getMessage(), batch, outcomes, batchFailure);
    }

    private static final class StreamSource implements Runnable, Connector {
        private final Stream<?> stream;
        private final Consumer<Object> consumer;
        private final AtomicBoolean runStarted = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean forceCloseRequested = new AtomicBoolean();
        private final AtomicBoolean streamClosed = new AtomicBoolean();
        private final AtomicReference<Thread> owner = new AtomicReference<>();

        private StreamSource(Stream<?> stream, Consumer<Object> consumer) {
            this.stream = stream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            if (!runStarted.compareAndSet(false, true)) {
                throw new IllegalStateException("Messaging stream source can only be run once");
            }
            if (closed.get()) {
                return;
            }
            Thread current = Thread.currentThread();
            owner.set(current);
            try {
                Iterator<?> iterator = stream.sequential().iterator();
                while (!closed.get() && iterator.hasNext()) {
                    consumer.accept(iterator.next());
                }
            } finally {
                closed.set(true);
                owner.compareAndSet(current, null);
                if (!forceCloseRequested.get()) {
                    closeStream();
                }
            }
        }

        @Override
        public void forceClose() {
            forceCloseRequested.set(true);
            stop();
        }

        @Override
        public void close() {
            stop();
            closeStream();
        }

        private void stop() {
            closed.set(true);
            Thread current = owner.get();
            if (current != null && current != Thread.currentThread()) {
                current.interrupt();
            }
        }

        private void closeStream() {
            if (streamClosed.compareAndSet(false, true)) {
                stream.close();
            }
        }
    }
}
