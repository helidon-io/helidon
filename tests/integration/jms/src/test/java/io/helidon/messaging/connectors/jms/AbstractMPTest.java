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

package io.helidon.messaging.connectors.jms;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class AbstractMPTest {

    static Session session;

    @BeforeAll
    static void beforeAll() throws Exception {
        ActiveMQConnectionFactory connectionFactory =
                new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        Connection connection = connectionFactory.createConnection();
        session = connection.createSession(false, AcknowledgeMode.AUTO_ACKNOWLEDGE.getAckMode());
    }


    @AfterAll
    static void tearDown() throws Exception {
        session.close();
    }

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
            assertTrue(done, String.format("Timeout waiting for results.\nExpected: %s \nBut was: %s",
                    expected.toString(), consumingBean.consumed().toString()));
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
                m = consumer.receive(50L);
                if (m == null) {
                    break;
                }
                result.add(m);
            }
            return result.stream();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }
}
