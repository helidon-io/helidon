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

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.helidon.common.reactive.Multi;
import io.helidon.config.Config;
import io.helidon.messaging.Channel;
import io.helidon.messaging.Messaging;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class KafkaSeTest extends AbstractKafkaTest {
    private static final Logger LOGGER = Logger.getLogger(KafkaSeTest.class.getName());
    private static final String TEST_SE_TOPIC_1 = "special-se-topic-1";
    private static final String TEST_SE_TOPIC_2 = "special-se-topic-2";
    private static final String TEST_SE_TOPIC_3 = "special-se-topic-3";
    private static final String TEST_SE_TOPIC_4 = "special-se-topic-4";
    private static final String TEST_SE_TOPIC_5 = "special-se-topic-4";


    @BeforeAll
    static void prepareTopics() {
        kafkaResource.getKafkaTestUtils().createTopic(TEST_SE_TOPIC_1, 4, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_SE_TOPIC_2, 4, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_SE_TOPIC_3, 4, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_SE_TOPIC_4, 4, (short) 2);
        kafkaResource.getKafkaTestUtils().createTopic(TEST_SE_TOPIC_5, 4, (short) 2);
        KAFKA_SERVER = kafkaResource.getKafkaConnectString();
    }

    @Test
    void sendToKafka() throws InterruptedException {

        Channel<Integer> toKafka = Channel.<Integer>builder()
                .name("to-kafka")
                .subscriberConfig(KafkaConnector.configBuilder()
                        .bootstrapServers(KAFKA_SERVER)
                        .groupId("test-group")
                        .topic(TEST_SE_TOPIC_2)
                        .acks("all")
                        .property("retries", "2")
                        .keySerializer(LongSerializer.class)
                        .valueSerializer(IntegerSerializer.class)
                        .build()
                ).build();

        KafkaConnector kafkaConnector = KafkaConnector.create(Config.empty());

        CountDownLatch countDownLatch = new CountDownLatch(10);
        HashSet<Integer> result = new HashSet<>();

        Messaging messaging = Messaging.builder()
                .connector(kafkaConnector)
                .publisher(toKafka,
                        Multi.from(IntStream.rangeClosed(0, 100).boxed())
                                .map(Message::of)
                )
                .build();

        try {
            messaging.start();
            IntegerDeserializer deserializer = new IntegerDeserializer();
            kafkaResource.getKafkaTestUtils().consumeAllRecordsFromTopic(TEST_SE_TOPIC_2).forEach(consumerRecord -> {
                result.add(deserializer.deserialize(TEST_SE_TOPIC_2, consumerRecord.value()));
                countDownLatch.countDown();
            });
            assertThat(countDownLatch.await(20, TimeUnit.SECONDS), is(true));
            assertThat(result, equalTo(IntStream.range(0, 100).boxed().collect(Collectors.toSet())));
        } finally {
            messaging.stop();
        }
    }

    @Test
    void consumeKafka() throws InterruptedException {
        Map<String, String> testData = IntStream.rangeClosed(0, 10)
                .boxed()
                .collect(Collectors.toMap(String::valueOf, String::valueOf));

        CountDownLatch countDownLatch = new CountDownLatch(testData.size());
        HashSet<String> result = new HashSet<>();

        Channel<String> fromKafka = Channel.<String>builder()
                .name("from-kafka")
                .publisherConfig(KafkaConnector.configBuilder()
                        .bootstrapServers(KAFKA_SERVER)
                        .groupId("test-group")
                        .topic(TEST_SE_TOPIC_1)
                        .autoOffsetReset(KafkaConfigBuilder.AutoOffsetReset.EARLIEST)
                        .enableAutoCommit(false)
                        .keyDeserializer(StringDeserializer.class)
                        .valueDeserializer(StringDeserializer.class)
                        .build()
                )
                .build();

        KafkaConnector kafkaConnector = KafkaConnector.create();

        Messaging messaging = Messaging.builder()
                .connector(kafkaConnector)
                .listener(fromKafka, payload -> {
                    LOGGER.info("Kafka says: " + payload);
                    result.add(payload);
                    countDownLatch.countDown();
                })
                .build();

        try {
            messaging.start();
            Map<byte[], byte[]> rawTestData = testData.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().getBytes(), e -> e.getValue().getBytes()));
            kafkaResource.getKafkaTestUtils().produceRecords(rawTestData, TEST_SE_TOPIC_1, 1);

            assertThat(countDownLatch.await(20, TimeUnit.SECONDS), is(true));
            assertThat(result, containsInAnyOrder(testData.values().toArray()));
        } finally {
            messaging.stop();
        }
    }

    @Test
    void consumeKafkaMultipleTopics() throws InterruptedException {
        Set<String> testData1 = Set.of("0", "2", "3", "4", "5");
        Set<String> testData2 = Set.of("6", "7", "8", "9", "10");

        Set<String> expected = new HashSet<>(testData1);
        expected.addAll(testData2);

        CountDownLatch countDownLatch = new CountDownLatch(expected.size());
        HashSet<String> result = new HashSet<>();

        Channel<String> fromKafka = Channel.<String>builder()
                .name("from-kafka")
                .publisherConfig(KafkaConnector.configBuilder()
                        .bootstrapServers(KAFKA_SERVER)
                        .groupId("test-group")
                        .topic(TEST_SE_TOPIC_3, TEST_SE_TOPIC_4)
                        .autoOffsetReset(KafkaConfigBuilder.AutoOffsetReset.EARLIEST)
                        .enableAutoCommit(false)
                        .keyDeserializer(StringDeserializer.class)
                        .valueDeserializer(StringDeserializer.class)
                        .build()
                )
                .build();

        KafkaConnector kafkaConnector = KafkaConnector.create();

        Messaging messaging = Messaging.builder()
                .connector(kafkaConnector)
                .listener(fromKafka, payload -> {
                    LOGGER.info("Kafka says: value=" + payload);
                    result.add(payload);
                    countDownLatch.countDown();
                })
                .build();

        try {
            messaging.start();

            Map<byte[], byte[]> rawTestData1 = testData1.stream()
                    .map(s -> s.getBytes(StandardCharsets.UTF_8))
                    .collect(Collectors.toMap(Function.identity(), Function.identity()));
            Map<byte[], byte[]> rawTestData2 = testData2.stream()
                    .map(s -> s.getBytes(StandardCharsets.UTF_8))
                    .collect(Collectors.toMap(Function.identity(), Function.identity()));
            kafkaResource.getKafkaTestUtils().produceRecords(rawTestData1, TEST_SE_TOPIC_3, 1);
            kafkaResource.getKafkaTestUtils().produceRecords(rawTestData2, TEST_SE_TOPIC_4, 1);

            assertThat(countDownLatch.await(20, TimeUnit.SECONDS), is(true));
            assertThat(result, containsInAnyOrder(expected.toArray()));
        } finally {
            messaging.stop();
        }
    }

    @Test
    void kafkaEcho() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(10);
        HashSet<Integer> result = new HashSet<>();

        Channel<Integer> toKafka = Channel.<Integer>builder()
                .name("to-kafka")
                .subscriberConfig(KafkaConnector.configBuilder()
                        .bootstrapServers(KAFKA_SERVER)
                        .groupId("test-group")
                        .topic(TEST_SE_TOPIC_1)
                        .keySerializer(LongSerializer.class)
                        .valueSerializer(IntegerSerializer.class)
                        .build()
                ).build();

        Channel<Integer> fromKafka = Channel.<Integer>builder()
                .name("from-kafka")
                .publisherConfig(KafkaConnector.configBuilder()
                        .bootstrapServers(KAFKA_SERVER)
                        .groupId("test-group")
                        .topic(TEST_SE_TOPIC_1)
                        .autoOffsetReset(KafkaConfigBuilder.AutoOffsetReset.EARLIEST)
                        .enableAutoCommit(false)
                        .keyDeserializer(LongDeserializer.class)
                        .valueDeserializer(IntegerDeserializer.class)
                        .build()
                )
                .build();

        KafkaConnector kafkaConnector = KafkaConnector.create();

        Messaging messaging = Messaging.builder()
                .connector(kafkaConnector)
                .publisher(toKafka,
                        Multi.from(IntStream.rangeClosed(0, 100).boxed())
                                .map(Message::of)
                )
                .subscriber(fromKafka, ReactiveStreams.<Message<Integer>>builder()
                        .peek(Message::ack)
                        .map(Message::getPayload)
                        .filter(i -> i < 10)
                        .forEach(payload -> {
                            LOGGER.info("Kafka says: " + payload);
                            result.add(payload);
                            countDownLatch.countDown();
                        }))
                .build();

        try {
            messaging.start();
            assertThat(countDownLatch.await(20, TimeUnit.SECONDS), is(true));
            assertThat(result, containsInAnyOrder(IntStream.range(0, 10).boxed().toArray()));
        } finally {
            messaging.stop();
        }
    }

    @Test
    void kafkaHeaderTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        HashSet<String> result = new HashSet<>();

        Channel<Integer> toKafka = Channel.<Integer>builder()
                .name("to-kafka")
                .subscriberConfig(KafkaConnector.configBuilder()
                        .bootstrapServers(KAFKA_SERVER)
                        .groupId("test-group")
                        .topic(TEST_SE_TOPIC_1)
                        .keySerializer(LongSerializer.class)
                        .valueSerializer(IntegerSerializer.class)
                        .build()
                ).build();

        Channel<Integer> fromKafka = Channel.<Integer>builder()
                .name("from-kafka")
                .publisherConfig(KafkaConnector.configBuilder()
                        .bootstrapServers(KAFKA_SERVER)
                        .groupId("test-group")
                        .topic(TEST_SE_TOPIC_1)
                        .autoOffsetReset(KafkaConfigBuilder.AutoOffsetReset.EARLIEST)
                        .enableAutoCommit(false)
                        .keyDeserializer(LongDeserializer.class)
                        .valueDeserializer(IntegerDeserializer.class)
                        .build()
                )
                .build();

        KafkaConnector kafkaConnector = KafkaConnector.create();

        Messaging messaging = Messaging.builder()
                .connector(kafkaConnector)
                .publisher(toKafka,
                        Multi.from(IntStream.rangeClosed(1, 3).boxed())
                                .map(KafkaMessage::of)
                                .peek(msg -> msg.getHeaders()
                                        .add("secret header",
                                                ("number: " + msg.getPayload()).getBytes(StandardCharsets.UTF_8))
                                )
                )
                .subscriber(fromKafka, ReactiveStreams.<KafkaMessage<Long, Integer>>builder()
                        .peek(Message::ack)
                        .forEach(msg -> {
                            byte[] headerRawValue = msg.getHeaders().lastHeader("secret header").value();
                            String value = new String(headerRawValue, StandardCharsets.UTF_8);
                            result.add(value);
                            countDownLatch.countDown();
                        }))
                .build();

        try {
            messaging.start();
            assertThat(countDownLatch.await(20, TimeUnit.SECONDS), is(true));
            assertThat(result, containsInAnyOrder(
                    "number: 1",
                    "number: 2",
                    "number: 3"
            ));
        } finally {
            messaging.stop();
        }
    }
}
