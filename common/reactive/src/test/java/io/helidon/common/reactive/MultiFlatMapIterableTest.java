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

import java.util.Iterator;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

public class MultiFlatMapIterableTest {

    Iterable<Integer> range(int count) {
        return () -> IntStream.range(0, count).boxed().iterator();
    }

    void crossMap(int count) {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);
        int rest = 1_000_000 / count;

        Multi.create(range(count))
                .flatMapIterable(v -> range(rest))
                .subscribe(ts);

        ts.assertItemCount(1_000_000)
                .assertComplete();
    }

    @Test
    public void crossMap1() {
        crossMap(1);
    }

    @Test
    public void crossMap10() {
        crossMap(10);
    }

    @Test
    public void crossMap100() {
        crossMap(100);
    }

    @Test
    public void crossMap1000() {
        crossMap(1000);
    }

    @Test
    public void crossMap10000() {
        crossMap(10_000);
    }

    @Test
    public void crossMap100000() {
        crossMap(100_000);
    }

    @Test
    public void crossMap1000000() {
        crossMap(1_000_000);
    }

    @Test
    public void cancelAfterIterator() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.just(1)
                .<Integer>flatMapIterable(v -> () -> {
                    ts.cancel();
                    return IntStream.range(0, 2).boxed().iterator();
                })
                .subscribe(ts);

        ts.assertEmpty();
    }


    @Test
    public void crashInIterator() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.just(1)
                .<Integer>flatMapIterable(v -> () -> {
                    throw new IllegalArgumentException();
                })
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
    }

    @Test
    public void cancelAfterFirstHasNext() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.just(1)
                .<Integer>flatMapIterable(v -> () -> {
                    return new Iterator<Integer>() {

                        @Override
                        public boolean hasNext() {
                            ts.cancel();
                            return true;
                        }

                        @Override
                        public Integer next() {
                            return 1;
                        }
                    };
                })
                .subscribe(ts);

        ts.assertEmpty();

    }

    @Test
    public void cancelAfterFirstNext() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.just(1)
                .<Integer>flatMapIterable(v -> () -> {
                    return new Iterator<Integer>() {

                        @Override
                        public boolean hasNext() {
                            return true;
                        }

                        @Override
                        public Integer next() {
                            ts.cancel();
                            return 1;
                        }
                    };
                })
                .subscribe(ts);

        ts.assertEmpty();
    }

    @Test
    public void crashAfterFirstNext() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.just(1)
                .<Integer>flatMapIterable(v -> () -> {
                    return new Iterator<Integer>() {

                        @Override
                        public boolean hasNext() {
                            return true;
                        }

                        @Override
                        public Integer next() {
                            throw new IllegalArgumentException();
                        }
                    };
                })
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
    }

    @Test
    public void limit() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.just(1)
                .flatMapIterable(v -> range(5))
                .limit(3)
                .subscribe(ts);

        ts.assertResult(0, 1, 2);
    }

    @Test
    public void crashAfterSecondHasNext() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.just(1)
                .<Integer>flatMapIterable(v -> () -> {
                    return new Iterator<Integer>() {
                        int count;

                        @Override
                        public boolean hasNext() {
                            if (++count == 2) {
                                throw new IllegalArgumentException();
                            }
                            return true;
                        }

                        @Override
                        public Integer next() {
                            return 1;
                        }
                    };
                })
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class, 1);
    }

    @Test
    public void cancelAfterSecondHasNext() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.just(1)
                .flatMapIterable(v -> () -> {
                    return new Iterator<Integer>() {
                        int count;

                        @Override
                        public boolean hasNext() {
                            if (++count == 2) {
                                ts.cancel();
                            }
                            return true;
                        }

                        @Override
                        public Integer next() {
                            return 1;
                        }
                    };
                })
                .subscribe(ts);

        ts.assertValuesOnly(1);
    }
}
