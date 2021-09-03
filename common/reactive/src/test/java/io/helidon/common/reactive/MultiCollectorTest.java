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
 */
package io.helidon.common.reactive;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

import java.util.Collections;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class MultiCollectorTest {

    @Test
    public void collectionSupplierCrash() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.just(1)
                .collectStream(Collector.of(
                        () -> { throw new IllegalArgumentException(); },
                        (a, b) -> { },
                        (a, b) -> a,
                        a -> a
                ))
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void accumulatorCrash() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.just(1)
                .collectStream(Collector.of(
                        () -> 0,
                        (a, b) -> { throw new IllegalArgumentException(); },
                        (a, b) -> a,
                        a -> a
                ))
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void finisherCrash() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.just(1)
                .collectStream(Collector.of(
                        () -> 0,
                        (a, b) -> { },
                        (a, b) -> a,
                        a -> { throw new IllegalArgumentException(); }
                ))
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void finisherNull() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.just(1)
                .collectStream(Collector.of(
                        () -> 0,
                        (a, b) -> { },
                        (a, b) -> a,
                        a -> null
                ))
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(NullPointerException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void upstreamError() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.error(new IllegalArgumentException())
                .collectStream(Collectors.toList())
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void collectorCrashSupplier() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.just(1)
                .collectStream(
                        new Collector<Integer, Object, Object>() {

                            @Override
                            public Supplier<Object> supplier() {
                                throw new IllegalArgumentException();
                            }

                            @Override
                            public BiConsumer<Object, Integer> accumulator() {
                                return (a,b) -> { };
                            }

                            @Override
                            public BinaryOperator<Object> combiner() {
                                return (a, b) -> a;
                            }

                            @Override
                            public Function<Object, Object> finisher() {
                                return a -> a;
                            }

                            @Override
                            public Set<Characteristics> characteristics() {
                                return Collections.emptySet();
                            }
                        }
                )
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void collectorNullSupplier() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.just(1)
                .collectStream(
                        new Collector<Integer, Object, Object>() {

                            @Override
                            public Supplier<Object> supplier() {
                                return null;
                            }

                            @Override
                            public BiConsumer<Object, Integer> accumulator() {
                                return (a,b) -> { };
                            }

                            @Override
                            public BinaryOperator<Object> combiner() {
                                return (a, b) -> a;
                            }

                            @Override
                            public Function<Object, Object> finisher() {
                                return a -> a;
                            }

                            @Override
                            public Set<Characteristics> characteristics() {
                                return Collections.emptySet();
                            }
                        }
                )
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(NullPointerException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void collectorCrashAccumulator() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.just(1)
                .collectStream(
                        new Collector<Integer, Object, Object>() {

                            @Override
                            public Supplier<Object> supplier() {
                                return () -> 0;
                            }

                            @Override
                            public BiConsumer<Object, Integer> accumulator() {
                                throw new IllegalArgumentException();
                            }

                            @Override
                            public BinaryOperator<Object> combiner() {
                                return (a, b) -> a;
                            }

                            @Override
                            public Function<Object, Object> finisher() {
                                return a -> a;
                            }

                            @Override
                            public Set<Characteristics> characteristics() {
                                return Collections.emptySet();
                            }
                        }
                )
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void collectorNullAccumulator() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.just(1)
                .collectStream(
                        new Collector<Integer, Object, Object>() {

                            @Override
                            public Supplier<Object> supplier() {
                                return () -> 0;
                            }

                            @Override
                            public BiConsumer<Object, Integer> accumulator() {
                                return null;
                            }

                            @Override
                            public BinaryOperator<Object> combiner() {
                                return (a, b) -> a;
                            }

                            @Override
                            public Function<Object, Object> finisher() {
                                return a -> a;
                            }

                            @Override
                            public Set<Characteristics> characteristics() {
                                return Collections.emptySet();
                            }
                        }
                )
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(NullPointerException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void collectorCrashFinisher() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.just(1)
                .collectStream(
                        new Collector<Integer, Object, Object>() {

                            @Override
                            public Supplier<Object> supplier() {
                                return () -> 0;
                            }

                            @Override
                            public BiConsumer<Object, Integer> accumulator() {
                                return (a, b) -> { };
                            }

                            @Override
                            public BinaryOperator<Object> combiner() {
                                return (a, b) -> a;
                            }

                            @Override
                            public Function<Object, Object> finisher() {
                                throw new IllegalArgumentException();
                            }

                            @Override
                            public Set<Characteristics> characteristics() {
                                return Collections.emptySet();
                            }
                        }
                )
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void collectorNullFinisher() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.just(1)
                .collectStream(
                        new Collector<Integer, Object, Object>() {

                            @Override
                            public Supplier<Object> supplier() {
                                return () -> 0;
                            }

                            @Override
                            public BiConsumer<Object, Integer> accumulator() {
                                return (a, b) -> { };
                            }

                            @Override
                            public BinaryOperator<Object> combiner() {
                                return (a, b) -> a;
                            }

                            @Override
                            public Function<Object, Object> finisher() {
                                return null;
                            }

                            @Override
                            public Set<Characteristics> characteristics() {
                                return Collections.emptySet();
                            }
                        }
                )
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(NullPointerException.class));
        assertThat(ts.isComplete(), is(false));
    }
}
