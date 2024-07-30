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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.messaging.connectors.jms.JmsMessage;

import jakarta.jms.TextMessage;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;

@SuppressWarnings("ALL")
class WeblogicSnippets {

    class Snippet1 {

        // tag::snippet_1[]
        @Incoming("from-wls")
        public void consumeWls(String msg) {
            System.out.println("WebLogic says: " + msg);
        }
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        @Incoming("from-wls")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public CompletionStage<?> consumewls(JmsMessage<String> msg) {
            System.out.println("WebLogic says: " + msg.getPayload());
            return msg.ack();
        }
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @Outgoing("to-wls")
        public PublisherBuilder<String> produceToWls() {
            return ReactiveStreams.of("test1", "test2");
        }
        // end::snippet_3[]
    }

    class Snippet4 {

        // tag::snippet_4[]
        @Outgoing("to-wls")
        public PublisherBuilder<Message<String>> produceToJms() {
            return ReactiveStreams.of("test1", "test2")
                    .map(s -> JmsMessage.builder(s)
                            .correlationId(UUID.randomUUID().toString())
                            .property("stringProp", "cool property")
                            .property("byteProp", 4)
                            .property("intProp", 5)
                            .onAck(() -> CompletableFuture.completedStage(null)
                                    .thenRun(() -> System.out.println("Acked!")))
                            .build());
        }
        // end::snippet_4[]
    }

    class Snippet5 {

        // tag::snippet_5[]
        @Outgoing("to-wls")
        public PublisherBuilder<Message<String>> produceToJms() {
            return ReactiveStreams.of("test1", "test2")
                    .map(s -> JmsMessage.builder(s)
                            .customMapper((p, session) -> {
                                TextMessage textMessage = session.createTextMessage(p);
                                textMessage.setStringProperty("custom-mapped-property", "XXX" + p);
                                return textMessage;
                            })
                            .build()
                    );
        }
        // end::snippet_5[]
    }

}
