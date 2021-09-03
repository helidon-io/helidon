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

package io.helidon.microprofile.messaging.inner.publisher;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Publisher;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.inner.AbstractShapeTestBean;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
public class PublisherSubscriberBuilderV2Bean extends AbstractShapeTestBean {

    @Outgoing("subscriber-builder-payload")
    public Publisher<Message<String>> sourceForSubscriberBuilderPayload() {
        return ReactiveStreams.fromIterable(TEST_DATA).map(Message::of).buildRs();
    }

    @Incoming("subscriber-builder-payload")
    public SubscriberBuilder<String, Void> subscriberBuilderOfPayloads() {
        return ReactiveStreams.<String>builder().forEach(p -> testLatch.countDown());
    }



}
