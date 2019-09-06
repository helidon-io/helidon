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
package io.helidon.common.reactive;

import java.util.List;
import java.util.Collections;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Flow.Subscription;

import org.junit.jupiter.api.Test;

import static io.helidon.common.CollectionsHelper.listOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

/**
 * {@link MultiTest} test.
 */
public class MultiTest {

    @Test
    public void testJust() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo", "bar"));
    }

    @Test
    public void testJustCollection() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just(listOf("foo", "bar")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo", "bar"));
    }

    @Test
    public void testEmpty() {
        MultiTestSubscriber<Object> subscriber = new MultiTestSubscriber<>();
        Multi.<Object>empty().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testError() {
        MultiTestSubscriber<Object> subscriber = new MultiTestSubscriber<>();
        Multi.<Object>error(new Exception("foo")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(notNullValue()));
        assertThat(subscriber.getLastError().getMessage(), is(equalTo("foo")));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNever() {
        MultiTestSubscriber<Object> subscriber = new MultiTestSubscriber<>();
        Multi.<Object>never().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testMapper() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.just("foo", "bar").map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("FOO", "BAR"));
    }

    @Test
    public void testErrorCollect() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.<String>error(new Exception("foo")).collectList().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(notNullValue()));
        assertThat(subscriber.getLastError().getMessage(), is(equalTo("foo")));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectList() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.just("foo", "bar").collectList().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems().get(0), hasItems("foo", "bar"));
    }

    @Test
    public void testFromPublisher() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.from(new TestPublisher<>("foo", "bar")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo", "bar"));
    }

    @Test
    public void testFromMulti() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.from(Multi.<String>just("foo", "bar")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo", "bar"));
    }

    @Test
    public void testFirst() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.just("foo", "bar").first().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems().get(0), is(equalTo("foo")));
    }

    @Test
    public void testErrorFirst() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.<String>error(new IllegalStateException("foo!")).first().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectorBadCollectMethod() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").collect(new Collector<String, List<String>>() {
            @Override
            public void collect(String item) {
                throw new IllegalStateException("foo!");
            }

            @Override
            public List<String> value() {
                return Collections.emptyList();
            }
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectorBadValueMethod() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").collect(new Collector<String, List<String>>() {
            @Override
            public void collect(String item) {
            }

            @Override
            public List<String> value() {
                throw new IllegalStateException("foo!");
            }
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectorBadNullValue() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").collect(new Collector<String, List<String>>() {
            @Override
            public void collect(String item) {
            }

            @Override
            public List<String> value() {
                return null;
            }
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNeverMap() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
                subscription.request(1);
            }
        };
        Multi.<String>never().map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testMapBadMapper() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").map(new Mapper<String, String>() {
            @Override
            public String map(String item) {
                throw new IllegalStateException("foo!");
            }
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testMapBadMapperNullValue() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").map(new Mapper<String, String>() {
            @Override
            public String map(String item) {
                return null;
            }
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    private static class MultiTestSubscriber<T> extends TestSubscriber<T> {

        @Override
        public void onSubscribe(Subscription subscription) {
            super.onSubscribe(subscription);
            requestMax();
        }
    }
}
