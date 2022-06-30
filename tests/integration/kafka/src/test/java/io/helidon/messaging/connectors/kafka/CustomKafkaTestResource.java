/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.kafka.test.AbstractKafkaTestResource;
import com.salesforce.kafka.test.KafkaTestCluster;
import com.salesforce.kafka.test.KafkaTestUtils;

public class CustomKafkaTestResource
        extends AbstractKafkaTestResource<CustomKafkaTestResource>{
    private static final Logger logger = LoggerFactory.getLogger(CustomKafkaTestResource.class);
    private KafkaTestUtils kafkaTestUtils;

    public CustomKafkaTestResource() {
        super();
    }

    public void startKafka() {
        logger.info("Starting kafka test server");

        // Validate state.
        validateState(false, "Unknown State! Kafka Test Server already exists!");

        getBrokerProperties().setProperty("controlled.shutdown.enable", "false");

        // Setup kafka test server
        setKafkaCluster(new KafkaTestCluster(
                getNumberOfBrokers(),
                getBrokerProperties(),
                Collections.singletonList(getRegisteredListener())
        ));
        try {
            getKafkaCluster().start();
            kafkaTestUtils = getKafkaTestUtils();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stopKafka() {
        logger.info("Shutting down kafka test server");

        // Close out kafka test server if needed
        if (getKafkaCluster() == null) {
            return;
        }
        try {
            getKafkaCluster().close();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        setKafkaCluster(null);
    }

    void produce(Map<String, String> map, String topic) {
        kafkaTestUtils.produceRecords(map.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().getBytes(), e -> e.getValue().getBytes())),
                topic, 0);
    }

    void produce(String key, String value, String topic) {
        kafkaTestUtils.produceRecords(Map.of(key.getBytes(), value.getBytes()), topic, 0);
    }

    void produce(byte[] key, byte[] value, String topic) {
        kafkaTestUtils.produceRecords(Map.of(key, value), topic, 0);
    }

    List<ConsumerRecord<String, String>> consume(String topic) {
        return kafkaTestUtils.consumeAllRecordsFromTopic(topic, StringDeserializer.class, StringDeserializer.class);
    }

    List<ConsumerRecord<Long, String>> consumeLongString(String topic) {
        return kafkaTestUtils.consumeAllRecordsFromTopic(topic, LongDeserializer.class, StringDeserializer.class);
    }
}
