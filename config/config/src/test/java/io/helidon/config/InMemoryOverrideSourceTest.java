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

package io.helidon.config;

import java.util.AbstractMap;
import java.util.stream.Collectors;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.internal.OverrideConfigFilter;
import io.helidon.config.spi.OverrideSource;

import static io.helidon.config.ConfigSources.from;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link InMemoryOverrideSource}.
 */
public class InMemoryOverrideSourceTest {

    @Test
    public void testWildcards() {
        Config config = Config.builder()
                .sources(from(
                        CollectionsHelper.mapOf(
                                "aaa.bbb.name", "app-name",
                                "aaa.bbb.url", "URL0",
                                "aaa.anything", "1",
                                "bbb", "ahoy"
                        )))
                .overrides(OverrideSources.from(CollectionsHelper.mapOf("*.*.url", "URL1")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("aaa.bbb.name").asString(), is("app-name"));
        assertThat(config.get("aaa.bbb.url").asString(), is("URL1"));
        assertThat(config.get("aaa.ccc.url").exists(), is(false));
        assertThat(config.get("aaa.anything").asInt(), is(1));
        assertThat(config.get("bbb").asString(), is("ahoy"));
        assertThat(config.get("bbb.ccc.url").exists(), is(false));
    }

    @Test
    public void testWildcards2() {
        Config config = Config.builder()
                .sources(from(
                        CollectionsHelper.mapOf(
                                "aaa.bbb.name", "app-name",
                                "aaa.bbb.url", "URL0",
                                "aaa.anything", "1",
                                "bbb", "ahoy"
                        )))
                .overrides(OverrideSources.from(CollectionsHelper.mapOf("*.url", "URL1")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("aaa.bbb.name").asString(), is("app-name"));
        assertThat(config.get("aaa.bbb.url").asString(), is("URL0"));
        assertThat(config.get("aaa.ccc.url").exists(), is(false));
        assertThat(config.get("aaa.anything").asInt(), is(1));
        assertThat(config.get("bbb").asString(), is("ahoy"));
        assertThat(config.get("bbb.ccc.url").exists(), is(false));
    }

    @Test
    public void testAsConfigFilter() {
        Config config = Config.builder()
                .sources(from(
                        CollectionsHelper.mapOf(
                                "aaa.bbb.name", "app-name",
                                "aaa.bbb.url", "URL0",
                                "aaa.anything", "1",
                                "bbb", "ahoy"
                        )))
                .addFilter(new OverrideConfigFilter(() -> OverrideSource.OverrideData.fromWildcards(
                        CollectionsHelper.mapOf("*.*.url", "URL1")
                                .entrySet()
                                .stream()
                                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()))
                                .collect(Collectors.toList())).data()))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("aaa.bbb.name").asString(), is("app-name"));
        assertThat(config.get("aaa.bbb.url").asString(), is("URL1"));
        assertThat(config.get("aaa.ccc.url").exists(), is(false));
        assertThat(config.get("aaa.anything").asInt(), is(1));
        assertThat(config.get("bbb").asString(), is("ahoy"));
        assertThat(config.get("bbb.ccc.url").exists(), is(false));
    }

    @Test
    public void testBuilderDefault() {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
                new InMemoryOverrideSource.Builder(CollectionsHelper.listOf()).build();
        });
        assertTrue(ex.getMessage().startsWith("Override values cannot be empty."));
    }

    @Test
    public void testBuilderNullOverrideValues() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> {
            new InMemoryOverrideSource.Builder(null);
        });
        assertTrue(ex.getMessage().startsWith("overrideValues cannot be null"));
    }

}
