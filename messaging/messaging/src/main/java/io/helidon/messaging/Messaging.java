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
 *
 */

package io.helidon.messaging;

import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.reactive.Multi;
import io.helidon.config.Config;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Helidon Reactive Messaging.
 */
public interface Messaging {

    /**
     * Connect all channels and start streaming.
     *
     * @return started messaging
     */
    Messaging start();

    /**
     * Invoke stop method in all connectors implementing it. Stopped messaging cannot be started again.
     */
    void stop();

    /**
     * Create builder for constructing new Messaging.
     *
     * @return new builder
     */
    static Builder builder() {
        return new Builder();
    }

    final class Builder implements io.helidon.common.Builder<Messaging> {

        private final MessagingImpl messaging;

        private Builder() {
            messaging = new MessagingImpl();
        }

        /**
         * Configuration needed for configuring connector and their routing.
         *
         * @param config config for connectors and their routes.
         * @return this builder
         */
        public Builder config(Config config) {
            messaging.setConfig(config);
            return this;
        }

        /**
         * Add connector implementing {@link IncomingConnectorFactory}, {@link OutgoingConnectorFactory} or both.
         *
         * @param connector connector to add.
         * @return this builder
         */
        public Builder connector(ConnectorFactory connector) {
            if (connector instanceof IncomingConnectorFactory) {
                this.messaging.addIncomingConnector((IncomingConnectorFactory) connector);
            }
            if (connector instanceof IncomingConnectorFactory) {
                this.messaging.addOutgoingConnector((OutgoingConnectorFactory) connector);
            }
            return this;
        }

        /**
         * Register new emitter and all its channels.
         *
         * @param emitter   to be registered
         * @param <PAYLOAD> message payload type
         * @return this builder
         */
        public <PAYLOAD> Builder emitter(Emitter<PAYLOAD> emitter) {
            messaging.addEmitter(emitter);
            for (Channel<PAYLOAD> ch : emitter.channels()) {
                this.messaging.registerChannel(ch);
                ch.setPublisher(emitter);
            }
            return this;
        }

        /**
         * Register {@link PublisherBuilder} to be used for construction of the publisher for supplied {@link Channel}.
         *
         * @param channel          to be publisher constructed for
         * @param publisherBuilder to be used for construction of the publisher
         * @param <PAYLOAD>        message payload type
         * @return this builder
         */
        public <PAYLOAD> Builder publisher(Channel<PAYLOAD> channel,
                                           PublisherBuilder<? extends Message<? extends PAYLOAD>> publisherBuilder) {
            return this.publisher(channel, publisherBuilder.buildRs());
        }

        /**
         * Register {@link Publisher} to be used for supplied {@link Channel}.
         *
         * @param channel   to use publisher in
         * @param publisher to publish in supplied channel
         * @param wrapper   function to be used for raw payload wrapping,
         *                  if null simple {@link Message#of(Object)} is used.
         * @param <PAYLOAD> message payload type
         * @return this builder
         */
        public <PAYLOAD> Builder publisher(Channel<PAYLOAD> channel,
                                           Publisher<? extends PAYLOAD> publisher,
                                           Function<? super PAYLOAD, ? extends Message<? extends PAYLOAD>> wrapper) {
            if (wrapper == null) {
                wrapper = Message::of;
            }
            return this.publisher(channel, ReactiveStreams.fromPublisher(publisher).map(wrapper).buildRs());
        }

        /**
         * Register {@link Flow.Publisher} to be used for supplied {@link Channel}.
         *
         * @param channel   to use publisher in
         * @param publisher to publish in supplied channel
         * @param wrapper   function to be used for raw payload wrapping,
         *                  if null simple {@link Message#of(Object)} is used.
         * @param <PAYLOAD> message payload type
         * @return this builder
         */
        public <PAYLOAD> Builder publisher(Channel<PAYLOAD> channel,
                                           Flow.Publisher<? extends PAYLOAD> publisher,
                                           Function<? super PAYLOAD, ? extends Message<? extends PAYLOAD>> wrapper) {
            if (wrapper == null) {
                wrapper = Message::of;
            }
            return this.publisher(channel, FlowAdapters.toPublisher(publisher), wrapper);
        }

