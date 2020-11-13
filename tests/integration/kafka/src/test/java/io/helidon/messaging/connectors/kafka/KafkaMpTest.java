/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import io.helidon.config.mp.MpConfigProviderResolver;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.messaging.MessagingCdiExtension;

import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaMpTest extends AbstractKafkaTest{

    private static final Logger LOGGER = Logger.getLogger(KafkaMpTest.class.getName());
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
    public static final SharedKafkaTestResource kafkaResource = new SharedKafkaTestResource()
        .withBrokers(4).withBrokerProperty("auto.create.topics.enable", Boolean.toString(false));
    private static final String TEST_TOPIC_1 = "graph-done-1";
    private static final String TEST_TOPIC_2 = "graph-done-2";
    private static final String TEST_TOPIC_3 = "graph-done-3";
    private static final String TEST_TOPIC_4 = "graph-done-4";
    private static final String TEST_TOPIC_5 = "graph-done-5";
    private static final String TEST_TOPIC_7 = "graph-done-7";
    private static final String TEST_TOPIC_10 = "graph-done-10";
    private static final String TEST_TOPIC_13 = "graph-done-13";
    private static final String UNEXISTING_TOPIC = "unexistingTopic2";

    private static SeContainer cdiContainer;

    private static Map<String, String> cdiConfig() {
        Map<String, String> p = new HashMap<>();
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-1.enable.auto.commit", Boolean.toString(true),
                "mp.messaging.incoming.test-channel-1.poll.timeout", "10",
                "mp.messaging.incoming.test-channel-1.period.executions", "10",
                "mp.messaging.incoming.test-channel-1.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-1.bootstrap.servers", KAFKA_SERVER,
                "mp.messaging.incoming.test-channel-1.topic", TEST_TOPIC_1,
                "mp.messaging.incoming.test-channel-1.group.id", UUID.randomUUID().toString(),
                "mp.messaging.incoming.test-channel-1.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-1.value.deserializer", StringDeserializer.class.getName()));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-2.enable.auto.commit", Boolean.toString(false),
                "mp.messaging.incoming.test-channel-2.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-2.bootstrap.servers", KAFKA_SERVER,
                "mp.messaging.incoming.test-channel-2.topic", TEST_TOPIC_2,
                "mp.messaging.incoming.test-channel-2.group.id", UUID.randomUUID().toString(),
                "mp.messaging.incoming.test-channel-2.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-2.value.deserializer", StringDeserializer.class.getName())
        );
        p.putAll(Map.of(
                "mp.messaging.outgoing.test-channel-3.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.outgoing.test-channel-3.bootstrap.servers", KAFKA_SERVER,
                "mp.messaging.outgoing.test-channel-3.topic", TEST_TOPIC_7,
                "mp.messaging.outgoing.test-channel-3.backpressure.size", "5",
                "mp.messaging.outgoing.test-channel-3.key.serializer", LongSerializer.class.getName(),
                "mp.messaging.outgoing.test-channel-3.value.serializer", StringSerializer.class.getName())
        );
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-error.enable.auto.commit", Boolean.toString(false),
                "mp.messaging.incoming.test-channel-error.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-error.bootstrap.servers", KAFKA_SERVER,
                "mp.messaging.incoming.test-channel-error.topic", TEST_TOPIC_3,
                "mp.messaging.incoming.test-channel-error.group.id", UUID.randomUUID().toString(),
                "mp.messaging.incoming.test-channel-error.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-error.value.deserializer", StringDeserializer.class.getName())
        );
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-4.enable.auto.commit", Boolean.toString(false),
                "mp.messaging.incoming.test-channel-4.poll.timeout", "10",
                "mp.messaging.incoming.test-channel-4.period.executions", "10",
                "mp.messaging.incoming.test-channel-4.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-4.bootstrap.servers", KAFKA_SERVER,
                "mp.messaging.incoming.test-channel-4.topic", TEST_TOPIC_4,
                "mp.messaging.incoming.test-channel-4.group.id", UUID.randomUUID().toString(),
                "mp.messaging.incoming.test-channel-4.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-4.value.deserializer", StringDeserializer.class.getName()));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-5.enable.auto.commit", Boolean.toString(false),
                "mp.messaging.incoming.test-channel-5.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-5.bootstrap.servers", KAFKA_SERVER,
                "mp.messaging.incoming.test-channel-5.topic", TEST_TOPIC_5,
                "mp.messaging.incoming.test-channel-5.group.id", UUID.randomUUID().toString(),
                "mp.messaging.incoming.test-channel-5.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-5.value.deserializer", StringDeserializer.class.getName()));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-7.enable.auto.commit", Boolean.toString(false),
                "mp.messaging.incoming.test-channel-7.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-7.bootstrap.servers", KAFKA_SERVER,
                "mp.messaging.incoming.test-channel-7.topic", TEST_TOPIC_7,
                "mp.messaging.incoming.test-channel-7.group.id", UUID.randomUUID().toString(),
                "mp.messaging.incoming.test-channel-7.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-7.value.deserializer", StringDeserializer.class.getName()));
        p.putAll(Map.of(
                "mp.messaging.outgoing.test-channel-9.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.outgoing.test-channel-9.bootstrap.servers", "unexsitingserver:7777",
                "mp.messaging.outgoing.test-channel-9.topic", "unexistingTopic",
                "mp.messaging.outgoing.test-channel-9.backpressure.size", "1",
                "mp.messaging.outgoing.test-channel-9.key.serializer", LongSerializer.class.getName(),
                "mp.messaging.outgoing.test-channel-9.value.serializer", StringSerializer.class.getName())
        );
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-10.enable.auto.commit", Boolean.toString(false),
                "mp.messaging.incoming.test-channel-10.auto.offset.reset", "earliest",
                "mp.messaging.incoming.test-channel-10.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-10.bootstrap.servers", KAFKA_SERVER,
                "mp.messaging.incoming.test-channel-10.topic", TEST_TOPIC_10,
                "mp.messaging.incoming.test-channel-10.group.id", UUID.randomUUID().toString(),
                "mp.messaging.incoming.test-channel-10.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-10.value.deserializer", StringDeserializer.class.getName()));
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-11.enable.auto.commit", Boolean.toString(false),
                "mp.messaging.incoming.test-channel-11.auto.offset.reset", "earliest",
                "mp.messaging.incoming.test-channel-11.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-11.bootstrap.servers", "unexsitingserver:7777",
                "mp.messaging.incoming.test-channel-11.topic", "any",
                "mp.messaging.incoming.test-channel-11.group.id", UUID.randomUUID().toString(),
                "mp.messaging.incoming.test-channel-11.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-11.value.deserializer", StringDeserializer.class.getName()));
        p.putAll(Map.of(
                "mp.messaging.outgoing.test-channel-12.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.outgoing.test-channel-12.bootstrap.servers", KAFKA_SERVER,
                "mp.messaging.outgoing.test-channel-12.topic", UNEXISTING_TOPIC,
                "mp.messaging.outgoing.test-channel-12.max.block.ms", "1000",
                "mp.messaging.outgoing.test-channel-12.backpressure.size", "1",
                "mp.messaging.outgoing.test-channel-12.batch.size", "1",
                "mp.messaging.outgoing.test-channel-12.acks", "1",
                "mp.messaging.outgoing.test-channel-12.retries", "0",
                "mp.messaging.outgoing.test-channel-12.key.serializer", LongSerializer.class.getName(),
                "mp.messaging.outgoing.test-channel-12.value.serializer", StringSerializer.class.getName())
        );
        p.putAll(Map.of(
                "mp.messaging.incoming.test-channel-13.enable.auto.commit", Boolean.toString(false),
                "mp.messaging.incoming.test-channel-13.auto.offset.reset", "earliest",
                "mp.messaging.incoming.test-channel-13.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.incoming.test-channel-13.bootstrap.servers", KAFKA_SERVER,
                "mp.messaging.incoming.test-channel-13.topic", TEST_TOPIC_13,
                "mp.messaging.incoming.test-channel-13.group.id", "sameGroup",
                "mp.messaging.incoming.test-channel-13.key.deserializer", LongDeserializer.class.getName(),
                "mp.messaging.incoming.test-channel-13.value.deserializer", StringDeserializer.class.getName()));
        return p;
    }

    @BeforeAll
    static void prepareTopics() {
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_1, 4, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_2, 4, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_3, 4, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_4, 4, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_5, 4, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_7, 4, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_10, 1, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_13, 1, (short) 1);
        KAFKA_SERVER = kafkaResource.getKafkaConnectString();
        cdiContainerUp();
    }

    @AfterAll
    static void cdiContainerDown() {
        KafkaConnector factory = getInstance(KafkaConnector.class, KAFKA_CONNECTOR_LITERAL).get();
        Collection<KafkaPublisher<?, ?>> resources = factory.resources();
        assertFalse(resources.isEmpty());
        cdiContainer.close();
        assertTrue(resources.isEmpty());
        LOGGER.info("Container destroyed");
    }

    private static void cdiContainerUp() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(AbstractSampleBean.Channel1.class);
        classes.add(AbstractSampleBean.Channel4.class);
        classes.add(AbstractSampleBean.Channel5.class);
        classes.add(AbstractSampleBean.ChannelError.class);
        classes.add(AbstractSampleBean.ChannelProcessor.class);
        classes.add(AbstractSampleBean.Channel9.class);
        classes.add(AbstractSampleBean.Channel11.class);
        classes.add(AbstractSampleBean.Channel12.class);
        classes.add(MessagingCdiExtension.class);

        Map<String, String> p = new HashMap<>(cdiConfig());
        cdiContainer = startCdiContainer(p, classes);
        assertTrue(cdiContainer.isRunning());
        List<String> topicsInKafka = new ArrayList<>(kafkaResource.getKafkaTestUtils().getTopicNames());
        //Wait till consumers are ready
        getInstance(KafkaConnector.class, KAFKA_CONNECTOR_LITERAL).stream()
        .flatMap(factory -> factory.resources().stream()).forEach(c -> {
            try {
                LOGGER.log(Level.FINE, "Waiting for Kafka topic");
                c.waitForPartitionAssigment(10, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException e) {
                fail(e);
            }
            topicsInKafka.removeAll(c.topics());
        });
        assertEquals(Collections.emptyList(), topicsInKafka);
        LOGGER.info("Container setup");
    }

    @Test
    void multipleTopics() {
        LOGGER.fine(() -> "==========> test multipleTopics()");
        Map<String, String> p = Map.of("topic", "topic1,topic2");
        Config config = Config.builder().sources(ConfigSources.create(p)).build();
        KafkaConfig kafkaConfig = KafkaConfig.create(config);
        List<String> topics = kafkaConfig.topics();
        assertEquals(2, topics.size());
        assertTrue(topics.containsAll(Arrays.asList("topic1", "topic2")));
    }

    @Test
    void multipleTopicsWithPattern() {
        LOGGER.fine(() -> "==========> test multipleTopicsWithPattern()");
        Map<String, String> p = Map.of("topic.pattern", "topic[1-2]");
        Config config = Config.builder().sources(ConfigSources.create(p)).build();
        KafkaConfig kafkaConfig = KafkaConfig.create(config);
        assertTrue(kafkaConfig.topicPattern().isPresent());
        assertTrue(kafkaConfig.topicPattern().get().matcher("topic1").matches());
        assertTrue(kafkaConfig.topicPattern().get().matcher("topic2").matches());
        assertFalse(kafkaConfig.topicPattern().get().matcher("topic3").matches());
    }

    @Test
    void incomingKafkaOk() {
        LOGGER.fine(() -> "==========> test incomingKafkaOk()");
        List<String> testData = IntStream.range(0, 99).mapToObj(i -> "test" + i).collect(Collectors.toList());
        AbstractSampleBean kafkaConsumingBean = cdiContainer.select(AbstractSampleBean.Channel1.class).get();
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_1, testData);
    }

    @Test
    void processor() {
        LOGGER.fine(() -> "==========> test processor()");
        // This test pushes in topic 2, it is processed and 
        // pushed in topic 7, and finally check the results coming from topic 7.
        List<String> testData = IntStream.range(0, 99).mapToObj(i -> Integer.toString(i)).collect(Collectors.toList());
        List<String> expected = testData.stream().map(i -> "Processed" + i).collect(Collectors.toList());
        AbstractSampleBean kafkaConsumingBean = cdiContainer.select(AbstractSampleBean.ChannelProcessor.class).get();
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_2, expected);
    }

    @Test
    void error() {
        LOGGER.fine(() -> "==========> test error()");
        AbstractSampleBean kafkaConsumingBean = cdiContainer.select(AbstractSampleBean.ChannelError.class).get();
        // This is correctly processed
        List<String> testData = Arrays.asList("10");
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_3, testData);
        // This will throw a run time error in KafkaSampleBean#error
        testData = Arrays.asList("error");
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_3, Arrays.asList("10"));
        // After an error, it cannot receive new data
        testData = Arrays.asList("20");
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_3, Arrays.asList("10"));
    }

    @Test
    void withBackPressure() {
        LOGGER.fine(() -> "==========> test withBackPressure()");
        List<String> testData = IntStream.range(0, 999).mapToObj(i -> "1").collect(Collectors.toList());
        List<String> expected = Arrays.asList("1", "1", "1");
        AbstractSampleBean kafkaConsumingBean = cdiContainer.select(AbstractSampleBean.Channel4.class).get();
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_4, expected);
    }

    @Test
    void withBackPressureAndError() {
        LOGGER.fine(() -> "==========> test withBackPressureAndError()");
        List<String> testData = Arrays.asList("2222", "2222");
        AbstractSampleBean kafkaConsumingBean = cdiContainer.select(AbstractSampleBean.Channel5.class).get();
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_5, testData);
        kafkaConsumingBean.restart();
        testData = Arrays.asList("not a number");
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_5, Arrays.asList("error"));
        kafkaResource.getKafkaTestUtils().consumeAllRecordsFromTopic(TEST_TOPIC_5);
    }

    @Test
    void wakeupCanBeInvokedWhenKafkaConsumerClosed() {
        LOGGER.fine(() -> "==========> test wakeupCanBeInvokedWhenKafkaConsumerClosed()");
        KafkaConnector kafkaConnector = getInstance(KafkaConnector.class, KAFKA_CONNECTOR_LITERAL).get();
        KafkaPublisher<?, ?> any = kafkaConnector.resources().poll();
        // Wake up and closes the KafkaConsumer
        any.stop();
        // Do it again to verify kafkaConsumer.wakeup() doesn't throw exception
        any.stop();
    }

    @Test
    void kafkaSubscriberConnectionError() throws InterruptedException {
        LOGGER.fine(() -> "==========> test kafkaSubscriberConnectionError()");
        // Cannot make the connection to Kafka
        List<String> testData = Arrays.asList("any");
        AbstractSampleBean.Channel9 kafkaConsumingBean = cdiContainer.select(AbstractSampleBean.Channel9.class).get();
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_10, Collections.emptyList(), 0);
        // As the channel is cancelled, we cannot wait till something happens. We need to explicitly wait some time.
        Thread.sleep(1000);
        assertEquals(Collections.emptyList(), kafkaConsumingBean.consumed());
        kafkaResource.getKafkaTestUtils().consumeAllRecordsFromTopic(TEST_TOPIC_10);
    }

    @Test
    void kafkaSubscriberSendError() throws InterruptedException {
        LOGGER.fine(() -> "==========> test kafkaSubscriberSendError()");
        // This message is captured, but will fail later to send it
        List<String> testData = Arrays.asList("any");
        AbstractSampleBean.Channel12 kafkaConsumingBean = cdiContainer.select(AbstractSampleBean.Channel12.class).get();
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_13, testData);
        kafkaConsumingBean.restart();
        testData = Arrays.asList("new message");
        produceAndCheck(kafkaConsumingBean, testData, TEST_TOPIC_13, Collections.emptyList(), 0);
        // As the channel is cancelled, we cannot wait till something happens. We need to explicitly wait some time.
        Thread.sleep(1000);
        assertEquals(Collections.emptyList(), kafkaConsumingBean.consumed());
        kafkaResource.getKafkaTestUtils().consumeAllRecordsFromTopic(TEST_TOPIC_13);
    }

    private static <T> Instance<T> getInstance(Class<T> beanType, Annotation annotation){
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