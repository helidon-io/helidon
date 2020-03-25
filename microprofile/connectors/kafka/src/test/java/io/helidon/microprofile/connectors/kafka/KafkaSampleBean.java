/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.connectors.kafka;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
// Must be public
public class KafkaSampleBean extends AbstractSampleBean {

    private static final Logger LOGGER = Logger.getLogger(KafkaSampleBean.class.getName());

    @Incoming("test-channel-1")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public CompletionStage<String> channel1(Message<ConsumerRecord<Long, String>> msg) throws InterruptedException, ExecutionException {
        LOGGER.fine(String.format("Recived %s", msg.getPayload().value()));
        consumed().add(msg.getPayload().value());
        msg.ack().toCompletableFuture().get();
        countDown("channel1()");
        return CompletableFuture.completedFuture(null);
    }

    @Incoming("test-channel-2")
    @Outgoing("test-channel-3")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public Message<String> channel2ToChannel3(Message<ConsumerRecord<Long, String>> msg) throws InterruptedException, ExecutionException {
        msg.ack().toCompletableFuture().get();
        return Message.of("Processed" + msg.getPayload().value());
    }
    
    @Incoming("test-channel-error")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public CompletionStage<String> error(Message<ConsumerRecord<Long, String>> msg) throws InterruptedException, ExecutionException {
        try {
            LOGGER.fine(String.format("Received possible error %s", msg.getPayload().value()));
            consumed().add(Integer.toString(Integer.parseInt(msg.getPayload().value())));
        } finally {
            msg.ack().toCompletableFuture().get();
            countDown("error()");
        }
        return CompletableFuture.completedFuture(null);
    }

}
