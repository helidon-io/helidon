/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;
import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigValue;
import io.helidon.common.mapper.DefaultsResolver;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

import static java.util.function.Predicate.not;

/**
 * A factory that looks up configuration and provides values to injection points.
 */
@Service.Singleton
class ConfigurationValueFactory implements Service.QualifiedFactory<Object, Configuration.Value> {
    private final Supplier<DefaultsResolver> defaultsResolver;
    private final Supplier<io.helidon.common.config.Config> config;

    ConfigurationValueFactory(Supplier<DefaultsResolver> defaultsResolver,
                              Supplier<Config> config) {
        this.defaultsResolver = defaultsResolver;
        this.config = config;
    }

    @Override
    public Optional<Service.QualifiedInstance<Object>> first(Qualifier qualifier, Lookup lookup, GenericType<Object> type) {
        var list = list(qualifier, lookup, type);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.getFirst());
    }

    @Override
    public List<Service.QualifiedInstance<Object>> list(Qualifier qualifier, Lookup lookup, GenericType<Object> type) {
        Optional<Dependency> maybeDependency = lookup.dependency();

        String key = qualifier.value()
                .filter(not(String::isBlank))
                .orElseGet(() -> obtainKey(lookup, maybeDependency));

        Config configInstance = config.get();
        Config configAtKey = configInstance.get(key);

        List<Service.QualifiedInstance<Object>> values = List.of();
        if (configAtKey.isList()) {
            values = listValues(qualifier, configAtKey, type);
        } else if (configAtKey.exists()) {
            ConfigValue<?> configValue = configInstance.get(key)
                    .as(type.rawType());

            if (configValue.isEmpty()) {
                values = List.of();
            } else {
                values = List.of(Service.QualifiedInstance.create(configValue.get(), qualifier));
            }
        }

        if (values.isEmpty()) {
            values = defaultValues(qualifier, configInstance, type, maybeDependency);
        }

        return values;
    }

    private String obtainKey(Lookup lookup, Optional<Dependency> maybeDependency) {
        Dependency ip = maybeDependency
                .orElseThrow(() -> new IllegalStateException("Configuration.Value does not specify a value "
                                                                     + "(configuration key), yet dependency is "
                                                                     + "not provided in lookup. Cannot infer key. "
                                                                     + "Lookup: " + lookup));
        return ip.service().fqName() + "." + ip.name();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Service.QualifiedInstance<Object>> listValues(Qualifier qualifier,
                                                               Config configAtKey,
                                                               GenericType<Object> type) {
        ConfigValue list = configAtKey.asList(type.rawType());
        if (list.isEmpty()) {
            return List.of();
        }
        return ((List<Object>) list.get())
                .stream()
                .map(it -> Service.QualifiedInstance.create(it, qualifier))
                .collect(Collectors.toUnmodifiableList());
    }

    private List<Service.QualifiedInstance<Object>> defaultValues(Qualifier qualifier,
                                                                  Config config,
                                                                  GenericType<Object> type,
                                                                  Optional<Dependency> maybeDependency) {

        // if there is no dependency, we cannot have defaults (as these are defined using an annotation)
        if (maybeDependency.isEmpty()) {
            return List.of();
        }

        Dependency dependency = maybeDependency.get();

        if (dependency.annotations().isEmpty()) {
            return List.of();
        }

        return defaultsResolver.get()
                .resolve(dependency.annotations(), type, dependency.name(), config)
                .stream()
                .map(it -> (Object) it)
                .map(it -> Service.QualifiedInstance.create(it, qualifier))
                .collect(Collectors.toUnmodifiableList());
    }
}
