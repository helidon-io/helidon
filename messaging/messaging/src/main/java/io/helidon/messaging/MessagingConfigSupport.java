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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.builder.api.Prototype;
import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.faulttolerance.Retry;
import io.helidon.messaging.spi.ChannelConnection;
import io.helidon.messaging.spi.IncomingChannel;
import io.helidon.messaging.spi.MessagingChannelConfig;
import io.helidon.messaging.spi.OutgoingChannel;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

final class MessagingConfigSupport {
    private MessagingConfigSupport() {
    }

    static Execution execution(MessagingConfig defaults, MessagingExecutionConfig overrides) {
        return new Execution(overrides.queueCapacity().orElse(defaults.queueCapacity()),
                             overrides.maxPendingAdmissions().orElse(defaults.maxPendingAdmissions()),
                             overrides.maxPendingMessages().orElse(defaults.maxPendingMessages()),
                             overrides.maxInFlightMessages().orElse(defaults.maxInFlightMessages()),
                             overrides.admissionTimeout().or(() -> defaults.admissionTimeout()));
    }

    static MessagingExecutionConfig channelExecution(MessagingConfig config, String channel) {
        MessagingChannelConfig channelConfig = config.incoming().get(channel);
        if (channelConfig == null) {
            channelConfig = config.outgoing().get(channel);
        }
        return channelConfig == null ? MessagingExecutionConfig.create() : channelConfig.execution();
    }

    static FailurePolicy failurePolicy(FailurePolicy declared, MessagingFailureConfig overrides) {
        FailurePolicy.Builder builder = FailurePolicy.builder(declared);
        overrides.retry().ifPresent(builder::retry);
        overrides.onExhausted().ifPresent(builder::onExhausted);
        overrides.deadLetter().ifPresent(builder::deadLetter);
        if (overrides.onExhausted().filter(it -> it != FailureDisposition.DEAD_LETTER).isPresent()
                && overrides.deadLetter().isEmpty()) {
            builder.clearDeadLetter();
        }
        return builder.build();
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be zero or greater");
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        try {
            duration.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(name + " must be representable in nanoseconds", e);
        }
    }

    record Execution(int queueCapacity,
                     int maxPendingAdmissions,
                     int maxPendingMessages,
                     int maxInFlightMessages,
                     Optional<Duration> admissionTimeout) {
    }

    @Service.Singleton
    @Weight(Weighted.DEFAULT_WEIGHT - 20)
    static final class ConfigurationProvider implements Supplier<MessagingConfig> {
        private final MessagingConfig config;

        @Service.Inject
        ConfigurationProvider(Config config,
                              ServiceRegistry serviceRegistry,
                              List<ConsumerRegistration> consumerRegistrations,
                              List<EmitterRegistration> emitterRegistrations) {
            this.config = MessagingConfig.builder()
                    .serviceRegistry(serviceRegistry)
                    .config(config.get("messaging"))
                    .consumerRegistrations(consumerRegistrations)
                    .emitterRegistrations(emitterRegistrations)
                    .buildPrototype();
        }

        @Override
        public MessagingConfig get() {
            return config;
        }
    }

