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

package io.helidon.microprofile.messaging.inner.ack.incoming;

import java.util.concurrent.CompletableFuture;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.AssertableTestBean;

import static org.hamcrest.Matchers.is;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Publisher;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
public class IncomingSubscriberBuilderMsgNoneAckBean implements AssertableTestBean {

    private static final String TEST_MSG = "test-data";
    private final CompletableFuture<Void> ackFuture = new CompletableFuture<>();
    private final CompletableFuture<String> consumed = new CompletableFuture<>();

    @Outgoing("test-channel")
    public Publisher<Message<String>> produceMessage() {
        return ReactiveStreams.of(Message.of(TEST_MSG, () -> {
            ackFuture.complete(null);
            return CompletableFuture.completedFuture(null);
        })).buildRs();
    }

    @Incoming("test-channel")
    @Acknowledgment(Acknowledgment.Strategy.NONE)
    public SubscriberBuilder<Message<String>, Void> receiveMessage() {
        return ReactiveStreams.<Message<String>>builder()
                .forEach(m -> {
                    consumed.complete(m.getPayload());
                });
    }

    @Override
    public void assertValid() {
        await("Message was not intercepted!", consumed);
        assertWithOrigin("Shouldn't be acked!", !ackFuture.isDone());
        assertWithOrigin("Payload corruption!", consumed.getNow(null), is(TEST_MSG));
    }
}
