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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.GenericType;

/**
 * Implementation of {@link Config} that represents {@link Config.Type#MISSING missing} node.
 */
class ConfigMissingImpl extends AbstractConfigImpl {

    ConfigMissingImpl(ConfigKeyImpl prefix,
                      ConfigKeyImpl key,
                      ConfigFactory factory,
                      ConfigMapperManager mapperManager) {
        super(Type.MISSING, prefix, key, factory, mapperManager);
    }

    @Override
    public boolean hasValue() {
        return false;
    }

    @Override
    public <T> ConfigValue<T> as(Class<T> type) {
        return ConfigValues.create(this, Optional::empty, aConfig -> aConfig.as(type));
    }

    @Override
    public <T> ConfigValue<T> as(Function<Config, T> mapper) {
        return ConfigValues.create(this, Optional::empty, aConfig -> aConfig.as(mapper));
    }

    @Override
    public <T> ConfigValue<T> as(GenericType<T> genericType) {
        return ConfigValues.create(this, Optional::empty, aConfig -> aConfig.as(genericType));
    }

    @Override
    public <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigMappingException {
        return ConfigValues.create(this, Optional::empty, aConfig -> aConfig.asList(type));
    }

    @Override
    public <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException {
        return ConfigValues.create(this, Optional::empty, aConfig -> aConfig.asList(mapper));
    }

    @Override
    public ConfigValue<Map<String, String>> asMap() {
        return ConfigValues.create(this, Optional::empty, Config::asMap);
    }

    @Override
    public Stream<Config> traverse(Predicate<Config> predicate) {
        return Stream.empty();
    }

    @Override
    public String toString() {
        return "[" + realKey() + "] MISSING";
    }

}
