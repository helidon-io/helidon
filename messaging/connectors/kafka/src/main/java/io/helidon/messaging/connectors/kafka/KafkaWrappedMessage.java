/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;

final class KafkaWrappedMessage<K, V> implements KafkaMessage<K, V> {
    private final KafkaMessage<K, V> original;
    private final V payload;
    private final Supplier<CompletionStage<Void>> ack;
    private final Function<Throwable, CompletionStage<Void>> nack;

    KafkaWrappedMessage(KafkaMessage<K, V> original,
                        V payload,
                        Supplier<CompletionStage<Void>> ack,
                        Function<Throwable, CompletionStage<Void>> nack) {
        this.original = Objects.requireNonNull(original);
        this.payload = Objects.requireNonNull(payload);
        this.ack = Objects.requireNonNull(ack);
        this.nack = Objects.requireNonNull(nack);
    }

    @Override
    public Optional<String> getTopic() {
        return original.getTopic();
    }

    @Override
    public Optional<Integer> getPartition() {
        return original.getPartition();
    }

    @Override
    public Optional<Long> getOffset() {
        return original.getOffset();
    }

    @Override
    public Optional<ConsumerRecord<K, V>> getConsumerRecord() {
        return original.getConsumerRecord();
    }

    @Override
    public Optional<K> getKey() {
        return original.getKey();
    }

    @Override
    public Headers getHeaders() {
        return original.getHeaders();
    }

    @Override
    public V getPayload() {
        return payload;
    }

    @Override
    public Supplier<CompletionStage<Void>> getAck() {
        return ack;
    }

    @Override
    public Function<Throwable, CompletionStage<Void>> getNack() {
        return nack;
    }

    @Override
    public <C> C unwrap(Class<C> unwrapType) {
        if (unwrapType.isInstance(this)) {
            return unwrapType.cast(this);
        }
        return original.unwrap(unwrapType);
    }
}
