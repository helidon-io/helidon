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
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.AssertableTestBean;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Publisher;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
public class IncomingMsgManualAckBean implements AssertableTestBean {

    private CompletableFuture<Void> ackFuture = new CompletableFuture<>();

    @Outgoing("test-channel")
    public Publisher<Message<String>> produceMessage() {
        return ReactiveStreams.of(Message.of("test-data", () -> {
            ackFuture.complete(null);
            return CompletableFuture.completedStage(null);
        })).buildRs();
    }

    @Incoming("test-channel")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public CompletionStage<Void> receiveMessage(Message<String> msg) {
        return msg.ack();
    }

    @Override
    public void assertValid() {
        await("Message not acked!", ackFuture);
    }
}