        /**
         * Register {@link Flow.Publisher} to be used for supplied {@link Channel}.
         *
         * @param channel   to use publisher in
         * @param publisher to publish in supplied channel
         * @param <PAYLOAD> message payload type
         * @return this builder
         */
        public <PAYLOAD> Builder publisher(Channel<PAYLOAD> channel,
                                           Flow.Publisher<? extends Message<? extends PAYLOAD>> publisher) {
            return publisher(channel, FlowAdapters.toPublisher(publisher));
        }

        /**
         * Register {@link Publisher} to be used for supplied {@link Channel}.
         *
         * @param channel   to use publisher in
         * @param publisher to publish in supplied channel
         * @param <PAYLOAD> message payload type
         * @return this builder
         */
        public <PAYLOAD> Builder publisher(Channel<PAYLOAD> channel,
                                           Publisher<? extends Message<? extends PAYLOAD>> publisher) {
            this.messaging.registerChannel(channel);
            channel.setPublisher(publisher);
            return this;
        }

        /**
         * Register {@link java.util.function.Consumer} for listening every payload coming from upstream.
         * This listener creates unbounded({@link Long#MAX_VALUE}) demand.
         * {@link org.eclipse.microprofile.reactive.messaging.Message}s are automatically acked and unwrapped.
         * Equivalent of
         * {@link org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder#forEach(java.util.function.Consumer)}.
         *
         * @param channel   to use subscriber in
         * @param consumer  to consume payloads
         * @param <PAYLOAD> message payload type
         * @return this builder
         */
        public <PAYLOAD> Builder listener(Channel<PAYLOAD> channel,
                                          Consumer<? super PAYLOAD> consumer) {
            this.messaging.registerChannel(channel);
            channel.setSubscriber(Builder.<PAYLOAD>unwrapProcessorBuilder()
                    .forEach(consumer)
                    .build());
            return this;
        }

        /**
         * Register {@link Flow.Subscriber} to be used for supplied {@link Channel}.
         *
         * @param channel    to use subscriber in
         * @param subscriber to subscribe to supplied channel
         * @param <PAYLOAD>  message payload type
         * @return this builder
         */
        public <PAYLOAD> Builder subscriber(Channel<PAYLOAD> channel,
                                            Flow.Subscriber<? extends Message<? extends PAYLOAD>> subscriber) {
            this.subscriber(channel, FlowAdapters.toSubscriber(subscriber));
            return this;
        }

        /**
         * Use provided {@link Multi} to subscribe to supplied {@link Channel}.
         *
         * @param channel   to use subscriber in
         * @param consumer  to subscribe to supplied channel
         * @param <PAYLOAD> message payload type
         * @return this builder
         */
        public <PAYLOAD> Builder subscriber(Channel<PAYLOAD> channel,
                                            Consumer<Multi<? extends Message<? extends PAYLOAD>>> consumer) {
            Processor<? extends Message<? extends PAYLOAD>, ? extends Message<? extends PAYLOAD>> processor =
                    ReactiveStreams.<Message<? extends PAYLOAD>>builder().buildRs();
            consumer.accept(Multi.create(FlowAdapters.toFlowPublisher(processor)));
            this.subscriber(channel, processor);
            return this;
        }

        /**
         * Register {@link SubscriberBuilder} to be used for creating {@link Subscriber} for supplied {@link Channel}.
         *
         * @param channel           to use subscriber in
         * @param subscriberBuilder to subscribe to supplied channel
         * @param <PAYLOAD>         message payload type
         * @param <RESULT>          result type
         * @return this builder
         */
        public <PAYLOAD, RESULT> Builder subscriber(
                Channel<PAYLOAD> channel,
                SubscriberBuilder<? extends Message<? extends PAYLOAD>, RESULT> subscriberBuilder) {
            this.subscriber(channel, subscriberBuilder.build());
            return this;
        }

