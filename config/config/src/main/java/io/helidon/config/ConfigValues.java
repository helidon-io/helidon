/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

/**
 * Factory for config values.
 */
final class ConfigValues {
    private ConfigValues() {
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
                                            () -> Optional.ofNullable(mapperManager.map(type, config)),
                                            aConfig -> aConfig.as(type));
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
                return config.asNodeList().value()
                        .map(list -> list.stream()
                                .map(theConfig -> getValue.apply(theConfig).getValue())
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

    public static ConfigValue<Map<String, String>> createMap(Config config,
                                                             ConfigMapperManager mapperManager) {

        Supplier<Optional<Map<String, String>>> valueSupplier = () -> {
            Map<?, ?> map = mapperManager.map(Map.class, config);

            if (map instanceof ConfigMappers.StringMap) {
                return Optional.of((ConfigMappers.StringMap) map);
            }
            return Optional.of(new ConfigMappers.StringMap(map));
        };

        return new GenericConfigValueImpl<>(config, valueSupplier, Config::asMap);
    }

    public static <T> ConfigValue<T> empty(Config config) {
        return new GenericConfigValueImpl<>(config, Optional::empty, ConfigValues::empty);
    }

    private static final class GenericConfigValueImpl<T> implements ConfigValue<T> {
        private final Supplier<Optional<T>> valueSupplier;
        private final Function<Config, ConfigValue<T>> configMethod;
        private final Config owningConfig;

        private GenericConfigValueImpl(Config owningConfig,
                                       Supplier<Optional<T>> valueSupplier,
                                       Function<Config, ConfigValue<T>> configMethod) {
            this.owningConfig = owningConfig;
            this.valueSupplier = valueSupplier;
            this.configMethod = configMethod;
        }

        @Override
        public Config.Key key() {
            return owningConfig.key();
        }

        @Override
        public Optional<T> value() {
            return valueSupplier.get();
        }

        @Override
        public Supplier<T> asSupplier() {
            return () -> configMethod.apply(latest()).getValue();
        }

        @Override
        public Supplier<T> asSupplier(T defaultValue) {
            return () -> configMethod.apply(latest()).getValue(defaultValue);
        }

        @Override
        public Supplier<Optional<T>> asOptionalSupplier() {
            return () -> configMethod.apply(latest()).value();
        }


        private Config latest() {
            return owningConfig.context().last();
        }
    }

}
