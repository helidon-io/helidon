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

import java.util.function.Consumer;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Reactive streams publisher using {@link java.util.function.Consumer} instead of reactive streams.
 *
 * @param <K> kafka record key type
 * @param <V> kafka record value type
 */
public class SimplePublisher<K, V> implements Publisher<KafkaMessage<K, V>> {

    private Consumer<Subscriber<? super KafkaMessage<K, V>>> publisher;

    /**
     * Create new Reactive Streams publisher using {@link java.util.function.Consumer} instead of reactive streams.
     *
     * @param publisher {@link java.util.function.Consumer}
     */
    public SimplePublisher(Consumer<Subscriber<? super KafkaMessage<K, V>>> publisher) {
        this.publisher = publisher;
    }

    @Override
    public void subscribe(Subscriber<? super KafkaMessage<K, V>> s) {
        publisher.accept(s);
    }
}
