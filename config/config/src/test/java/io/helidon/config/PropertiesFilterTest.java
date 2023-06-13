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

package io.helidon.config;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.config.PropertiesFilter.KEY_FILTER_PROPERTY;
import static io.helidon.config.PropertiesFilter.USE_DEFAULT_FILTER_PROPERTY;
import static io.helidon.config.PropertiesFilter.VALUE_FILTER_PROPERTY;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

public class PropertiesFilterTest {

    @BeforeEach
    void clearProperties() {
        clear();
    }

    @AfterAll
    static void cleanUp() {
        clear();
    }
    private static void clear() {
        System.clearProperty(KEY_FILTER_PROPERTY);
        System.clearProperty(VALUE_FILTER_PROPERTY);
        System.clearProperty(USE_DEFAULT_FILTER_PROPERTY);
    }

    @Test
    void testDefaultFilter() {
        PropertiesFilter filter = PropertiesFilter.create(System.getProperties());
        Map<String, String> filtered = filter.filter(Map.of("foo", "bar",
                                                            "BASH_FUNC_%%", "bash function"));
        assertThat(filtered.size(), is(1));
        assertThat(filtered.keySet(), contains("foo"));
    }

    @Test
    void testWithoutDefaultFilter() {
        System.setProperty(USE_DEFAULT_FILTER_PROPERTY, "false");
        PropertiesFilter filter = PropertiesFilter.create(System.getProperties());

        Map<String, String> result = filter.filter(Map.of("foo", "bar",
                "BASH_FUNC_foo%%", "bash function"));
        assertThat(result.size(), is(2));
        assertThat(result.keySet(), contains("foo", "BASH_FUNC_foo%%"));
    }

    @Test
    void testFilter() {
        System.setProperty(KEY_FILTER_PROPERTY, "foo.(.*?),(.*?).foo.(.*?)");
        System.setProperty(VALUE_FILTER_PROPERTY, "bar.(.*?)");
        PropertiesFilter filter = PropertiesFilter.create(System.getProperties());

        Map<String, String> result = filter.filter(Map.of("foo", "bar",
                                                          "foo.bar", "foo.bar",
                                                          "bar.foo.bar", "bar.foo.bar",
                                                          "bar.foo", "bar.foo",
                                                          "BASH_FUNC_foo%%", "bash function"));
        assertThat(result.size(), is(1));
        assertThat(result.keySet(), contains("foo"));
    }
}
