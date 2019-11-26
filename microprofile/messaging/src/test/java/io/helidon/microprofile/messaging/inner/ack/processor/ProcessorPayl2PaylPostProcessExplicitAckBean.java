/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.messaging.inner.ack.processor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.AssertableTestBean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Publisher;

@ApplicationScoped
public class ProcessorPayl2PaylPostProcessExplicitAckBean implements AssertableTestBean {

    private CompletableFuture<Void> ackFuture = new CompletableFuture<>();
    private AtomicBoolean completedBeforeProcessor = new AtomicBoolean(false);

    @Outgoing("inner-processor")
    public Publisher<Message<String>> produceMessage() {
        return ReactiveStreams.of(Message.of("test-data", () -> ackFuture)).buildRs();
    }

    @Incoming("inner-processor")
    @Outgoing("inner-consumer")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public String process(String msg) {
        completedBeforeProcessor.set(ackFuture.isDone());
        return msg.toUpperCase();
    }

    @Incoming("inner-consumer")
    public CompletionStage<Void> receiveMessage(Message<String> msg) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void assertValid() {
        try {
            ackFuture.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail(e);
        }
        assertFalse(completedBeforeProcessor.get());
    }
}
