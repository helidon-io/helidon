/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.connectors.kafka;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * Basic Kafka producer covering basic use-cases.
 * Configurable by Helidon {@link io.helidon.config.Config Config},
 * For more info about configuration see {@link HelidonToKafkaConfigParser}.
 *
 * @param <K> Key type
 * @param <V> Value type
 * @see HelidonToKafkaConfigParser
 * @see io.helidon.config.Config
 */
class BasicKafkaProducer<K, V> implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(BasicKafkaProducer.class.getName());
    private final List<String> topics;
    private final Producer<K, V> producer;

    BasicKafkaProducer(List<String> topics, Producer<K, V> producer) {
        this.topics = topics;
        this.producer = producer;
    }

    /**
     * Send record to all provided topics, doesn't wait for server acknowledgement.
     *
     * @param value Will be serialized by value.serializer class defined in configuration
     * @return Futures of server acknowledged metadata about sent topics
     */
    List<Future<RecordMetadata>> produceAsync(V value) {
        List<Future<RecordMetadata>> recordMetadataFutures = new ArrayList<>(topics.size());
        for (String topic : topics) {
            ProducerRecord<K, V> record = new ProducerRecord<K, V>(topic, value);
            recordMetadataFutures.add(producer.send(record));
        }
        return recordMetadataFutures;
    }

    /**
     * Closes the Kafka producer.
     */
    @Override
    public void close() {
        LOGGER.fine("Closing kafka producer");
        producer.close();
    }
}
