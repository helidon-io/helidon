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
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.concurrent.ForkJoinPool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValveIteratorTest {

    @Test
    void standard() {
        Valve<Integer> valve = Valves.from(ValveTest.LIST_0_100);
        assertToIterator(ValveTest.LIST_0_100, valve);
    }

    @Test
    void async() {
        Valve<Integer> valve = Valves.from(ValveTest.LIST_0_10).executeOn(ForkJoinPool.commonPool());
        assertToIterator(ValveTest.LIST_0_10, valve);
    }

    @Test
    void failing() {
        Valve<Integer> valve = Valves.from(ValveTest.LIST_0_10)
                                     .peek(i -> {
                                         if (i == 5) {
                                             throw new IllegalStateException("Test exception");
                                         }
                                     });
        ValveIterator<Integer> iterator = valve.toIterator();
        int lastValue = -1;
        while (iterator.hasNext()) {
            lastValue = iterator.next();
        }
        assertEquals(4, lastValue);
        assertNotNull(iterator.getThrowable());
        assertEquals("Test exception", iterator.getThrowable().getMessage());
    }

    @Test
    void multipleHasNext() {
        ValveIterator<Integer> iterator = Valves.from(ValveTest.LIST_0_5).toIterator();
        while (iterator.hasNext()) {
            iterator.next();
        }
        assertFalse(iterator.hasNext());
        assertFalse(iterator.hasNext());
    }

    @Test
    void nextAfterFinished() {
        ValveIterator<Integer> iterator = Valves.from(ValveTest.LIST_0_5).toIterator();
        while (iterator.hasNext()) {
            iterator.next();
        }
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    private <C> void assertToIterator(Collection<C> data, Valve<C> valve) {
        Collection<C> result = new ArrayList<>(data.size());
        for (C item : toIterable(valve)) {
            result.add(item);
        }
        assertEquals(data, result);
    }

    private static <T> Iterable<T> toIterable(Valve<T> valve) {
        return valve::toIterator;
    }
}
