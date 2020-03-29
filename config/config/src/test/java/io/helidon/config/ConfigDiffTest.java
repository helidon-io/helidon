/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.core.Is.is;

/**
 * Tests {@link ConfigDiff}.
 */
public class ConfigDiffTest {

    private static final Map<String, String> OBJECT_WITH_LEAVES = Map.of(
            "o.p.q", "something",
            "a.a", "value",
            "a.b", "value"
    );

    private static final Map<String, String> OBJECT_WITH_LEAVES_CHANGED_LEAF = Map.of(
            "o.p.q", "something",
            "a.a", "value",
            "a.b", "new value"
    );

    private static final Map<String, String> OBJECT_WITH_LEAVES_CHANGED_LEAF_TO_OBJECT = Map.of(
            "o.p.q", "something",
            "a.a", "value",
            "a.b.a", "value"
    );

    private static final Map<String, String> OBJECT_WITH_LEAVES_ADDED_LEAF = Map.of(
            "o.p.q", "something",
            "a.a", "value",
            "a.b", "value",
            "a.c", "value"
    );

    private static final Map<String, String> OBJECT_WITH_LEAVES_ADDED_OBJECT = Map.of(
            "o.p.q", "something",
            "a.a", "value",
            "a.b", "value",
            "a.c.a", "value"
    );

    @Test
    public void testNoChange() throws Exception {

        Config left = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Config right = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        ConfigDiff diff = ConfigDiff.from(left, right);

        assertThat(diff.config(), is(right));
        assertThat(diff.changedKeys(), is(empty()));

    }

    @Test
    public void testChangeLeaf() throws Exception {

        Config left = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Config right = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES_CHANGED_LEAF))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        ConfigDiff diff = ConfigDiff.from(left, right);

        assertThat(diff.config(), is(right));
        assertThatChangedKeysContainsInAnyOrder(diff, "", "a", "a.b");

    }

    @Test
    public void testAddLeaf() throws Exception {

        Config left = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Config right = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES_ADDED_LEAF))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        ConfigDiff diff = ConfigDiff.from(left, right);

        assertThat(diff.config(), is(right));
        assertThatChangedKeysContainsInAnyOrder(diff, "", "a", "a.c");

    }

    @Test
    public void testRemovedLeaf() throws Exception {

        Config left = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES_ADDED_LEAF))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Config right = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        ConfigDiff diff = ConfigDiff.from(left, right);

        assertThat(diff.config(), is(right));
        assertThatChangedKeysContainsInAnyOrder(diff, "", "a", "a.c");

    }

    @Test
    public void testChangedAndRemovedLeaves() throws Exception {

        Config left = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES_ADDED_LEAF))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Config right = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES_CHANGED_LEAF))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        ConfigDiff diff = ConfigDiff.from(left, right);

        assertThat(diff.config(), is(right));
        assertThatChangedKeysContainsInAnyOrder(diff, "", "a", "a.b", "a.c");

    }

    @Test
    public void testLeafChangedToObject() throws Exception {

        Config left = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Config right = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES_CHANGED_LEAF_TO_OBJECT))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        ConfigDiff diff = ConfigDiff.from(left, right);

        assertThat(diff.config(), is(right));
        assertThatChangedKeysContainsInAnyOrder(diff, "", "a", "a.b", "a.b.a");

    }

    @Test
    public void testObjectChangedToLeaf() throws Exception {

        Config left = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES_CHANGED_LEAF_TO_OBJECT))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Config right = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        ConfigDiff diff = ConfigDiff.from(left, right);

        assertThat(diff.config(), is(right));
        assertThatChangedKeysContainsInAnyOrder(diff, "", "a", "a.b", "a.b.a");

    }

    @Test
    public void testAddedObject() throws Exception {

        Config left = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Config right = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES_ADDED_OBJECT))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        ConfigDiff diff = ConfigDiff.from(left, right);

        assertThat(diff.config(), is(right));
        assertThatChangedKeysContainsInAnyOrder(diff, "a.c.a", "a.c", "a", "");

    }

    @Test
    public void testRemovedObject() throws Exception {

        Config left = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES_ADDED_OBJECT))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        Config right = Config.builder()
                .sources(ConfigSources.create(OBJECT_WITH_LEAVES))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        ConfigDiff diff = ConfigDiff.from(left, right);

        assertThat(diff.config(), is(right));
        assertThatChangedKeysContainsInAnyOrder(diff, "a.c.a", "a.c", "a", "");

    }

    static void assertThatChangedKeysContainsInAnyOrder(ConfigDiff diff, String... expectedKeys) {
        assertThat(diff.changedKeys().stream()
                           .map(Config.Key::toString)
                           .collect(Collectors.toSet()),
                   containsInAnyOrder(expectedKeys));
    }

}
