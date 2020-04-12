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

import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.Objects;

public final class Channel<PAYLOAD> {
    private String name;
    private Publisher<Message<PAYLOAD>> publisher;
    private Subscriber<Message<PAYLOAD>> subscriber;

    void connect() {
        Objects.requireNonNull(publisher, "Missing publisher for channel " + name);
        Objects.requireNonNull(subscriber, "Missing subscriber for channel " + name);
        publisher.subscribe(subscriber);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Publisher<Message<PAYLOAD>> getPublisher() {
        return publisher;
    }

    public void setPublisher(Publisher<Message<PAYLOAD>> publisher) {
        this.publisher = publisher;
    }

    public Subscriber<Message<PAYLOAD>> getSubscriber() {
        return subscriber;
    }

    public void setSubscriber(Subscriber<Message<PAYLOAD>> subscriber) {
        this.subscriber = subscriber;
    }

    public String name() {
        return name;
    }

    public static <PAYLOAD> Channel<PAYLOAD> create(String name) {
       return Channel.<PAYLOAD>builder().name(name).build();
    }

    public static <PAYLOAD> Channel.Builder<PAYLOAD> builder() {
        return new Channel.Builder<PAYLOAD>();
    }

    final static class Builder<PAYLOAD> implements io.helidon.common.Builder<Channel<PAYLOAD>> {

        private final Channel<PAYLOAD> channel = new Channel<>();

        public Builder<PAYLOAD> name(String name){
            channel.setName(name);
            return this;
        }

        @Override
        public Channel<PAYLOAD> build() {
            return channel;
        }

        @Override
        public Channel<PAYLOAD> get() {
            return channel;
        }
    }
}
