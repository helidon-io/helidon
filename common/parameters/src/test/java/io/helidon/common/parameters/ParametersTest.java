/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.parameters;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import io.helidon.common.mapper.OptionalValue;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParametersTest {
    private static final String UNIT_TEST = "unit-test";

    @Test
    void testEmpty() {
        Parameters empty = Parameters.empty(UNIT_TEST);
        assertThat(empty.component(), is(UNIT_TEST));
        assertThat("Empty parameters should not contain anything", empty.contains("anything"), is(false));
        assertThat("Empty parameters should have size 0", empty.size(), is(0));
        assertThat("Empty parameters should have empty names", empty.names(), hasSize(0));
        assertThrows(NoSuchElementException.class, () -> empty.get("anything"));
        assertThrows(NoSuchElementException.class, () -> empty.all("anything"));
        assertThat(empty.toString(), containsString(UNIT_TEST));
        assertThat("Empty parameters should be empty", empty.isEmpty(), is(true));
    }

    @Test
    void testMultiValueMap() {
        Map<String, List<String>> map = Map.of("something", List.of("first"),
                                               "other", List.of("first", "second"));

        Parameters params = Parameters.create(UNIT_TEST, map);
        assertThat(params.component(), is(UNIT_TEST));
        assertThat("Parameters should contain \"something\"", params.contains("something"), is(true));
        assertThat("Parameters should contain \"other\"", params.contains("other"), is(true));
        assertThat("Parameters should not contain \"anything\"", params.contains("anything"), is(false));
        assertThat("Parameters should have size 2", params.size(), is(2));
        assertThat("parameters should have correct names", params.names(), hasItems("something", "other"));
        assertThrows(NoSuchElementException.class, () -> params.get("anything"));
        assertThrows(NoSuchElementException.class, () -> params.all("anything"));
        assertThat(params.get("something"), is("first"));
        assertThat(params.get("other"), is("first"));
        assertThat(params.all("something"), hasItems("first"));
        assertThat(params.all("other"), hasItems("first", "second"));
        assertThat(params.toString(), containsString(UNIT_TEST));
        assertThat("Parameters should not be empty", params.isEmpty(), is(false));
    }

    @Test
    void testSingleValueMap() {
        Map<String, String> map = Map.of("something", "first",
                                               "other", "first");

        Parameters params = Parameters.createSingleValueMap(UNIT_TEST, map);
        assertThat(params.component(), is(UNIT_TEST));
        assertThat("Parameters should contain \"something\"", params.contains("something"), is(true));
        assertThat("Parameters should contain \"other\"", params.contains("other"), is(true));
        assertThat("Parameters should not contain \"anything\"", params.contains("anything"), is(false));
        assertThat("Parameters should have size 2", params.size(), is(2));
        assertThat("parameters should have correct names", params.names(), hasItems("something", "other"));
        assertThrows(NoSuchElementException.class, () -> params.get("anything"));
        assertThrows(NoSuchElementException.class, () -> params.all("anything"));
        assertThat(params.get("something"), is("first"));
        assertThat(params.get("other"), is("first"));
        assertThat(params.all("something"), hasItems("first"));
        assertThat(params.all("other"), hasItems("first"));
        assertThat(params.toString(), containsString(UNIT_TEST));
        assertThat("Parameters should not be empty", params.isEmpty(), is(false));
    }

    @Test
    void testBuilder() {
        Parameters params = Parameters.builder(UNIT_TEST)
                .add("something", "first")
                .add("other", "first", "second")
                .build();

        assertThat(params.component(), is(UNIT_TEST));
        assertThat("Parameters should contain \"something\"", params.contains("something"), is(true));
        assertThat("Parameters should contain \"other\"", params.contains("other"), is(true));
        assertThat("Parameters should not contain \"anything\"", params.contains("anything"), is(false));
        assertThat("Parameters should have size 2", params.size(), is(2));
        assertThat("parameters should have correct names", params.names(), hasItems("something", "other"));
        assertThrows(NoSuchElementException.class, () -> params.get("anything"));
        assertThrows(NoSuchElementException.class, () -> params.all("anything"));
        assertThat(params.get("something"), is("first"));
        assertThat(params.get("other"), is("first"));
        assertThat(params.all("something"), hasItems("first"));
        assertThat(params.all("other"), hasItems("first", "second"));
        assertThat(params.toString(), containsString(UNIT_TEST));
        assertThat("Parameters should not be empty", params.isEmpty(), is(false));
    }

    @Test
    void issue8710() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("param", Collections.emptyList());
        Parameters parameters = Parameters.create("test", params);
        OptionalValue<String> value = parameters.first("param");
        assertThat(value.isEmpty(), is(true));
    }
}