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

package io.helidon.common.reactive;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

public class EmitterTest {

    @Test
    void testBackPressureWithCompleteNow() {
        BufferedEmittingPublisher<Integer> emitter = BufferedEmittingPublisher.create();

        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        emitter.subscribe(subscriber);

        emitter.emit(0);
        assertBufferSize(emitter.bufferSize(), 1);
        emitter.emit(1);
        assertBufferSize(emitter.bufferSize(), 2);
        emitter.emit(2);
        assertBufferSize(emitter.bufferSize(), 3);
        emitter.emit(3);
        assertBufferSize(emitter.bufferSize(), 4);

        subscriber
                .assertEmpty()
                .request(1)
                .assertItemCount(1)
                .request(2)
                .assertItemCount(3)
                .assertNotTerminated();

        emitter.emit(4);
        assertBufferSize(emitter.bufferSize(), 2);

        emitter.completeNow();

        assertBufferSize(emitter.bufferSize(), 0);
        subscriber.requestMax()
                .assertValues(0, 1, 2)
                .assertComplete();
    }

    @Test
    void testOnEmitCallback() {
        List<Integer> intercepted = new ArrayList<>();

        List<Integer> data = IntStream.range(0, 10)
                .boxed()
                .collect(Collectors.toList());

        BufferedEmittingPublisher<Integer> emitter = BufferedEmittingPublisher.create();
        emitter.onEmit(intercepted::add);

        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        emitter.subscribe(subscriber);

        assertThat("onEmit callback executed before first emit", intercepted.size(), is(0));

        data.forEach(emitter::emit);

        assertThat("onEmit callback executed before first request", intercepted.size(), is(0));

        subscriber.request1()
                .assertValues(0);

        assertThat("onEmit callback should have been executed exactly once", intercepted.size(), is(1));

        List<Integer> firstSixItems = data.stream().limit(6).collect(Collectors.toList());

        subscriber.request(5)
                .assertValues(firstSixItems.toArray(Integer[]::new));

        assertThat("onEmit callback should have been executed exactly 6 times", intercepted, is(firstSixItems));

        subscriber.requestMax()
                .assertValues(data.toArray(Integer[]::new));

        assertThat("onEmit callback should have been executed exactly 10 times", intercepted, is(data));

    }

    @Test
    void testCancelledEmitterReleaseSubscriberReference() throws InterruptedException {
        assertThat("Subscriber reference should be released after cancel!",
                checkReleasedSubscriber((e, s) -> {
                    s.cancel();
                }));
        assertThat("Subscriber reference should be released after cancel followed by complete!",
                checkReleasedSubscriber((e, s) -> {
                    s.cancel();
                    e.complete();
                }));
        assertThat("Subscriber reference should be released after complete followed by cancel!",
                checkReleasedSubscriber((e, s) -> {
                    e.complete();
                    s.cancel();
                }));
        assertThat("Subscriber reference should be released after fail followed by cancel!",
                checkReleasedSubscriber((e, s) -> {
                    e.fail(new RuntimeException("BOOM!"));
                    s.cancel();
                }));
        assertThat("Subscriber reference should be released after complete followed by fail!",
                checkReleasedSubscriber((e, s) -> {
                    s.cancel();
                    e.fail(new RuntimeException("BOOM!"));
                }));
    }

    private boolean checkReleasedSubscriber(
            BiConsumer<BufferedEmittingPublisher<Integer>, TestSubscriber<Integer>> biConsumer)
            throws InterruptedException {
        BufferedEmittingPublisher<Integer> emitter = BufferedEmittingPublisher.create();
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        final ReferenceQueue<TestSubscriber<Integer>> queue = new ReferenceQueue<>();
        WeakReference<TestSubscriber<Integer>> ref = new WeakReference<>(subscriber, queue);
        emitter.subscribe(subscriber);
        biConsumer.accept(emitter, subscriber);
        subscriber = null;
        System.gc();

        return ref.equals(queue.remove(100));

    }

    @Test
    void testBackPressureWithLazyComplete() {
        BufferedEmittingPublisher<Integer> emitter = BufferedEmittingPublisher.create();

        List<Integer> data = IntStream.range(0, 10)
                .boxed()
                .collect(Collectors.toList());


        data.forEach(emitter::emit);

        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        emitter.subscribe(subscriber);

        subscriber
                .assertEmpty()
                .request(1)
                .assertItemCount(1)
                .request(2)
                .assertItemCount(3)
                .assertNotTerminated();

        emitter.emit(10);
        assertThat(emitter.bufferSize(), is(equalTo(8)));

        subscriber
                .request(3)
                .assertItemCount(6);

        emitter.emit(11);
        assertThat(emitter.bufferSize(), is(equalTo(6)));

        subscriber.requestMax()
                .assertNotTerminated();

        emitter.complete();
        subscriber
                .assertValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
                .assertComplete();
    }

    private void assertBufferSize(int result, int expected) {
        assertThat("Wrong buffer size!", result, is(equalTo(expected)));
    }
}
