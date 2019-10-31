/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.messaging;

import io.reactivex.Flowable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.reactivestreams.Publisher;

import javax.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

@ApplicationScoped
public class KafkaConsumingTestBean {

    public static int EXPECTED_TOPIC_RECORD_NUMBER = 3;
    //Two methods -> two consumers of same topic
    public static CountDownLatch testChannelLatch = new CountDownLatch(EXPECTED_TOPIC_RECORD_NUMBER * 2);
    public static CountDownLatch selfCallLatch = new CountDownLatch(2);

    @Incoming("test-channel")
    public void receiveMethod1(Message<ConsumerRecord<Long, String>> msg) {
        testChannelLatch.countDown();
        System.out.println("Received message ->" + msg.getPayload().value());
    }

    @Incoming("test-channel")
    public void receiveMethod2(Message<ConsumerRecord<Long, String>> msg) {
        testChannelLatch.countDown();
        System.out.println("Received message in second consumer ->" + msg.getPayload().value());
    }

    @Outgoing("self-call-channel")
    public Publisher<String> produceMessage() {
        return Flowable.fromIterable(Arrays.asList("test1", "test2"));
    }

    @Incoming("self-call-channel")
    public void receiveFromSelfMethod(String msg) {
        selfCallLatch.countDown();
        System.out.println("Received message from myself ->" + msg);
    }
}
