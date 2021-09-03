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
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Publisher;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.inner.AbstractShapeTestBean;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
public class PublisherFromPublisherV2Bean extends AbstractShapeTestBean {

    @Outgoing("publisher-for-publisher-payload")
    public PublisherBuilder<Integer> streamForProcessorOfPayloads() {
        return ReactiveStreams.of(TEST_INT_DATA.toArray(new Integer[0]));
    }

    @Incoming("publisher-for-publisher-payload")
    @Outgoing("publisher-payload")
    public Publisher<String> processorOfPayloads(Publisher<Integer> stream) {
        return ReactiveStreams.fromPublisher(stream)
                .map(i -> i + 1)
                .flatMap(i -> ReactiveStreams.of(i, i))
                .map(i -> Integer.toString(i))
                .buildRs();
    }

    @Incoming("publisher-payload")
    public void getMessgesFromProcessorOfPayloads(String value) {
        getTestLatch().countDown();
    }
}
