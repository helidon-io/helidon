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

package io.helidon.messaging.connectors.kafka;

import java.lang.annotation.Annotation;
import java.time.Duration;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.mp.MpConfigProviderResolver;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.messaging.MessagingCdiExtension;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

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

    private static final String DLQ_TOPIC = "DLQ_TOPIC";
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
                "mp.messaging.incoming.test-channel-5.auto.offset.reset", "earliest",
                "mp.messaging.incoming.test-channel-5.group.id", "test-group",
                "mp.messaging.incoming.test-channel-5.nack-dlq", DLQ_TOPIC,
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
        p.putAll(Map.of(
                "mp.messaging.outgoing.test-channel-14.connector", KafkaConnector.CONNECTOR_NAME,
                "mp.messaging.outgoing.test-channel-14.max.block.ms", "100",
                "mp.messaging.outgoing.test-channel-14.bootstrap.servers", KAFKA_SERVER,
                "mp.messaging.outgoing.test-channel-14.topic", UNEXISTING_TOPIC,
                "mp.messaging.outgoing.test-channel-14.key.serializer", LongSerializer.class.getName(),
                "mp.messaging.outgoing.test-channel-14.value.serializer", StringSerializer.class.getName()));
        return p;
    }

    @BeforeAll
    static void prepareTopics() {
        kafkaResource.startKafka();

        KAFKA_SERVER = kafkaResource.getKafkaConnectString();

        kafkaResource.getKafkaTestUtils().createTopic(DLQ_TOPIC, 1, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_1, 4, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_2, 4, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_3, 4, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_4, 4, (short) 1);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_TOPIC_5, 1, (short) 1);
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
        assertThat(resources, not(empty()));
        cdiContainer.close();
        assertThat(resources, empty());
        LOGGER.info("Container destroyed");

        kafkaResource.stopKafka();
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
        classes.add(AbstractSampleBean.Channel14.class);
        classes.add(MessagingCdiExtension.class);

        Map<String, String> p = new HashMap<>(cdiConfig());
        cdiContainer = startCdiContainer(p, classes);
        assertThat(cdiContainer.isRunning(), is(true));
        List<String> topicsInKafka = new ArrayList<>(kafkaResource.getKafkaTestUtils().getTopicNames());
        topicsInKafka.remove(DLQ_TOPIC);
        //Wait till consumers are ready
        getInstance(KafkaConnector.class, KAFKA_CONNECTOR_LITERAL).stream()
        .flatMap(factory -> factory.resources().stream()).forEach(c -> {
            try {
                LOGGER.log(Level.FINE, "Waiting for Kafka topic");
                c.waitForPartitionAssigment(30, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException e) {
                fail(e);
            }
            topicsInKafka.removeAll(c.topics());
        });
        assertThat(topicsInKafka, empty());
        LOGGER.info("Container setup");
    }

    @Test
    void multipleTopics() {
        LOGGER.fine(() -> "==========> test multipleTopics()");
        Map<String, String> p = Map.of("topic", "topic1,topic2");
        Config config = Config.builder().sources(ConfigSources.create(p)).build();
        KafkaConfig kafkaConfig = KafkaConfig.create(config);
        List<String> topics = kafkaConfig.topics();
        assertThat(topics, contains("topic1", "topic2"));
    }

    @Test
    void multipleTopicsWithPattern() {
        LOGGER.fine(() -> "==========> test multipleTopicsWithPattern()");
        Map<String, String> p = Map.of("topic.pattern", "topic[1-2]");
        Config config = Config.builder().sources(ConfigSources.create(p)).build();
        KafkaConfig kafkaConfig = KafkaConfig.create(config);
        assertThat(kafkaConfig.topicPattern().isPresent(), is(true));
        assertThat(kafkaConfig.topicPattern().get().matcher("topic1").matches(), is(true));
        assertThat(kafkaConfig.topicPattern().get().matcher("topic2").matches(), is(true));
        assertThat(kafkaConfig.topicPattern().get().matcher("topic3").matches(), is(false));
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
    void noAckDQL() {
        LOGGER.fine(() -> "==========> test noAckDQL()");
        Map<byte[], byte[]> testData = LongStream.rangeClosed(0, 10)
                .boxed()
                .collect(Collectors.toMap(k -> new LongSerializer().serialize(null, k), v -> v.toString().getBytes()));

        testData.forEach((k, v) -> kafkaResource.produce(k, v, TEST_TOPIC_5));

        AbstractSampleBean.Channel5 channel5 = cdiContainer.select(AbstractSampleBean.Channel5.class).get();
        List<String> result = channel5.getLatch().await(Duration.ofSeconds(35));

        assertThat(result, containsInAnyOrder("0", "1", "2", "4", "5", "6", "7", "8", "9", "10"));

        List<ConsumerRecord<Long, String>> dlqRecords = kafkaResource.consumeLongString(DLQ_TOPIC);

        assertThat(dlqRecords.size(), is(1));
        ConsumerRecord<Long, String> consumerRecord = dlqRecords.get(0);
        Map<String, String> headersMap = Arrays.stream(consumerRecord.headers().toArray())
                .collect(Collectors.toMap(Header::key, h -> new String(h.value())));

        assertThat(consumerRecord.key(), is(3L));
        assertThat(consumerRecord.value(), is("3"));
        assertThat(headersMap.get("dlq-error"), is("java.lang.Exception"));
        assertThat(headersMap.get("dlq-error-msg"), is("BOOM!"));
        assertThat(headersMap.get("dlq-orig-topic"), is(TEST_TOPIC_5));
        assertThat(headersMap.get("dlq-orig-offset"), is(String.valueOf(channel5.getBoomOffset())));
        assertThat(headersMap.get("dlq-orig-partition"), is("0"));

        kafkaResource.getKafkaTestUtils().consumeAllRecordsFromTopic(TEST_TOPIC_5);
        kafkaResource.getKafkaTestUtils().consumeAllRecordsFromTopic(DLQ_TOPIC);
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
        assertThat(kafkaConsumingBean.consumed(), empty());
        kafkaResource.getKafkaTestUtils().consumeAllRecordsFromTopic(TEST_TOPIC_10);
    }

    @Test
    void kafkaProduceWithNack() throws InterruptedException, ExecutionException, TimeoutException {
        LOGGER.fine(() -> "==========> test kafkaProduceWithNack()");
        AbstractSampleBean.Channel14 kafkaProdBean = cdiContainer.select(AbstractSampleBean.Channel14.class).get();
        Throwable t = kafkaProdBean.getNacked().get(5, TimeUnit.SECONDS);
        assertThat(t, notNullValue());
        assertThat(t.getCause(), Matchers.instanceOf(org.apache.kafka.common.errors.TimeoutException.class));
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
        assertThat(kafkaConsumingBean.consumed(), empty());
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
        assertThat(initializer, notNullValue());
        initializer.addBeanClasses(beanClasses.toArray(new Class<?>[0]));
        return initializer.initialize();
    }
}
