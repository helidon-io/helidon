/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import io.helidon.common.mapper.DefaultsResolver;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Service.QualifiedInstance;

import static java.util.function.Predicate.not;

/**
 * A factory that looks up configuration and provides values to injection points.
 */
@Service.Singleton
class ConfigurationValueFactory implements Service.QualifiedFactory<Object, Configuration.Value> {
    private final Supplier<DefaultsResolver> defaultsResolver;
    private final Supplier<Config> config;

    ConfigurationValueFactory(Supplier<DefaultsResolver> defaultsResolver,
                              Supplier<Config> config) {
        this.defaultsResolver = defaultsResolver;
        this.config = config;
    }

    @Override
    public Optional<QualifiedInstance<Object>> first(Qualifier qualifier, Lookup lookup, GenericType<Object> type) {
        var list = list(qualifier, lookup, type);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.getFirst());
    }

    @Override
    public List<QualifiedInstance<Object>> list(Qualifier qualifier, Lookup lookup, GenericType<Object> type) {
        Optional<Dependency> maybeDependency = lookup.dependency();

        String expression = qualifier.value()
                .filter(not(String::isBlank))
                .orElseGet(() -> obtainKey(lookup, maybeDependency));

        // expression is either a key, a ${key:default} expression, or a "https://${host}:${port:80}" complex expression
        Config configInstance = config.get();
        List<QualifiedInstance<Object>> values = expressionValue(qualifier, configInstance, expression, type);
        if (values.isEmpty()) {
            values = defaultValues(qualifier, type, maybeDependency);
        }

        return values;
    }

    private List<QualifiedInstance<Object>> expressionValue(Qualifier qualifier,
                                                            Config configInstance,
                                                            String expression,
                                                            GenericType<Object> type) {

        if (expression.startsWith("${") && expression.indexOf('}', 2) == expression.length() - 1) {
            // a single expression
            return singleExpression(qualifier, configInstance, expression, type);
        } else if (expression.contains("${")) {
            // multiple expressions ("${" is not a valid key)
            return multipleExpressions(qualifier, configInstance, expression, type);
        } else {
            // no expression, just a key
            return configValue(qualifier, configInstance.get(expression), type);
        }
    }

    private List<QualifiedInstance<Object>> multipleExpressions(Qualifier qualifier,
                                                                Config configInstance,
                                                                String expression,
                                                                GenericType<Object> type) {
        // multiple expressions always resolve into a single value
        Object value = configInstance.mapper()
                .map(ConfigBuilderSupport.resolveExpression(configInstance, expression), type, expression);
        return List.of(QualifiedInstance.create(value, qualifier));
    }

    private List<QualifiedInstance<Object>> singleExpression(Qualifier qualifier,
                                                             Config configInstance,
                                                             String expression,
                                                             GenericType<Object> type) {
        // not using regexp here
        String key;
        String defaultValue;
        int colonIndex = expression.indexOf(':');
        if (colonIndex == -1) {
            key = expression.substring(2, expression.length() - 1);
            defaultValue = null;
        } else {
            key = expression.substring(2, colonIndex);
            defaultValue = expression.substring(colonIndex + 1, expression.length() - 1);
        }

        Config configAtKey = configInstance.get(key);

        if (defaultValue == null) {
            // single expression, but no default
            return configValue(qualifier, configAtKey, type);
        }

        if (configAtKey.exists()) {
            // when the key exists, preserve the same mapping behavior as a plain key lookup
            return configValue(qualifier, configAtKey, type);
        }
        // map the default to expected type
        return List.of(QualifiedInstance.create(configInstance.mapper().map(defaultValue, type, key), qualifier));
    }

    private String obtainKey(Lookup lookup, Optional<Dependency> maybeDependency) {
        Dependency ip = maybeDependency
                .orElseThrow(() -> new IllegalStateException("Configuration.Value does not specify a value "
                                                                     + "(configuration key), yet dependency is "
                                                                     + "not provided in lookup. Cannot infer key. "
                                                                     + "Lookup: " + lookup));
        return ip.service().fqName() + "." + ip.name();
    }

    private List<QualifiedInstance<Object>> configValue(Qualifier qualifier,
                                                        Config configAtKey,
                                                        GenericType<Object> type) {
        List<QualifiedInstance<Object>> values = List.of();
        if (configAtKey.isList()) {
            values = listValues(qualifier, configAtKey, type);
        } else if (configAtKey.exists()) {
            ConfigValue<?> configValue = configAtKey.as(type.rawType());

            if (configValue.isEmpty()) {
                values = List.of();
            } else {
                values = List.of(QualifiedInstance.create(configValue.get(), qualifier));
            }
        }
        return values;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<QualifiedInstance<Object>> listValues(Qualifier qualifier,
                                                       Config configAtKey,
                                                       GenericType<Object> type) {
        ConfigValue list = configAtKey.asList(type.rawType());
        if (list.isEmpty()) {
            return List.of();
        }
        return ((List<Object>) list.get())
                .stream()
                .map(it -> QualifiedInstance.create(it, qualifier))
                .collect(Collectors.toUnmodifiableList());
    }

    private List<QualifiedInstance<Object>> defaultValues(Qualifier qualifier,
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
                .resolve(dependency.annotations(), type, dependency.name())
                .stream()
                .map(it -> (Object) it)
                .map(it -> QualifiedInstance.create(it, qualifier))
                .collect(Collectors.toUnmodifiableList());
    }
}
