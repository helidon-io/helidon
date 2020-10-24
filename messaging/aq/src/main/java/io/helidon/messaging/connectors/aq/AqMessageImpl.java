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
import java.util.Set;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import io.helidon.messaging.MessagingException;
import io.helidon.messaging.connectors.jms.JmsMessage;
import io.helidon.messaging.connectors.jms.SessionMetadata;

import oracle.jms.AQjmsSession;

class AqMessageImpl<T> implements AqMessage<T> {

    private final JmsMessage<?> jmsMessage;
    private final AQjmsSession session;

    AqMessageImpl(JmsMessage<?> msg, SessionMetadata sessionMetadata) {
        this.jmsMessage = msg;
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
    public <P> P getProperty(String name) {
        return jmsMessage.getProperty(name);
    }

    @Override
    public boolean hasProperty(final String name) {
        return jmsMessage.hasProperty(name);
    }

    @Override
    public Set<String> getPropertyNames() {
        return jmsMessage.getPropertyNames();
    }

    @Override
    public Message getJmsMessage() {
        return this.jmsMessage.getJmsMessage();
    }

    @Override
    public Session getJmsSession() {
        return jmsMessage.getJmsSession();
    }

    @Override
    public javax.jms.Connection getJmsConnection() {
        return jmsMessage.getJmsConnection();
    }

    @Override
    public ConnectionFactory getJmsConnectionFactory() {
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
