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

package io.helidon.messaging.mp.example;

import java.util.concurrent.SubmissionPublisher;

import javax.enterprise.context.ApplicationScoped;
import javax.jms.JMSException;

import io.helidon.common.reactive.Multi;
import io.helidon.messaging.connectors.jms.JmsMessage;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;

/**
 * Bean for message processing.
 */
@ApplicationScoped
public class MsgProcessingBean {

    private final SubmissionPublisher<String> emitter = new SubmissionPublisher<>();
    private final SubmissionPublisher<String> broadCaster = new SubmissionPublisher<>();

//    @Produces
//    @ApplicationScoped
//    public ConnectionFactory connectionFactory() {
//        return new ActiveMQConnectionFactory("tcp://127.0.0.1:61616");
//    }

    /**
     * Create a publisher for the emitter.
     *
     * @return A Publisher from the emitter
     */
    @Outgoing("multiplyVariants")
    public Publisher<String> preparePublisher() {
        // Create new publisher for emitting to by this::process
        return ReactiveStreams
                .fromPublisher(FlowAdapters.toPublisher(Multi.create(emitter)))
                .buildRs();
    }

    /**
     * Returns a builder for a processor that maps a string into three variants.
     *
     * @return ProcessorBuilder
     */
    @Incoming("multiplyVariants")
    @Outgoing("toJms")
    public ProcessorBuilder<String, Message<String>> multiply() {
        // Multiply to 3 variants of same message
        return ReactiveStreams.<String>builder()
                .flatMap(o ->
                        ReactiveStreams.of(
                                // upper case variant
                                o.toUpperCase(),
                                // repeat twice variant
                                o.repeat(2),
                                // reverse chars 'tnairav'
                                new StringBuilder(o).reverse().toString())
                ).map(Message::of);
    }

    /**
     * Broadcasts an event.
     *
     * @param msg Message to broadcast
     */
    @Incoming("fromJms")
    public void broadcast(JmsMessage<String> msg) {
        // Broadcast to all subscribers
        try {
            broadCaster.submit("Msg " + msg.getPayload() + " with client id  "
                    + msg.getSessionMetadata().getConnection().getClientID());
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    /**
     * Same JMS session, different connector.
     *
     * @param msg Message to broadcast
     */
    @Incoming("fromJmsSameSession")
    public void sameSession(JmsMessage<String> msg) {
        // Broadcast to all subscribers
        try {
            broadCaster.submit("Msg " + msg.getPayload() + " with client id  "
                    + msg.getSessionMetadata().getConnection().getClientID());
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    /**
     * Subscribe new Multi to broadcasting publisher.
     *
     * @return new Multi subscribed to broadcaster
     */
    public Multi<String> subscribeMulti() {
        return Multi.create(broadCaster);
    }

    /**
     * Emit a message.
     *
     * @param msg message to emit
     */
    public void process(final String msg) {
        emitter.submit(msg);
    }
}
