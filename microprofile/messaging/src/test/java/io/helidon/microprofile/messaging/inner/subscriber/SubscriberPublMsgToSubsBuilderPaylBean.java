/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.messaging.inner.subscriber;

import java.util.concurrent.CopyOnWriteArraySet;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.AssertableTestBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Publisher;

@ApplicationScoped
public class SubscriberPublMsgToSubsBuilderPaylBean implements AssertableTestBean {

    CopyOnWriteArraySet<String> RESULT_DATA = new CopyOnWriteArraySet<>();

    @Outgoing("subscriber-builder-payload")
    public Publisher<Message<String>> sourceForSubscriberBuilderPayload() {
        return ReactiveStreams.fromIterable(TEST_DATA)
                .map(Message::of)
                .buildRs();
    }

    @Incoming("subscriber-builder-payload")
    public SubscriberBuilder<String, Void> subscriberBuilderOfPayloads() {
        return ReactiveStreams.<String>builder()
                .forEach(p -> RESULT_DATA.add(p));
    }

    @Override
    public void assertValid() {
        assertTrue(RESULT_DATA.containsAll(TEST_DATA));
        assertEquals(TEST_DATA.size(), RESULT_DATA.size());
    }
}
