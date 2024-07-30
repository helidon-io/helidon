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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.reactive.Multi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

@SuppressWarnings("ALL")
class IntroductionSnippets {

    class Snippet1 {

        // tag::snippet_1[]
        @Incoming("example-channel-2")
        public void printMessage(String msg) {
            System.out.println("Just received message: " + msg);
        }
        // end::snippet_1[]
    }

    class Snippet2 {
        // tag::snippet_2[]
        @Incoming("example-channel-2")
        public Subscriber<String> printMessage() {
            return ReactiveStreams.<String>builder()
                    .forEach(msg -> System.out.println("Just received message: " + msg))
                    .build();
        }
        // end::snippet_2[]
    }

    class Snippet3 {

        class MyBean {
            // tag::snippet_3[]
            @Inject
            public MyBean(@Channel("example-channel-1") Multi<String> multiChannel) {
                multiChannel
                        .map(String::toUpperCase)
                        .forEach(s -> System.out.println("Received " + s));
            }
            // end::snippet_3[]
        }
    }

    class Snippet4 {

        // tag::snippet_4[]
        @Outgoing("example-channel-1")
        public String produceMessage() {
            return "foo";
        }
        // end::snippet_4[]
    }

    class Snippet5 {

        // tag::snippet_5[]
        @Outgoing("example-channel-1")
        public Publisher<String> printMessage() {
            return ReactiveStreams.of("foo", "bar", "baz").buildRs();
        }
        // end::snippet_5[]
    }

    class Snippet6 {
        // tag::snippet_6[]
        @Inject
        @Channel("example-channel-1")
        private Emitter<String> emitter;

        @PUT
        @Path("/sendMessage")
        @Consumes(MediaType.TEXT_PLAIN)
        public Response sendMessage(final String payload) {
            emitter.send(payload);
            return Response.ok().build();
        }
        // end::snippet_6[]
    }

    class Snippet7 {

        // tag::snippet_7[]
        @Incoming("example-channel-1")
        @Outgoing("example-channel-2")
        public String processMessage(String msg) {
            return msg.toUpperCase();
        }
        // end::snippet_7[]
    }

    class Snippet8 {
        // tag::snippet_8[]
        @Incoming("example-channel-1")
        @Outgoing("example-channel-2")
        public Processor<String, String> processMessage() {
            return ReactiveStreams.<String>builder()
                    .map(String::toUpperCase)
                    .buildRs();
        }
        // end::snippet_8[]
    }

    class Snippet9 {

        // tag::snippet_9[]
        @Incoming("example-channel-1")
        @Outgoing("example-channel-2")
        public Publisher<String> processMessage(String msg) {
            return ReactiveStreams.of(msg.toUpperCase(), msg.toLowerCase()).buildRs();
        }
        // end::snippet_9[]
    }

    // tag::snippet_10[]
    @ApplicationScoped
    @Connector("example-connector")
    public class ExampleConnector implements IncomingConnectorFactory, OutgoingConnectorFactory {

        @Override
        public PublisherBuilder<? extends Message<?>> getPublisherBuilder(Config config) {
            return ReactiveStreams.of("foo", "bar")
                    .map(Message::of);
        }

        @Override
        public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(Config config) {
            return ReactiveStreams.<Message<?>>builder()
                    .map(Message::getPayload)
                    .forEach(o -> System.out.println("Connector says: " + o));
        }
    }
    // end::snippet_10[]

    class Snippet11 {

        // tag::snippet_11[]
        @Outgoing("publisher-payload")
        public PublisherBuilder<Integer> streamOfMessages() {
            return ReactiveStreams.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        }

        @Incoming("publisher-payload")
        @Outgoing("wrapped-message")
        public Message<String> rewrapMessageManually(Message<Integer> message) {
            return Message.of(Integer.toString(message.getPayload()));
        }

        @Incoming("wrapped-message")
        public void consumeImplicitlyUnwrappedMessage(String value) {
            System.out.println("Consuming message: " + value);
        }
        // end::snippet_11[]
    }

    class Snippet12 {

