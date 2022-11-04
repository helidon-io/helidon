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
 */

package io.helidon.messaging.connectors.jms;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.messaging.MessagingException;
import io.helidon.messaging.NackHandler;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import org.eclipse.microprofile.reactive.messaging.Message;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

abstract class JmsNackHandler implements NackHandler<JmsMessage<?>> {

    static JmsNackHandler create(BufferedEmittingPublisher<Message<?>> emitter,
                                 Config config,
                                 JmsConnector jmsConnector) {
        Config dlq = config.get("nack-dlq");
        Config logOnly = config.get("nack-log-only");
        if (dlq.exists()) {
            dlq = dlq.detach();
            return new JmsDLQ(config, dlq, jmsConnector);
        } else if (logOnly.exists() && logOnly.asBoolean().orElse(true)) {
            logOnly = logOnly.detach();
            return new JmsNackHandler.Log(config, logOnly);
        }
        // Default nack handling strategy
        return new JmsNackHandler.KillChannel(emitter, config);
    }

    static class Log extends JmsNackHandler {

        private final System.Logger logger;
        private final String channelName;
        private final System.Logger.Level level;

        Log(Config config, Config logOnlyConfig) {
            this.channelName = config.get(JmsConnector.CHANNEL_NAME_ATTRIBUTE)
                    .asString()
                    .orElseThrow(() -> new MessagingException("Missing channel name!"));

            this.level = logOnlyConfig.get("level")
                    .as(System.Logger.Level.class)
                    .orElse(WARNING);

            this.logger = System.getLogger(logOnlyConfig.get("logger")
                    .asString()
                    .orElse(JmsNackHandler.class.getName()));
        }


        @Override
        public Function<Throwable, CompletionStage<Void>> getNack(JmsMessage<?> message) {
            return t -> nack(t, message);
        }

        private CompletionStage<Void> nack(Throwable t, JmsMessage<?> message) {
            logger.log(level, messageToString("NACKED Message ignored", channelName, message));
            message.ack();
            return CompletableFuture.completedFuture(null);
        }
    }

    static class KillChannel extends JmsNackHandler {

        private static final System.Logger LOGGER = System.getLogger(JmsNackHandler.KillChannel.class.getName());
        private final BufferedEmittingPublisher<Message<?>> emitter;
        private final String channelName;

        KillChannel(BufferedEmittingPublisher<Message<?>> emitter, Config config) {
            this.emitter = emitter;
            this.channelName = config.get(JmsConnector.CHANNEL_NAME_ATTRIBUTE)
                    .asString()
                    .orElseThrow(() -> new MessagingException("Missing channel name!"));
        }

        @Override
        public Function<Throwable, CompletionStage<Void>> getNack(JmsMessage<?> message) {
            return throwable -> nack(throwable, message);
        }

        private CompletionStage<Void> nack(Throwable t, JmsMessage<?> message) {
            LOGGER.log(ERROR, messageToString("NACKED message, killing the channel", channelName, message), t);
            emitter.fail(t);
            return CompletableFuture.failedStage(t);
        }
    }

    static <V> String messageToString(String prefix, String channel, JmsMessage<V> message) {
        StringBuilder msg = new StringBuilder(prefix);
        msg.append("\n");
        appendNonNull(msg, "channel", channel);
        appendNonNull(msg, "correlationId", message.getCorrelationId());
        appendNonNull(msg, "replyTo", message.getReplyTo());
        for (String prop : message.getPropertyNames()) {
            appendNonNull(msg, prop, message.getProperty(prop));
        }
        return msg.toString();
    }

    static StringBuilder appendNonNull(StringBuilder sb, String name, Object value) {
        if (Objects.isNull(value)) return sb;
        return sb.append(name + ": ").append(value).append("\n");
    }

    static class JmsDLQ extends JmsNackHandler {
        private static final System.Logger LOGGER = System.getLogger(JmsNackHandler.JmsDLQ.class.getName());
        private final MessageProducer producer;
        private final SessionMetadata sessionMetadata;
        private final AtomicReference<MessageMapper> mapper = new AtomicReference<>();
        private final String channelName;
        private Config config;
        private JmsConnector jmsConnector;
        private Config dlq;

        JmsDLQ(Config config, Config dlq, JmsConnector jmsConnector) {
            this.config = config;
            this.jmsConnector = jmsConnector;
            this.channelName = config.get(JmsConnector.CHANNEL_NAME_ATTRIBUTE)
                    .asString()
                    .orElseThrow(() -> new MessagingException("Missing channel name!"));

            Config.Builder dlqCfgBuilder = Config.builder();
            HashMap<String, String> dlqCfgMap = new HashMap<>();
            if (dlq.isLeaf()) {
                // nack-dlq=destination_name - Uses actual connection config, just set dlq destination
                String destination = dlq.asString().orElseThrow(() -> new MessagingException("nack-dlq with no value!"));
                dlqCfgMap.put(JmsConnector.DESTINATION_ATTRIBUTE, destination);
                dlqCfgMap.put("type", "queue"); // default is queue
                this.dlq = dlqCfgBuilder
                        .sources(
                                ConfigSources.create(dlqCfgMap),
                                ConfigSources.create(config.detach())
                        )
                        .disableEnvironmentVariablesSource()
                        .disableSystemPropertiesSource()
                        .build();
            } else {
                // Custom dlq connection config
                this.dlq = dlq.detach();
            }

            try {
                ConnectionContext ctx = new ConnectionContext(this.dlq);
                ConnectionFactory factory = jmsConnector.getFactory(ctx)
                        .orElseThrow(() -> new MessagingException("No ConnectionFactory found."));
                sessionMetadata = jmsConnector.prepareSession(dlq, factory);
                Destination destination = jmsConnector.createDestination(sessionMetadata.session(), ctx);
                producer = jmsConnector.createProducer(destination, ctx, sessionMetadata);
            } catch (JMSException e) {
                throw new MessagingException("Error when setting up DLQ nack handler for channel " + channelName, e);
            }
        }

        @Override
        public Function<Throwable, CompletionStage<Void>> getNack(JmsMessage<?> message) {
            return throwable -> nack(throwable, message);
        }

        private CompletionStage<Void> nack(Throwable t, JmsMessage<?> message) {
            try {

                Throwable cause = t;
                while (cause.getCause() != null && cause != cause.getCause()) {
                    cause = cause.getCause();
                }

                // It has to be incoming JMS message as this nack handler cannot be used outside of connector
                JmsMessage.OutgoingJmsMessageBuilder<Object> builder = JmsMessage.builder(message.getJmsMessage());
                builder.property(DLQ_ERROR_PROP, cause.getClass().getName())
                        .property(DLQ_ERROR_MSG_PROP, cause.getMessage())
                        .correlationId(message.getCorrelationId())
                        .payload(message.getPayload());

                config.get(JmsConnector.DESTINATION_ATTRIBUTE)
                        .asString()
                        .ifPresent(s -> builder.property(DLQ_ORIG_TOPIC_PROP, s));

                Message<?> dlqMessage = builder.build();
                jmsConnector.consume(dlqMessage, sessionMetadata.session(), mapper, producer, config);
            } catch (Throwable e) {
                e.addSuppressed(t);
                LOGGER.log(ERROR, "Error when sending nacked message to DLQ", e);
                return CompletableFuture.completedStage(null);
            }
            return CompletableFuture.completedStage(null);
        }
    }
}

