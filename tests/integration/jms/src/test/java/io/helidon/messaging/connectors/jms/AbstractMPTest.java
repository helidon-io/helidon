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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.TextMessage;
import org.hamcrest.Matchers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public abstract class AbstractMPTest extends AbstractJmsTest {


    protected void produceAndCheck(final AbstractSampleBean consumingBean,
                                   final List<String> testData,
                                   final String topic,
                                   final List<String> expected) {
        produceAndCheck(consumingBean, testData, topic, expected, message -> {
        });
    }

    protected void produceAndCheck(final AbstractSampleBean consumingBean,
                                   final List<String> testData,
                                   final String topic,
                                   final List<String> expected, Consumer<TextMessage> messageConsumer) {
        consumingBean.expectedRequests(expected.size());
        produce(topic, testData, messageConsumer);
        if (expected.size() > 0) {
            // Wait till records are delivered
            boolean done = consumingBean.await();
            assertThat(String.format("Timeout waiting for results.\nExpected: %s \nBut was: %s",
                    expected.toString(), consumingBean.consumed().toString()), done, is(true));
        }
        if (!expected.isEmpty()) {
            assertThat(consumingBean.consumed(), Matchers.containsInAnyOrder(expected.toArray()));
        }
    }

    void produce(String topic, final List<String> testData, Consumer<TextMessage> messageConsumer) {
        try {
            Destination dest = topic.startsWith("topic")
                    ? session.createTopic(topic)
                    : session.createQueue(topic);
            MessageProducer producer = session.createProducer(dest);
            for (String s : testData) {
                TextMessage textMessage = session.createTextMessage(s);
                messageConsumer.accept(textMessage);
                producer.send(textMessage);
            }
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    Stream<Message> consumeAllCurrent(String topic) {
        try {
            Destination dest = topic.startsWith("topic")
                    ? session.createTopic(topic)
                    : session.createQueue(topic);
            MessageConsumer consumer = session.createConsumer(dest);
            Message m;
            List<Message> result = new ArrayList<>();
            for (; ; ) {
                m = consumer.receive(500L);
                if (m == null) {
                    break;
                }
                result.add(m);
            }
            consumer.close();
            return result.stream();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }
}
