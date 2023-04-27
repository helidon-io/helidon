/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.common.testing;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptionalMatcherTest {
    @Test
    void testNull() {
        Optional<String> nullOptional = null;

        assertThrows(AssertionError.class, () -> assertThat(nullOptional, optionalEmpty()));
        assertThrows(AssertionError.class, () -> assertThat(nullOptional, optionalPresent()));
        assertThrows(AssertionError.class, () -> assertThat(nullOptional, optionalValue(any(Object.class))));
    }
    @Test
    void testEmpty() {
        Optional<String> empty = Optional.empty();
        assertThat(empty, is(optionalEmpty()));
        assertThat(empty, not(optionalPresent()));
    }

    @Test
    void testPresent() {
        Optional<String> stringOptional = Optional.of("my-value");
        assertThat(stringOptional, not(optionalEmpty()));
        assertThat(stringOptional, is(optionalPresent()));
    }

    @Test
    void testNestedStringMatcher() {
        Optional<String> stringOptional = Optional.of("my-value");
        assertThat(stringOptional, optionalValue(startsWith("my-")));
        assertThat(stringOptional, optionalValue(not(startsWith("her-"))));
    }

    @Test
    void testNestedListMatcher() {
        Optional<List<String>> listOptional = Optional.of(List.of("one", "two"));
        assertThat(listOptional, optionalValue(hasItems("one", "two")));
        assertThat(listOptional, optionalValue(not(hasItems("one", "three"))));
    }
}