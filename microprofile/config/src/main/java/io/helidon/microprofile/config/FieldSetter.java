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

import java.lang.reflect.Field;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

import javax.enterprise.inject.spi.DeploymentException;

import io.helidon.config.ConfigException;

import org.eclipse.microprofile.config.Config;

/**
 * A helper component to set values of fields.
 */
class FieldSetter {
    private final Field field;
    private final String configKey;
    private final Class<?> fieldType;
    private final String defaultValue;
    private final boolean isOptional;

    FieldSetter(Field field, String configKey, String defaultValue) {
        this.field = field;
        this.configKey = configKey;
        this.defaultValue = defaultValue;
        FieldTypes fieldTypes = FieldTypes.create(field.getGenericType());
        if (fieldTypes.field0().rawType().equals(Optional.class)) {
            fieldType = fieldTypes.field1().rawType();
            isOptional = true;
        } else {
            fieldType = field.getType();
            isOptional = false;
        }
        field.setAccessible(true);
    }

    void set(Config config, String prefix, Object instance) {
        String prefixedKey = prefixed(prefix, configKey);
        try {
            if (defaultValue != null) {
                setValue(prefixedKey, field, instance, valueWithDefault(config, prefixedKey));
            } else {
                Object currentValue = field.get(instance);
                Optional<?> value = config.getOptionalValue(prefixedKey, fieldType);
                if (currentValue == null) {
                    // no current value, need configuration
                    if (value.isPresent()) {
                        setValue(prefixedKey, field, instance, value.get());
                    } else {
                        setValue(prefixedKey, field, instance, null);
                    }
                } else {
                    // there is existing value, only override if value in config
                    if (value.isPresent()) {
                        setValue(prefixedKey, field, instance, value.get());
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new ConfigException("Failed to set config properties field value for " + field, e);
        }
    }

    void setValue(String prefixedKey, Field field, Object instance, Object value) throws IllegalAccessException {
        if (value == null) {
            if (isOptional) {
                field.set(instance, Optional.empty());
                return;
            } else {
                throw new NoSuchElementException("Cannot set field " + field + ", as configuration key " + prefixedKey
                                                         + " is missing.");
            }
        }
        if (isOptional) {
            field.set(instance, Optional.of(value));
        } else {
            field.set(instance, value);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object valueWithDefault(Config config, String prefixedKey) {
        Optional maybeValue = config.getOptionalValue(prefixedKey, fieldType);
        return maybeValue
                .orElseGet(() -> defaultValue(config));
    }

    private Object defaultValue(Config config) {
        return config.getConverter(fieldType)
                .map(it -> it.convert(defaultValue))
                .orElseThrow(() -> new IllegalArgumentException("Cannot find converter for type "
                                                                        + fieldType
                                                                        + " to "
                                                                        + "convert default value of field "
                                                                        + field));
    }

    String prefixed(String prefix, String key) {
        if (prefix == null) {
            return key;
        }
        return prefix + "." + key;
    }

    // validate if the field has either a default value, or a configuration property available
    void validate(Supplier<Object> instanceCreator, Config config, String prefix) {
        String prefixedKey = prefixed(prefix, configKey);
        Optional<String> value = config.getOptionalValue(prefixedKey, String.class)
                .or(() -> Optional.ofNullable(defaultValue));

        if (value.isPresent()) {
            // there is a configured value, fine
            return;
        }

        if (isOptional) {
            // there is no configured value, but the field is optional
            return;
        }

        if (hasFieldValue(instanceCreator, field)) {
            // there is an explicit initializer of the field (or it is a primitive type)
            return;
        }

        throw new DeploymentException("Cannot find configuration key " + prefixedKey + " for field " + field);
    }

    private boolean hasFieldValue(Supplier<Object> instanceCreator, Field field) {
        Object instance = instanceCreator.get();
        try {
            Object value = field.get(instance);
            return value != null;
        } catch (Exception e) {
            throw new DeploymentException("Failed to get field value for field " + field, e);
        }
    }
}
