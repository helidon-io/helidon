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
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.inner.AbstractShapeTestBean;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
public class PublisherBuilderTransformerV1Bean extends AbstractShapeTestBean {

    @Outgoing("publisher-for-publisher-builder-message")
    public PublisherBuilder<Integer> streamForProcessorBuilderOfMessages() {
        return ReactiveStreams.of(TEST_INT_DATA.toArray(new Integer[0]));
    }

    @Incoming("publisher-for-publisher-builder-message")
    @Outgoing("publisher-builder-message")
    public PublisherBuilder<Message<String>> processorBuilderOfMessages(PublisherBuilder<Message<Integer>> stream) {
        return stream
                .map(Message::getPayload)
                .map(i -> i + 1)
                .flatMap(i -> ReactiveStreams.of(i, i))
                .map(i -> Integer.toString(i))
                .map(Message::of);
    }

    @Incoming("publisher-builder-message")
    public void getMessgesFromProcessorBuilderOfMessages(String value) {
        getTestLatch().countDown();
    }

}
