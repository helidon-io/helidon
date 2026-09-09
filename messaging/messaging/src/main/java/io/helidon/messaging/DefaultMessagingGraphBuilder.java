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
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.messaging.spi.ChannelConnection;
import io.helidon.messaging.spi.IncomingChannel;
import io.helidon.messaging.spi.OutgoingChannel;

/**
 * Typed programmatic registrations for the shared messaging configuration.
 */
final class DefaultMessagingGraphBuilder extends MessagingGraph.Builder {
    private boolean buildAttempted;

    @Override
    public <T> MessagingChannel<T> channel(String name, Class<T> payloadType) {
        return channel(name, GenericType.create(Objects.requireNonNull(payloadType)));
    }

    @Override
    public <T> MessagingChannel<T> channel(String name, GenericType<T> payloadType) {
        requireMutable();
        String channelName = Objects.requireNonNull(name);
        GenericType<T> actualType = Objects.requireNonNull(payloadType);
        if (channelName.isBlank()) {
            throw new IllegalArgumentException("Messaging channel name must not be blank");
        }
        if (actualType.rawType().isPrimitive()) {
            throw new IllegalArgumentException("Messaging channel payload type must not be primitive: "
                                                       + actualType.getTypeName());
        }
        if (channelHandles().stream().anyMatch(channel -> channel.name().equals(channelName))) {
            throw new IllegalArgumentException("Duplicate messaging channel " + channelName);
        }
        MessagingChannel<T> handle = new DefaultMessagingChannelHandle<>(channelName, actualType);
        addChannelHandle(handle);
        return handle;
    }

    @Override
    public <T> MessagingChannel<T> channel(String name,
                                           GenericType<T> payloadType,
                                           MessagingExecutionConfig executionConfig) {
        Objects.requireNonNull(executionConfig);
        MessagingChannel<T> handle = channel(name, payloadType);
        putChannel(name, MessagingChannelConfig.builder()
                .execution(executionConfig)
                .build());
        return handle;
    }

    @Override
    public <T> MessagingGraph.Builder payloadSource(MessagingChannel<T> channel, Stream<? extends T> source) {
        return source(channel, source, false);
    }

    @Override
    public <T> MessagingGraph.Builder messageSource(MessagingChannel<T> channel,
                                                     Stream<? extends Message<? extends T>> source) {
        return source(channel, source, true);
    }

    @Override
    public <T> MessagingGraph.Builder route(MessagingChannel<T> source, MessagingChannel<T> target) {
        requireChannel(source);
        requireChannel(target);
        if (!source.payloadType().equals(target.payloadType())) {
            throw new IllegalArgumentException("Messaging route " + source.name() + " -> " + target.name()
                                                       + " has incompatible payload types "
                                                       + source.payloadType().getTypeName() + " and "
                                                       + target.payloadType().getTypeName());
        }
        if (consumerRegistrations().stream().anyMatch(registration ->
                registration instanceof RouteRegistration route
                        && route.channel().equals(source.name())
                        && route.outgoingChannel().equals(target.name()))) {
            throw new IllegalArgumentException("Duplicate messaging route " + source.name() + " -> " + target.name());
        }
        addConsumerRegistration(new RouteRegistration(source, target));
        return this;
    }

