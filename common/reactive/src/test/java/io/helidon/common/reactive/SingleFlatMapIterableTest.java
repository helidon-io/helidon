/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

import org.junit.jupiter.api.Test;

public class SingleFlatMapIterableTest {

    @Test
    public void sourceEmpty() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.<Integer>empty()
                .flatMapIterable(v -> Arrays.asList(v, v + 1))
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void sourceError() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.<Integer>error(new IOException())
                .flatMapIterable(v -> Arrays.asList(v, v + 1))
                .subscribe(ts);

        ts.assertFailure(IOException.class);
    }

    @Test
    public void normal() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Single.<Integer>just(1)
                .flatMapIterable(v -> Arrays.asList(v, v + 1))
                .subscribe(ts);

        ts.assertResult(1, 2);
    }

    @Test
    public void mappedToEmpty() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Single.<Integer>just(1)
                .flatMapIterable(v -> Collections.emptyList())
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void normalBackpressured() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.<Integer>just(1)
                .flatMapIterable(v -> Arrays.asList(v, v + 1))
                .subscribe(ts);

        ts.assertEmpty()
                .request(1)
                .assertValuesOnly(1)
                .request(2)
                .assertResult(1, 2);
    }

    @Test
    public void normalOneBackpressured() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.<Integer>just(1)
                .flatMapIterable(Collections::singletonList)
                .subscribe(ts);

        ts.assertEmpty()
                .request(1)
                .assertResult(1);
    }

    @Test
    public void mapperNull() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.<Integer>just(1)
                .flatMapIterable(v -> null)
                .subscribe(ts);

        ts.assertFailure(NullPointerException.class);
    }

    @Test
    public void mapperCrash() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.<Integer>just(1)
                .flatMapIterable(v -> { throw new IllegalArgumentException(); })
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
    }

    @Test
    public void nullIteratorValue() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.<Integer>just(1)
                .flatMapIterable(v -> Collections.singletonList(null))
                .subscribe(ts);

        ts.assertEmpty()
                .request(1)
                .assertFailure(NullPointerException.class);
    }

    @Test
    public void iteratorNextCrash() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.just(1).flatMapIterable(v -> () -> new Iterator<Integer>() {

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Integer next() {
                throw new IllegalArgumentException();
            }
        })
                .subscribe(ts);

        ts.request1();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void iteratorNextCancel() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.just(1).flatMapIterable(v -> () -> new Iterator<Integer>() {

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Integer next() {
                ts.getSubcription().cancel();
                return 1;
            }
        })
                .subscribe(ts);

        ts.request1();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), is(nullValue()));
    }

    @Test
    public void iteratorHasNextCrash2ndCall() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.just(1).flatMapIterable(v -> () -> new Iterator<Integer>() {
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
        })
                .subscribe(ts);

        ts.request1();

        assertThat(ts.getItems(), contains(1));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
    }


    @Test
    public void iteratorHasNextCancel2ndCall() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.just(1).flatMapIterable(v -> () -> new Iterator<Integer>() {
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
        })
                .subscribe(ts);

        ts.request1();

        assertThat(ts.getItems(), contains(1));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), is(nullValue()));
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

        Single.just(1).flatMapIterable(v -> Collections.singleton(1))
                .subscribe(ts);

        ts.requestMax();
        assertThat(ts.getItems(), contains(1));
        assertThat(ts.isComplete(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
    }
}
