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

import java.util.Objects;
import java.util.UUID;

import io.helidon.config.Config;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Channel representing publisher - subscriber relationship.
 *
 * @param <PAYLOAD> payload type wrapped in {@link Message} being emitted by publisher and received by subscriber.
 */
public final class Channel<PAYLOAD> {
    private String name;
    private Publisher<Message<PAYLOAD>> publisher;
    private Subscriber<Message<PAYLOAD>> subscriber;
    private Config publisherConfig;
    private Config subscriberConfig;

    void connect() {
        Objects.requireNonNull(publisher, "Missing publisher for channel " + name);
        Objects.requireNonNull(subscriber, "Missing subscriber for channel " + name);
        publisher.subscribe(subscriber);
    }

    void setName(String name) {
        this.name = name;
    }

    Publisher<Message<PAYLOAD>> getPublisher() {
        return publisher;
    }

    @SuppressWarnings("unchecked")
    void setPublisher(Publisher<? extends Message<?>> publisher) {
        this.publisher = (Publisher<Message<PAYLOAD>>) publisher;
    }

    Subscriber<Message<PAYLOAD>> getSubscriber() {
        return subscriber;
    }

    @SuppressWarnings("unchecked")
    void setSubscriber(Subscriber<? extends Message<?>> subscriber) {
        this.subscriber = (Subscriber<Message<PAYLOAD>>) subscriber;
    }

    Config getPublisherConfig() {
        return publisherConfig;
    }

    Config getSubscriberConfig() {
        return subscriberConfig;
    }

    /**
     * Channel name, used to pair configuration of connectors vs. channel configuration.
     *
     * @return channel name
     */
    public String name() {
        return name;
    }

    /**
     * Create new empty channel with given name.
     *
     * @param name      channel name
     * @param <PAYLOAD> message payload type
     * @return new channel
     */
    public static <PAYLOAD> Channel<PAYLOAD> create(String name) {
        return Channel.<PAYLOAD>builder().name(name).build();
    }

    /**
     * Create new empty channel with random name.
     *
     * @param <PAYLOAD> message payload type
     * @return new channel
     */
    public static <PAYLOAD> Channel<PAYLOAD> create() {
        return Channel.<PAYLOAD>builder().build();
    }

    /**
     * New builder for configuring new channel.
     *
     * @param <PAYLOAD> message payload type
     * @return channel builder
     */
    public static <PAYLOAD> Channel.Builder<PAYLOAD> builder() {
        return new Channel.Builder<PAYLOAD>();
    }

    /**
     * Channel builder.
     *
     * @param <PAYLOAD> message payload type
     */
    public static final class Builder<PAYLOAD> implements io.helidon.common.Builder<Channel<PAYLOAD>> {

        private final Channel<PAYLOAD> channel = new Channel<>();

        /**
         * Channel name, used to pair configuration of connectors vs. channel configuration.
         *
         * @param name channel name
         * @return this builder
         */
        public Builder<PAYLOAD> name(String name) {
            channel.setName(name);
            return this;
        }

        /**
         * Config available to publisher connector.
         *
         * @param config config supplied to publishing connector
         * @return this builder
         */
        public Builder<PAYLOAD> publisherConfig(Config config) {
            channel.publisherConfig = config;
            return this;
        }

        /**
         * Config available to subscriber connector.
         *
         * @param config config supplied to subscribing connector
         * @return this builder
         */
        public Builder<PAYLOAD> subscriberConfig(Config config) {
            channel.subscriberConfig = config;
            return this;
        }

        @Override
        public Channel<PAYLOAD> build() {
            if (channel.name == null) {
                channel.name = UUID.randomUUID().toString();
            }
            return channel;
        }

        @Override
        public Channel<PAYLOAD> get() {
            return channel;
        }
    }
}
