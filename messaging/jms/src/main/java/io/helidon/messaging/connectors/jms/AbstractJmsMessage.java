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
 */

package io.helidon.messaging.connectors.jms;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.logging.Level;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

abstract class AbstractJmsMessage<T> implements JmsMessage<T> {

    private Executor executor;
    private SessionMetadata sharedSessionEntry;
    private volatile boolean acked = false;

    protected AbstractJmsMessage() {
    }

    protected AbstractJmsMessage(Executor executor, SessionMetadata sharedSessionEntry) {
        this.sharedSessionEntry = sharedSessionEntry;
        this.executor = executor;
    }

    abstract JmsProperties properties();

    @Override
    public <P> P getJmsProperty(final String name) {
        return properties().property(name);
    }

    @Override
    public void setJmsProperty(final String name, final boolean value) {
        properties().property(name, value);
    }

    @Override
    public void setJmsProperty(final String name, final byte value) {
        properties().property(name, value);
    }

    @Override
    public void setJmsProperty(final String name, final short value) {
        properties().property(name, value);
    }

    @Override
    public void setJmsProperty(final String name, final int value) {
        properties().property(name, value);
    }

    @Override
    public void setJmsProperty(final String name, final long value) {
        properties().property(name, value);
    }

    @Override
    public void setJmsProperty(final String name, final float value) {
        properties().property(name, value);
    }

    @Override
    public void setJmsProperty(final String name, final double value) {
        properties().property(name, value);
    }

    @Override
    public void setJmsProperty(final String name, final String value) {
        properties().property(name, value);
    }

    @Override
    public boolean hasJmsProperty(final String name) {
        return properties().propertyExists(name);
    }

    @Override
    public Optional<Session> getJmsSession() {
        return Optional.ofNullable(sharedSessionEntry).map(SessionMetadata::getSession);
    }

    @Override
    public Optional<Connection> getJmsConnection() {
        return Optional.ofNullable(sharedSessionEntry).map(SessionMetadata::getConnection);
    }

    @Override
    public Optional<ConnectionFactory> getJmsConnectionFactory() {
        return Optional.ofNullable(sharedSessionEntry).map(SessionMetadata::getConnectionFactory);
    }

    @Override
    public boolean isAck() {
        return acked;
    }

    @Override
    public CompletionStage<Void> ack() {
        Runnable ackRunnable = () -> {
            try {
                Optional<Message> jmsMessage = this.getJmsMessage();
                if (jmsMessage.isPresent()) {
                    jmsMessage.get().acknowledge();
                }
                acked = true;
            } catch (JMSException e) {
                LOGGER.log(Level.SEVERE, e, () -> "Error during acknowledgement of JMS message");
            }
        };
        return Optional.ofNullable(executor)
                .map(e -> CompletableFuture.runAsync(ackRunnable, e))
                .orElseGet(() -> {
                    ackRunnable.run();
                    return CompletableFuture.completedFuture(null);
                });
    }

}
