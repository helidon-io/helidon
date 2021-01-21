/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.config;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * MP Config that is serializable. This is required by the specification.
 * The config is in fact transient and obtained from ConfigProvider on deserialization.
 */
final class SerializableConfig implements org.eclipse.microprofile.config.Config, Serializable {
    private static final long serialVersionUID = 1;

    private transient org.eclipse.microprofile.config.Config theConfig;

    SerializableConfig() {
        this.theConfig = ConfigProvider.getConfig();
    }

    @Override
    public ConfigValue getConfigValue(String propertyName) {
        return theConfig.getConfigValue(propertyName);
    }

    @Override
    public <T> Optional<Converter<T>> getConverter(Class<T> forType) {
        return theConfig.getConverter(forType);
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        return theConfig.unwrap(type);
    }

    @Override
    public <T> T getValue(String propertyName, Class<T> propertyType) {
        return theConfig.getValue(propertyName, propertyType);
    }

    @Override
    public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
        return theConfig.getOptionalValue(propertyName, propertyType);
    }

    @Override
    public Iterable<String> getPropertyNames() {
        return theConfig.getPropertyNames();
    }

    @Override
    public Iterable<ConfigSource> getConfigSources() {
        return theConfig.getConfigSources();
    }

    private void readObject(ObjectInputStream ios) throws ClassNotFoundException, IOException {
        ios.defaultReadObject();
        this.theConfig = ConfigProvider.getConfig();
    }
}
