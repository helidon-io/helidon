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

import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.common.CollectionsHelper;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import org.junit.jupiter.api.Test;

/**
 * Tests part of {@link BuilderImpl} related to {@link ConfigMapper}s or {@link io.helidon.config.spi.ConfigMapperProvider}.
 */
public class BuilderImplMappersTest {

    @Test
    public void testUserDefinedHasPrecedenceInteger() {
        ConfigMapperManager manager = BuilderImpl.buildMappers(false, CollectionsHelper.mapOf(Integer.class, node -> 42));
        Config config = Config.builder()
                .sources(ConfigSources.from(CollectionsHelper.mapOf("int-p", "2147483647")))
                .build();

        assertThat(manager.map(Integer.class, config.get("int-p")), is(42));
        assertThat(manager.map(OptionalInt.class, config.get("int-p")).getAsInt(), is(2147483647));
    }

    @Test
    public void testUserDefinedHasPrecedenceOptionalInt() {
        ConfigMapperManager manager = BuilderImpl.buildMappers(false, CollectionsHelper.mapOf(OptionalInt.class, node -> OptionalInt.of(42)));
        Config config = Config.builder()
                .sources(ConfigSources.from(CollectionsHelper.mapOf("int-p", "2147483647")))
                .build();

        assertThat(manager.map(Integer.class, config.get("int-p")), is(2147483647));
        assertThat(manager.map(OptionalInt.class, config.get("int-p")).getAsInt(), is(42));
    }

    @Test
    public void testUserDefinedMapperProviderHasPrecedenceInteger() {
        Config config = Config.builder()
                .sources(ConfigSources.from(CollectionsHelper.mapOf("int-p", "2147483647")))
                .addMapper(() -> CollectionsHelper.mapOf(Integer.class, c -> 43))
                .build();

        assertThat(config.get("int-p").asInt(), is(43));
    }

    @Test
    public void testUserOverrideMapperFromMapperProvider() {
        Config config = Config.builder()
                .sources(ConfigSources.from(CollectionsHelper.mapOf("int-p", "2147483647")))
                .addMapper(() -> CollectionsHelper.mapOf(Integer.class, c -> 43))
                .addMapper(Integer.class, (Function<String, Integer>) s -> 44)
                .build();

        assertThat(config.get("int-p").asInt(), is(44));
    }

    @Test
    public void testDefaultMapMapper() {
        Config config = Config.from(ConfigSources.from(CollectionsHelper.mapOf("int-p", "2147483647")));

        assertThat(config.asMap().get("int-p"), is("2147483647"));
    }

    @Test
    public void testUserDefinedHasPrecedenceStringMapMapper() {
        Config config = Config.withSources(ConfigSources.from(CollectionsHelper.mapOf("int-p", "2147483647")))
                .addMapper(Map.class, new CustomStringMapMapper())
                .build();

        assertThat(config.asMap().get("int-p"), is(nullValue()));
        assertThat(config.asMap().get("prefix-int-p"), is("[2147483647]"));
    }

    @Test
    public void testUserDefinedHasPrecedenceStringBuilderMapMapper() {
        Config config = Config.withSources(ConfigSources.from(CollectionsHelper.mapOf("int-p", "2147483647")))
                .addMapper(Map.class, new CustomStringBuilderMapMapper())
                .build();

        assertThat(config.asMap().get("int-p"), is(nullValue()));
        assertThat(config.asMap().get("prefix2-int-p"), is("{2147483647}"));
    }

    private static class CustomStringMapMapper implements ConfigMapper<Map> {
        @Override
        public Map apply(Config config) throws ConfigMappingException, MissingValueException {
            return ConfigMappers.toMap(config)
                    .entrySet().stream()
                    .map(entry -> CollectionsHelper.mapEntry("prefix-" + entry.getKey(),
                                            "[" + entry.getValue() + "]"))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    private static class CustomStringBuilderMapMapper implements ConfigMapper<Map> {
        @Override
        public Map apply(Config config) throws ConfigMappingException, MissingValueException {
            return ConfigMappers.toMap(config)
                    .entrySet().stream()
                    .map(entry -> CollectionsHelper.mapEntry(new StringBuilder("prefix2-" + entry.getKey()),
                                            new StringBuilder("{" + entry.getValue() + "}")))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

}
