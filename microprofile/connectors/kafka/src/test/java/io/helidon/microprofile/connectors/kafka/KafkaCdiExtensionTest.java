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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MpConfigProviderResolver;
import io.helidon.microprofile.messaging.MessagingCdiExtension;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaCdiExtensionTest {

    private static final Logger LOGGER = Logger.getLogger(KafkaCdiExtensionTest.class.getName());
    protected SeContainer cdiContainer;

    protected static final Connector KAFKA_CONNECTOR_LITERAL = new Connector() {

        @Override
        public Class<? extends Annotation> annotationType() {
            return Connector.class;
        }

        @Override
        public String value() {
            return KafkaConnector.CONNECTOR_NAME;
        }
    };

    @RegisterExtension
    public static final SharedKafkaTestResource kafkaResource = new SharedKafkaTestResource();
    public static final String TEST_TOPIC_1 = "graph-done-1";
    public static final String TEST_TOPIC_2 = "graph-done-2";
    public static final String TEST_TOPIC_3 = "graph-done-3";
    public static final String TEST_TOPIC_4 = "graph-done-4";
    public static final String TEST_TOPIC_5 = "graph-done-5";

    protected Map<String, String> cdiConfig() {
        Map<String, String> p = new HashMap<>();
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-1.poll.timeout", "10",
                "mp.messaging.incoming.test-channel-1.period.executions", "10",
                "mp.messaging.incoming.test-channel-1.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-1.bootstrap.servers", kafkaResource.getKafkaConnectString(),
                "mp.messaging.incoming.test-channel-1.topic", TEST_TOPIC_1,
                "mp.messaging.incoming.test-channel-1.group.id", "group1",
                "mp.messaging.incoming.test-channel-1.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-1.value.deserializer", StringDeserializer.class.getName()));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-2.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-2.bootstrap.servers", kafkaResource.getKafkaConnectString(),
                "mp.messaging.incoming.test-channel-2.topic", TEST_TOPIC_2,
                "mp.messaging.incoming.test-channel-2.group.id", "group2",
                "mp.messaging.incoming.test-channel-2.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-2.value.deserializer", StringDeserializer.class.getName())
        );
        p.putAll(Map.of(
                "mp.messaging.outgoing.test-channel-3.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.outgoing.test-channel-3.bootstrap.servers", kafkaResource.getKafkaConnectString(),
                "mp.messaging.outgoing.test-channel-3.topic", TEST_TOPIC_1,
                "mp.messaging.outgoing.test-channel-3.backpressure.size", "5",
                "mp.messaging.outgoing.test-channel-3.key.serializer", LongSerializer.class.getName(),
                "mp.messaging.outgoing.test-channel-3.value.serializer", StringSerializer.class.getName())
        );
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-error.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-error.bootstrap.servers", kafkaResource.getKafkaConnectString(),
                "mp.messaging.incoming.test-channel-error.topic", TEST_TOPIC_3,
                "mp.messaging.incoming.test-channel-error.group.id", "group3",
                "mp.messaging.incoming.test-channel-error.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-error.value.deserializer", StringDeserializer.class.getName())
        );
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-4.poll.timeout", "10",
                "mp.messaging.incoming.test-channel-4.period.executions", "10",
                "mp.messaging.incoming.test-channel-4.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-4.bootstrap.servers", kafkaResource.getKafkaConnectString(),
                "mp.messaging.incoming.test-channel-4.topic", TEST_TOPIC_4,
                "mp.messaging.incoming.test-channel-4.group.id", "group4",
                "mp.messaging.incoming.test-channel-4.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-4.value.deserializer", StringDeserializer.class.getName()));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-5.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-5.bootstrap.servers", kafkaResource.getKafkaConnectString(),
                "mp.messaging.incoming.test-channel-5.topic", TEST_TOPIC_5,
                "mp.messaging.incoming.test-channel-5.group.id", "group4",
                "mp.messaging.incoming.test-channel-5.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-5.value.deserializer", StringDeserializer.class.getName()));
        return p;
    }

    @BeforeAll
    static void prepareTopics() {
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_1, 10, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_2, 10, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_3, 10, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_4, 10, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_5, 10, (short) 1);
    }

    @BeforeEach
    void setUp() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(KafkaConnector.class);
        classes.add(KafkaSampleBean.class);
        classes.add(KafkaNoFullAck1Bean.class);
        classes.add(KafkaNoFullAck2Bean.class);
        classes.add(MessagingCdiExtension.class);

        Map<String, String> p = new HashMap<>(cdiConfig());
        cdiContainer = startCdiContainer(p, classes);
        assertTrue(cdiContainer.isRunning());
        
        //Wait till consumers are ready
        getInstance(KafkaConnector.class, KAFKA_CONNECTOR_LITERAL).stream()
        .flatMap(factory -> factory.resources().stream())
        .filter(closeable -> closeable instanceof KafkaPublisher).forEach(c -> {
            try {
                LOGGER.log(Level.FINE, "Waiting for Kafka topic");
                ((KafkaPublisher)c).waitForPartitionAssigment(10, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException e) {
                fail(e);
            }
        });
        LOGGER.info("Container started");
    }

    @AfterEach
    void tearDown() {
        KafkaConnector factory = getInstance(KafkaConnector.class, KAFKA_CONNECTOR_LITERAL).get();
        Collection<Closeable> resources = factory.resources();
        assertFalse(resources.isEmpty());
        cdiContainer.close();
        assertTrue(resources.isEmpty());
        LOGGER.info("Container destroyed");
    }

    @Test
    void multipleTopics() {
        LOGGER.fine("==========> test multipleTopics()");
        Map<String, String> p = Map.of("topic", "topic1,topic2");
        Config config = Config.builder().sources(ConfigSources.create(p)).build();
        Map<String, Object> kafkaProperties = HelidonToKafkaConfigParser.toMap(config);
        List<String> topics = HelidonToKafkaConfigParser.topicNameList(kafkaProperties);
        assertEquals(2, topics.size());
        assertTrue(topics.containsAll(Arrays.asList("topic1", "topic2")));
    }

    @Test
    void incomingKafkaOk() {
        LOGGER.fine("==========> test incomingKafkaOk()");
        List<String> testData = IntStream.range(0, 999).mapToObj(i -> "test" + i).collect(Collectors.toList());
        CountDownLatch testChannelLatch = new CountDownLatch(testData.size());
        KafkaSampleBean kafkaConsumingBean = cdiContainer.select(KafkaSampleBean.class).get();
        kafkaConsumingBean.setCountDownLatch(testChannelLatch);
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_1, testChannelLatch, testData);
    }

    @Test
    void processor() {
        LOGGER.fine("==========> test processor()");
        // This test pushes in topic 2, it is processed and 
        // pushed in topic 1, and finally check the results coming from topic 1.
        List<String> testData = IntStream.range(0, 999).mapToObj(i -> Integer.toString(i)).collect(Collectors.toList());
        List<String> expected = testData.stream().map(i -> "Processed" + i).collect(Collectors.toList());
        CountDownLatch testChannelLatch = new CountDownLatch(testData.size());
        KafkaSampleBean kafkaConsumingBean = cdiContainer.select(KafkaSampleBean.class).get();
        kafkaConsumingBean.setCountDownLatch(testChannelLatch);
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_2, testChannelLatch, expected);
    }

    @Test
    void error() {
        LOGGER.fine("==========> test error()");
        KafkaSampleBean kafkaConsumingBean = cdiContainer.select(KafkaSampleBean.class).get();
        // This is correctly processed
        List<String> testData = Arrays.asList("1");
        CountDownLatch testChannelLatch = new CountDownLatch(testData.size());
        kafkaConsumingBean.setCountDownLatch(testChannelLatch);
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_3, testChannelLatch, testData);
        // This will throw a run time error in KafkaSampleBean#error
        testData = Arrays.asList("error");
        testChannelLatch = new CountDownLatch(testData.size());
        kafkaConsumingBean.setCountDownLatch(testChannelLatch);
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_3, testChannelLatch, Arrays.asList("1"));
        // After an error, it cannot receive new data
        testData = Arrays.asList("2");
        testChannelLatch = new CountDownLatch(0);
        kafkaConsumingBean.setCountDownLatch(testChannelLatch);
        // The expected result is not very relevant because there is no waiting time
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_3, testChannelLatch, Arrays.asList("1"));
        // But other channels are working, and previous message is not in the list
        testData = Arrays.asList("3");
        testChannelLatch = new CountDownLatch(testData.size());
        kafkaConsumingBean.setCountDownLatch(testChannelLatch);
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_1, testChannelLatch, Arrays.asList("1", "3"));
    }

    @Test
    void withBackPresure() {
        LOGGER.fine("==========> test withBackPresure()");
        List<String> testData = IntStream.range(0, 999).mapToObj(i -> "1").collect(Collectors.toList());
        List<String> expected = Arrays.asList("1", "1", "1");
        CountDownLatch testChannelLatch = new CountDownLatch(expected.size());
        /*
         * We use different bean because this test doesn't ACK everything.
         * Kafka will push again uncommit records and it will make a mess in the tests.
         */
        KafkaNoFullAck1Bean kafkaConsumingBean = cdiContainer.select(KafkaNoFullAck1Bean.class).get();
        kafkaConsumingBean.setCountDownLatch(testChannelLatch);
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_4, testChannelLatch, expected);
    }

    @Test
    void withBackPresureAndError() {
        LOGGER.fine("==========> test withBackPresureAndError()");
        List<String> testData = Arrays.asList("2", "2");
        CountDownLatch testChannelLatch = new CountDownLatch(testData.size());
        /*
         * We use different bean because this test doesn't ACK everything.
         * Kafka will push again uncommit records and it will make a mess in the tests.
         */
        KafkaNoFullAck2Bean kafkaConsumingBean = cdiContainer.select(KafkaNoFullAck2Bean.class).get();
        kafkaConsumingBean.setCountDownLatch(testChannelLatch);
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_5, testChannelLatch, testData);
        testData = Arrays.asList("not a number");
        testChannelLatch = new CountDownLatch(testData.size());
        kafkaConsumingBean.setCountDownLatch(testChannelLatch);
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_5, testChannelLatch, Arrays.asList("2", "2", "error"));
    }

    private void produceAndCheck(AbstractSampleBean kafkaConsumingBean, List<String> testData, String topic,
            CountDownLatch testChannelLatch, List<String> expected) {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", kafkaResource.getKafkaConnectString());
        config.put("key.serializer", LongSerializer.class.getName());
        config.put("value.serializer", StringSerializer.class.getName());
        try (BasicKafkaProducer<Long, String> producer =
                new BasicKafkaProducer<>(Arrays.asList(topic), new KafkaProducer<>(config))) {
            LOGGER.fine("Producing " + testData.size() + " events");
            //Send all test messages(async send means order is not guaranteed) and in parallel
            testData.parallelStream().forEach(msg -> producer.produceAsync(msg));
            // Wait till records are delivered
            boolean consumed = false;
            try {
                consumed = testChannelLatch.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOGGER.fine("Time out");
            }
            assertTrue(consumed, "All expected messages were not consumed. Fix the test to avoid unnecessary waitings");
            Collections.sort(kafkaConsumingBean.consumed());
            Collections.sort(expected);
            assertEquals(expected, kafkaConsumingBean.consumed());
        }
    }

    private <T> Instance<T> getInstance(Class<T> beanType, Annotation annotation){
        return cdiContainer.select(beanType, annotation);
    }

    private SeContainer startCdiContainer(Map<String, String> p, Set<Class<?>> beanClasses) {
        p.put("mp.initializer.allow", "true");
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();
        MpConfigProviderResolver.instance()
                .registerConfig((org.eclipse.microprofile.config.Config) config,
                        Thread.currentThread().getContextClassLoader());
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        assertNotNull(initializer);
        initializer.addBeanClasses(beanClasses.toArray(new Class<?>[0]));
        return initializer.initialize();
    }
}