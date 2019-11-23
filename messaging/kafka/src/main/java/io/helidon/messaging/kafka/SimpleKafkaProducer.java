/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.messaging.kafka;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import io.helidon.config.Config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;

/**
 * Simple Kafka producer covering basic use-cases.
 * Configurable by Helidon {@link io.helidon.config.Config Config},
 * For more info about configuration see {@link KafkaConfigProperties}.
 * <p>
 * Usage:
 * <pre>{@code new SimpleKafkaProducer<Long, String>("job-done-producer", Config.create())
 *             .produce("Hello world!");
 * }</pre>
 *
 * @param <K> Key type
 * @param <V> Value type
 * @see KafkaConfigProperties
 * @see io.helidon.config.Config
 */
public class SimpleKafkaProducer<K, V> implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(SimpleKafkaProducer.class.getName());
    private final KafkaConfigProperties properties;

    private KafkaProducer<K, V> producer;

    /**
     * Kafka producer created from {@link io.helidon.config.Config config} under kafka-producerId,
     * see configuration {@link KafkaConfigProperties example}.
     *
     * @param producerId key in configuration
     * @param config     Helidon {@link io.helidon.config.Config config}
     * @see KafkaConfigProperties
     * @see io.helidon.config.Config
     */
    public SimpleKafkaProducer(String producerId, Config config) {
        properties = new KafkaConfigProperties(config.get("mp.messaging.outgoing").get(producerId));
        producer = new KafkaProducer<>(properties);
    }

    /**
     * Kafka producer created from {@link io.helidon.config.Config config} under kafka-producerId,
     * see configuration {@link KafkaConfigProperties example}.
     *
     * @param config Helidon {@link io.helidon.config.Config config}
     */
    public SimpleKafkaProducer(Config config) {
        properties = new KafkaConfigProperties(config);
        producer = new KafkaProducer<>(properties);
    }

    /**
     * Send record to all provided topics,
     * blocking until all records are acknowledged by broker.
     *
     * @param value Will be serialized by <b>value.serializer</b> class
     *              defined in {@link KafkaConfigProperties configuration}
     * @return Server acknowledged metadata about sent topics
     */
    public List<RecordMetadata> produce(V value) {
        List<Future<RecordMetadata>> futureRecords =
                this.produceAsync(null, null, null, null, value, null);
        List<RecordMetadata> metadataList = new ArrayList<>(futureRecords.size());

        for (Future<RecordMetadata> future : futureRecords) {
            try {
                metadataList.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed to send topic", e);
            }
        }
        return metadataList;
    }

    /**
     * Produce asynchronously.
     *
     * @param value value to be produced
     * @return list of futures
     */
    public List<Future<RecordMetadata>> produceAsync(V value) {
        return this.produceAsync(null, null, null, null, value, null);
    }

    /**
     * Send record to all provided topics, don't wait for server acknowledgement.
     *
     * @param customTopics Can be null, list of topics appended to the list from configuration,
     *                     record will be sent to all topics iteratively
     * @param partition    Can be null, if key is also null topic is sent to random partition
     * @param timestamp    Can be null System.currentTimeMillis() is used then
     * @param key          Can be null, if not, topics are grouped to partitions by key
     * @param value        Will be serialized by value.serializer class defined in configuration
     * @param headers      Can be null, custom headers for additional meta information if needed
     * @return Futures of server acknowledged metadata about sent topics
     */
    public List<Future<RecordMetadata>> produceAsync(List<String> customTopics,
                                                     Integer partition,
                                                     Long timestamp,
                                                     K key,
                                                     V value,
                                                     Iterable<Header> headers) {

        List<String> mergedTopics = new ArrayList<>();
        mergedTopics.addAll(properties.getTopicNameList());
        mergedTopics.addAll(Optional.ofNullable(customTopics).orElse(Collections.emptyList()));

        if (mergedTopics.isEmpty()) {
            LOGGER.warning("No topic names provided in configuration or by parameter. Nothing sent.");
            return Collections.emptyList();
        }

        List<Future<RecordMetadata>> recordMetadataFutures = new ArrayList<>(mergedTopics.size());

        for (String topic : mergedTopics) {
            ProducerRecord<K, V> record = new ProducerRecord<>(topic, partition, timestamp, key, value, headers);
            LOGGER.fine(String.format("Sending topic: %s to partition %d", topic, partition));
            recordMetadataFutures.add(producer.send(record));
        }
        return recordMetadataFutures;
    }

    @Override
    public void close() {
        producer.close();
    }
}
