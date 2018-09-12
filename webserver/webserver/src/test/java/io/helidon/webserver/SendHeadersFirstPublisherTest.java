/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.ReactiveStreamsAdapter;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link SendHeadersFirstPublisher}.
 */
public class SendHeadersFirstPublisherTest {

    @Disabled // see JC-368
    @Test
    public void subscribeUnbounded() throws Exception {
        Flow.Publisher<String> stringPublisher = ReactiveStreamsAdapter.publisherToFlow(Flux.just("a", "b", "c"));
        MockTracer tracer = new MockTracer();
        MockSpan span = tracer.buildSpan("write").start();
        HashResponseHeaders headers = mock(HashResponseHeaders.class);
        CollectStringSubscriber subscriber = new CollectStringSubscriber(s -> s.request(Long.MAX_VALUE));
        when(headers.send()).thenAnswer(c -> {
            subscriber.buffer.append("[SEND]");
            return CompletableFuture.completedFuture(headers);
        });
        SendHeadersFirstPublisher<String> publisher = new SendHeadersFirstPublisher<>(headers, span, stringPublisher);
        ForkJoinPool.commonPool().submit(() -> publisher.subscribe(subscriber));
        // Assert
        String s = subscriber.whenComplete.get(5, TimeUnit.SECONDS);
        assertEquals("[SEND]abc[COMPLETE]", s);
        assertThat(tracer.finishedSpans(), IsCollectionContaining.hasItem(span));
    }

    @Disabled // see JC-403
    @Test
    public void subscribeOnEmpty() throws Exception {
        Flow.Publisher<String> stringPublisher = ReactiveStreamsAdapter.publisherToFlow(Mono.empty());
        MockTracer tracer = new MockTracer();
        MockSpan span = tracer.buildSpan("write").start();
        HashResponseHeaders headers = mock(HashResponseHeaders.class);
        CollectStringSubscriber subscriber = new CollectStringSubscriber(s -> s.request(Long.MAX_VALUE));
        when(headers.send()).thenAnswer(c -> {
            subscriber.buffer.append("[SEND]");
            return CompletableFuture.completedFuture(headers);
        });
        SendHeadersFirstPublisher<String> publisher = new SendHeadersFirstPublisher<>(headers, span, stringPublisher);
        ForkJoinPool.commonPool().submit(() -> publisher.subscribe(subscriber));
        // Assert
        String s = subscriber.whenComplete.get(5, TimeUnit.SECONDS);
        assertEquals("[SEND][COMPLETE]", s);
        assertThat(tracer.finishedSpans(), IsCollectionContaining.hasItem(span));
    }

    @Test
    public void rejectSecondSubscriber() throws Exception {
        Flow.Publisher<String> stringPublisher = ReactiveStreamsAdapter.publisherToFlow(Mono.empty());
        MockTracer tracer = new MockTracer();
        MockSpan span = tracer.buildSpan("write").start();
        HashResponseHeaders headers = mock(HashResponseHeaders.class);
        CollectStringSubscriber subscriber = new CollectStringSubscriber(s -> s.request(Long.MAX_VALUE));
        CollectStringSubscriber subscriberFail = new CollectStringSubscriber(s -> s.request(Long.MAX_VALUE));
        when(headers.send()).thenAnswer(c -> {
            subscriber.buffer.append("[SEND-FIRST]");
            subscriberFail.buffer.append("[SEND-SECOND]");
            return CompletableFuture.completedFuture(headers);
        });
        SendHeadersFirstPublisher<String> publisher = new SendHeadersFirstPublisher<>(headers, span, stringPublisher);
        // First path
        ForkJoinPool.commonPool().submit(() -> publisher.subscribe(subscriber));
        String s = subscriber.whenComplete.get(5, TimeUnit.SECONDS);
        assertEquals("[SEND-FIRST][COMPLETE]", s);
        // Second fail
        subscriberFail.buffer.setLength(0);
        ForkJoinPool.commonPool().submit(() -> publisher.subscribe(subscriberFail));
        s = subscriberFail.whenComplete.exceptionally(t -> t.getClass().getSimpleName()).get(5, TimeUnit.SECONDS);
        assertEquals("IllegalStateException", s);
        assertThat(tracer.finishedSpans(), IsCollectionContaining.hasItem(span));
    }

    static class CollectStringSubscriber implements Flow.Subscriber<String> {

        private final StringBuffer buffer = new StringBuffer();
        private final CompletableFuture<String> whenComplete = new CompletableFuture<>();
        private final Consumer<Flow.Subscription> subscribeConsumer;
        private final BiConsumer<Flow.Subscription, String> nextConsumer;
        private volatile Flow.Subscription subscription;

        CollectStringSubscriber(Consumer<Flow.Subscription> subscribeConsumer,
                                BiConsumer<Flow.Subscription, String> nextConsumer) {
            this.subscribeConsumer = subscribeConsumer;
            this.nextConsumer = nextConsumer;
        }

        CollectStringSubscriber(Consumer<Flow.Subscription> subscribeConsumer) {
            this(subscribeConsumer, null);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (subscribeConsumer == null) {
                subscription.request(Long.MAX_VALUE);
            } else {
                subscribeConsumer.accept(subscription);
            }
        }

        @Override
        public void onNext(String item) {
            buffer.append(item);
            if (nextConsumer != null) {
                nextConsumer.accept(subscription, item);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            buffer.append("[").append(throwable.getClass().getSimpleName()).append("]");
            whenComplete.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            buffer.append("[COMPLETE]");
            whenComplete.complete(buffer.toString());
        }
    }

}
