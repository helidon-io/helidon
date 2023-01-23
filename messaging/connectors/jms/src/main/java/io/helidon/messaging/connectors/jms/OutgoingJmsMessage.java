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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import io.helidon.messaging.MessagingException;

import jakarta.jms.JMSException;
import jakarta.jms.Session;
import org.eclipse.microprofile.reactive.messaging.Message;

class OutgoingJmsMessage<PAYLOAD> implements Message<PAYLOAD> {

    private static final System.Logger LOGGER = System.getLogger(OutgoingJmsMessage.class.getName());

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

    jakarta.jms.Message toJmsMessage(Session session, MessageMapper defaultMapper) throws JMSException {
        jakarta.jms.Message jmsMessage;
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
    static <PAYLOAD> OutgoingJmsMessage<PAYLOAD> fromJmsMessage(jakarta.jms.Message jmsMessage) throws JMSException {
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
                throw new MessagingException("Error when acking original jakarta.jms.Message");
            }
        });
        return msg;
    }

    @FunctionalInterface
    interface PostProcessor {
        void accept(jakarta.jms.Message m) throws JMSException;
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
            LOGGER.log(Level.DEBUG, () -> "Unable to retrieve JMS " + propName, e);
            return;
        }

        try {
            consumer.accept(prop);
        } catch (Throwable e) {
            LOGGER.log(Level.DEBUG, () -> "Unable to set JMS " + propName, e);
        }
    }
}
