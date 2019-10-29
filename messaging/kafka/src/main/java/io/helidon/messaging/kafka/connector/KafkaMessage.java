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

package io.helidon.messaging.kafka.connector;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Kafka specific MP messaging message
 *
 * @param <K> kafka record key type
 * @param <V> kafka record value type
 */
public class KafkaMessage<K, V> implements Message<ConsumerRecord<K, V>> {

    private ConsumerRecord<K, V> consumerRecord;

    public KafkaMessage(ConsumerRecord<K, V> consumerRecord) {
        this.consumerRecord = consumerRecord;
    }

    @Override
    public ConsumerRecord<K, V> getPayload() {
        return consumerRecord;
    }

    @Override
    public CompletionStage<Void> ack() {
        //TODO: implement acknowledge
        return new CompletableFuture<>();
    }

    @Override
    public <C> C unwrap(Class<C> unwrapType) {
        if (consumerRecord.getClass().isAssignableFrom(unwrapType)) {
            return (C) consumerRecord;
        } else {
            throw new IllegalArgumentException("Can't unwrap to " + unwrapType.getName());
        }
    }
}
