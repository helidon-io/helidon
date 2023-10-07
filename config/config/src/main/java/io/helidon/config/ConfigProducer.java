/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import java.util.function.Function;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;
import io.helidon.common.config.ConfigValue;
import io.helidon.common.config.GlobalConfig;
import io.helidon.config.spi.ConfigSource;
import io.helidon.inject.api.ExternalContracts;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@ExternalContracts(Config.class)
class ConfigProducer implements Config {
    private final Config config;

    @Inject
    ConfigProducer(List<Provider<ConfigSource>> serviceProviders) {
        if (GlobalConfig.configured()) {
            config = GlobalConfig.config();
        } else {
            config = io.helidon.config.Config.builder()
                    .metaConfig()
                    .update(it -> serviceProviders.stream()
                            .map(Provider::get)
                            .map(ConfigSource.class::cast)
                            .forEach(it::addSource))
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
    public Config get(String key) throws ConfigException {
        return config.get(key);
    }

    @Override
    public Config detach() throws ConfigException {
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
    public <T> ConfigValue<T> as(Class<T> type) {
        return config.as(type);
    }

    @Override
    public <T> ConfigValue<T> map(Function<Config, T> mapper) {
        return config.map(mapper);
    }

    @Override
    public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigException {
        return config.asList(type);
    }

    @Override
    public <T> ConfigValue<List<T>> mapList(Function<Config, T> mapper) throws ConfigException {
        return config.mapList(mapper);
    }

    @Override
    public <C extends Config> ConfigValue<List<C>> asNodeList() throws ConfigException {
        return config.asNodeList();
    }

    @Override
    public ConfigValue<Map<String, String>> asMap() throws ConfigException {
        return config.asMap();
    }
}
