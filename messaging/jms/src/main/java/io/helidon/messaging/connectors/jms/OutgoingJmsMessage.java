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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.jms.JMSException;
import javax.jms.Session;

import org.eclipse.microprofile.reactive.messaging.Message;

class OutgoingJmsMessage<PAYLOAD> implements Message<PAYLOAD> {

    private final PAYLOAD payload;
    private JmsMessage.CustomMapper<PAYLOAD> mapper = null;
    private Supplier<CompletionStage<Void>> ack = () -> CompletableFuture.completedFuture(null);
    private volatile boolean acked = false;
    private PostProcessor postProcessor;

    OutgoingJmsMessage(PAYLOAD payload) {
        super();
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
        this.postProcessor = processor;
    }

    javax.jms.Message toJmsMessage(Session session, MessageMappers.MessageMapper defaultMapper) throws JMSException {
        javax.jms.Message jmsMessage;
        if (mapper != null) {
            jmsMessage = mapper.apply(getPayload(), session);
        } else {
            jmsMessage = defaultMapper.apply(session, this);
        }
        postProcessor.accept(jmsMessage);
        return jmsMessage;
    }

    @FunctionalInterface
    interface PostProcessor {
        void accept(javax.jms.Message m) throws JMSException;
    }
}
