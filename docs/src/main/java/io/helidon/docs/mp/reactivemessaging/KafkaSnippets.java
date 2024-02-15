/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp.reactivemessaging;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;

@SuppressWarnings("ALL")
class KafkaSnippets {

    // tag::snippet_1[]
    @Incoming("from-kafka")
    public void consumeKafka(String msg) {
        System.out.println("Kafka says: " + msg);
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Outgoing("to-kafka")
    public PublisherBuilder<String> produceToKafka() {
        return ReactiveStreams.of("test1", "test2");
    }
    // end::snippet_2[]

}
