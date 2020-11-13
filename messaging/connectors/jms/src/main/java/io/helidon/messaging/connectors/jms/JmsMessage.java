/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.messaging.connectors.jms;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import io.helidon.common.Builder;
import io.helidon.messaging.MessagingException;

import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Message representing JMS message together with all the metadata.
 *
 * @param <PAYLOAD> Type of the payload.
 */
public interface JmsMessage<PAYLOAD> extends Message<PAYLOAD> {

    /**
     * Metadata about the JMS session.
     *
     * @return JMS session
     */
    Session getJmsSession();

    /**
     * Get client's connection to its JMS provider.
     *
     * @return JMS connection
     */
    Connection getJmsConnection();

    /**
     * Get JMS connection factory.
     *
     * @return JMS connection factory
     */
    ConnectionFactory getJmsConnectionFactory();

    /**
     * Original JMS message received.
     *
     * @param <MSG> expected sub-type of {@link javax.jms.Message}
     * @return original JMS message received
     * @throws java.lang.ClassCastException when original payload cannot be casted to expected type
     */
    <MSG extends javax.jms.Message> MSG getJmsMessage();

    /**
     * Return type identifier set by sending JMS client.
     *
     * @return JMS type or null
     */
    default String getType() {
        try {
            return getJmsMessage().getJMSType();
        } catch (JMSException e) {
            throw new MessagingException("Error when retrieving JMS type.", e);
        }
    }

    /**
     * Return correlation id of the message.
     *
     * @return correlation id or null
     */
    default String getCorrelationId() {
        try {
            return getJmsMessage().getJMSCorrelationID();
        } catch (JMSException e) {
            throw new MessagingException("Error when retrieving JMS correlation id.", e);
        }
    }

    /**
     * Return reply to destination of the message.
     *
     * @return destination or null
     */
    default Destination getReplyTo() {
        try {
            return getJmsMessage().getJMSReplyTo();
        } catch (JMSException e) {
            throw new MessagingException("Error when retrieving JMS reply to destination.", e);
        }
    }

    /**
     * Check if message has been acknowledged yet.
     *
     * @return true if message has been acknowledged
     */
    boolean isAck();

    /**
     * Get JMS property by name.
     *
     * @param name the name of the JMS property
     * @param <P>  expected type of the property, has to be Boolean, Byte, Short, Integer, Long, Float, Double or String
     * @return property value with the specified name or null
     * @throws java.lang.ClassCastException when property cannot be casted to expected type
     */
    <P> P getProperty(String name);

    /**
     * Check if JMS property exists.
     *
     * @param name the name of the JMS property
     * @return true if property exists
     */
    boolean hasProperty(String name);

    /**
     * Return set of all JMS property names.
     *
     * @return JMS property names
     */
    Set<String> getPropertyNames();

    /**
     * Create a JmsMessage with the given payload and ack function.
     *
     * @param payload   The payload.
     * @param ack       The ack function, this will be invoked when the returned messages {@link #ack()} method is invoked.
     * @param <PAYLOAD> the type of payload
     * @return A message with the given payload and ack function.
     */
    static <PAYLOAD> Message<PAYLOAD> of(PAYLOAD payload, Supplier<CompletionStage<Void>> ack) {
        return builder(payload).onAck(ack).build();
    }

    /**
     * Create a JmsMessage with the given payload.
     *
     * @param payload   The payload.
     * @param <PAYLOAD> The type of payload
     * @return A message with the given payload, and a no-op ack function.
     */
    static <PAYLOAD> Message<PAYLOAD> of(PAYLOAD payload) {
        return builder(payload).build();
    }

    /**
     * Outgoing JMS message builder.
     * Makes possible to create JMS message with properties.
     *
     * @param payload   JMS message payload
     * @param <PAYLOAD> JMS message payload type
     * @return Message builder
     */
    static <PAYLOAD> OutgoingJmsMessageBuilder<PAYLOAD> builder(PAYLOAD payload) {
        return new OutgoingJmsMessageBuilder<>(payload);
    }

    /**
     * Outgoing JMS message builder.
     * Makes possible to create JMS message from {@link javax.jms.Message} and customize result.
     * Does NOT copy payload, only headers and properties.
     *
     * @param msg       {@link javax.jms.Message} to derive metadata
     * @param <PAYLOAD> payload type
     * @return Message builder
     * @throws MessagingException       when JMS provider can't retrieve message body, properties or metadata
     */
    static <PAYLOAD> OutgoingJmsMessageBuilder<PAYLOAD> builder(javax.jms.Message msg) {
        try {
            return new OutgoingJmsMessageBuilder<>(msg);
        } catch (JMSException e) {
            throw new MessagingException("Error when retrieving contents of javax.jms.Message");
        }
    }

    /**
     * Outgoing JMS message builder.
     * Makes possible to create JMS message with properties.
     *
     * @param <PAYLOAD> payload type
     */
    class OutgoingJmsMessageBuilder<PAYLOAD> implements Builder<Message<PAYLOAD>> {

