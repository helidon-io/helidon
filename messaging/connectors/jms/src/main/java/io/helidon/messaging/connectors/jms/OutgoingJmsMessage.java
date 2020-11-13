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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.JMSException;
import javax.jms.Session;

import io.helidon.messaging.MessagingException;

import org.eclipse.microprofile.reactive.messaging.Message;

class OutgoingJmsMessage<PAYLOAD> implements Message<PAYLOAD> {

    private static final Logger LOGGER = Logger.getLogger(OutgoingJmsMessage.class.getName());

    private PAYLOAD payload;
    private JmsMessage.CustomMapper<PAYLOAD> mapper = null;
    private Supplier<CompletionStage<Void>> ack = () -> CompletableFuture.completedFuture(null);
    private volatile boolean acked = false;
    private List<PostProcessor> postProcessors = new ArrayList<>(2);

    OutgoingJmsMessage(PAYLOAD payload) {
        this.payload = payload;
    }

    OutgoingJmsMessage() {
    }

    void setPayload(PAYLOAD payload) {
        this.payload = payload;
    }

    void onAck(final Supplier<CompletionStage<Void>> ack) {
        this.ack = ack;
    }

    public void mapper(JmsMessage.CustomMapper<PAYLOAD> mapper) {
        this.mapper = mapper;
    }

    @Override
    public PAYLOAD getPayload() {
        return this.payload;
    }

    public boolean isAck() {
        return acked;
    }

    @Override
    public CompletionStage<Void> ack() {
        return this.ack.get().thenRun(() -> acked = true);
    }

    void postProcess(PostProcessor processor) {
        this.postProcessors.add(processor);
    }

    javax.jms.Message toJmsMessage(Session session, MessageMappers.MessageMapper defaultMapper) throws JMSException {
        javax.jms.Message jmsMessage;
        if (mapper != null) {
            jmsMessage = mapper.apply(getPayload(), session);
        } else {
            jmsMessage = defaultMapper.apply(session, this);
        }
        for (PostProcessor p : postProcessors) {
            p.accept(jmsMessage);
        }
        return jmsMessage;
    }

    @SuppressWarnings("unchecked")
    static <PAYLOAD> OutgoingJmsMessage<PAYLOAD> fromJmsMessage(javax.jms.Message jmsMessage) throws JMSException {
        OutgoingJmsMessage<PAYLOAD> msg = new OutgoingJmsMessage<>();
        msg.postProcess(m -> {
            Enumeration<String> e = jmsMessage.getPropertyNames();
            while (e.hasMoreElements()) {
                String key = e.nextElement();
                m.setObjectProperty(key, jmsMessage.getObjectProperty(key));
            }
            //MessageId and timestamp is deliberately omitted
            getAndSet("correlationId", jmsMessage::getJMSCorrelationID, m::setJMSCorrelationID);
            getAndSet("deliveryMode", jmsMessage::getJMSDeliveryMode, m::setJMSDeliveryMode);
            getAndSet("deliveryTime", jmsMessage::getJMSDeliveryTime, m::setJMSDeliveryTime);
            getAndSet("destination", jmsMessage::getJMSDestination, m::setJMSDestination);
            getAndSet("expiration", jmsMessage::getJMSExpiration, m::setJMSExpiration);
            getAndSet("priority", jmsMessage::getJMSPriority, m::setJMSPriority);
            getAndSet("redelivered", jmsMessage::getJMSRedelivered, m::setJMSRedelivered);
            getAndSet("replyTo", jmsMessage::getJMSReplyTo, m::setJMSReplyTo);
            getAndSet("type", jmsMessage::getJMSType, m::setJMSType);
        });
        msg.onAck(() -> {
            try {
                jmsMessage.acknowledge();
                return CompletableFuture.completedFuture(null);
            } catch (IllegalStateException e) {
                // deliberately noop, original's jms session is closed
                return CompletableFuture.completedFuture(null);
            } catch (JMSException e) {
                throw new MessagingException("Error when acking original javax.jms.Message");
            }
        });
        return msg;
    }

    @FunctionalInterface
    interface PostProcessor {
        void accept(javax.jms.Message m) throws JMSException;
    }

    @FunctionalInterface
    interface JmsConsumer<T> {
        void accept(T m) throws Throwable;
    }

    @FunctionalInterface
    interface JmsProducer<T> {
        T accept() throws Throwable;
    }

    static <T> void getAndSet(String propName, JmsProducer<T> supplier, JmsConsumer<T> consumer) {
        T prop;
        try {
            prop = supplier.accept();
        } catch (Throwable e) {
            LOGGER.log(Level.FINE, e, () -> "Unable to retrieve JMS " + propName);
            return;
        }

        try {
            consumer.accept(prop);
        } catch (Throwable e) {
            LOGGER.log(Level.FINE, e, () -> "Unable to set JMS " + propName);
        }
    }
}
