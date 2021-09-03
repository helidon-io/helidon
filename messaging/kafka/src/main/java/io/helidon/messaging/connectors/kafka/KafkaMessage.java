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
 */

package io.helidon.messaging.connectors.kafka;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Kafka specific Micro Profile Reactive Messaging Message.
 *
 * @param <K> the type of Kafka record key
 * @param <V> the type of Kafka record value
 */
public interface KafkaMessage<K, V> extends Message<V> {

    /**
     * Name of the topic from which was this message received.
     *
     * @return topic name
     */
    Optional<String> getTopic();

    /**
     * Number of partition from which was this message received.
     *
     * @return partition number
     */
    Optional<Integer> getPartition();

    /**
     * Offset of the record in partition from which was this message received.
     *
     * @return offset number
     */
    Optional<Long> getOffset();

    /**
     * Returns {@link org.apache.kafka.clients.consumer.ConsumerRecord} if message was received from Kafka,
     * otherwise return {@code Optional.empty()}.
     *
     * @return {@link org.apache.kafka.clients.consumer.ConsumerRecord} or {@code Optional.empty()}
     */
    Optional<ConsumerRecord<K, V>> getConsumerRecord();

    /**
     * Key or {@code Optional.empty()} if non is specified.
     *
     * @return Key or {@code Optional.empty()}
     */
    Optional<K> getKey();

    /**
     * Returns {@link org.apache.kafka.common.header.Headers} received from Kafka with record
     * or empty headers if message was not created by Kafka connector.
     *
     * @return {@link org.apache.kafka.common.header.Headers} received from Kafka
     * or empty headers if message was not created by Kafka connector
     */
    Headers getHeaders();

    /**
     * Create a message with the given payload and ack function.
     *
     * @param key     Kafka record key
     * @param payload Kafka record value
     * @param ack     The ack function, this will be invoked when the returned messages {@link #ack()} method is invoked
     * @param <K>     the type of Kafka record key
     * @param <V>     the type of Kafka record value
     * @return A message with the given payload and ack function
     */
    static <K, V> KafkaMessage<K, V> of(K key, V payload, Supplier<CompletionStage<Void>> ack) {
        Objects.requireNonNull(payload);
        return new KafkaProducerMessage<>(key, payload, ack);
    }

    /**
     * Create a message with the given payload and ack function.
     *
     * @param payload Kafka record value
     * @param ack     The ack function, this will be invoked when the returned messages {@link #ack()} method is invoked
     * @param <K>     the type of Kafka record key
     * @param <V>     the type of Kafka record value
     * @return A message with the given payload and ack function
     */
    static <K, V> KafkaMessage<K, V> of(V payload, Supplier<CompletionStage<Void>> ack) {
        Objects.requireNonNull(payload);
        return new KafkaProducerMessage<>(null, payload, ack);
    }

    /**
     * Create a message with the given payload and ack function.
     *
     * @param key     Kafka record key
     * @param payload Kafka record value
     * @param <K>     the type of Kafka record key
     * @param <V>     the type of Kafka record value
     * @return A message with the given payload and ack function
     */
    static <K, V> KafkaMessage<K, V> of(K key, V payload) {
        Objects.requireNonNull(payload);
        return new KafkaProducerMessage<>(key, payload, () -> CompletableFuture.completedFuture(null));
    }

    /**
     * Create a message with the given payload and ack function.
     *
     * @param payload Kafka record value
     * @param <K>     the type of Kafka record key
     * @param <V>     the type of Kafka record value
     * @return A message with the given payload and ack function
     */
    static <K, V> KafkaMessage<K, V> of(V payload) {
        Objects.requireNonNull(payload);
        return new KafkaProducerMessage<>(null, payload, () -> CompletableFuture.completedFuture(null));
    }
}
