/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.testing;

import java.util.Optional;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.OptionalMatcher.empty;
import static io.helidon.config.testing.OptionalMatcher.present;
import static io.helidon.config.testing.OptionalMatcher.value;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link OptionalMatcher}.
 */
class OptionalMatcherTest {
    private static final Optional<String> EMPTY = Optional.empty();
    private static final Optional<String> NULL = null;
    private static final Optional<String> EXPECTED = Optional.of("expected");
    private static final Optional<String> ACTUAL = Optional.of("actual");

    @Test
    void testEmpty() {
        Matcher<Optional<String>> matcher = empty();

        assertThat(EMPTY, matcher);
        assertFail(NULL, matcher);
        assertFail(EXPECTED, matcher);
        assertFail(ACTUAL, matcher);
    }

    @Test
    void testValue() {
        Matcher<Optional<String>> matcher = value(is("expected"));

        assertFail(EMPTY, matcher);
        assertFail(NULL, matcher);
        assertThat(EXPECTED, matcher);
        assertFail(ACTUAL, matcher);
    }

    @Test
    void testPresent() {
        Matcher<Optional<String>> matcher = present();

        assertFail(EMPTY, matcher);
        assertFail(NULL, matcher);
        assertThat(EXPECTED, matcher);
        assertThat(ACTUAL, matcher);
    }

    private static <T> void assertFail(Optional<T> actual, Matcher<Optional<T>> matcher) {
        assertThrows(AssertionError.class, () -> assertThat(actual, matcher));
    }
}
