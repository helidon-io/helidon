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

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Manages registered Mappers to be used by Config implementation.
 */
class ConfigMapperManager {
    private static final Map<Class<?>, Class<?>> REPLACED_TYPES = new HashMap<>();

    static {
        REPLACED_TYPES.put(byte.class, Byte.class);
        REPLACED_TYPES.put(short.class, Short.class);
        REPLACED_TYPES.put(int.class, Integer.class);
        REPLACED_TYPES.put(long.class, Long.class);
        REPLACED_TYPES.put(float.class, Float.class);
        REPLACED_TYPES.put(double.class, Double.class);
        REPLACED_TYPES.put(boolean.class, Boolean.class);
        REPLACED_TYPES.put(char.class, Character.class);
    }

    private final Map<Class<?>, Mapper<?>> mappers;
    private final MapperProviders mapperProviders;

    ConfigMapperManager(Map<Class<?>, Mapper<?>> mappers,
                        MapperProviders mapperProviders) {
        this.mappers = new ConcurrentHashMap<>(mappers);
        this.mapperProviders = mapperProviders;
    }

    /**
     * Transforms the specified {@code Config} node into the target type.
     * <p>
     * The method uses the mapper function instance associated with the
     * specified {@code type} to convert the {@code Config} subtree. If there is
     * none it tries to find one from {@link io.helidon.config.spi.ConfigMapperProvider#mapper(Class)} from configured
     * mapper providers.
     * If none is found, mapping will throw a {@link ConfigMappingException}.
     *
     * @param type   type to which the config node is to be transformed
     * @param config config node to be transformed
     * @param <T>    type to which the config node is to be transformed
     * @return transformed value of type {@code T}; never returns {@code null}
     * @throws MissingValueException  in case the configuration node does not represent an existing configuration node
     * @throws ConfigMappingException in case the mapper fails to map the existing configuration value
     *                                to an instance of a given Java type
     */
    public <T> T map(Class<T> type, Config config) throws MissingValueException, ConfigMappingException {
        type = supportedType(type);
        Function<Config, ?> converter = mappers.computeIfAbsent(type, theType -> findMapper(theType, config.key()));
        return cast(type, converter.apply(config), config.key());
    }

    @SuppressWarnings("unchecked")
    <T> Optional<? extends Function<Config, T>> mapper(Class<T> type) {
        Mapper<T> mapper = (Mapper<T>) mappers.get(type);
        if (null == mapper) {
            return mapperProviders.findMapper(type, Config.Key.of(""));
        } else {
            return Optional.of(mapper);
        }
    }

    <T> T map(String key, Class<T> type, String value) throws MissingValueException, ConfigMappingException {
        return map(type, new SingleValueConfigImpl(this, key, value));
    }

    private <T> Mapper<T> findMapper(Class<T> type, Config.Key key) {
        return mapperProviders.findMapper(type, key)
                .orElseGet(() -> noMapper(type));
    }

    private <T> Mapper<T> noMapper(Class<T> type) {
        return new NoMapperFound<>(type);
    }

    public static <T> T cast(Class<T> type, Object instance, Config.Key key) throws ConfigMappingException {
        try {
            return type.cast(instance);
        } catch (ClassCastException ex) {
            throw new ConfigMappingException(key,
                                             type,
                                             "Created instance is not assignable to the type.",
                                             ex);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> Class<T> supportedType(Class<T> type) {
        return (Class<T>) REPLACED_TYPES.getOrDefault(type, type);
    }

    public Config simpleConfig(String name, String stringValue) {
        return new SingleValueConfigImpl(this, name, stringValue);
    }

    @FunctionalInterface
    interface Mapper<T> extends Function<Config, T> {
        static <T> Mapper<T> create(Function<Config, T> function) {
            return function::apply;
        }
    }

    static final class MapperProviders {
        private final List<Function<Class<?>, Optional<? extends Function<Config, ?>>>> providers = new LinkedList<>();

        private MapperProviders() {
        }

        static MapperProviders create() {
            return new MapperProviders();
        }

        void add(Function<Class<?>, Optional<? extends Function<Config, ?>>> function) {
            this.providers.add(function);
        }

        public void addAll(MapperProviders other) {
            this.providers.addAll(other.providers);
        }

        public <T> Optional<Mapper<T>> findMapper(Class<T> type, Config.Key key) {
            return providers.stream()
                    .map(provider -> provider.apply(type))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .map(mapper -> castMapper(type, mapper, key))
                    .map(Mapper::create);
        }

        @SuppressWarnings("unchecked")
        private static <T> Function<Config, T> castMapper(Class<T> type, Function<Config, ?> mapper, Config.Key key) {
            try {
                return (Function<Config, T>) mapper;
            } catch (ClassCastException e) {
                throw new ConfigMappingException(key, type, "Mapper provider returned wrong mapper type", e);
            }
        }
    }

    /**
     * This simplified implementation of Config VALUE node to be used to represent a single string value to
     * map using {@link #map(String, Class, String)}.
     */
    static class SingleValueConfigImpl implements Config {

        private final ConfigMapperManager mapperManager;
        private final Key key;
        private final String value;
        private final Instant timestamp;

        SingleValueConfigImpl(ConfigMapperManager mapperManager, String key, String value) {
            this.mapperManager = mapperManager;
            this.key = Key.of(key);
            this.value = value;

            this.timestamp = Instant.now();
        }

        @Override
        public boolean hasValue() {
            return null != value;
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public Optional<String> value() throws ConfigMappingException {
            return Optional.of(value);
        }

        @Override
        public Type type() {
            return Type.VALUE;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }

        @Override
        public Config get(Key key) {
            if (key.isRoot()) {
                return this;
            } else {
                return Config.empty().get(this.key).get(key);
            }
        }

        @Override
        public Config detach() {
            if (key.isRoot()) {
                return this;
            } else {
                return new SingleValueConfigImpl(mapperManager, "", value);
            }
        }

        @Override
        public Stream<Config> traverse(Predicate<Config> predicate) {
            return Stream.empty();
        }

        @Override
        public <T> ConfigValue<T> as(Class<T> type) {
            return ConfigValues.create(this, type, mapperManager);
        }

        @Override
        public <T> ConfigValue<T> as(Function<Config, T> mapper) {
            return ConfigValues.create(this, mapper);
        }

        @Override
        public ConfigValue<List<Config>> asNodeList() throws ConfigMappingException {
            throw new ConfigMappingException(key(), "The Config node represents single value.");
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigMappingException {
            throw new ConfigMappingException(key(), "The Config node represents single value.");
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException {
            throw new ConfigMappingException(key(), "The Config node represents single value.");
        }

        @Override
        public ConfigValue<Map<String, String>> asMap() {
            return ConfigValues.createMap(this, mapperManager);
        }

        @Override
        public <T> T convert(Class<T> type, String value) throws ConfigMappingException {
            return mapperManager.map("", type, value);
        }

        @Override
        public ConfigValue<Config> asNode() {
            return as(Config.class);
        }
    }

    // this class exists for debugging purposes - it is clearly seen that this mapper was not found
    // rather then having a lambda as a mapper
    private static final class NoMapperFound<T> implements Mapper<T> {
        private final Class<T> type;

        private NoMapperFound(Class<T> type) {
            this.type = type;
        }

        @Override
        public T apply(Config config) {
            throw new ConfigMappingException(config.key(), type, "No mapper configured");
        }

        @Override
        public String toString() {
            return "Mapper for " + type.getSimpleName() + " is not defined";
        }
    }
}
