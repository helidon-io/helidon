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

import java.lang.reflect.Array;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.config.spi.ConfigMapper;
import io.helidon.config.spi.ConfigMapperProvider;

/**
 * Manages registered Mappers to be used by Config implementation.
 */
class ConfigMapperManager implements ConfigMapper {
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

    private final Map<GenericType<?>, Mapper<?>> mappers;
    private final MapperProviders mapperProviders;

    ConfigMapperManager(MapperProviders mapperProviders) {
        this.mappers = new ConcurrentHashMap<>();
        this.mapperProviders = mapperProviders;
    }

    @Override
    public <T> T map(Config config, Class<T> type) throws MissingValueException, ConfigMappingException {
        if (type.isArray()) {
            return mapArray(config, type);
        }
        return map(config, GenericType.create(supportedType(type)));
    }

    @SuppressWarnings("unchecked")
    private <T> T mapArray(Config config, Class<T> type) {
        Class<?> componentType = type.getComponentType();
        List<?> listValue = config.asList(componentType).get();
        Object result = Array.newInstance(componentType, listValue.size());
        for (int i = 0; i < listValue.size(); i++) {
             Object component = listValue.get(i);
            Array.set(result, i, component);
        }
        return (T) result;
    }

    @Override
    public <T> T map(Config config, GenericType<T> type) throws MissingValueException, ConfigMappingException {
        Mapper<?> mapper = mappers.computeIfAbsent(type, theType -> findMapper(theType, config.key()));

        return cast(type, mapper.apply(config, this), config.key());
    }

    @Override
    public <T> T map(String value, Class<T> type, String key) throws MissingValueException, ConfigMappingException {
        return map(simpleConfig(key, value), type);
    }

    @Override
    public <T> T map(String value, GenericType<T> type, String key) throws MissingValueException, ConfigMappingException {
        return map(simpleConfig(key, value), type);
    }

    @SuppressWarnings("unchecked")
    <T> Optional<? extends BiFunction<Config, ConfigMapper, T>> mapper(GenericType<T> type) {
        Mapper<T> mapper = (Mapper<T>) mappers.get(type);
        if (null == mapper) {
            return mapperProviders.findMapper(type, Config.Key.create(""));
        } else {
            return Optional.of(mapper);
        }
    }

    private <T> Mapper<T> findMapper(GenericType<T> type, Config.Key key) {
        return mapperProviders.findMapper(type, key)
                .orElseGet(() -> noMapper(type));
    }

    private <T> Mapper<T> noMapper(GenericType<T> type) {
        return new NoMapperFound<>(type);
    }

    @SuppressWarnings("unchecked")
    static <T> T cast(GenericType<T> type, Object instance, Config.Key key) throws ConfigMappingException {
        try {
            return (T) instance;
        } catch (ClassCastException ex) {
            throw new ConfigMappingException(key,
                                             type,
                                             "Created instance is not assignable to the type.",
                                             ex);
        }
    }

    /**
     * Provides mapping from a class to a supported class.
     * This is used to map Java primitives to their respective object classes.
     *
     * @param type type to map
     * @param <T> type of the class
     * @return object type for primitives, or the same class if not a primitive
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> supportedType(Class<T> type) {
        return (Class<T>) REPLACED_TYPES.getOrDefault(type, type);
    }

    Config simpleConfig(String name, String stringValue) {
        return new SingleValueConfigImpl(this, name, stringValue);
    }

    @FunctionalInterface
    interface Mapper<T> extends BiFunction<Config, ConfigMapper, T> {
        static <T> Mapper<T> create(BiFunction<Config, ConfigMapper, T> function) {
            return function::apply;
        }

        static <T> Mapper<T> create(Function<Config, T> function) {
            return (config, configMapper) -> function.apply(config);
        }
    }

    static final class MapperProviders {
        // functions that provide mapping functions based on the type expected
        private final LinkedList<Function<GenericType<?>,
                Optional<? extends BiFunction<Config, ConfigMapper, ?>>>> providers = new LinkedList<>();

        private MapperProviders() {
        }

        static MapperProviders create() {
            return new MapperProviders();
        }

        void add(Function<GenericType<?>, Optional<? extends BiFunction<Config, ConfigMapper, ?>>> function) {
            this.providers.addFirst(function);
        }

        void add(ConfigMapperProvider provider) {
            add(new ProviderWrapper(provider));
        }

        void addAll(MapperProviders other) {
            LinkedList<Function<GenericType<?>,
                    Optional<? extends BiFunction<Config, ConfigMapper, ?>>>> otherProviders = new LinkedList<>(other.providers);

            Collections.reverse(otherProviders);

            otherProviders
                    .forEach(this::add);
        }

        // generic type map, generic type method, specific type map, specific type method
        <T> Optional<Mapper<T>> findMapper(GenericType<T> type, Config.Key key) {
            return providers.stream()
                    .map(provider -> provider.apply(type))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .map(mapper -> castMapper(type, mapper, key))
                    .map(Mapper::create);
        }

        @SuppressWarnings("unchecked")
        private static <T> BiFunction<Config, ConfigMapper, T> castMapper(GenericType<T> type,
                                                                          BiFunction<Config, ConfigMapper, ?> mapper,
                                                                          Config.Key key) {
            try {
                return (BiFunction<Config, ConfigMapper, T>) mapper;
            } catch (ClassCastException e) {
                throw new ConfigMappingException(key, type, "Mapper provider returned wrong mapper type", e);
            }
        }
    }

    /**
     * This simplified implementation of Config VALUE node to be used to represent a single string value,
     * to support easy mapping of String using methods expecting a Config.
     */
    static class SingleValueConfigImpl implements Config {

