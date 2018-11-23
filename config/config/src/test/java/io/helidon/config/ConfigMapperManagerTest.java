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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.config.ConfigMapperManager.MapperProviders;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link ConfigMapperManager}.
 */
public class ConfigMapperManagerTest {
    private static final ConfigMapperManager managerNoServices = BuilderImpl.buildMappers(false,
                                                                                          Collections.emptyMap(),
                                                                                          MapperProviders.create());

    private static final ConfigMapperManager managerWithServices = BuilderImpl.buildMappers(true,
                                                                                            Collections.emptyMap(),
                                                                                            MapperProviders.create());

    @Test
    public void testUnknownMapper() {
        assertThrows(ConfigMappingException.class, () -> managerNoServices.map(CustomClass.class, mock(Config.class)));
        assertThrows(ConfigMappingException.class, () -> managerWithServices.map(CustomClass.class, mock(Config.class)));
    }

    @Test
    public void testBuiltInMappers() {
        Integer builtIn = managerWithServices.map(Integer.class, managerWithServices.simpleConfig("builtIn", "49"));
        assertThat(builtIn, is(49));
    }

    @Test
    public void testCustomMapper() {
        Config config = managerWithServices.simpleConfig("custom", "49");
        Integer custom = config.asString().map(str -> Integer.parseInt(str) + 1).get();
        assertThat(custom, is(50));
    }

    @Test
    void testSingleValueConfigImpl() {
        Config config = managerNoServices.simpleConfig("key1", "42");

        assertThat(config.key(), Matchers.is(Config.Key.of("key1")));
        assertThat(config.value(), Matchers.is(Optional.of("42")));
        assertThat(config.type(), Matchers.is(Config.Type.VALUE));
        assertThat(config.timestamp(), not(nullValue()));
        {
            Config sub = config.get("sub");
            assertThat(sub.key(), Matchers.is(Config.Key.of("key1.sub")));
            assertThat(sub.value(), Matchers.is(Optional.empty()));
            assertThat(sub.type(), Matchers.is(Config.Type.MISSING));
        }
        {
            Config detached = config.detach();
            assertThat(detached.key(), Matchers.is(Config.Key.of("")));
            assertThat(detached.value(), Matchers.is(Optional.of("42")));
            assertThat(detached.type(), Matchers.is(Config.Type.VALUE));
        }
        assertThat(config.traverse().collect(Collectors.toList()), empty());
        {
            Map<String, String> map = config.asMap().get();
            assertThat(map.entrySet(), hasSize(1));
            assertThat(map, hasEntry("key1", "42"));
        }
        assertThat(config.as(Integer.class).get(), Matchers.is(42));
        assertThrows(ConfigMappingException.class, () -> config.asNodeList().get());
        assertThrows(ConfigMappingException.class, () -> config.asList(String.class).asOptional());

        Config.Context context = config.context();

        assertThat(context.timestamp(), is(config.timestamp()));
        assertThat(context.reload(), sameInstance(config));
        assertThat(context.last(), sameInstance(config));
    }

    // this will not work, as beans are moved away from core
    public static class CustomClass {
        public CustomClass() {
        }
    }
}