    static final class BuilderDecorator implements Prototype.BuilderDecorator<MessagingConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(MessagingConfig.BuilderBase<?, ?> target) {
            requireNonNegative(target.queueCapacity(), "messaging.queue-capacity");
            requirePositive(target.maxPendingAdmissions(), "messaging.max-pending-admissions");
            requirePositive(target.maxPendingMessages(), "messaging.max-pending-messages");
            requirePositive(target.maxInFlightMessages(), "messaging.max-in-flight-messages");
            target.admissionTimeout().ifPresent(timeout -> requirePositive(timeout,
                                                                           "messaging.admission-timeout"));
            requirePositive(target.shutdownTimeout(), "messaging.shutdown-timeout");
        }
    }

    static final class DeadLetterBuilderDecorator
            implements Prototype.BuilderDecorator<DeadLetterConfig.BuilderBase<?, ?>> {
        static void validate(String channel) {
            if (channel == null) {
                throw new IllegalArgumentException("failure.dead-letter.channel must be configured");
            }
            if (channel.isBlank()) {
                throw new IllegalArgumentException("failure.dead-letter.channel must not be blank");
            }
        }

        @Override
        public void decorate(DeadLetterConfig.BuilderBase<?, ?> target) {
            validate(target.channel().orElse(null));
        }
    }

    static final class FailurePolicyBuilderDecorator
            implements Prototype.BuilderDecorator<FailurePolicy.BuilderBase<?, ?>> {
        static final int DEFAULT_RETRY_CALLS = Integer.MAX_VALUE;
        static final Duration DEFAULT_RETRY_MAX_DELAY = Duration.ofMinutes(1);
        static final Duration DEFAULT_RETRY_OVERALL_TIMEOUT = Duration.ofNanos(Long.MAX_VALUE);

        FailurePolicyBuilderDecorator() {
        }

        static Retry defaultRetry() {
            return DefaultRetry.INSTANCE;
        }

        @Override
        public void decorate(FailurePolicy.BuilderBase<?, ?> target) {
            FailureDisposition onExhausted = target.onExhausted();
            Optional<DeadLetterConfig> deadLetter = target.deadLetter();
            String deadLetterChannel = null;
            if (onExhausted == FailureDisposition.DEAD_LETTER) {
                if (deadLetter.isEmpty()) {
                    throw new IllegalArgumentException(
                            "failure.dead-letter.channel must be configured for DEAD_LETTER");
                }
                deadLetterChannel = deadLetter.orElseThrow().channel();
                DeadLetterBuilderDecorator.validate(deadLetterChannel);
            } else if (deadLetter.isPresent()) {
                throw new IllegalArgumentException(
                        "failure.dead-letter.channel is only valid for DEAD_LETTER");
            }

            DeadLetterConfig canonicalDeadLetter = deadLetterChannel == null
                    ? null
                    : DeadLetterConfig.builder().channel(deadLetterChannel).build();
            if (canonicalDeadLetter != null) {
                target.deadLetter(canonicalDeadLetter);
            }
        }

        private static final class DefaultRetry {
            private static final Retry INSTANCE = Retry.builder()
                    .name("messaging-delivery")
                    .calls(DEFAULT_RETRY_CALLS)
                    .delay(Duration.ofSeconds(1))
                    .delayFactor(2)
                    .maxDelay(DEFAULT_RETRY_MAX_DELAY)
                    .overallTimeout(DEFAULT_RETRY_OVERALL_TIMEOUT)
                    .addApplyOn(RuntimeException.class)
                    .addSkipOn(Error.class)
                    .addSkipOn(MessagingRejectedException.class)
                    .build();
        }
    }

    static final class ExecutionBuilderDecorator
            implements Prototype.BuilderDecorator<MessagingExecutionConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(MessagingExecutionConfig.BuilderBase<?, ?> target) {
            target.config().ifPresent(config -> {
                if (config.get("shutdown-timeout").exists()) {
                    throw new IllegalArgumentException("Channel execution configuration must not override global shutdown-timeout");
                }
            });
            target.queueCapacity().ifPresent(value -> requireNonNegative(value, "messaging.execution.queue-capacity"));
            target.maxPendingAdmissions().ifPresent(value -> requirePositive(value,
                                                                             "messaging.execution.max-pending-admissions"));
            target.maxPendingMessages().ifPresent(value -> requirePositive(value, "messaging.execution.max-pending-messages"));
            target.maxInFlightMessages().ifPresent(value -> requirePositive(value,
                                                                            "messaging.execution.max-in-flight-messages"));
            target.admissionTimeout().ifPresent(timeout -> requirePositive(timeout,
                                                                           "messaging.execution.admission-timeout"));
        }
    }

    static final class ChannelCustomMethods {
        private ChannelCustomMethods() {
        }

        /**
         * Create a typed channel handle.
         *
         * @param name channel name
         * @param payloadType payload type
         * @param <T> payload type
         * @return channel handle
         */
        @Prototype.PrototypeFactoryMethod
        static <T> MessagingChannel<T> create(String name, Class<T> payloadType) {
            return create(name, GenericType.create(Objects.requireNonNull(payloadType)));
        }

        /**
         * Create a channel handle preserving the complete payload type.
         *
         * @param name channel name
         * @param payloadType payload type
         * @param <T> payload type
         * @return channel handle
         */
        @Prototype.PrototypeFactoryMethod
        static <T> MessagingChannel<T> create(String name, GenericType<T> payloadType) {
            return MessagingChannel.<T>builder()
                    .name(name)
                    .payloadType(payloadType)
                    .build();
        }
    }

    static final class ChannelBuilderDecorator
            implements Prototype.BuilderDecorator<MessagingChannel.BuilderBase<?, ?, ?>> {
        @Override
        public void decorate(MessagingChannel.BuilderBase<?, ?, ?> target) {
            target.name().ifPresent(name -> {
                if (name.isBlank()) {
                    throw new IllegalArgumentException("Messaging channel name must not be blank");
                }
            });
            target.payloadType().ifPresent(payloadType -> {
                if (payloadType.rawType().isPrimitive()) {
                    throw new IllegalArgumentException("Messaging channel payload type must not be primitive: "
                                                               + payloadType.getTypeName());
                }
            });
        }
    }

    static final class MessageBatchCustomMethods {
        private MessageBatchCustomMethods() {
        }

        static String defaultId() {
            return UUID.randomUUID().toString();
        }

        // The explicit non-option name keeps this top-level generic factory from being offered as an option conversion.
        @Prototype.RuntimeTypeFactoryMethod("messageBatch")
        static <T> MessageBatch<T> create(MessageBatchConfig<T> config) {
            return MessageBatch.create(config);
        }
    }

    static final class GraphCustomMethods {
        private GraphCustomMethods() {
        }

        /**
         * Register a typed channel handle.
         *
         * @param builder target builder
         * @param channel channel handle
         * @throws IllegalArgumentException if a channel with this name is already registered
         */
        @Prototype.BuilderMethod
        static void channel(MessagingConfig.BuilderBase<?, ?> builder, MessagingChannel<?> channel) {
            Objects.requireNonNull(channel);
            if (builder.channelHandles().stream().anyMatch(existing -> existing.name().equals(channel.name()))) {
                throw new IllegalArgumentException("Duplicate messaging channel " + channel.name());
            }
            builder.addChannelHandles(List.of(channel));
        }

        /**
         * Add a payload stream source.
         * <p>
         * The built graph owns the stream and closes it on shutdown. A failed build also closes registered streams.
         * A channel can have at most one stream source; explicit multi-source fan-in is not part of this API version.
         * Downstream paths of distinct stream sources must not converge on the same channel.
         *
         * @param builder target builder
         * @param channel target channel
         * @param source source stream
         * @param <T> payload type
         * @throws IllegalArgumentException if the channel already has a stream source
         */
        @Prototype.BuilderMethod
        static <T> void payloadSource(MessagingConfig.BuilderBase<?, ?> builder,
                                      MessagingChannel<T> channel,
                                      Stream<? extends T> source) {
            source(builder, channel, source, false);
        }

        /**
         * Add a message stream source.
         * <p>
         * The built graph owns the stream and closes it on shutdown. A failed build also closes registered streams.
         * A channel can have at most one stream source; explicit multi-source fan-in is not part of this API version.
         * Downstream paths of distinct stream sources must not converge on the same channel.
         *
         * @param builder target builder
         * @param channel target channel
         * @param source source stream
         * @param <T> payload type
         * @throws IllegalArgumentException if the channel already has a stream source
         */
        @Prototype.BuilderMethod
        static <T> void messageSource(MessagingConfig.BuilderBase<?, ?> builder,
                                      MessagingChannel<T> channel,
                                      Stream<? extends Message<? extends T>> source) {
            source(builder, channel, source, true);
        }

        /**
         * Route each delivery batch unchanged from one channel to another channel of the same type.
         *
         * @param builder target builder
         * @param source source channel
         * @param target target channel
         * @param <T> payload type
         */
        @Prototype.BuilderMethod
        static <T> void route(MessagingConfig.BuilderBase<?, ?> builder,
                              MessagingChannel<T> source,
                              MessagingChannel<T> target) {
            requireChannel(builder, source);
            requireChannel(builder, target);
            if (!source.payloadType().equals(target.payloadType())) {
                throw new IllegalArgumentException("Messaging route " + source.name() + " -> " + target.name()
                                                           + " has incompatible payload types "
                                                           + source.payloadType().getTypeName() + " and "
                                                           + target.payloadType().getTypeName());
            }
            if (builder.consumerRegistrations().stream().anyMatch(registration ->
                    registration instanceof RouteRegistration route
                            && route.channel().equals(source.name())
                            && route.outgoingChannel().equals(target.name()))) {
                throw new IllegalArgumentException("Duplicate messaging route " + source.name() + " -> " + target.name());
            }
            builder.addConsumerRegistration(new RouteRegistration(source, target));
        }

        /**
         * Add a payload processor. The processor is invoked once per batch item in order and its results form one
         * lineage-preserving derived batch. Message metadata is not propagated by a payload processor.
         *
         * @param builder target builder
         * @param source source channel
         * @param target target channel
         * @param processor payload processor
         * @param <I> input payload type
         * @param <O> output payload type
         */
        @Prototype.BuilderMethod
        static <I, O> void payloadProcessor(MessagingConfig.BuilderBase<?, ?> builder,
                                            MessagingChannel<I> source,
                                            MessagingChannel<O> target,
                                            Function<? super I, ? extends O> processor) {
            requireChannel(builder, source);
            requireChannel(builder, target);
            Objects.requireNonNull(processor);
            builder.addConsumerRegistration(new ProcessorDefinition(source, target, batch -> {
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
        }

        /**
         * Add a message processor. The processor is invoked once per batch item in order and its results form one
         * lineage-preserving derived batch.
         *
         * @param builder target builder
         * @param source source channel
         * @param target target channel
         * @param processor message processor
         * @param <I> input payload type
         * @param <O> output payload type
         */
        @Prototype.BuilderMethod
        static <I, O> void messageProcessor(MessagingConfig.BuilderBase<?, ?> builder,
                                            MessagingChannel<I> source,
                                            MessagingChannel<O> target,
                                            Function<? super Message<I>, ? extends Message<? extends O>> processor) {
            requireChannel(builder, source);
            requireChannel(builder, target);
            Objects.requireNonNull(processor);
            builder.addConsumerRegistration(new ProcessorDefinition(source, target, batch -> {
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
        }

        /**
         * Add a payload sink.
         *
         * @param builder target builder
         * @param source source channel
         * @param sink payload sink
         * @param <T> payload type
         */
        @Prototype.BuilderMethod
        static <T> void payloadSink(MessagingConfig.BuilderBase<?, ?> builder,
                                    MessagingChannel<T> source,
                                    Consumer<? super T> sink) {
            requireChannel(builder, source);
            Objects.requireNonNull(sink);
            builder.addConsumerRegistration(new ConsumerDefinition(source, batch -> {
                for (int i = 0; i < batch.size(); i++) {
                    try {
                        sink.accept(source.payloadType().cast(batch.get(i).entity()));
                    } catch (RuntimeException e) {
                        throw BatchDeliveryExceptionSupport.sequential("Messaging payload sink", batch, i, e);
                    }
                }
            }));
        }

        /**
         * Add a message sink.
         *
         * @param builder target builder
         * @param source source channel
         * @param sink message sink
         * @param <T> payload type
         */
        @Prototype.BuilderMethod
        static <T> void messageSink(MessagingConfig.BuilderBase<?, ?> builder,
                                    MessagingChannel<T> source,
                                    Consumer<? super Message<T>> sink) {
            requireChannel(builder, source);
            Objects.requireNonNull(sink);
            builder.addConsumerRegistration(new ConsumerDefinition(source, batch -> {
                for (int i = 0; i < batch.size(); i++) {
                    try {
                        sink.accept(castMessage(batch.get(i)));
                    } catch (RuntimeException e) {
                        throw BatchDeliveryExceptionSupport.sequential("Messaging message sink", batch, i, e);
                    }
                }
            }));
        }

        /**
         * Add a message batch sink.
         *
         * @param builder target builder
         * @param source source channel
         * @param sink message batch sink
         * @param <T> payload type
         */
        @Prototype.BuilderMethod
        static <T> void batchSink(MessagingConfig.BuilderBase<?, ?> builder,
                                  MessagingChannel<T> source,
                                  Consumer<MessageBatch<T>> sink) {
            requireChannel(builder, source);
            Objects.requireNonNull(sink);
            builder.addConsumerRegistration(new ConsumerDefinition(source, batch -> sink.accept(castBatch(batch))));
        }

        /**
         * Add an incoming channel connection as a source.
         * <p>
         * The built graph owns the connection and manages its startup, delivery admission, draining, and shutdown.
         * A failed build also closes registered connections.
         *
         * @param builder target builder
         * @param target target channel
         * @param connection incoming channel connection
         * @param <T> payload type
         */
        @Prototype.BuilderMethod
        static <T> void incomingChannel(MessagingConfig.BuilderBase<?, ?> builder,
                                        MessagingChannel<T> target,
                                        IncomingChannel connection) {
            requireChannel(builder, target);
            requireConnection(builder, connection);
            requireNoSource(builder, target);
            builder.putIncomingConnection(target, connection);
        }

        /**
         * Add an outgoing channel connection as a required channel output.
         * <p>
         * The built graph owns the connection and closes it on shutdown. A failed build also closes it.
         *
         * @param builder target builder
         * @param source source channel
         * @param connection outgoing channel connection
         * @param <T> payload type
         */
        @Prototype.BuilderMethod
        static <T> void outgoingChannel(MessagingConfig.BuilderBase<?, ?> builder,
                                        MessagingChannel<T> source,
                                        OutgoingChannel connection) {
            requireChannel(builder, source);
            requireConnection(builder, connection);
            builder.addConsumerRegistration(new OutgoingDefinition(source, connection));
        }

        @SuppressWarnings("unchecked")
        private static <T> Message<T> castMessage(Message<?> message) {
            return (Message<T>) message;
        }

        @SuppressWarnings("unchecked")
        private static <T> MessageBatch<T> castBatch(MessageBatch<?> batch) {
            return (MessageBatch<T>) batch;
        }

        private static void requireChannel(MessagingConfig.BuilderBase<?, ?> builder, MessagingChannel<?> channel) {
            Objects.requireNonNull(channel);
            if (builder.channelHandles().stream().noneMatch(handle -> handle == channel)) {
                throw new IllegalArgumentException("Messaging channel " + channel.name()
                                                           + " belongs to another messaging graph builder");
            }
        }

        private static void requireConnection(MessagingConfig.BuilderBase<?, ?> builder, ChannelConnection connection) {
            Objects.requireNonNull(connection);
            if (builder.incomingConnections().values().stream().anyMatch(incoming -> incoming == connection)
                    || builder.consumerRegistrations().stream().anyMatch(registration ->
                            registration instanceof OutgoingDefinition outgoing && outgoing.connection() == connection)) {
                throw new IllegalArgumentException("Channel connection is already owned by this messaging graph builder");
            }
        }

        private static void requireNoSource(MessagingConfig.BuilderBase<?, ?> builder, MessagingChannel<?> channel) {
            if (builder.emitterRegistrations().stream().anyMatch(registration ->
                    registration instanceof SourceDefinition source && source.handle() == channel)
                    || builder.incomingConnections().containsKey(channel)) {
                throw new IllegalArgumentException("Messaging channel " + channel.name() + " already has a source");
            }
        }

        private static void source(MessagingConfig.BuilderBase<?, ?> builder,
                                   MessagingChannel<?> channel,
                                   Stream<?> source,
                                   boolean messages) {
            requireChannel(builder, channel);
            Objects.requireNonNull(source);
            requireNoSource(builder, channel);
            if (builder.emitterRegistrations().stream().anyMatch(registration ->
                    registration instanceof SourceDefinition definition && definition.stream() == source)) {
                throw new IllegalArgumentException("Stream source is already owned by this messaging graph builder");
            }
            builder.addEmitterRegistration(new SourceDefinition(channel, source, messages));
        }
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

    static final class MessageBatchIdDecorator
            implements Prototype.OptionDecorator<MessageBatchConfig.BuilderBase<?, ?, ?>, String> {
        @Override
        public void decorate(MessageBatchConfig.BuilderBase<?, ?, ?> target, String id) {
            MessageBatch.validateId(id);
        }
    }

    static final class MessageBatchBuilderDecorator
            implements Prototype.BuilderDecorator<MessageBatchConfig.BuilderBase<?, ?, ?>> {
        @Override
        public void decorate(MessageBatchConfig.BuilderBase<?, ?, ?> target) {
            MessageBatch.validateId(target.id());
            if (target.messages().isEmpty()) {
                throw new IllegalArgumentException("Message batch must contain at least one message");
            }
        }
    }
}
