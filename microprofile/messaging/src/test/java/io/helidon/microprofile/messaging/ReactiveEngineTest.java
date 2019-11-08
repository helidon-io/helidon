/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.messaging;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

//TODO: Move to reactive bridge module
public class ReactiveEngineTest {

    @Test
    void multipleStages() {
        // Create a stream of words
        ReactiveStreams.of("hello", "from", "helidon", "reactive", "stream", "operators")
                .map(String::toUpperCase) // Transform the words
                .filter(s -> s.length() > 4) // Filter items
                .forEach(word -> System.out.println(">> " + word)) // Terminal operation
                .run(); // Run it (create the streams, subscribe to it...)
    }

    @Test
    void testTestHelidon() {
        Publisher<String> publisher = ReactiveStreams.of("test1", "test2", "test3")
                .buildRs();
        LatchSubscriber<String> subscriber = new LatchSubscriber<>();

        ReactiveStreams
                .fromPublisher(publisher)
                .to(ReactiveStreams.fromSubscriber(subscriber))
                .run()
                .toCompletableFuture();
        subscriber.assertNextCalled();
    }

    private class LatchSubscriber<T> extends CountDownLatch implements Subscriber<T> {

        public LatchSubscriber() {
            super(1);
        }


        @Override
        public void onSubscribe(Subscription s) {

        }

        @Override
        public void onNext(T t) {
            countDown();
        }

        @Override
        public void onError(Throwable t) {

        }

        @Override
        public void onComplete() {

        }

        public void assertNextCalled() {
            try {
                assertTrue(this.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                fail(e);
            }
        }
    }
}
