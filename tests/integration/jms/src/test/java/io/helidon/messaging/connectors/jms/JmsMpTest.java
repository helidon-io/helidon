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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import io.helidon.config.mp.MpConfigProviderResolver;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.messaging.MessagingCdiExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.jndi.ActiveMQInitialContextFactory;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JmsMpTest {

    private static final Logger LOGGER = Logger.getLogger(JmsMpTest.class.getName());

    private static final String TEST_TOPIC_1 = "topic-1";
    private static final String TEST_TOPIC_2 = "topic-2";
    private static final String TEST_TOPIC_3 = "topic-3";
    private static final String TEST_TOPIC_4 = "topic-4";
    private static final String TEST_TOPIC_5 = "topic-5";
    private static final String TEST_QUEUE_ACK = "queue-ack";
    private static final String TEST_TOPIC_ERROR = "topic-error";

    private static SeContainer cdiContainer;
    private static Session session;

    private static Map<String, String> cdiConfig() {
        Map<String, String> p = new HashMap<>();
        p.putAll(Map.of(
                "mp.messaging.connector.helidon-jms.jndi.provider.url", "vm://localhost?broker.persistent=false",
                "mp.messaging.connector.helidon-jms.jndi.factory.initial", ActiveMQInitialContextFactory.class.getName()
        ));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-1.connector", JmsConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-1.type", "topic",
                "mp.messaging.incoming.test-channel-1.destination", "dynamicTopics/" + TEST_TOPIC_1
        ));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-2.connector", JmsConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-2.type", "topic",
                "mp.messaging.incoming.test-channel-2.destination", "dynamicTopics/" + TEST_TOPIC_2
        ));
        p.putAll(Map.of(
                "mp.messaging.outgoing.test-channel-3.connector", JmsConnector.CONNECTOR_NAME,
                "mp.messaging.outgoing.test-channel-3.type", "topic",
                "mp.messaging.outgoing.test-channel-3.destination", "dynamicTopics/" + TEST_TOPIC_3

        ));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-7.connector", JmsConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-7.type", "topic",
                "mp.messaging.incoming.test-channel-7.destination", "dynamicTopics/" + TEST_TOPIC_3

        ));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-error.connector", JmsConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-error.type", "topic",
                "mp.messaging.incoming.test-channel-error.destination", "dynamicTopics/" + TEST_TOPIC_ERROR

        ));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-4.connector", JmsConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-4.type", "topic",
                "mp.messaging.incoming.test-channel-4.destination", "dynamicTopics/" + TEST_TOPIC_4
        ));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-5.connector", JmsConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-5.type", "topic",
                "mp.messaging.incoming.test-channel-5.destination", "dynamicTopics/" + TEST_TOPIC_5
        ));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-ack-1.connector", JmsConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-ack-1.acknowledge-mode", AcknowledgeMode.CLIENT_ACKNOWLEDGE.name(),
                "mp.messaging.incoming.test-channel-ack-1.type", "queue",
                "mp.messaging.incoming.test-channel-ack-1.destination", "dynamicQueues/" + TEST_QUEUE_ACK
        ));

        return p;
    }


    @BeforeAll
    static void beforeAll() throws Exception {
        cdiContainerUp();
        ActiveMQConnectionFactory connectionFactory =
                new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        Connection connection = connectionFactory.createConnection();
        session = connection.createSession(false, AcknowledgeMode.AUTO_ACKNOWLEDGE.getAckMode());
    }

    @AfterAll
    static void cdiContainerDown() throws Exception {
        cdiContainer.close();
        session.close();
        LOGGER.info("Container destroyed");
    }

    private static void cdiContainerUp() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(AbstractSampleBean.Channel1.class);
        classes.add(AbstractSampleBean.Channel4.class);
        classes.add(AbstractSampleBean.Channel5.class);
        classes.add(AbstractSampleBean.ChannelAck.class);
        classes.add(AbstractSampleBean.ChannelError.class);
        classes.add(AbstractSampleBean.ChannelProcessor.class);
        classes.add(MessagingCdiExtension.class);

        Map<String, String> p = new HashMap<>(cdiConfig());
        cdiContainer = startCdiContainer(p, classes);
        assertTrue(cdiContainer.isRunning());
        LOGGER.info("Container setup");
    }

    private void produceAndCheck(final AbstractSampleBean consumingBean,
                                 final List<String> testData,
                                 final String topic,
                                 final List<String> expected) {
        consumingBean.expectedRequests(expected.size());
        produce(topic, testData);
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

    void produce(String topic, final List<String> testData) {
        try {
            Destination dest = topic.startsWith("topic")
                    ? session.createTopic(topic)
                    : session.createQueue(topic);
            MessageProducer producer = session.createProducer(dest);
            for (String s : testData) {
                producer.send(session.createTextMessage(s));
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

    @Test
    void resendAckTest() {
        try {
            //Messages starting with NO_ACK is not acked by ChannelAck bean
            List<String> testData = List.of("0", "1", "2", "NO_ACK-1", "NO_ACK-2", "NO_ACK-3");
            AbstractSampleBean bean = cdiContainer.select(AbstractSampleBean.ChannelAck.class).get();
            produceAndCheck(bean, testData, TEST_QUEUE_ACK, testData);
            bean.restart();
            //restart container
            cdiContainer.close();
            cdiContainerUp();
            bean = cdiContainer.select(AbstractSampleBean.ChannelAck.class).get();
            //Send nothing just check if not acked messages are redelivered
            produceAndCheck(bean, List.of(), TEST_QUEUE_ACK, List.of("NO_ACK-1", "NO_ACK-2", "NO_ACK-3"));
        } finally {
            //cleanup not acked messages
            consumeAllCurrent(TEST_QUEUE_ACK).map(JmsMessage::of).forEach(JmsMessage::ack);
        }
    }

    @Test
    void incomingOk() {
        List<String> testData = IntStream.range(0, 99).mapToObj(i -> "test" + i).collect(Collectors.toList());
        AbstractSampleBean kafkaConsumingBean = cdiContainer.select(AbstractSampleBean.Channel1.class).get();
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_1, testData);
    }

    @Test
    void processor() {
        // This test pushes in topic 2, it is processed and
        // pushed in topic 7, and finally check the results coming from topic 7.
        List<String> testData = IntStream.range(0, 99).mapToObj(Integer::toString).collect(Collectors.toList());
        List<String> expected = testData.stream().map(i -> "Processed" + i).collect(Collectors.toList());
        AbstractSampleBean kafkaConsumingBean = cdiContainer.select(AbstractSampleBean.ChannelProcessor.class).get();
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_2, expected);
    }

    @Test
    void error() {
        AbstractSampleBean bean = cdiContainer.select(AbstractSampleBean.ChannelError.class).get();
        // This is correctly processed
        List<String> testData = Collections.singletonList("10");
        produceAndCheck(bean, testData, TEST_TOPIC_ERROR, testData);
        // This will throw a run time error in KafkaSampleBean#error
        testData = Collections.singletonList("error");
        produceAndCheck(bean, testData, TEST_TOPIC_ERROR, Collections.singletonList("10"));
        // After an error, it cannot receive new data
        testData = Collections.singletonList("20");
        produceAndCheck(bean, testData, TEST_TOPIC_ERROR, Collections.singletonList("10"));
    }

    @Test
    void withBackPressure() {
        List<String> testData = IntStream.range(0, 999).mapToObj(i -> "1").collect(Collectors.toList());
        List<String> expected = Arrays.asList("1", "1", "1");
        AbstractSampleBean bean = cdiContainer.select(AbstractSampleBean.Channel4.class).get();
        produceAndCheck(bean, testData, TEST_TOPIC_4, expected);
    }

    @Test
    void withBackPressureAndError() {
        List<String> testData = Arrays.asList("2222", "2222");
        AbstractSampleBean bean = cdiContainer.select(AbstractSampleBean.Channel5.class).get();
        produceAndCheck(bean, testData, TEST_TOPIC_5, testData);
        bean.restart();
        testData = Collections.singletonList("not a number");
        produceAndCheck(bean, testData, TEST_TOPIC_5, Collections.singletonList("error"));
    }

    private static <T> Instance<T> getInstance(Class<T> beanType, Annotation annotation) {
        return cdiContainer.select(beanType, annotation);
    }

    private static SeContainer startCdiContainer(Map<String, String> p, Set<Class<?>> beanClasses) {
        p.put("mp.initializer.allow", "true");
        org.eclipse.microprofile.config.Config config = ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(p))
                .build();

        MpConfigProviderResolver.instance()
                .registerConfig(config,
                        Thread.currentThread().getContextClassLoader());
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        assertNotNull(initializer);
        initializer.addBeanClasses(beanClasses.toArray(new Class<?>[0]));
        return initializer.initialize();
    }
}