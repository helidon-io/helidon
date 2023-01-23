/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import io.helidon.messaging.MessagingException;
import io.helidon.messaging.NackHandler;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Session;

abstract class AbstractJmsMessage<T> implements JmsMessage<T> {

    private static final System.Logger LOGGER = System.getLogger(AbstractJmsMessage.class.getName());

    private Executor executor;
    private SessionMetadata sharedSessionEntry;
    private volatile boolean acked = false;
    private final NackHandler nackHandler;

    protected AbstractJmsMessage(NackHandler nackHandler,
                                 Executor executor,
                                 SessionMetadata sharedSessionEntry) {
        this.nackHandler = nackHandler;
        this.sharedSessionEntry = sharedSessionEntry;
        this.executor = executor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <P> P getProperty(String name) {
        try {
            return (P) getJmsMessage().getObjectProperty(name);
        } catch (JMSException | ClassCastException e) {
            throw new MessagingException("Error when getting property " + name);
        }
    }

    @Override
    public boolean hasProperty(String name) {
        try {
            return getJmsMessage().propertyExists(name);
        } catch (JMSException e) {
            throw new MessagingException("Error when checking existence of property " + name);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getPropertyNames() {
        try {
            return new HashSet<String>(Collections.list(getJmsMessage().getPropertyNames()));
        } catch (JMSException e) {
            throw new MessagingException("Error when getting property names ");
        }
    }

    @Override
    public Session getJmsSession() {
        return sharedSessionEntry.session();
    }

    @Override
    public Connection getJmsConnection() {
        return sharedSessionEntry.connection();
    }

    @Override
    public ConnectionFactory getJmsConnectionFactory() {
        return sharedSessionEntry.connectionFactory();
    }

    @Override
    public boolean isAck() {
        return acked;
    }

    @Override
    public CompletionStage<Void> ack() {
        Runnable ackRunnable = () -> {
            try {
                getJmsMessage().acknowledge();
                acked = true;
            } catch (JMSException e) {
                LOGGER.log(Level.ERROR, () -> "Error during acknowledgement of JMS message", e);
            }
        };
        return Optional.ofNullable(executor)
                .map(e -> CompletableFuture.runAsync(ackRunnable, e))
                .orElseGet(() -> {
                    ackRunnable.run();
                    return CompletableFuture.completedFuture(null);
                });
    }

    @Override
    public Function<Throwable, CompletionStage<Void>> getNack() {
        return this.nackHandler != null ? this.nackHandler.getNack(this) : reason -> CompletableFuture.completedFuture(null);
    }
}
