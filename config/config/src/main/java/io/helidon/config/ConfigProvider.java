/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigMapper;
import io.helidon.config.spi.ConfigMapperProvider;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10) // less than default, so it can be easily overridden
class ConfigProvider implements Supplier<Config> {
    private final Config config;

    @SuppressWarnings("removal")
    @Service.Inject
    ConfigProvider(Supplier<Optional<MetaConfig>> metaConfigSupplier,
                   Supplier<List<ConfigSource>> configSources,
                   Supplier<List<ConfigParser>> configParsers,
                   Supplier<List<ConfigFilter>> configFilters,
                   Supplier<List<ConfigMapperProvider>> configMappers) {
        if (io.helidon.common.config.GlobalConfig.configured()) {
            config = wrapCommon(io.helidon.common.config.GlobalConfig.config());
        } else {
            Optional<MetaConfig> metaConfig = metaConfigSupplier.get();
            config = Config.builder()
                    .update(it -> metaConfig.ifPresent(metaConfigInstance ->
                                                                     it.config(metaConfigInstance.metaConfiguration())))
                    .update(it -> configSources.get()
                            .forEach(it::addSource))
                    .update(it -> {
                        if (metaConfig.isEmpty()) {
                            defaultConfigSources(it, configParsers);
                        }
                    })
                    .disableParserServices()
                    .update(it -> configParsers.get()
                            .forEach(it::addParser))
                    .disableFilterServices()
                    .update(it -> configFilters.get()
                            .forEach(it::addFilter))
                    //.disableMapperServices()
                    // cannot do this for now, removed ConfigMapperProvider from service loaded services, config does it on its
                    // own
                    // ObjectConfigMapper is before EnumMapper, and both are before essential and built-in
                    .update(it -> configMappers.get()
                            .forEach(it::addMapper))
                    .build();
        }
    }

    @Override
    public Config get() {
        return config;
    }

    static Config wrapCommon(io.helidon.common.config.Config config) {
        if (config instanceof Config cfg) {
            return cfg;
        }
        return new CommonConfigWrapper(Config.empty(), config);
    }

    private void defaultConfigSources(io.helidon.config.Config.Builder configBuilder,
                                      Supplier<List<ConfigParser>> configParsers) {

        Set<String> supportedSuffixes = configParsers.get()
                .stream()
                .map(ConfigParser::supportedSuffixes)
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        // profile source(s) before defaults
        MetaConfigFinder.profile()
                .ifPresent(profile -> MetaConfigFinder.configSources(new ArrayList<>(supportedSuffixes),
                                                                     profile)
                        .forEach(configBuilder::addSource));

        // default config source(s)
        MetaConfigFinder.configSources(new ArrayList<>(supportedSuffixes))
                .forEach(configBuilder::addSource);
    }

    private static class CommonConfigWrapper implements Config {
        private final Config emptyConfig;
        private final Instant timestamp;
        private final io.helidon.common.config.Config delegate;

        private CommonConfigWrapper(Config realConfig, io.helidon.common.config.Config delegate) {
            this(realConfig, Instant.now(), delegate);
        }

        private CommonConfigWrapper(Config realConfig,
                                    Instant timestamp,
                                    io.helidon.common.config.Config delegate) {
            this.emptyConfig = realConfig;
            this.delegate = delegate;
            this.timestamp = timestamp;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }

