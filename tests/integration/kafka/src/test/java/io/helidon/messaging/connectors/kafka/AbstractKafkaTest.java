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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractKafkaTest {

    private static final Logger LOGGER = Logger.getLogger(AbstractKafkaTest.class.getName());

    static String KAFKA_SERVER;

    @RegisterExtension
    public static final SharedKafkaTestResource kafkaResource = new SharedKafkaTestResource()
            .withBrokers(4)
            .withBrokerProperty("replication.factor", "2")
            .withBrokerProperty("min.insync.replicas", "1")
            .withBrokerProperty("auto.create.topics.enable", Boolean.toString(false));

    @BeforeAll
    static void prepareTopics() {
        KAFKA_SERVER = kafkaResource.getKafkaConnectString();
    }

    static <T> void produceSync(String topic, Map<String, Object> config, List<T> testData) {
        try (Producer<Object, T> producer = new KafkaProducer<>(config)) {
            LOGGER.fine(() -> "Producing " + testData.size() + " events");
            //Send all test messages(async send means order is not guaranteed) and in parallel
            List<Future<RecordMetadata>> sent = testData.parallelStream()
                    .map(s -> producer.send(new ProducerRecord<>(topic, s))).collect(Collectors.toList());
            sent.stream().forEach(future -> {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    fail("Some of next messages were not sent in time: " + testData, e);
                }
            });
        }
    }

    static void produceAndCheck(AbstractSampleBean kafkaConsumingBean, List<String> testData, String topic,
            List<String> expected) {
        produceAndCheck(kafkaConsumingBean, testData, topic, expected, expected.size());
    }

    static void produceAndCheck(AbstractSampleBean kafkaConsumingBean, List<String> testData, String topic,
            List<String> expected, long requested) {
        kafkaConsumingBean.expectedRequests(requested);
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", KAFKA_SERVER);
        config.put("key.serializer", LongSerializer.class.getName());
        config.put("value.serializer", StringSerializer.class.getName());
        
        produceSync(topic, config, testData);
        if (requested > 0) {
            // Wait till records are delivered
            boolean done = kafkaConsumingBean.await();
            assertTrue(done, String.format("Timeout waiting for results.\nExpected: %s \nBut was: %s",
                    expected.toString(), kafkaConsumingBean.consumed().toString()));
        }
        Collections.sort(kafkaConsumingBean.consumed());
        Collections.sort(expected);
        if (!expected.isEmpty()) {
            assertEquals(expected, kafkaConsumingBean.consumed());
        }
    }

    static List<String> readTopic(String topic, int expected, String group){
        final long timeout = 30000;
        List<String> events = new LinkedList<>();
        Map<String, Object> config = new HashMap<>();
        config.put("enable.auto.commit", Boolean.toString(true));
        config.put("auto.offset.reset", "earliest");
        config.put("bootstrap.servers", KAFKA_SERVER);
        config.put("group.id", group);
        config.put("key.deserializer", LongDeserializer.class.getName());
        config.put("value.deserializer", StringDeserializer.class.getName());
        try (Consumer<Object, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(Arrays.asList(topic));
            long current = System.currentTimeMillis();
            while (events.size() < expected && System.currentTimeMillis() - current < timeout) {
                consumer.poll(Duration.ofSeconds(5)).forEach(c -> events.add(c.value()));
            }
        }
        return events;
    }
}
