/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.common.reactive;

import org.testng.annotations.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.testng.Assert.assertEquals;

public class MultiFromStreamTest {
    @Test
    public void emptyIterable() {
        TestSubscriber<Object> ts = new TestSubscriber<>();
        AtomicInteger close = new AtomicInteger();

        Multi.create(Stream.empty().onClose(close::incrementAndGet))
        .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.isComplete(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(close.get(), is(1));
    }

    @Test
    public void singletonIterableUnboundedRequest() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.create(Stream.of(1))
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.getSubcription(), is(notNullValue()));

        ts.requestMax();

        assertEquals(ts.getItems(), Collections.singleton(1));
        assertThat(ts.isComplete(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
    }

    @Test
    public void limit() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.create(Stream.of(1))
                .limit(1)
                .subscribe(ts);

        ts.requestMax();
        assertEquals(ts.getItems(), Collections.singleton(1));
        assertThat(ts.isComplete(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
    }

    @Test
    public void singletonIterableBoundedRequest() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.create(Stream.of(1))
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.getSubcription(), is(notNullValue()));

        ts.request1();

        assertEquals(ts.getItems(), Collections.singleton(1));
        assertThat(ts.isComplete(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
    }

    @Test
    public void iteratorNull() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.create(Stream.of((Integer)null))
                .subscribe(ts);

        ts.request1();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), instanceOf(NullPointerException.class));
    }

    @Test
    public void iteratorNextCrash() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        AtomicInteger close = new AtomicInteger();

        Multi.create(withIterator(new Iterator<Integer>() {

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Integer next() {
                throw new IllegalArgumentException();
            }
        }, close))
        .subscribe(ts);

        ts.request1();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(close.get(), is(1));
    }

    @Test
    public void iteratorNextCancel() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        AtomicInteger close = new AtomicInteger();

        Multi.create(withIterator(new Iterator<Integer>() {

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Integer next() {
                ts.getSubcription().cancel();
                return 1;
            }
        }, close))
        .subscribe(ts);

        ts.request1();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(close.get(), is(1));
    }

    @Test
    public void iteratorHasNextCrash2ndCall() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        AtomicInteger close = new AtomicInteger();

        Multi.create(withIterator(new Iterator<Integer>() {
            int calls;
            @Override
            public boolean hasNext() {
                if (++calls == 2) {
                    throw new IllegalArgumentException();
                }
                return true;
            }

            @Override
            public Integer next() {
                return 1;
            }
        }, close))
                .subscribe(ts);

        ts.request1();

        assertEquals(ts.getItems(), Collections.singleton(1));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(close.get(), is(1));
    }


    @Test
    public void iteratorHasNextCancel2ndCall() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        AtomicInteger close = new AtomicInteger();

        Multi.create(withIterator(new Iterator<Integer>() {
            int calls;
            @Override
            public boolean hasNext() {
                if (++calls == 2) {
                    ts.getSubcription().cancel();
                }
                return true;
            }

            @Override
            public Integer next() {
                return 1;
            }
        }, close))
        .subscribe(ts);

        ts.request1();

        assertEquals(ts.getItems(), Collections.singleton(1));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(close.get(), is(1));
    }

    @Test
    public void cancelInOnNext() {
        TestSubscriber<Integer> ts = new TestSubscriber<>() {
            @Override
            public void onNext(Integer item) {
                super.onNext(item);
                getSubcription().cancel();
                super.onComplete();
            }
        };

        Multi.create(Stream.of(1))
                .subscribe(ts);

        ts.requestMax();
        assertEquals(ts.getItems(), Collections.singleton(1));
        assertThat(ts.isComplete(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));

    }

    @SuppressWarnings("unchecked")
    static <T> Stream<T> withIterator(Iterator<T> it, AtomicInteger close) {
        return (Stream<T>)Proxy.newProxyInstance(Stream.class.getClassLoader(), new Class[] { Stream.class }, (proxy, method, args) -> {
            if (method.getName().equals("iterator")) {
                return it;
            }
            if (method.getName().equals("close")) {
                close.getAndIncrement();
            }

            return null;
        });
    };

    @Test
    public void normal() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.create(IntStream.range(0, 5).boxed())
                .subscribe(ts);

        ts.assertResult(0, 1, 2, 3, 4);
    }

    @Test
    public void normalBackpressured() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.create(IntStream.range(0, 5).boxed())
                .subscribe(ts);

        ts.assertEmpty()
                .request(2)
                .assertValuesOnly(0, 1)
                .request(3)
                .assertResult(0, 1, 2, 3, 4);
    }

    @Test
    public void streamReuse() {
        AtomicInteger close = new AtomicInteger();

        Stream<Integer> stream = Stream.of(1).onClose(close::incrementAndGet);
        stream.iterator();

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.create(stream)
                .subscribe(ts);

        ts.assertFailure(IllegalStateException.class);

        assertThat(close.get(), is(1));
    }

    @Test
    public void streamReuseCloseCrash() {
        Stream<Integer> stream = Stream.of(1).onClose(() -> {
            throw new IllegalArgumentException();
        });
        stream.iterator();

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.create(stream)
                .subscribe(ts);

        ts.assertFailure(IllegalStateException.class);

        assertThat(ts.getLastError().getSuppressed()[0],  instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void emptyCloseCrash() {
        Stream<Integer> stream = Stream.<Integer>empty().onClose(() -> {
            throw new IllegalArgumentException();
        });

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.create(stream)
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
    }

    @Test
    public void someCloseCrash() {
        Stream<Integer> stream = Stream.<Integer>of(1, 2, 3, 4, 5).onClose(() -> {
            throw new IllegalArgumentException();
        });

        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.create(stream)
                .subscribe(ts);

        ts.assertResult(1, 2, 3, 4, 5);
    }
}
