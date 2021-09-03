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

package io.helidon.microprofile.messaging.inner.ack.processor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.microprofile.messaging.AssertableTestBean;
import io.helidon.microprofile.messaging.MessagingException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
public class ProcessorComplStageQueueOverflowBean implements AssertableTestBean {

    public static final String TEST_DATA = "test-data";
    private static final long QUEUE_MAX_SIZE = 2048;
    private final CompletableFuture<Throwable> overflowFuture = new CompletableFuture<>();
    private final CompletableFuture<String> forbiddenCall = new CompletableFuture<>();

    @Outgoing("inner-processor")
    public Publisher<Message<String>> produceMessage() {
        return ReactiveStreams
                .generate(() -> Message.of(TEST_DATA))
                .limit(QUEUE_MAX_SIZE + 1)
                .buildRs();
    }

    @Incoming("inner-processor")
    @Outgoing("inner-consumer")
    public CompletionStage<Message<String>> process(Message<String> msg) {
        return new CompletableFuture<>();
    }

    @Incoming("inner-consumer")
    public Subscriber<CompletionStage<Message<String>>> receiveMessage() {
        return ReactiveStreams.fromSubscriber(new Subscriber<CompletionStage<Message<String>>>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final CompletionStage<Message<String>> messageCompletionStage) {
                forbiddenCall.complete("Forbidden onNext signal intercepted.");
            }

            @Override
            public void onError(final Throwable t) {
                overflowFuture.complete(t);
            }

            @Override
            public void onComplete() {
                forbiddenCall.complete("Forbidden onComplete signal intercepted.");
            }
        }).build();
    }

    @Override
    public void assertValid() {
        await("Buffer overflow expected!", overflowFuture);
        Throwable t = overflowFuture.getNow(null);
        assertWithOrigin("Wrong exception intercepted!", t, instanceOf(MessagingException.class));
        assertWithOrigin("Wrong exception message!", t.getMessage(), containsString("Maximum size"));
        assertFalse(forbiddenCall.isDone(), () -> await("Unexpected test state!", forbiddenCall));
    }
}
