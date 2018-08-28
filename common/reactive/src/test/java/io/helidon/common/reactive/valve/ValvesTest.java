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

import java.util.List;
import java.util.stream.Collectors;

import io.helidon.common.CollectionsHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValvesTest {

    @Test
    void fromIterable() throws Exception {
        List<String> list = CollectionsHelper.listOf("a", "b", "c", "d", "e", "f", "g");
        String s = Valves.from(list).collect(Collectors.joining()).toCompletableFuture().get();
        assertEquals("abcdefg", s);
    }

    @Test
    void fromNullIterable() throws Exception {
        String s = Valves.from((Iterable<String>) null).collect(Collectors.joining()).toCompletableFuture().get();
        assertEquals("", s);
    }

    @Test
    void fromArray() throws Exception {
        String[] array = new String[] {"a", "b", "c", "d", "e", "f", "g"};
        String s = Valves.from(array).collect(Collectors.joining()).toCompletableFuture().get();
        assertEquals("abcdefg", s);
    }

    @Test
    void fromNullArray() throws Exception {
        String[] array = null;
        String s = Valves.from(array).collect(Collectors.joining()).toCompletableFuture().get();
        assertEquals("", s);
    }

    @Test
    void empty() throws Exception {
        Valve<String> valve = Valves.empty();
        String s = valve.collect(Collectors.joining()).toCompletableFuture().get();
        assertEquals("", s);
    }
}
