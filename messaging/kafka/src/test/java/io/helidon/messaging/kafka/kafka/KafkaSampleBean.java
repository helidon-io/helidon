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

package io.helidon.messaging.kafka.kafka;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

import javax.enterprise.context.ApplicationScoped;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
public class KafkaSampleBean {

    private CountDownLatch testChannelLatch;
    private final List<String> consumed = Collections.synchronizedList(new ArrayList<>());

    @Incoming("test-channel-1")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public CompletionStage<String> channel1(Message<ConsumerRecord<Long, String>> msg) {
        consumed.add(msg.getPayload().value());
        msg.ack();
        testChannelLatch.countDown();
        return CompletableFuture.completedFuture(null);
    }

    @Incoming("test-channel-2")
    @Outgoing("test-channel-3")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public Message<String> channel2ToChannel3(Message<ConsumerRecord<Long, String>> msg) {
        msg.ack();
        return Message.of("Processed" + msg.getPayload().value());
    }

    public List<String> getConsumed() {
        return consumed;
    }

    public void setCountDownLatch(CountDownLatch testChannelLatch) {
        this.testChannelLatch = testChannelLatch;
    }

}
