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
 */

package io.helidon.messaging.connectors.kafka;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;

public class KafkaProducerMessage<K, V> implements KafkaMessage<K, V> {

    private final Headers headers;
    private final K key;
    private final V payload;
    private final Supplier<CompletionStage<Void>> ack;

    KafkaProducerMessage(K key, V payload, Supplier<CompletionStage<Void>> ack) {
        Objects.requireNonNull(payload);
        this.key = key;
        this.payload = payload;
        this.ack = ack;
        headers = new RecordHeaders();
    }

    @Override
    public Optional<String> getTopic() {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> getPartition() {
        return Optional.empty();
    }

    @Override
    public Optional<Long> getOffset() {
        return Optional.empty();
    }

    @Override
    public Optional<ConsumerRecord<K, V>> getConsumerRecord() {
        return Optional.empty();
    }

    @Override
    public Optional<K> getKey() {
        return Optional.ofNullable(key);
    }

    @Override
    public Headers getHeaders() {
        return this.headers;
    }

    @Override
    public V getPayload() {
        return this.payload;
    }

    @Override
    public CompletionStage<Void> ack() {
        return ack.get();
    }
}
