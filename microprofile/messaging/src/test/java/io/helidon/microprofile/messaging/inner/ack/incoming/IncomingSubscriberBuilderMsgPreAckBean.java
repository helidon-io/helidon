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

package io.helidon.microprofile.messaging.inner.ack.incoming;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.AssertableTestBean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Publisher;

@ApplicationScoped
public class IncomingSubscriberBuilderMsgPreAckBean implements AssertableTestBean {

    private static final String TEST_MSG = "test-data";
    private CompletableFuture<Void> ackFuture = new CompletableFuture<>();
    private AtomicBoolean completedBeforeProcessor = new AtomicBoolean(false);
    private AtomicBoolean interceptedMessage = new AtomicBoolean(false);

    @Outgoing("test-channel")
    public Publisher<Message<String>> produceMessage() {
        return ReactiveStreams.of(Message.of(TEST_MSG, () -> {
            ackFuture.complete(null);
            return CompletableFuture.completedFuture(null);
        })).buildRs();
    }

    @Incoming("test-channel")
    @Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)
    public SubscriberBuilder<Message<String>, Void> receiveMessage() {
        return ReactiveStreams.<Message<String>>builder()
                .forEach(m -> {
                    completedBeforeProcessor.set(ackFuture.isDone());
                    interceptedMessage.set(TEST_MSG.equals(m.getPayload()));
                });
    }

    @Override
    public void assertValid() {
        try {
            ackFuture.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail(e);
        }
        assertTrue(completedBeforeProcessor.get());
        assertTrue(interceptedMessage.get());
    }
}
