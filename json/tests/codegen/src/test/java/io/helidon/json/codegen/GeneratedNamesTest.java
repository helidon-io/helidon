/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json.codegen;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class GeneratedNamesTest {
    @Test
    void readableAsciiNames() {
        Map<String, String> expectedNames = Map.ofEntries(
                Map.entry("SOME_NAME", "SOME_NAME"),
                Map.entry("SOME-NAME", "SOME_NAME"),
                Map.entry("1NAME", "NAME_1NAME"),
                Map.entry("", "NAME"),
                Map.entry("_", "NAME"),
                Map.entry("É", "E"),
                Map.entry("E\u0301", "E"),
                Map.entry("ø", "NAME"),
                Map.entry("a中b", "a_b"),
                Map.entry("a\u0000b", "a_b"));
        expectedNames.forEach((input, expected) -> assertThat(input, GeneratedNames.normalize(input), is(expected)));
    }

    @Test
    void normalizedNamesRemainUnique() {
        var names = new GeneratedNames();
        assertThat(names.unique("E"), is("E"));
        assertThat(names.unique("É"), is("E_1"));
        assertThat(names.unique("E\u0301"), is("E_2"));
        assertThat(names.unique("E_1"), is("E_1_1"));
        assertThat(names.unique(""), is("NAME"));
        assertThat(names.unique("NAME"), is("NAME_1"));
        assertThat(names.unique("_"), is("NAME_2"));
        assertThat(names.unique("1NAME"), is("NAME_1NAME"));
        assertThat(names.unique("NAME_1NAME"), is("NAME_1NAME_1"));
    }

    @Test
    void reservedNamesRemainUnavailable() {
        var names = new GeneratedNames(Set.of("E", "E_1", "NAME", "NAME_1NAME"));
        assertThat(names.unique("É"), is("E_2"));
        assertThat(names.unique(""), is("NAME_1"));
        assertThat(names.unique("1NAME"), is("NAME_1NAME_1"));
    }
}
