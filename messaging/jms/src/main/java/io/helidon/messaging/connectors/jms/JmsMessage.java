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


import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.TextMessage;

import io.helidon.messaging.MessagingException;

import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Message representing JMS message together with all the metadata.
 *
 * @param <T> Type of the payload.
 */
public interface JmsMessage<T> extends Message<T> {

    /**
     * Get JMS property by name.
     *
     * @param name the name of the JMS property
     * @param <P>  expected type of the property
     * @return property value with the specified name or null
     */
    <P> P getJmsProperty(String name);

    /**
     * Set JMS of given name.
     *
     * @param name  the name of the JMS property
     * @param value boolean value to stored as JMS property
     */
    void setJmsProperty(String name, boolean value);

    /**
     * Set JMS of given name.
     *
     * @param name  the name of the JMS property
     * @param value byte value to stored as JMS property
     */
    void setJmsProperty(String name, byte value);

    /**
     * Set JMS of given name.
     *
     * @param name  the name of the JMS property
     * @param value short value to stored as JMS property
     */
    void setJmsProperty(String name, short value);

    /**
     * Set JMS of given name.
     *
     * @param name  the name of the JMS property
     * @param value int value to stored as JMS property
     */
    void setJmsProperty(String name, int value);

    /**
     * Set JMS of given name.
     *
     * @param name  the name of the JMS property
     * @param value long value to stored as JMS property
     */
    void setJmsProperty(String name, long value);

    /**
     * Set JMS of given name.
     *
     * @param name  the name of the JMS property
     * @param value float value to stored as JMS property
     */
    void setJmsProperty(String name, float value);

    /**
     * Set JMS of given name.
     *
     * @param name  the name of the JMS property
     * @param value double value to stored as JMS property
     */
    void setJmsProperty(String name, double value);

    /**
     * Set JMS of given name.
     *
     * @param name  the name of the JMS property
     * @param value string value to be stored as JMS property
     */
    void setJmsProperty(String name, String value);

    /**
     * Check whether JMS property of given name is present.
     *
     * @param name the name of the JMS property
     * @return true if property exists
     */
    boolean hasJmsProperty(String name);

    /**
     * Original JMS message received.
     *
     * @return original JMS message received
     */
    Optional<javax.jms.Message> getJmsMessage();

    /**
     * Metadata about the JMS session.
     *
     * @return JMS session
     */
    Optional<Session> getJmsSession();

    /**
     * Get client's connection to its JMS provider.
     *
     * @return JMS connection
     */
    Optional<Connection> getJmsConnection();

    /**
     * Get JMS connection factory.
     *
     * @return JMS connection factory
     */
    Optional<ConnectionFactory> getJmsConnectionFactory();

    /**
     * Check if message has been acknowledged yet.
     *
     * @return true if message has been acknowledged
     */
    boolean isAck();

    /**
     * Create a message with the given payload.
     *
     * @param msg          JMS message to be wrapped
     * @param executor     Executor used for invoking ack
     * @param sessionEntry metadata about the JMS session
     * @return A message with the given payload, and an ack function
     */
    static JmsMessage<?> of(javax.jms.Message msg, Executor executor, SessionMetadata sessionEntry) {
        if (msg instanceof TextMessage) {
            return new JmsTextMessage((TextMessage) msg, executor, sessionEntry);
        } else if (msg instanceof BytesMessage) {
            return new JmsBytesMessage((BytesMessage) msg, executor, sessionEntry);
        } else {
            IncomingProperties properties = new IncomingProperties(msg);
            return new AbstractJmsMessage<javax.jms.Message>(executor, sessionEntry) {

                @Override
                JmsProperties properties() {
                    return properties;
                }

                @Override
                public Optional<javax.jms.Message> getJmsMessage() {
                    return Optional.of(msg);
                }

                @Override
                public javax.jms.Message getPayload() {
                    return msg;
                }
            };
        }
    }

    /**
     * Create a JmsMessage with the given payload and ack function.
     *
     * @param payload The payload.
     * @param ack     The ack function, this will be invoked when the returned messages {@link #ack()} method is invoked.
     * @param <T>     the type of payload
     * @return A message with the given payload and ack function.
     */
    static <T> JmsMessage<T> of(T payload, Supplier<CompletionStage<Void>> ack) {
        return new OutgoingJmsMessage<>(payload, ack);
    }

    /**
     * Create a JmsMessage with the given payload.
     *
     * @param payload The payload.
     * @param <T>     The type of payload
     * @return A message with the given payload, and a no-op ack function.
     */
    static <T> JmsMessage<T> of(T payload) {
        return new OutgoingJmsMessage<>(payload, () -> CompletableFuture.completedStage(null));
    }

    /**
     * Create a JMSMessage with custom mapper to {@link javax.jms.Message}.
     *
     * @param payload The payload.
     * @param mapper  Custom mapper to {@link javax.jms.Message}
     * @param ack     The ack function, this will be invoked when the returned messages {@link #ack()} method is invoked.
     * @param <T>     The type of payload
     * @return A message with the given payload and ack function.
     */
    static <T> JmsMessage<T> of(T payload,
                                CustomMapper<T> mapper,
                                Supplier<CompletionStage<Void>> ack) {
        return new OutgoingJmsMessage<>(payload, mapper, ack);
    }

    /**
     * Create a JMSMessage with custom mapper to {@link javax.jms.Message}.
     *
     * @param payload The payload.
     * @param mapper  Custom mapper to {@link javax.jms.Message}
     * @param <T>     The type of payload
     * @return A message with the given payload and ack function.
     */
    static <T> JmsMessage<T> of(T payload,
                                CustomMapper<T> mapper) {
        return new OutgoingJmsMessage<>(payload, mapper, () -> CompletableFuture.completedFuture(null));
    }

    /**
     * Mapper for creating {@link javax.jms.Message}.
     *
     * @param <P> The payload.
     */
    @FunctionalInterface
    interface CustomMapper<P> extends BiFunction<P, Session, javax.jms.Message> {

        @Override
        default javax.jms.Message apply(P p, Session session) {
            try {
                return applyThrows(p, session);
            } catch (JMSException e) {
                throw new MessagingException("Error when invoking custom mapper.", e);
            }
        }

        javax.jms.Message applyThrows(P p, Session session) throws JMSException;
    }

}
