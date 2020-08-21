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

package io.helidon.examples.messaging.mp;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.SubmissionPublisher;

import javax.enterprise.context.ApplicationScoped;
import javax.jms.JMSException;
import javax.jms.MapMessage;

import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Multi;
import io.helidon.messaging.connectors.aq.AqMessage;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;

/**
 * Bean for message processing.
 */
@ApplicationScoped
public class MsgProcessingBean {

    private final BufferedEmittingPublisher<String> emitter = BufferedEmittingPublisher.create();
    private final SubmissionPublisher<String> broadCaster = new SubmissionPublisher<>();

    /**
     * Create a publisher for the emitter.
     *
     * @return A Publisher from the emitter
     */
    @Outgoing("to-queue-1")
    public Publisher<String> toFirstQueue() {
        // Create new publisher for emitting to by this::process
        return ReactiveStreams
                .fromPublisher(FlowAdapters.toPublisher(emitter))
                .buildRs();
    }

    /**
     * Example of resending message from one queue to another and logging the payload to DB in the process.
     *
     * @param msg received message
     * @return message to be sent
     */
    @Incoming("from-queue-1")
    @Outgoing("to-queue-2")
    //Leave commit by ack to outgoing connector
    @Acknowledgment(Acknowledgment.Strategy.NONE)
    public CompletionStage<AqMessage<String>> betweenQueues(AqMessage<String> msg) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PreparedStatement statement = msg.getDBConnection()
                        .prepareStatement("INSERT INTO frank.message_log (message) VALUES (?)");
                statement.setString(1, msg.getPayload());
                statement.executeUpdate();
            } catch (SQLException e) {
                //Gets caught by messaging engine and translated to onError signal
                throw new RuntimeException("Error when saving message to log table.", e);
            }
            return msg;
        });
    }

    /**
     * Broadcasts an event.
     *
     * @param msg Message to broadcast
     */
    @Incoming("from-queue-2")
    public void fromSecondQueue(AqMessage<String> msg) {
        // Broadcast to all subscribers
        broadCaster.submit(msg.getPayload());
    }

    /**
     * Example of receiving a byte message.
     *
     * @param bytes received byte array
     */
    @Incoming("from-byte-queue")
    public void fromByteQueue(byte[] bytes) {
        broadCaster.submit(new String(bytes));
    }

    /**
     * Example of receiving a map message.
     *
     * @param msg received JMS MapMessage
     * @throws JMSException when error arises during work with JMS message
     */
    @Incoming("from-map-queue")
    public void fromMapQueue(MapMessage msg) throws JMSException {
        String head = msg.getString("head");
        byte[] body = msg.getBytes("body");
        String tail = msg.getString("tail");
        broadCaster.submit(String.join(" ", List.of(head, new String(body), tail)));
    }

    Multi<String> subscribeMulti() {
        return Multi.create(broadCaster).log();
    }

    void process(final String msg) {
        emitter.emit(msg);
    }
}
