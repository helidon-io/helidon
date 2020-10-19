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

package io.helidon.messaging.connectors.aq;

import java.sql.Connection;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import io.helidon.messaging.MessagingException;
import io.helidon.messaging.connectors.jms.JmsMessage;
import io.helidon.messaging.connectors.jms.SessionMetadata;

import oracle.jms.AQjmsMessage;
import oracle.jms.AQjmsSession;

public class AqMessageImpl<T> implements AqMessage<T> {

    private final JmsMessage<?> jmsMessage;
    private final AQjmsMessage msg;
    private final AQjmsSession session;

    AqMessageImpl(AQjmsMessage msg, Executor executor, SessionMetadata sessionMetadata) {
        this.jmsMessage = JmsMessage.of(msg, executor, sessionMetadata);
        this.msg = msg;
        this.session = (AQjmsSession) sessionMetadata.session();
    }

    @Override
    public Connection getDbConnection() {
        try {
            return session.getDBConnection();
        } catch (JMSException e) {
            throw new MessagingException("Error when obtaining db connection.", e);
        }
    }

    @Override
    public <P> P getJmsProperty(String name) {
        return jmsMessage.getJmsProperty(name);
    }

    @Override
    public void setJmsProperty(String name, boolean value) {
        jmsMessage.setJmsProperty(name, value);
    }

    @Override
    public void setJmsProperty(String name, byte value) {
        jmsMessage.setJmsProperty(name, value);
    }

    @Override
    public void setJmsProperty(String name, short value) {
        jmsMessage.setJmsProperty(name, value);
    }

    @Override
    public void setJmsProperty(String name, int value) {
        jmsMessage.setJmsProperty(name, value);
    }

    @Override
    public void setJmsProperty(String name, long value) {
        jmsMessage.setJmsProperty(name, value);
    }

    @Override
    public void setJmsProperty(String name, float value) {
        jmsMessage.setJmsProperty(name, value);
    }

    @Override
    public void setJmsProperty(String name, double value) {
        jmsMessage.setJmsProperty(name, value);
    }

    @Override
    public void setJmsProperty(String name, String value) {
        jmsMessage.setJmsProperty(name, value);
    }

    @Override
    public boolean hasJmsProperty(String name) {
        return jmsMessage.hasJmsProperty(name);
    }

    @Override
    public Optional<Message> getJmsMessage() {
        return Optional.of(msg);
    }

    @Override
    public Optional<Session> getJmsSession() {
        return jmsMessage.getJmsSession();
    }

    @Override
    public Optional<javax.jms.Connection> getJmsConnection() {
        return jmsMessage.getJmsConnection();
    }

    @Override
    public Optional<ConnectionFactory> getJmsConnectionFactory() {
        return jmsMessage.getJmsConnectionFactory();
    }

    @Override
    public boolean isAck() {
        return jmsMessage.isAck();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getPayload() {
        return (T) jmsMessage.getPayload();
    }
}