        private final ConfigMapperManager mapperManager;
        private final Key key;
        private final String value;
        private final Instant timestamp;

        SingleValueConfigImpl(ConfigMapperManager mapperManager, String key, String value) {
            this.mapperManager = mapperManager;
            this.key = Key.create(key);
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
        public ConfigValue<String> asString() {
            return ConfigValues.create(this, () -> Optional.ofNullable(value), Config::asString);
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
        public <T> ConfigValue<T> as(GenericType<T> genericType) {
            return ConfigValues.create(this, genericType, mapperManager);
        }

        @Override
        public ConfigValue<List<Config>> asNodeList() throws ConfigMappingException {
            return ConfigValues.create(this, () -> Optional.of(List.of(this)), Config::asNodeList);
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigMappingException {
            return ConfigValues.create(this, () -> as(type).map(List::of), config -> config.asList(type));
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException {
            return ConfigValues.create(this, () -> as(mapper).map(List::of), config -> config.asList(mapper));
        }

        @Override
        public ConfigValue<Map<String, String>> asMap() {
            return ConfigValues.createMap(this, mapperManager);
        }

        @Override
        public <T> T convert(Class<T> type, String value) throws ConfigMappingException {
            return mapperManager.map(
                    mapperManager.simpleConfig("", value),
                    type);
        }

        @Override
        public ConfigValue<Config> asNode() {
            return as(Config.class);
        }

        @Override
        public ConfigMapper mapper() {
            return mapperManager;
        }
    }

    // this class exists for debugging purposes - it is clearly seen that this mapper was not found
    // rather then having a lambda as a mapper
    private static final class NoMapperFound<T> implements Mapper<T> {
        private final GenericType<T> type;

        private NoMapperFound(GenericType<T> type) {
            this.type = type;
        }

        @Override
        public T apply(Config config, ConfigMapper configMapper) {
            throw new ConfigMappingException(config.key(), type, "No mapper configured");
        }

        @Override
        public String toString() {
            return "Mapper for " + type.getTypeName() + " is not defined";
        }
    }

    private static final class ProviderWrapper
            implements Function<GenericType<?>, Optional<? extends BiFunction<Config, ConfigMapper, ?>>> {
        private final ConfigMapperProvider provider;

        private ProviderWrapper(ConfigMapperProvider wrapped) {
            this.provider = wrapped;
        }

        @Override
        public Optional<? extends BiFunction<Config, ConfigMapper, ?>> apply(GenericType<?> genericType) {
            // first try to get it from generic type mappers map
            BiFunction<Config, ConfigMapper, ?> converter = provider.genericTypeMappers().get(genericType);

            if (null != converter) {
                return Optional.of(converter);
            }

            // second try to get it from generic type method
            Optional<? extends BiFunction<Config, ConfigMapper, ?>> mapper1 = provider.mapper(genericType);

            if (mapper1.isPresent()) {
                return mapper1;
            }

            if (!genericType.isClass()) {
                return Optional.empty();
            }

            // third try the specific class map
            Class<?> rawType = genericType.rawType();

            Function<Config, ?> configConverter = provider.mappers().get(rawType);

            if (null != configConverter) {
                return Optional.of((config, mapper) -> configConverter.apply(config));
            }

            // and last, the specific class method
            return provider.mapper(rawType)
                    .map(funct -> (config, mapper) -> funct.apply(config));
        }

        @Override
        public String toString() {
            return provider.toString();
        }
    }
}
