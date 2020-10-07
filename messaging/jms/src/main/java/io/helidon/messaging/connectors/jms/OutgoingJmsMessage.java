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
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

class OutgoingJmsMessage<T> extends AbstractJmsMessage<T> {

    private final T payload;
    private CustomMapper<T> mapper = null;
    private final Supplier<CompletionStage<Void>> ack;
    private volatile boolean acked = false;
    private final OutgoingProperties properties = new OutgoingProperties();

    OutgoingJmsMessage(T payload, Supplier<CompletionStage<Void>> ack) {
        super();
        this.payload = payload;
        this.ack = ack;
    }

    OutgoingJmsMessage(T payload, CustomMapper<T> mapper, Supplier<CompletionStage<Void>> ack) {
        super();
        this.payload = payload;
        this.mapper = mapper;
        this.ack = ack;
    }

    @Override
    JmsProperties properties() {
        return properties;
    }

    @Override
    public Optional<Message> getJmsMessage() {
        return Optional.empty();
    }

    @Override
    public Optional<Session> getJmsSession() {
        return Optional.empty();
    }

    @Override
    public Optional<Connection> getJmsConnection() {
        return Optional.empty();
    }

    @Override
    public Optional<ConnectionFactory> getJmsConnectionFactory() {
        return Optional.empty();
    }

    @Override
    public T getPayload() {
        return this.payload;
    }

    @Override
    public boolean isAck() {
        return acked;
    }

    @Override
    public CompletionStage<Void> ack() {
        return this.ack.get().thenRun(() -> acked = true);
    }

    Message toJmsMessage(Session session, MessageMappers.MessageMapper defaultMapper) throws JMSException {
        javax.jms.Message jmsMessage;
        if (mapper != null) {
            jmsMessage = mapper.apply(getPayload(), session);
        } else {
            jmsMessage = defaultMapper.apply(session, this);
        }
        properties.writeToMessage(jmsMessage);
        return jmsMessage;
    }
}