    @Override
    public <I, O> MessagingGraph.Builder payloadProcessor(MessagingChannel<I> source,
                                                           MessagingChannel<O> target,
                                                           Function<? super I, ? extends O> processor) {
        requireChannel(source);
        requireChannel(target);
        Objects.requireNonNull(processor);
        addConsumerRegistration(new ProcessorDefinition(source, target, batch -> {
            List<Message<O>> results = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                try {
                    results.add(Message.create(processor.apply(source.payloadType().cast(batch.get(i).entity()))));
                } catch (RuntimeException e) {
                    throw BatchDeliveryExceptionSupport.attemptedPrefix("Messaging payload processor", batch, i, e);
                }
            }
            return batch.derive(results);
        }));
        return this;
    }

    @Override
    public <I, O> MessagingGraph.Builder messageProcessor(
            MessagingChannel<I> source,
            MessagingChannel<O> target,
            Function<? super Message<I>, ? extends Message<? extends O>> processor) {
        requireChannel(source);
        requireChannel(target);
        Objects.requireNonNull(processor);
        addConsumerRegistration(new ProcessorDefinition(source, target, batch -> {
            List<Message<? extends O>> results = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                try {
                    results.add(Objects.requireNonNull(processor.apply(castMessage(batch.get(i))),
                                                      "Message processor result"));
                } catch (RuntimeException e) {
                    throw BatchDeliveryExceptionSupport.attemptedPrefix("Messaging message processor", batch, i, e);
                }
            }
            return batch.derive(results);
        }));
        return this;
    }

    @Override
    public <T> MessagingGraph.Builder payloadSink(MessagingChannel<T> source, Consumer<? super T> sink) {
        requireChannel(source);
        Objects.requireNonNull(sink);
        addConsumerRegistration(new ConsumerDefinition(source, batch -> {
            for (int i = 0; i < batch.size(); i++) {
                try {
                    sink.accept(source.payloadType().cast(batch.get(i).entity()));
                } catch (RuntimeException e) {
                    throw BatchDeliveryExceptionSupport.sequential("Messaging payload sink", batch, i, e);
                }
            }
        }));
        return this;
    }

    @Override
    public <T> MessagingGraph.Builder messageSink(MessagingChannel<T> source,
                                                  Consumer<? super Message<T>> sink) {
        requireChannel(source);
        Objects.requireNonNull(sink);
        addConsumerRegistration(new ConsumerDefinition(source, batch -> {
            for (int i = 0; i < batch.size(); i++) {
                try {
                    sink.accept(castMessage(batch.get(i)));
                } catch (RuntimeException e) {
                    throw BatchDeliveryExceptionSupport.sequential("Messaging message sink", batch, i, e);
                }
            }
        }));
        return this;
    }

    @Override
    public <T> MessagingGraph.Builder batchSink(MessagingChannel<T> source, Consumer<MessageBatch<T>> sink) {
        requireChannel(source);
        Objects.requireNonNull(sink);
        addConsumerRegistration(new ConsumerDefinition(source, batch -> sink.accept(castBatch(batch))));
        return this;
    }

    @Override
    public <T> MessagingGraph.Builder incomingChannel(MessagingChannel<T> target, IncomingChannel connection) {
        requireChannel(target);
        requireConnection(connection);
        requireNoSource(target);
        putIncomingConnection(target, connection);
        return this;
    }

    @Override
    public <T> MessagingGraph.Builder outgoingChannel(MessagingChannel<T> source, OutgoingChannel connection) {
        requireChannel(source);
        requireConnection(connection);
        addConsumerRegistration(new OutgoingDefinition(source, connection));
        return this;
    }

    @Override
    public MessagingConfig buildPrototype() {
        requireMutable();
        return MessagingConfig.builder().from(this).buildPrototype();
    }

    @Override
    public MessagingGraph build() {
        MessagingConfig config = buildPrototype();
        buildAttempted = true;
        return MessagingGraph.create(config);
    }

    @SuppressWarnings("unchecked")
    private static <T> Message<T> castMessage(Message<?> message) {
        return (Message<T>) message;
    }

    @SuppressWarnings("unchecked")
    private static <T> MessageBatch<T> castBatch(MessageBatch<?> batch) {
        return (MessageBatch<T>) batch;
    }

    private void requireMutable() {
        if (buildAttempted) {
            throw new IllegalStateException("Messaging graph builder cannot be reused after build");
        }
    }

    private void requireChannel(MessagingChannel<?> channel) {
        requireMutable();
        Objects.requireNonNull(channel);
        if (channelHandles().stream().noneMatch(handle -> handle == channel)) {
            throw new IllegalArgumentException("Messaging channel " + channel.name()
                                                       + " belongs to another messaging graph builder");
        }
    }

    private void requireConnection(ChannelConnection connection) {
        Objects.requireNonNull(connection);
        if (incomingConnections().values().stream().anyMatch(incoming -> incoming == connection)
                || consumerRegistrations().stream().anyMatch(registration ->
                        registration instanceof OutgoingDefinition outgoing && outgoing.connection() == connection)) {
            throw new IllegalArgumentException("Channel connection is already owned by this messaging graph builder");
        }
    }

    private void requireNoSource(MessagingChannel<?> channel) {
        if (emitterRegistrations().stream().anyMatch(registration ->
                registration instanceof SourceDefinition source && source.handle() == channel)
                || incomingConnections().containsKey(channel)) {
            throw new IllegalArgumentException("Messaging channel " + channel.name() + " already has a source");
        }
    }

    private MessagingGraph.Builder source(MessagingChannel<?> channel, Stream<?> source, boolean messages) {
        requireChannel(channel);
        Objects.requireNonNull(source);
        requireNoSource(channel);
        if (emitterRegistrations().stream().anyMatch(registration ->
                registration instanceof SourceDefinition definition && definition.stream() == source)) {
            throw new IllegalArgumentException("Stream source is already owned by this messaging graph builder");
        }
        addEmitterRegistration(new SourceDefinition(channel, source, messages));
        return this;
    }

    record SourceDefinition(MessagingChannel<?> handle, Stream<?> stream, boolean messages)
            implements EmitterRegistration {
        @Override
        public String channel() {
            return handle.name();
        }

        @Override
        public String producerId() {
            return channel() + "-source";
        }

        @Override
        public GenericType<?> payloadGenericType() {
            return handle.payloadType();
        }

        @Override
        public GenericType<?> envelopeGenericType() {
            return GenericType.create(Message.class);
        }
    }

    record OutgoingDefinition(MessagingChannel<?> handle, OutgoingChannel connection) implements ConsumerRegistration {
        @Override
        public String channel() {
            return handle.name();
        }

        @Override
        public GenericType<?> payloadGenericType() {
            return handle.payloadType();
        }

        @Override
        public GenericType<?> envelopeGenericType() {
            return GenericType.create(Message.class);
        }

        @Override
        public void dispatch(MessageBatch<?> batch) {
            connection.sendBatch(batch);
        }
    }

    private record ConsumerDefinition(MessagingChannel<?> handle, Consumer<MessageBatch<?>> consumer)
            implements ConsumerRegistration {
        @Override
        public String channel() {
            return handle.name();
        }

        @Override
        public GenericType<?> payloadGenericType() {
            return handle.payloadType();
        }

        @Override
        public GenericType<?> envelopeGenericType() {
            return GenericType.create(Message.class);
        }

        @Override
        public void dispatch(MessageBatch<?> batch) {
            consumer.accept(batch);
        }
    }

    private record ProcessorDefinition(MessagingChannel<?> source,
                                       MessagingChannel<?> target,
                                       Function<MessageBatch<?>, MessageBatch<?>> processor)
            implements ProcessorRegistration {
        @Override
        public String channel() {
            return source.name();
        }

        @Override
        public GenericType<?> payloadGenericType() {
            return source.payloadType();
        }

        @Override
        public GenericType<?> envelopeGenericType() {
            return GenericType.create(Message.class);
        }

        @Override
        public String outgoingChannel() {
            return target.name();
        }

        @Override
        public GenericType<?> outgoingPayloadGenericType() {
            return target.payloadType();
        }

        @Override
        public GenericType<?> outgoingEnvelopeGenericType() {
            return GenericType.create(Message.class);
        }

        @Override
        public MessageBatch<?> process(MessageBatch<?> batch) {
            return processor.apply(batch);
        }
    }

    record RouteRegistration(MessagingChannel<?> source, MessagingChannel<?> target) implements ProcessorRegistration {
        @Override
        public String channel() {
            return source.name();
        }

        @Override
        public GenericType<?> payloadGenericType() {
            return source.payloadType();
        }

        @Override
        public GenericType<?> envelopeGenericType() {
            return GenericType.create(Message.class);
        }

        @Override
        public String outgoingChannel() {
            return target.name();
        }

        @Override
        public GenericType<?> outgoingPayloadGenericType() {
            return target.payloadType();
        }

        @Override
        public GenericType<?> outgoingEnvelopeGenericType() {
            return GenericType.create(Message.class);
        }

        @Override
        public MessageBatch<?> process(MessageBatch<?> batch) {
            return batch;
        }
    }
}