        @Override
        public Key key() {
            return new CommonKeyWrapper(delegate.key());
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public Config get(String key) {
            return new CommonConfigWrapper(emptyConfig, timestamp, delegate.get(key));
        }

        @Override
        public Config root() {
            return new CommonConfigWrapper(emptyConfig, timestamp, delegate.root());
        }

        @Override
        public Config get(Key key) {
            return new CommonConfigWrapper(emptyConfig, timestamp, delegate.get(key));
        }

        @Override
        public Config detach() {
            return new CommonConfigWrapper(emptyConfig, timestamp, delegate.detach());
        }

        @Override
        public Type type() {
            if (delegate.isList()) {
                return Type.LIST;
            }
            if (delegate.isObject()) {
                return Type.OBJECT;
            }
            if (delegate.exists()) {
                return Type.VALUE;
            }
            return Type.MISSING;
        }

        @Override
        public boolean exists() {
            return delegate.exists();
        }

        @Override
        public boolean isLeaf() {
            return delegate.isLeaf();
        }

        @Override
        public boolean isObject() {
            return delegate.isObject();
        }

        @Override
        public boolean isList() {
            return delegate.isList();
        }

        @Override
        public boolean hasValue() {
            return delegate.hasValue();
        }

        @Override
        public void ifExists(Consumer<Config> action) {
            if (delegate.exists()) {
                action.accept(this);
            }
        }

        @Override
        public Stream<Config> traverse() {
            return delegate.asList(io.helidon.common.config.Config.class)
                    .stream()
                    .flatMap(List::stream)
                    .map(it -> new CommonConfigWrapper(emptyConfig, timestamp, it));
        }

        @Override
        public Stream<Config> traverse(Predicate<Config> predicate) {
            return traverse()
                    .filter(predicate);
        }

        @Override
        public <T> T convert(Class<T> type, String value) throws ConfigMappingException {
            return emptyConfig.convert(type, value);
        }

        @Override
        public ConfigMapper mapper() {
            return emptyConfig.mapper();
        }

        @Override
        public ConfigValue<String> asString() {
            var commonValue = delegate.asString();
            if (commonValue.isPresent()) {
                return ConfigValues.create(this, commonValue::asOptional, Config::asString);
            }
            return ConfigValues.create(this, Optional::empty, Config::asString);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> ConfigValue<T> as(GenericType<T> genericType) {
            if (genericType.isClass()) {
                return (ConfigValue<T>) as(genericType.rawType());
            }
            return ConfigValues.create(this, genericType, mapper());
        }

        @Override
        public <T> ConfigValue<T> as(Class<T> type) {
            return ConfigValues.create(this, type, mapper());
        }

        @Override
        public <T> ConfigValue<T> as(Function<Config, T> mapper) {
            return ConfigValues.create(this, mapper);
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigMappingException {
            return ConfigValues.createList(this, cfg -> cfg.as(type), cfg -> cfg.asList(type));
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException {
            return ConfigValues.createList(this, cfg -> cfg.as(mapper), cfg -> cfg.asList(mapper));
        }

        @Override
        public ConfigValue<List<Config>> asNodeList() throws ConfigMappingException {
            var nodeList = delegate.asNodeList();
            if (nodeList.isEmpty()) {
                return ConfigValues.create(this, Optional::empty, Config::asNodeList);
            }

            return ConfigValues.create(this,
                                       () -> Optional.of(nodeList.stream()
                                                                 .flatMap(List::stream)
                                                                 .map(ConfigProvider::wrapCommon)
                                                                 .toList()),
                                       Config::asNodeList);
        }

        @Override
        public ConfigValue<Map<String, String>> asMap() throws MissingValueException {
            return ConfigValues.createMap(this, mapper());
        }

        @Override
        public io.helidon.common.config.Config get(io.helidon.common.config.Config.Key key) {
            return delegate.get(key);
        }

        @Override
        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }
    }

    static class CommonKeyWrapper implements Config.Key {
        private final io.helidon.common.config.Config.Key delegate;

        CommonKeyWrapper(io.helidon.common.config.Config.Key key) {
            this.delegate = key;
        }

        @Override
        public Config.Key parent() {
            return new CommonKeyWrapper(delegate.parent());
        }

        @Override
        public Config.Key child(io.helidon.common.config.Config.Key key) {
            return new CommonKeyWrapper(delegate.child(key));
        }

        @Override
        public boolean isRoot() {
            return delegate.isRoot();
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public int compareTo(io.helidon.common.config.Config.Key o) {
            return delegate.compareTo(o);
        }
    }
}
