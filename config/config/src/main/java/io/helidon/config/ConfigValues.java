/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;

/**
 * Factory for config values.
 */
public final class ConfigValues {
    private ConfigValues() {
    }

    /**
     * Simple empty value that can be used e.g. for unit testing.
     * All ConfigValues use equals method that only cares about the optional value.
     *
     * @param <T> type of the value
     * @return a config value that is empty
     */
    public static <T> ConfigValue<T> empty() {
        return new ConfigValueBase<>(Config.Key.create("")) {
            @Override
            public Optional<T> asOptional() {
                return Optional.empty();
            }

            @Override
            public <N> ConfigValue<N> as(Function<T, N> mapper) {
                return empty();
            }

            @Override
            public Supplier<T> supplier() {
                return () -> {
                    throw MissingValueException.create(key());
                };
            }

            @Override
            public Supplier<T> supplier(T defaultValue) {
                return () -> defaultValue;
            }

            @Override
            public Supplier<Optional<T>> optionalSupplier() {
                return Optional::empty;
            }

            @Override
            public String toString() {
                return "ConfigValue(empty)";
            }
        };
    }

    /**
     * Simple value that can be used e.g. for unit testing.
     * All ConfigValues use equals method that only cares about the optional value.
     *
     * @param value value to use
     * @param <T>   type of the value
     * @return a config value that uses the value provided
     */
    public static <T> ConfigValue<T> simpleValue(T value) {
        return new ConfigValueBase<>(Config.Key.create("")) {
            @Override
            public Optional<T> asOptional() {
                return Optional.ofNullable(value);
            }

            @Override
            public <N> ConfigValue<N> as(Function<T, N> mapper) {
                return simpleValue(mapper.apply(value));
            }

            @Override
            public Supplier<T> supplier() {
                return () -> value;
            }

            @Override
            public Supplier<T> supplier(T defaultValue) {
                return () -> asOptional().orElse(defaultValue);
            }

            @Override
            public Supplier<Optional<T>> optionalSupplier() {
                return this::asOptional;
            }

            @Override
            public String toString() {
                return "ConfigValue(" + value + ")";
            }
        };
    }

    static <T> ConfigValue<T> create(Config config,
                                     Supplier<Optional<T>> supplier,
                                     Function<Config, ConfigValue<T>> configMethod) {
        return new GenericConfigValueImpl<>(config, supplier, configMethod);
    }

    static <T> ConfigValue<T> create(Config config,
                                     Class<T> type,
                                     ConfigMapperManager mapperManager) {

        return new GenericConfigValueImpl<>(config,
                                            () -> Optional.ofNullable(mapperManager.map(config, type)),
                                            aConfig -> aConfig.as(type));
    }

    static <T> ConfigValue<T> create(Config config,
                                     GenericType<T> genericType,
                                     ConfigMapperManager mapperManager) {
        return new GenericConfigValueImpl<>(config,
                                            () -> Optional.ofNullable(mapperManager.map(config, genericType)),
                                            aConfig -> aConfig.as(genericType));
    }

    static <T> ConfigValue<T> create(Config config,
                                     Function<Config, T> mapper) {

        return new GenericConfigValueImpl<>(config,
                                            () -> Optional.ofNullable(mapper.apply(config)),
                                            aConfig -> aConfig.as(mapper));
    }

    static <T> ConfigValue<List<T>> createList(Config config,
                                               Function<Config, ConfigValue<T>> getValue,
                                               Function<Config, ConfigValue<List<T>>> getListValue) {

        Supplier<Optional<List<T>>> valueSupplier = () -> {
            try {
                return config.asNodeList()
                        .map(list -> list.stream()
                                .map(theConfig -> getValue.apply(theConfig).get())
                                .collect(Collectors.toList())
                        );
            } catch (MissingValueException | ConfigMappingException ex) {
                throw new ConfigMappingException(config.key(),
                                                 "Error to map complex node item to list. " + ex.getLocalizedMessage(),
                                                 ex);
            }
        };
        return new GenericConfigValueImpl<>(config, valueSupplier, getListValue);
    }

    static ConfigValue<Map<String, String>> createMap(Config config,
                                                             ConfigMapperManager mapperManager) {

        Supplier<Optional<Map<String, String>>> valueSupplier = () -> {
            Map<?, ?> map = mapperManager.map(config, Map.class);

            if (map instanceof ConfigMappers.StringMap) {
                return Optional.of((ConfigMappers.StringMap) map);
            }
            return Optional.of(new ConfigMappers.StringMap(map));
        };

        return new GenericConfigValueImpl<>(config, valueSupplier, Config::asMap);
    }

    private abstract static class ConfigValueBase<T> implements ConfigValue<T> {
        private final Config.Key key;

        protected ConfigValueBase(Config.Key key) {
            this.key = key;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ConfigValue) {
                return ((ConfigValue<?>) obj).asOptional().equals(this.asOptional());
            }
            return false;
        }

        @Override
        public Config.Key key() {
            return key;
        }

        @Override
        public int hashCode() {
            return asOptional().hashCode();
        }
    }

    private static final class GenericConfigValueImpl<T> extends ConfigValueBase<T> {
        private final Supplier<Optional<T>> valueSupplier;
        private final Function<Config, ConfigValue<T>> configMethod;
        private final Config owningConfig;

        private GenericConfigValueImpl(Config owningConfig,
                                       Supplier<Optional<T>> valueSupplier,
                                       Function<Config, ConfigValue<T>> configMethod) {
            super(owningConfig.key());
            this.owningConfig = owningConfig;
            this.valueSupplier = valueSupplier;
            this.configMethod = configMethod;
        }

        @Override
        public Optional<T> asOptional() {
            try {
                return valueSupplier.get();
            } catch (MissingValueException e) {
                return Optional.empty();
            }
        }

        @Override
        public Supplier<T> supplier() {
            return () -> configMethod.apply(latest()).get();
        }

        @Override
        public Supplier<T> supplier(T defaultValue) {
            return () -> configMethod.apply(latest()).orElse(defaultValue);
        }

        @Override
        public Supplier<Optional<T>> optionalSupplier() {
            return () -> configMethod.apply(latest()).asOptional();
        }

        private Config latest() {
            return owningConfig.context().last();
        }

        @Override
        public <N> ConfigValue<N> as(Function<T, N> mapper) {
            return new GenericConfigValueImpl<>(owningConfig,
                                                () -> map(mapper),
                                                config -> configMethod.apply(config).as(mapper));
        }

        @Override
        public String toString() {
            return key() + ": " + asOptional().map(String::valueOf).orElse("");
        }
    }

}
