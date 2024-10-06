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

package io.helidon.service.inject;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;
import io.helidon.common.config.ConfigValue;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.Configuration;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Ip;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.registry.ServiceRegistry;

@Injection.Singleton
class ConfigValueProvider implements Injection.QualifiedProvider<Object, Configuration.Value> {
    private final ServiceRegistry registry;
    private final Supplier<Config> config;

    ConfigValueProvider(ServiceRegistry registry, Supplier<Config> config) {
        this.registry = registry;
        this.config = config;
    }

    @Override
    public Optional<Injection.QualifiedInstance<Object>> first(Qualifier qualifier, Lookup lookup, GenericType<Object> type) {
        String value = qualifier.stringValue()
                .orElseThrow(() -> new IllegalStateException("Annotation "
                                                                     + Configuration.Value.class.getName()
                                                                     + " must have a value defined, yet received it without "
                                                                     + "value: " + qualifier));

        // if it contains :, then it separates default value from key
        String defaultValue = null;
        String key;

        if (value.contains(":")) {
            int index = value.indexOf(':');

            key = value.substring(0, index);
            defaultValue = value.substring(index + 1);
        } else {
            if (value.isEmpty()) {
                Ip ip = lookup.injectionPoint()
                        .orElseThrow(() -> new IllegalStateException("Configuration.Value does not specify a value "
                                                                             + "(configuration key), yet injection point is "
                                                                             + "not provided. Cannot infer key."));
                key = ip.service().fqName() + "." + ip.name();
            } else {
                key = value;
            }
        }

        Config configInstance = config.get();
        ConfigValue<?> configValue = configInstance.get(key)
                .as(type.rawType());

        Object result;
        if (configValue.isEmpty()) {
            result = defaultValue(configInstance, qualifier, type, defaultValue);
        } else {
            result = configValue.get();
        }

        return Optional.of(Injection.QualifiedInstance.create(result, qualifier));
    }

    @SuppressWarnings("unchecked")
    private Object defaultValue(Config config,
                                Qualifier qualifier,
                                GenericType<Object> type,
                                String defaultValue) {
        if (defaultValue != null) {
            return MapperManager.global()
                    .map(defaultValue, GenericType.STRING, type, "config");
        }

        // there may be a provider
        Optional<TypeName> typeName = qualifier.typeValue("defaultProvider");
        if (typeName.isEmpty()) {
            return null;
        }
        TypeName defaultProvider = typeName.get();
        if (defaultProvider.equals(Configuration.Value.NoProvider.TYPE)) {
            // no custom provider
            return null;
        }
        Object provider = registry.first(defaultProvider)
                .orElseThrow(() -> new ConfigException("Default value provider: " + defaultProvider.fqName()
                                                               + " must be available through service registry,"
                                                               + " maybe annotate it with @Service.Provider?"));

        // provider implements Function<Config, ?>
        Function<Config, Object> providerAsFunction = (Function<Config, Object>) provider;
        return providerAsFunction.apply(config);
    }
}
