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

package io.helidon.common.reactive.valve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

class ValveTest {

    static final List<Integer> LIST_0_100 = new ArrayList<>(100);
    static final List<Integer> LIST_0_10 = new ArrayList<>(10);
    static final List<Integer> LIST_0_5 = new ArrayList<>(5);
    static {
        for (int i = 0; i < 100; i++) {
            LIST_0_100.add(i);
        }
        for (int i = 0; i < 10; i++) {
            LIST_0_10.add(i);
        }
        for (int i = 0; i < 5; i++) {
            LIST_0_5.add(i);
        }
    }

    @Test
    void testHandle() {
        List<Integer> buffer = Collections.synchronizedList(new ArrayList<>(10));
        AtomicReference<Throwable> thrRef = new AtomicReference<>();
        AtomicReference<Boolean> resultRef = new AtomicReference<>(false);
        // --- Basic consumer
        // Simple
        Valves.from(LIST_0_10).handle((Consumer<Integer>) buffer::add);
        assertThat(buffer, is(LIST_0_10));
        buffer.clear();
        // Two Params
        Valves.from(LIST_0_10).handle((Consumer<Integer>) buffer::add, thrRef::set);
        assertThat(buffer, is(LIST_0_10));
        assertThat(thrRef.get(), nullValue());
        buffer.clear();
        // Three Params
        Valves.from(LIST_0_10).handle((Consumer<Integer>) buffer::add, thrRef::set, () -> resultRef.set(true));
        assertThat(buffer, is(LIST_0_10));
        assertThat(thrRef.get(), nullValue());
        assertThat(resultRef.get(), is(true));
        buffer.clear();
        resultRef.set(false);

        // --- Pausable consumer
        // Simple
        Valve<Integer> valve = Valves.from(LIST_0_10);
        valve.handle((i, p) -> {
            if (i == 4) {
                p.pause();
            }
            buffer.add(i);
        });
        assertThat(buffer, is(LIST_0_5));
        valve.resume();
        assertThat(buffer, is(LIST_0_10));
        buffer.clear();
        // Two Params
        valve = Valves.from(LIST_0_10);
        valve.handle((i, p) -> {
            if (i == 4) {
                p.pause();
            }
            buffer.add(i);
        }, thrRef::set);
        assertThat(buffer, is(LIST_0_5));
        valve.resume();
        assertThat(buffer, is(LIST_0_10));
        assertThat(thrRef.get(), nullValue());
        buffer.clear();
        // Three Params
        valve = Valves.from(LIST_0_10);
        valve.handle((i, p) -> {
            if (i == 4) {
                p.pause();
            }
            buffer.add(i);
        }, thrRef::set, () -> resultRef.set(true));
        assertThat(buffer, is(LIST_0_5));
        valve.resume();
        assertThat(buffer, is(LIST_0_10));
        assertThat(thrRef.get(), nullValue());
        assertThat(resultRef.get(), is(true));
        buffer.clear();
        resultRef.set(false);
    }

    @Test
    void filter() throws Exception {
        List<Integer> result = Valves.from(LIST_0_10)
                                     .filter(i -> i < 5)
                                     .collect(Collectors.toList())
                                     .toCompletableFuture()
                                     .get();

        assertThat(result, is(LIST_0_5));
    }

    @Test
    void map() throws Exception {
        String result = Valves.from(LIST_0_10)
                              .map(String::valueOf)
                              .collect(Collectors.joining())
                              .toCompletableFuture()
                              .get();

        String expected = LIST_0_10.stream()
                .map(String::valueOf)
                .collect(Collectors.joining());

        assertThat(result, is(expected));
    }

    @Test
    void peek() throws Exception {
        List<Integer> buffer = Collections.synchronizedList(new ArrayList<>(10));
        List<Integer> result = Valves.from(LIST_0_10)
                                     .peek(buffer::add)
                                     .collect(Collectors.toList())
                                     .toCompletableFuture()
                                     .get();
        assertThat(buffer, is(LIST_0_10));
        assertThat(result, is(LIST_0_10));
    }

    @Test
    void onExecutorService() throws Exception {
        Set<String> threadNames = Collections.synchronizedSet(new TreeSet<>());
        threadNames.add(Thread.currentThread().getName());
        List<Integer> result = Valves.from(LIST_0_100)
                                     .executeOn(ForkJoinPool.commonPool())
                                     .peek(i -> threadNames.add(Thread.currentThread().getName()))
                                     .collect(Collectors.toList())
                                     .toCompletableFuture()
                                     .get();

        assertThat(result, is(LIST_0_100));
        assertThat(threadNames.size(), greaterThan(1));
    }
}
