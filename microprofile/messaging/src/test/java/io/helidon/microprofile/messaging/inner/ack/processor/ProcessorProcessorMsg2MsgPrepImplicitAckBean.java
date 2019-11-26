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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.AssertableTestBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;

@ApplicationScoped
public class ProcessorProcessorMsg2MsgPrepImplicitAckBean implements AssertableTestBean {

    public static final String TEST_DATA = "test-data";
    private CompletableFuture<Void> ackFuture = new CompletableFuture<>();
    private AtomicBoolean completedBeforeProcessor = new AtomicBoolean(false);
    private AtomicReference<String> RESULT_DATA = new AtomicReference<>();

    @Outgoing("inner-processor")
    public Publisher<Message<String>> produceMessage() {
        return ReactiveStreams.of(Message.of(TEST_DATA, () -> {
            ackFuture.complete(null);
            return ackFuture;
        })).buildRs();
    }

    @Incoming("inner-processor")
    @Outgoing("inner-consumer")
    public Processor<Message<String>, Message<String>> process() {
        return ReactiveStreams.<Message<String>>builder()
                .map(m -> {
                    completedBeforeProcessor.set(ackFuture.isDone());
                    return Message.of(m.getPayload(), m::ack);
                })
                .buildRs();
    }

    @Incoming("inner-consumer")
    @Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)
    public void receiveMessage(String msg) {
        RESULT_DATA.set(msg);
    }

    @Override
    public void assertValid() {
        try {
            ackFuture.toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail(e);
        }
        assertTrue(completedBeforeProcessor.get());
        assertEquals(TEST_DATA, RESULT_DATA.get());
    }
}
