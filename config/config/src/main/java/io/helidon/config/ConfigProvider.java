/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;
import io.helidon.common.config.ConfigValue;
import io.helidon.common.config.GlobalConfig;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigMapperProvider;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.service.registry.Service;

@Service.Provider
@Service.ExternalContracts(Config.class)
class ConfigProvider implements Config {
    private final Config config;

    ConfigProvider(Supplier<MetaConfig> metaConfig,
                   Supplier<List<ConfigSource>> configSources,
                   Supplier<List<ConfigParser>> configParsers,
                   Supplier<List<ConfigFilter>> configFilters,
                   Supplier<List<ConfigMapperProvider>> configMappers) {
        if (GlobalConfig.configured()) {
            config = GlobalConfig.config();
        } else {
            config = io.helidon.config.Config.builder()
                    .config(metaConfig.get().metaConfiguration())
                    .update(it -> configSources.get()
                            .forEach(it::addSource))
                    .update(it -> defaultConfigSources(it, configParsers))
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
    public Key key() {
        return config.key();
    }

    @Override
    public Config root() {
        return config.root();
    }

    @Override
    public Config get(String key) throws io.helidon.common.config.ConfigException {
        return config.get(key);
    }

    @Override
    public Config detach() throws io.helidon.common.config.ConfigException {
        return config.detach();
    }

    @Override
    public boolean exists() {
        return config.exists();
    }

    @Override
    public boolean isLeaf() {
        return config.isLeaf();
    }

    @Override
    public boolean isObject() {
        return config.isObject();
    }

    @Override
    public boolean isList() {
        return config.isList();
    }

    @Override
    public boolean hasValue() {
        return config.hasValue();
    }

    @Override
    public <T> io.helidon.common.config.ConfigValue<T> as(Class<T> type) {
        return config.as(type);
    }

    @Override
    public <T> io.helidon.common.config.ConfigValue<T> map(Function<Config, T> mapper) {
        return config.map(mapper);
    }

    @Override
    public <T> io.helidon.common.config.ConfigValue<List<T>> asList(Class<T> type) throws
            io.helidon.common.config.ConfigException {
        return config.asList(type);
    }

    @Override
    public <T> io.helidon.common.config.ConfigValue<List<T>> mapList(Function<Config, T> mapper) throws
            io.helidon.common.config.ConfigException {
        return config.mapList(mapper);
    }

    @Override
    public <C extends Config> io.helidon.common.config.ConfigValue<List<C>> asNodeList() throws
            io.helidon.common.config.ConfigException {
        return config.asNodeList();
    }

    @Override
    public ConfigValue<Map<String, String>> asMap() throws ConfigException {
        return config.asMap();
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
                                                                     profile));
        // default config source(s)
        MetaConfigFinder.configSources(new ArrayList<>(supportedSuffixes))
                .forEach(configBuilder::addSource);

    }
}