        private final HashMap<String, Object> properties = new HashMap<>();
        private final OutgoingJmsMessage<PAYLOAD> message;
        private String correlationId = null;
        private Destination replyTo = null;
        private String type;

        private OutgoingJmsMessageBuilder(final PAYLOAD payload) {
            message = new OutgoingJmsMessage<>(payload);
        }

        private OutgoingJmsMessageBuilder(javax.jms.Message msg) throws JMSException {
            message = OutgoingJmsMessage.fromJmsMessage(msg);
        }

        /**
         * Set or replace JMS payload.
         *
         * @param payload new payload
         */
        OutgoingJmsMessageBuilder<PAYLOAD> payload(PAYLOAD payload) {
            message.setPayload(payload);
            return this;
        }

        /**
         * Set JMS of given name.
         *
         * @param name  the name of the JMS property
         * @param value boolean value to stored as JMS property
         */
        OutgoingJmsMessageBuilder<PAYLOAD> property(String name, boolean value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Set JMS of given name.
         *
         * @param name  the name of the JMS property
         * @param value byte value to stored as JMS property
         */
        OutgoingJmsMessageBuilder<PAYLOAD> property(String name, byte value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Set JMS of given name.
         *
         * @param name  the name of the JMS property
         * @param value short value to stored as JMS property
         */
        OutgoingJmsMessageBuilder<PAYLOAD> property(String name, short value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Set JMS of given name.
         *
         * @param name  the name of the JMS property
         * @param value int value to stored as JMS property
         */
        OutgoingJmsMessageBuilder<PAYLOAD> property(String name, int value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Set JMS of given name.
         *
         * @param name  the name of the JMS property
         * @param value long value to stored as JMS property
         */
        OutgoingJmsMessageBuilder<PAYLOAD> property(String name, long value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Set JMS of given name.
         *
         * @param name  the name of the JMS property
         * @param value float value to stored as JMS property
         */
        OutgoingJmsMessageBuilder<PAYLOAD> property(String name, float value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Set JMS of given name.
         *
         * @param name  the name of the JMS property
         * @param value double value to stored as JMS property
         */
        OutgoingJmsMessageBuilder<PAYLOAD> property(String name, double value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Set JMS of given name.
         *
         * @param name  the name of the JMS property
         * @param value string value to be stored as JMS property
         */
        OutgoingJmsMessageBuilder<PAYLOAD> property(String name, String value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Callback invoked when message is acknowledged.
         *
         * @param ack callback
         * @return this builder
         */
        OutgoingJmsMessageBuilder<PAYLOAD> onAck(Supplier<CompletionStage<Void>> ack) {
            this.message.onAck(ack);
            return this;
        }

        /**
         * Custom mapper used by connector for mapping to {@link javax.jms.Message}.
         *
         * @param mapper supplying this message and {@link javax.jms.Session} for manual creation of {@link javax.jms.Message}
         * @return this builder
         */
        OutgoingJmsMessageBuilder<PAYLOAD> customMapper(CustomMapper<PAYLOAD> mapper) {
            this.message.mapper(mapper);
            return this;
        }

        /**
         * Correlation ID for creating {@link javax.jms.Message}.
         *
         * @param correlationId provider specific or application specific correlation ID
         * @return this builder
         */
        OutgoingJmsMessageBuilder<PAYLOAD> correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        /**
         * Destination to which a reply to this message is expected.
         *
         * @param replyTo destination to reply to
         * @return this builder
         */
        OutgoingJmsMessageBuilder<PAYLOAD> replyTo(Destination replyTo) {
            this.replyTo = replyTo;
            return this;
        }

        /**
         * JMS Message type.
         *
         * @param type the message type
         * @return this builder
         */
        OutgoingJmsMessageBuilder<PAYLOAD> type(String type) {
            this.type = type;
            return this;
        }

        @Override
        public Message<PAYLOAD> build() {
            message.postProcess(m -> {
                // set jms properties
                for (Map.Entry<String, Object> e : properties.entrySet()) {
                    m.setObjectProperty(e.getKey(), e.getValue());
                }
                if (null != this.correlationId) m.setJMSCorrelationID(this.correlationId);
                if (null != this.replyTo) m.setJMSReplyTo(this.replyTo);
                if (null != this.type) m.setJMSType(this.type);
            });
            if (Objects.isNull(message.getPayload())) {
                throw new IllegalStateException("Message is missing payload.");
            }
            return message;
        }
    }

    /**
     * Mapper for creating {@link javax.jms.Message}.
     *
     * @param <PAYLOAD> The payload.
     */
    @FunctionalInterface
    interface CustomMapper<PAYLOAD> extends BiFunction<PAYLOAD, Session, javax.jms.Message> {

        @Override
        default javax.jms.Message apply(PAYLOAD p, Session session) {
            try {
                return applyThrows(p, session);
            } catch (JMSException e) {
                throw new MessagingException("Error when invoking custom mapper.", e);
            }
        }

        javax.jms.Message applyThrows(PAYLOAD p, Session session) throws JMSException;
    }

}
