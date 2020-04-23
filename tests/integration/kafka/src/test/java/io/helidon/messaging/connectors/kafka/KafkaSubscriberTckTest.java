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

package io.helidon.messaging.connectors.kafka;

import java.util.Arrays;
import java.util.UUID;

import org.apache.kafka.clients.producer.Producer;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.mockito.Mockito;
import org.reactivestreams.Subscriber;
import org.reactivestreams.tck.SubscriberBlackboxVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.Test;

@Test
class KafkaSubscriberTckTest extends SubscriberBlackboxVerification<Message<String>> {

    protected KafkaSubscriberTckTest() {
        super(new TestEnvironment(1000));
    }

    @Override
    public Message<String> createElement(int element) {
        return Message.of(UUID.randomUUID().toString());
    }

    @Override
    public Subscriber<Message<String>> createSubscriber() {
        Producer<Object, String> producer = Mockito.mock(Producer.class);
        return KafkaSubscriber.<Object, String>builder().producerSupplier(() -> producer)
                .topics(Arrays.asList("topic")).backpressure(1).build();
    }

}
