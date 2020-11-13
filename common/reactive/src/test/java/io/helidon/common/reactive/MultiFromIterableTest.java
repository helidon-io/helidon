/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
import static org.testng.Assert.assertEquals;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;

public class MultiFromIterableTest {
    @Test
    public void emptyIterable() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.create(Collections.emptyList())
        .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.isComplete(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
    }

    @Test
    public void singletonIterableUnboundedRequest() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.create(Collections.singleton(1))
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

        Multi.create(Collections.singleton(1))
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

        Multi.create(Collections.singleton(1))
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

        Multi.create(Collections.singleton((Integer)null))
                .subscribe(ts);

        ts.request1();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), instanceOf(NullPointerException.class));
    }

    @Test
    public void iteratorNextCrash() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.create(() -> new Iterator<Integer>() {

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

        Multi.create(() -> new Iterator<Integer>() {

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

        Multi.create(() -> new Iterator<Integer>() {
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

        assertEquals(ts.getItems(), Collections.singleton(1));
        assertThat(ts.isComplete(), is(false));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
    }


    @Test
    public void iteratorHasNextCancel2ndCall() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.create(() -> new Iterator<Integer>() {
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

        assertEquals(ts.getItems(), Collections.singleton(1));
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

        Multi.create(Collections.singleton(1))
                .subscribe(ts);

        ts.requestMax();
        assertEquals(ts.getItems(), Collections.singleton(1));
        assertThat(ts.isComplete(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));

    }
}
