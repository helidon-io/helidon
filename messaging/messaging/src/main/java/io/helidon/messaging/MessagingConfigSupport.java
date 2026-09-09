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
import java.util.Optional;
import java.util.UUID;

import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;
import io.helidon.faulttolerance.RetryConfig;

final class MessagingConfigSupport {
    static final String CHANNEL_PREFIX = "messaging.channel.";
    static final String CONNECTOR_PREFIX = "messaging.connector.";
    static final String EXECUTION = "messaging.execution";
    static final String INCOMING_PREFIX = "messaging.incoming.";
    static final String OUTGOING_PREFIX = "messaging.outgoing.";

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
        MessagingChannelConfig channelConfig = config.channel().get(channel);
        return channelConfig == null ? MessagingExecutionConfig.create() : channelConfig.execution();
    }

    static void applyExecution(MessagingConfig.BuilderBase<?, ?> target, MessagingExecutionConfig overrides) {
        overrides.queueCapacity().ifPresent(target::queueCapacity);
        overrides.maxPendingAdmissions().ifPresent(target::maxPendingAdmissions);
        overrides.maxPendingMessages().ifPresent(target::maxPendingMessages);
        overrides.maxInFlightMessages().ifPresent(target::maxInFlightMessages);
        overrides.admissionTimeout().ifPresent(target::admissionTimeout);
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

        static RetryConfig defaultRetry() {
            return RetryConfig.builder()
                    .calls(DEFAULT_RETRY_CALLS)
                    .delay(Duration.ofSeconds(1))
                    .delayFactor(2)
                    .maxDelay(DEFAULT_RETRY_MAX_DELAY)
                    .overallTimeout(DEFAULT_RETRY_OVERALL_TIMEOUT)
                    .addApplyOn(RuntimeException.class)
                    .buildPrototype();
        }

        @Prototype.ConfigFactoryMethod("retry")
        static RetryConfig configuredRetry(Config config) {
            return RetryConfig.builder(defaultRetry())
                    .config(config)
                    .buildPrototype();
        }

        @Override
        public void decorate(FailurePolicy.BuilderBase<?, ?> target) {
            RetryConfig retry = target.retry();
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

            RetryConfig canonicalRetry = RetryConfig.builder(retry).buildPrototype();
            DeadLetterConfig canonicalDeadLetter = deadLetterChannel == null
                    ? null
                    : DeadLetterConfig.builder().channel(deadLetterChannel).build();
            target.retry(canonicalRetry);
            if (canonicalDeadLetter != null) {
                target.deadLetter(canonicalDeadLetter);
            }
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

        // The graph has a typed channel-handle builder extending the generated configuration builder base.
        @Prototype.RuntimeTypeFactoryMethod("messagingGraph")
        static MessagingGraph create(MessagingConfig config) {
            return MessagingGraph.create(config);
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
