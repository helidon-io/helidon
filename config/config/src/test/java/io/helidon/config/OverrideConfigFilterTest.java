/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import io.helidon.config.spi.OverrideSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

/**
 * Tests {@link OverrideConfigFilter}.
 */
public class OverrideConfigFilterTest {

    private static final LinkedHashMap<String, String> ORDERED_MAP = new LinkedHashMap<String,String>() {{
        put("*", "libor");
    }};

    @Test
    public void testCreateFilterWithNullParam() {
        OverrideConfigFilter filter = new OverrideConfigFilter(() -> null);

        assertThat(filter, notNullValue());
        assertThat(filter.apply(Config.Key.create("name"), "ondrej"), is("ondrej"));
    }

    @Test
    public void testCreateFilterWithEmptyParam() {
        OverrideConfigFilter filter = new OverrideConfigFilter(List::of);

        assertThat(filter, notNullValue());
        assertThat(filter.apply(Config.Key.create("name"), "ondrej"), is("ondrej"));
    }

    @Test
    public void testCreateFilterWithParam() {
        OverrideConfigFilter filter = new OverrideConfigFilter(() -> OverrideSource.OverrideData.createFromWildcards(
                ORDERED_MAP
                        .entrySet()
                        .stream()
                        .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()))
                        .collect(Collectors.toList())).data());

        assertThat(filter, notNullValue());
        assertThat(filter.apply(Config.Key.create("name"), "ondrej"), is("libor"));
    }

    @Test
    public void testWithRegexizeFunction() {
        OverrideConfigFilter filter = new OverrideConfigFilter(() -> OverrideSource.OverrideData.createFromWildcards(
                ORDERED_MAP
                        .entrySet()
                        .stream()
                        .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()))
                        .collect(Collectors.toList())).data());

        assertThat(filter, notNullValue());
        assertThat(filter.apply(Config.Key.create("name"), "ondrej"), is("libor"));
    }
}
