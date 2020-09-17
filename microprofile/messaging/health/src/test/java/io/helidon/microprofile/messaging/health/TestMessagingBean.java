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
 */

package io.helidon.microprofile.messaging.health;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

import io.helidon.common.reactive.BufferedEmittingPublisher;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.FlowAdapters;

@ApplicationScoped
@Named("testBean")
public class TestMessagingBean {

    static final String CHANNEL_1 = "test-channel-1";
    static final String CHANNEL_2 = "test-channel-2";
    private final BufferedEmittingPublisher<String> emitter1 = BufferedEmittingPublisher.create();
    private final BufferedEmittingPublisher<String> emitter2 = BufferedEmittingPublisher.create();
    private final TestSubscriber<String> subscriber1 = new TestSubscriber<>();
    private final TestSubscriber<String> subscriber2 = new TestSubscriber<>();

    @Outgoing(CHANNEL_1)
    public PublisherBuilder<String> channel1Out() {
        return ReactiveStreams.fromPublisher(FlowAdapters.toPublisher(emitter1));
    }

    @Incoming(CHANNEL_1)
    public SubscriberBuilder<String, Void> channel1In() {
        return ReactiveStreams.fromSubscriber(subscriber1);
    }

    @Outgoing(CHANNEL_2)
    public PublisherBuilder<String> channel2Out() {
        return ReactiveStreams.fromPublisher(FlowAdapters.toPublisher(emitter2));
    }

    @Incoming(CHANNEL_2)
    public SubscriberBuilder<String, Void> channel2In() {
        return ReactiveStreams.fromSubscriber(subscriber2);
    }

    public BufferedEmittingPublisher<String> getEmitter1() {
        return emitter1;
    }

    public BufferedEmittingPublisher<String> getEmitter2() {
        return emitter2;
    }

    public TestSubscriber<String> getSubscriber1() {
        return subscriber1;
    }

    public TestSubscriber<String> getSubscriber2() {
        return subscriber2;
    }
}
