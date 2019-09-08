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

package io.helidon.dbclient.common;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import io.helidon.common.reactive.Flow;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsEmptyCollection.empty;

/**
 * Unit test for {@link SubmissionPublisher}.
 */
class SubmissionPublisherTest {
    @Test
    void testEmpty() {
        SubmissionPublisher<String> publisher = SubmissionPublisher.create();
        publisher.offer("test1");
        publisher.submit("test2");
        publisher.completeExceptionally(new TimeoutException("Timed out"));
    }

    @Test
    void testEarly() {
        TestSubscriber subscriber = new TestSubscriber();
        SubmissionPublisher<String> publisher = SubmissionPublisher.create();
        publisher.offer("test1");
        publisher.submit("test2");
        publisher.complete();

        publisher.subscribe(subscriber);
        assertThat(subscriber.received, empty());
        subscriber.request(1);
        assertThat(subscriber.received, contains("test1"));
        subscriber.request(1);
        assertThat(subscriber.received, contains("test1", "test2"));
        assertThat(subscriber.completed, is(true));
    }

    @Test
    void testUsual() {
        TestSubscriber subscriber = new TestSubscriber();
        SubmissionPublisher<String> publisher = SubmissionPublisher.create();
        publisher.subscribe(subscriber);
        publisher.offer("test1");
        publisher.submit("test2");
        publisher.complete();

        assertThat(subscriber.received, empty());
        subscriber.request(1);
        assertThat(subscriber.received, contains("test1"));
        subscriber.request(1);
        assertThat(subscriber.received, contains("test1", "test2"));
        assertThat(subscriber.completed, is(true));
    }

    private final class TestSubscriber implements Flow.Subscriber<String> {
        private Flow.Subscription subscription;
        private List<String> received = new LinkedList<>();
        private Throwable throwable = null;
        private boolean completed = false;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(String item) {
            received.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            this.throwable = throwable;
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        private void request(int count) {
            subscription.request(count);
        }

        private void cancel() {
            subscription.cancel();
        }
    }
}