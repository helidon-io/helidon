/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.builder.test.testsubjects.ConfigMap;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigMapTest {
    private static Config config;

    @BeforeAll
    static void beforeAll() {
        config = Config.just(ConfigSources.classpath("config-map-test.yaml"));
    }

    @Test
    void testMap() {
        ConfigMap configMap = ConfigMap.create(config);
        Map<String, String> properties = configMap.properties();

        assertThat(properties, hasEntry("my.first.key", "firstValue"));
        assertThat(properties, hasEntry("my.second.key", "secondValue"));
    }

    @Test
    void testOptionalContainersFromConfig() {
        ConfigMap configMap = ConfigMap.create(config.get("optional-containers"));

        Map<String, String> optionalProperties = configMap.optionalProperties().orElseThrow();
        assertThat(optionalProperties, is(Map.of("my.first.key", "firstValue",
                                                 "my.second.key", "secondValue")));
        Map<String, Integer> optionalNumbers = configMap.optionalNumbers().orElseThrow();
        assertThat(optionalNumbers, is(Map.of("one", 1, "two", 2)));
        assertThat(configMap.optionalList().orElseThrow(), is(List.of("first", "second")));
        assertThat(configMap.optionalSet().orElseThrow(), is(Set.of("first", "second")));
    }

    @Test
    void testOptionalContainersEmptyFromConfig() {
        ConfigMap configMap = ConfigMap.create(config.get("optional-empty-containers"));

        assertThat(configMap.optionalProperties().orElseThrow().entrySet(), empty());
        assertThat(configMap.optionalNumbers().orElseThrow().entrySet(), empty());
        assertThat(configMap.optionalList().orElseThrow(), empty());
        assertThat(configMap.optionalSet().orElseThrow(), empty());
    }

    @Test
    void testOptionalContainersAbsentFromConfig() {
        ConfigMap configMap = ConfigMap.create(config.get("absent"));

        assertThat(configMap.optionalProperties().isEmpty(), is(true));
        assertThat(configMap.optionalNumbers().isEmpty(), is(true));
        assertThat(configMap.optionalList().isEmpty(), is(true));
        assertThat(configMap.optionalSet().isEmpty(), is(true));
    }

    @Test
    void testOptionalMapAdders() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("first", "one");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("second", "two");

        ConfigMap configMap = ConfigMap.builder()
                .addOptionalProperties(first)
                .addOptionalProperties(second)
                .build();

        first.clear();
        second.clear();

        Map<String, String> builtMap = configMap.optionalProperties().orElseThrow();
        assertThat(builtMap, is(Map.of("first", "one", "second", "two")));
        assertThrows(UnsupportedOperationException.class, () -> builtMap.put("third", "three"));

        configMap = ConfigMap.builder()
                .addOptionalProperties(Map.of())
                .build();
        assertThat(configMap.optionalProperties().orElseThrow().entrySet(), empty());

        configMap = ConfigMap.builder()
                .addOptionalProperties(Map.of("first", "one"))
                .optionalProperties(Map.of("replacement", "value"))
                .build();
        assertThat(configMap.optionalProperties().orElseThrow(), is(Map.of("replacement", "value")));
    }

    @Test
    void testOptionalCollectionAdders() {
        ConfigMap configMap = ConfigMap.builder()
                .addOptionalList(List.of())
                .addOptionalSet(Set.of())
                .build();

        assertThat(configMap.optionalList().orElseThrow(), empty());
        assertThat(configMap.optionalSet().orElseThrow(), empty());

        List<String> firstList = new ArrayList<>();
        firstList.add("first");
        List<String> secondList = new ArrayList<>();
        secondList.add("second");
        Set<String> firstSet = new LinkedHashSet<>();
        firstSet.add("first");
        firstSet.add("duplicate");
        Set<String> secondSet = new LinkedHashSet<>();
        secondSet.add("second");
        secondSet.add("duplicate");

        ConfigMap.Builder builder = ConfigMap.builder()
                .addOptionalList(firstList)
                .addOptionalList(secondList)
                .addOptionalSet(firstSet)
                .addOptionalSet(secondSet);
        firstList.clear();
        secondList.clear();
        firstSet.clear();
        secondSet.clear();

        configMap = builder.build();

        List<String> builtList = configMap.optionalList().orElseThrow();
        Set<String> builtSet = configMap.optionalSet().orElseThrow();

        assertThat(builtList, is(List.of("first", "second")));
        assertThat(builtSet, is(Set.of("first", "second", "duplicate")));
        assertThrows(UnsupportedOperationException.class, () -> builtList.add("third"));
        assertThrows(UnsupportedOperationException.class, () -> builtSet.add("third"));
    }

    @Test
    void testOptionalContainerDefensiveCopies() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key", "value");
        List<String> list = new ArrayList<>();
        list.add("value");
        Set<String> set = new LinkedHashSet<>();
        set.add("value");

        ConfigMap configMap = ConfigMap.builder()
                .optionalProperties(map)
                .optionalList(list)
                .optionalSet(set)
                .build();

        map.clear();
        list.clear();
        set.clear();

        Map<String, String> builtMap = configMap.optionalProperties().orElseThrow();
        List<String> builtList = configMap.optionalList().orElseThrow();
        Set<String> builtSet = configMap.optionalSet().orElseThrow();

        assertThat(builtMap, is(Map.of("key", "value")));
        assertThat(builtList, is(List.of("value")));
        assertThat(builtSet, is(Set.of("value")));
        assertThrows(UnsupportedOperationException.class, () -> builtMap.put("another", "value"));
        assertThrows(UnsupportedOperationException.class, () -> builtList.add("another"));
        assertThrows(UnsupportedOperationException.class, () -> builtSet.add("another"));
    }
}
