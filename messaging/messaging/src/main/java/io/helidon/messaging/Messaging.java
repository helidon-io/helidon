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
 *
 */

package io.helidon.messaging;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
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
 * Helidon Reactive Messaging
 */
public interface Messaging {

    /**
     * Connect all channels and start streaming.
     */
    void start();

    /**
     * Invoke stop method in all connectors implementing it. Stopped messaging cannot be started again.
     */
    void stop();

    <T> Emitter<T> emitter(String channel);

    <T> CompletionStage<Void> send(String channel, T msg);

    <T, M extends Message<T>> void send(String channel, M msg);

    /**
     * Create builder for constructing new Messaging.
     *
     * @return new builder
     */
    static Builder builder() {
        return new Builder();
    }

    final class Builder implements io.helidon.common.Builder<Messaging> {

        static {
            HelidonFeatures.register(HelidonFlavor.SE, "Messaging");
        }

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

        public <PAYLOAD> Builder emitter(Emitter<PAYLOAD> emitter) {
            messaging.addEmitter(emitter);
            for (Channel<PAYLOAD> ch : emitter.channels()) {
                this.messaging.registerChannel(ch);
                ch.setPublisher(emitter);
            }
            return this;
        }

        public <PAYLOAD> Builder publisher(Channel<PAYLOAD> channel,
                                           PublisherBuilder<Message<PAYLOAD>> publisherBuilder) {
            return this.publisher(channel, publisherBuilder.buildRs());
        }

        public <PAYLOAD> Builder publisher(Channel<PAYLOAD> channel,
                                           Publisher<PAYLOAD> publisher,
                                           Function<PAYLOAD, Message<PAYLOAD>> wrapper) {
            return this.publisher(channel, ReactiveStreams.fromPublisher(publisher).map(wrapper).buildRs());
        }

        public <PAYLOAD> Builder publisher(Channel<PAYLOAD> channel,
                                           Flow.Publisher<PAYLOAD> publisher,
                                           Function<PAYLOAD, Message<PAYLOAD>> wrapper) {
            return this.publisher(channel, FlowAdapters.toPublisher(publisher), wrapper);
        }

        public <PAYLOAD> Builder publisher(Channel<PAYLOAD> channel,
                                           Flow.Publisher<Message<PAYLOAD>> publisher) {
            return publisher(channel, FlowAdapters.toPublisher(publisher));
        }

        public <PAYLOAD> Builder publisher(Channel<PAYLOAD> channel,
                                           Publisher<Message<PAYLOAD>> publisher) {
            this.messaging.registerChannel(channel);
            channel.setPublisher(publisher);
            return this;
        }

        public <PAYLOAD> Builder listener(Channel<PAYLOAD> channel, Consumer<PAYLOAD> consumer) {
            this.messaging.registerChannel(channel);
            channel.setSubscriber(Builder.<PAYLOAD>unwrapProcessorBuilder()
                    .forEach(consumer)
                    .build());
            return this;
        }

        public <PAYLOAD, RESULT> Builder subscriber(Channel<PAYLOAD> channel, Flow.Subscriber<Message<PAYLOAD>> subscriber) {
            this.subscriber(channel, FlowAdapters.toSubscriber(subscriber));
            return this;
        }

        public <PAYLOAD, RESULT> Builder subscriber(Channel<PAYLOAD> channel, Consumer<Multi<Message<PAYLOAD>>> subscriber) {
            Processor<Message<PAYLOAD>, Message<PAYLOAD>> processor = ReactiveStreams.<Message<PAYLOAD>>builder().buildRs();
            subscriber.accept(Multi.from(FlowAdapters.toFlowPublisher(processor)));
            this.subscriber(channel, processor);
            return this;
        }

        public <PAYLOAD, RESULT> Builder subscriber(Channel<PAYLOAD> channel,
                                                    SubscriberBuilder<Message<PAYLOAD>, RESULT> subscriberBuilder) {
            this.subscriber(channel, subscriberBuilder.build());
            return this;
        }

        public <PAYLOAD> Builder subscriber(Channel<PAYLOAD> channel,
                                            Subscriber<Message<PAYLOAD>> subscriber) {
            this.messaging.registerChannel(channel);
            ((Channel<PAYLOAD>) channel)
                    .setSubscriber(subscriber);
            return this;
        }

        public <PAYLOAD, RESULT> Builder processor(Channel<PAYLOAD> in, Channel<RESULT> out,
                                                   Processor<Message<PAYLOAD>, Message<RESULT>> processor) {
            this.messaging.registerChannel(in);
            this.messaging.registerChannel(out);
            in.setSubscriber(processor);
            out.setPublisher(processor);
            return this;
        }

        public <PAYLOAD, RESULT> Builder processor(Channel<PAYLOAD> in, Channel<RESULT> out,
                                                   ProcessorBuilder<Message<PAYLOAD>, Message<RESULT>> processorBuilder) {

            Processor<Message<PAYLOAD>, Message<RESULT>> processor = processorBuilder.buildRs();
            return processor(in, out, processor);
        }

        public <PAYLOAD, RESULT> Builder processor(Channel<PAYLOAD> in, Channel<RESULT> out,
                                                   Function<PAYLOAD, RESULT> messageFunction) {

            Processor<Message<PAYLOAD>, Message<RESULT>> processor =
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
            return messaging;
        }

        private static <PAYLOAD> ProcessorBuilder<PAYLOAD, Message<PAYLOAD>> wrapProcessorBuilder() {
            return ReactiveStreams.<PAYLOAD>builder()
                    .<Message<PAYLOAD>>map(Message::of);
        }

        private static <PAYLOAD> ProcessorBuilder<Message<PAYLOAD>, PAYLOAD> unwrapProcessorBuilder() {
            return ReactiveStreams.<Message<PAYLOAD>>builder()
                    .peek(Message::ack)
                    .<PAYLOAD>map(Message::getPayload);
        }
    }


}