        /**
         * Register {@link Subscriber} to be used for supplied {@link Channel}.
         *
         * @param channel    to use subscriber in
         * @param subscriber to subscribe to supplied channel
         * @param <PAYLOAD>  message payload type
         * @return this builder
         */
        public <PAYLOAD> Builder subscriber(Channel<PAYLOAD> channel,
                                            Subscriber<? extends Message<? extends PAYLOAD>> subscriber) {
            this.messaging.registerChannel(channel);
            channel.setSubscriber(subscriber);
            return this;
        }

        /**
         * Register {@link Processor} to be used is {@code in} {@link Channel}'s subscriber
         * and {@code out} {@link Channel}'s publisher.
         *
         * @param in        {@link Channel} to use supplied {@link Processor} as subscriber in
         * @param out       {@link Channel} to use supplied {@link Processor} as publisher in
         * @param processor to be used between supplied channels
         * @param <PAYLOAD> message payload type of in channel
         * @param <RESULT>  message payload type of out channel
         * @return this builder
         */
        public <PAYLOAD, RESULT> Builder processor(
                Channel<PAYLOAD> in,
                Channel<RESULT> out,
                Processor<? extends Message<? extends PAYLOAD>, ? extends Message<? extends RESULT>> processor) {

            this.messaging.registerChannel(in);
            this.messaging.registerChannel(out);
            in.setSubscriber(processor);
            out.setPublisher(processor);
            return this;
        }

        /**
         * Register {@link ProcessorBuilder} for building {@link Processor}
         * to be used is {@code in} {@link Channel}'s subscriber and {@code out} {@link Channel}'s publisher.
         *
         * @param in               {@link Channel} to use supplied {@link Processor} as subscriber in
         * @param out              {@link Channel} to use supplied {@link Processor} as publisher in
         * @param processorBuilder to be used between supplied channels
         * @param <PAYLOAD>        message payload type of in channel
         * @param <RESULT>         message payload type of out channel
         * @return this builder
         */
        public <PAYLOAD, RESULT> Builder processor(
                Channel<PAYLOAD> in, Channel<RESULT> out,
                ProcessorBuilder<? extends Message<? extends PAYLOAD>, ? extends Message<? extends RESULT>> processorBuilder) {

            return processor(in, out, processorBuilder.buildRs());
        }

        /**
         * Register a mapping function between two channels.
         *
         * @param in              {@link Channel} to map from
         * @param out             {@link Channel} to map to
         * @param messageFunction mapping function
         * @param <PAYLOAD>       message payload type of in channel
         * @param <RESULT>        message payload type of out channel
         * @return this builder
         */
        public <PAYLOAD, RESULT> Builder processor(Channel<PAYLOAD> in, Channel<RESULT> out,
                                                   Function<? super PAYLOAD, ? extends RESULT> messageFunction) {

            Processor<? extends Message<? extends PAYLOAD>, ? extends Message<? extends RESULT>> processor =
                    Builder.<PAYLOAD>unwrapProcessorBuilder()
                            .map(messageFunction)
                            .via(Builder.<RESULT>wrapProcessorBuilder())
                            .buildRs();

            return processor(in, out, processor);
        }

        /**
         * Build new {@link io.helidon.messaging.Messaging} instance.
         *
         * @return new instance of {@link io.helidon.messaging.Messaging}
         */
        public Messaging build() {
            if (messaging.getConfig() == null) {
                messaging.setConfig(Config.create());
            }
            return messaging;
        }

        private static <PAYLOAD> ProcessorBuilder<? super PAYLOAD, Message<? extends PAYLOAD>> wrapProcessorBuilder() {
            return ReactiveStreams.<PAYLOAD>builder()
                    .map(Message::of);
        }

        private static <PAYLOAD> ProcessorBuilder<? extends Message<? extends PAYLOAD>, ? extends PAYLOAD>
        unwrapProcessorBuilder() {
            return ReactiveStreams.<Message<? extends PAYLOAD>>builder()
                    .peek(Message::ack)
                    .<PAYLOAD>map(Message::getPayload);
        }
    }


}