        // tag::snippet_12[]
        @Outgoing("consume-and-ack")
        public Publisher<Message<String>> streamOfMessages() {
            return ReactiveStreams.of(Message.of("This is Payload", () -> {
                System.out.println("This particular message was acked!");
                return CompletableFuture.completedFuture(null);
            })).buildRs();
        }

        @Incoming("consume-and-ack")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public CompletionStage<Void> receiveAndAckMessage(Message<String> msg) {
            return msg.ack(); //<1>
        }
        // end::snippet_12[]
    }

    class Snippet13 {

        // tag::snippet_13[]
        @Outgoing("consume-and-ack")
        public Publisher<Message<String>> streamOfMessages() {
            return ReactiveStreams.of(Message.of("This is Payload", () -> {
                System.out.println("This particular message was acked!");
                return CompletableFuture.completedFuture(null);
            })).buildRs();
        }

        @Incoming("consume-and-ack")
        @Acknowledgment(Acknowledgment.Strategy.MANUAL)
        public CompletionStage<Void> receiveAndAckMessage(Message<String> msg) {
            return msg.ack(); //<1>
        }
    }
    // end::snippet_13[]

    class Snippet14 {

        // tag::snippet_14[]
        @Outgoing("consume-and-ack")
        public Publisher<Message<String>> streamOfMessages() {
            return ReactiveStreams.of(Message.of("This is Payload", () -> {
                System.out.println("This particular message was acked!");
                return CompletableFuture.completedFuture(null);
            })).buildRs();
        }

        /**
         * Prints to the console:
         * > This particular message was acked!
         * > Method invocation!
         */
        @Incoming("consume-and-ack")
        @Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)
        public CompletionStage<Void> receiveAndAckMessage(Message<String> msg) {
            System.out.println("Method invocation!");
            return CompletableFuture.completedFuture(null);
        }
        // end::snippet_14[]
    }

    class Snippet15 {

        // tag::snippet_15[]
        @Outgoing("consume-and-ack")
        public Publisher<Message<String>> streamOfMessages() {
            return ReactiveStreams.of(Message.of("This is Payload", () -> {
                System.out.println("This particular message was acked!");
                return CompletableFuture.completedFuture(null);
            })).buildRs();
        }

        /**
         * Prints to the console:
         * > Method invocation!
         * > This particular message was acked!
         */
        @Incoming("consume-and-ack")
        @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
        public CompletionStage<Void> receiveAndAckMessage(Message<String> msg) {
            System.out.println("Method invocation!");
            return CompletableFuture.completedFuture(null);
        }
        // end::snippet_15[]
    }

    class Snippet16 {

        // tag::snippet_16[]
        @Outgoing("to-connector-channel")
        public Publisher<String> produce() {
            return ReactiveStreams.of("fee", "fie").buildRs();
        }

        // > Connector says: fee
        // > Connector says: fie
        // end::snippet_16[]
    }

    class Snippet17 {

        // tag::snippet_17[]
        @Incoming("from-connector-channel")
        public void consume(String value) {
            System.out.println("Consuming: " + value);
        }

        // >Consuming:foo
        // >Consuming:bar
        // end::snippet_17[]
    }

    class Snippet18 {

        // tag::snippet_18[]
        @ApplicationScoped
        @Connector("example-connector")
        public class ExampleConnector implements IncomingConnectorFactory {

            @Override
            public PublisherBuilder<? extends Message<?>> getPublisherBuilder(final Config config) {

                String firstPropValue = config.getValue("channel-specific-prop", String.class); // <1>
                String secondPropValue = config.getValue("connector-specific-prop", String.class);
                String channelName = config.getValue("channel-name", String.class); // <2>

                return ReactiveStreams.of(firstPropValue, secondPropValue)
                        .map(Message::of);
            }
        }
        // end::snippet_18[]
    }

    class Snippet19 {

        // tag::snippet_19[]
        @Incoming("from-connector-channel")
        public void consume(String value) {
            System.out.println("Consuming: " + value);
        }

        // > Consuming: foo
        // > Consuming: bar
        // end::snippet_19[]
    }
}
