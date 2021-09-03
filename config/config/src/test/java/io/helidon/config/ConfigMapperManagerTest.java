/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;
import io.helidon.config.ConfigMapperManager.MapperProviders;
import io.helidon.config.spi.ConfigMapper;
import io.helidon.config.spi.ConfigMapperProvider;

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
    private static final ConfigMapperManager managerNoServices = BuilderImpl.buildMappers(MapperProviders.create());

    private static final ConfigMapperManager managerWithServices = BuilderImpl.buildMappers(MapperProviders.create());

    @Test
    public void testUnknownMapper() {
        assertThrows(ConfigMappingException.class, () -> managerNoServices.map(mock(Config.class), CustomClass.class));
        assertThrows(ConfigMappingException.class, () -> managerWithServices.map(mock(Config.class), CustomClass.class));
    }

    @Test
    public void testBuiltInMappers() {
        Integer builtIn = managerWithServices.map(managerWithServices.simpleConfig("builtIn", "49"), Integer.class);
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

        assertThat(config.key(), is(Config.Key.create("key1")));
        assertThat(config.asString(), is(ConfigValues.simpleValue("42")));
        assertThat(config.type(), is(Config.Type.VALUE));
        assertThat(config.timestamp(), not(nullValue()));
        {
            Config sub = config.get("sub");
            assertThat(sub.key(), is(Config.Key.create("key1.sub")));
            assertThat(sub.asString(), is(ConfigValues.empty()));
            assertThat(sub.type(), is(Config.Type.MISSING));
        }
        {
            Config detached = config.detach();
            assertThat(detached.key(), is(Config.Key.create("")));
            assertThat(detached.asString(), is(ConfigValues.simpleValue("42")));
            assertThat(detached.type(), is(Config.Type.VALUE));
        }
        assertThat(config.traverse().collect(Collectors.toList()), empty());
        {
            Map<String, String> map = config.asMap().get();
            assertThat(map.entrySet(), hasSize(1));
            assertThat(map, hasEntry("key1", "42"));
        }
        assertThat(config.as(Integer.class).get(), is(42));
        assertThat(config.asNodeList().get(), is(List.of(config)));
        assertThat(config.asList(String.class).get(), is(List.of("42")));

        Config.Context context = config.context();

        assertThat(context.timestamp(), is(config.timestamp()));
        assertThat(context.reload(), sameInstance(config));
        assertThat(context.last(), sameInstance(config));
    }

    @Test
    void testGenericTypeMapperMap() {
        MapperProviders providers = MapperProviders.create();
        providers.add(new ParametrizedConfigMapper());
        ConfigMapperManager configMapperManager = BuilderImpl.buildMappers(providers);

        Config config = configMapperManager.simpleConfig("test", "1");

        ConfigValue<Map<String, Integer>> map = config.as(new GenericType<Map<String, Integer>>() { });

        assertThat(map.get(), is(Map.of("test", 1)));
    }

    @Test
    void testGenericTypeMapperList() {
        MapperProviders providers = MapperProviders.create();
        providers.add(new ParametrizedConfigMapper());
        ConfigMapperManager configMapperManager = BuilderImpl.buildMappers(providers);

        Config config = configMapperManager.simpleConfig("test", "1");

        ConfigValue<Map<String, List<Integer>>> map = config.as(new GenericType<Map<String, List<Integer>>>() { });

        assertThat(map.get(), is(Map.of("test", List.of(1))));
    }

    // this will not work, as beans are moved away from core
    public static class CustomClass {
        public CustomClass() {
        }
    }

    private static class ParametrizedConfigMapper implements ConfigMapperProvider {
        @Override
        public Map<Class<?>, Function<Config, ?>> mappers() {
            return Map.of();
        }

        @Override
        public <T> Optional<BiFunction<Config, ConfigMapper, T>> mapper(GenericType<T> type) {
            Class<?> rawType = type.rawType();

            if (rawType.equals(Map.class)) {
                // this is our class - we support Map<String, ?>
                Type theType = type.type();
                if (theType instanceof ParameterizedType) {
                    ParameterizedType ptype = (ParameterizedType) theType;
                    Type[] typeArgs = ptype.getActualTypeArguments();
                    if (typeArgs.length == 2) {
                        if (typeArgs[0].equals(String.class)) {
                            return Optional.of((config, mapper) -> {
                                Map<String, ?> theMap = new HashMap<>();

                                config.asMap().ifPresent(configMap -> {
                                    configMap.forEach((key, value) -> {
                                        theMap.put(key, mapper.map(value,
                                                                   GenericType.create(typeArgs[1]),
                                                                   key));
                                    });
                                });

                                return type.cast(theMap);
                            });
                        }
                    }
                }
            }

            if (rawType.equals(List.class)) {
                // we support List<?>, defaults to List<String>
                Type theType = type.type();
                if (theType instanceof ParameterizedType) {
                    ParameterizedType ptype = (ParameterizedType) theType;
                    Type[] typeArgs = ptype.getActualTypeArguments();
                    if (typeArgs.length == 1) {
                        Type elementType = typeArgs[0];
                        return Optional.of((config, mapper) -> {
                            List<Object> theList = new LinkedList<>();

                            config.asNodeList()
                                    .ifPresent(nodes -> {
                                        nodes.forEach(confNode -> {
                                            theList.add(confNode.as(GenericType.create(elementType)).get());
                                        });
                                    });

                            return type.cast(theList);
                        });
                    }
                } else {
                    return Optional.of((config, mapper) -> type.cast(config.asList(String.class).get()));
                }
            }

            return Optional.empty();
        }
    }
}
