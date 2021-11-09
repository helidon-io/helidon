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
 */

package io.helidon.microprofile.messaging.inner.ack.incoming;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.microprofile.messaging.AssertableTestBean;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import static org.hamcrest.Matchers.is;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
public class IncomingSubscriberMsgNoneAckBean implements AssertableTestBean {

    private static final String TEST_MSG = "test-data";
    private final CompletableFuture<Void> ackFuture = new CompletableFuture<>();
    private final AtomicReference<String> interceptedMessage = new AtomicReference<>();
    private final CompletableFuture<String> consumerFuture = new CompletableFuture<>();


    @Outgoing("test-channel")
    public Publisher<Message<String>> produceMessage() {
        return ReactiveStreams.of(Message.of(TEST_MSG, () -> {
            ackFuture.complete(null);
            return CompletableFuture.completedFuture(null);
        })).buildRs();
    }

    @Incoming("test-channel")
    @Acknowledgment(Acknowledgment.Strategy.NONE)
    public Subscriber<Message<String>> receiveMessage() {
        return ReactiveStreams.<Message<String>>builder()
                .forEach(m -> {
                    interceptedMessage.set(m.getPayload());
                    consumerFuture.complete(m.getPayload());
                }).build();
    }

    @Override
    public void assertValid() {
        await("Consuming method not invoked in time!", consumerFuture);
        assertWithOrigin("Shouldn't be acked!", !ackFuture.isDone());
        assertWithOrigin("Payload corruption!", consumerFuture.getNow(null), is(TEST_MSG));
    }
}
