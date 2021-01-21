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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.InjectionPoint;

import io.helidon.config.ConfigException;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;

final class ConfigBeanDescriptor {
    private final Class<?> type;
    private final String prefix;
    private final Supplier<Object> instanceCreator;
    private final List<FieldSetter> fieldSetters;

    private ConfigBeanDescriptor(Class<?> type,
                                 String prefix,
                                 Supplier<Object> instanceCreator,
                                 List<FieldSetter> fieldSetters) {
        this.type = type;
        this.prefix = prefix;
        this.instanceCreator = instanceCreator;
        this.fieldSetters = fieldSetters;
    }

    static ConfigBeanDescriptor create(AnnotatedType<?> annotatedType,
                                       ConfigProperties configProperties) {

        Class<?> type = annotatedType.getJavaClass();

        if (type.isInterface()) {
            throw new DeploymentException("Only concrete classes can be annotated with ConfigProperties, got " + type
                    .getName());
        }

        Supplier<Object> instanceCreator;
        try {
            Constructor<?> defaultConstructor = type.getConstructor();
            instanceCreator = () -> {
                try {
                    return defaultConstructor.newInstance();
                } catch (Exception e) {
                    throw new ConfigException("Failed to instantiate ConfigProperties type " + type.getName(), e);
                }
            };
        } catch (NoSuchMethodException e) {
            throw new DeploymentException("Failed to find default constructor on config properties class " + type.getName());
        }

        List<FieldSetter> fieldSetters = new LinkedList<>();
        for (Field field : type.getDeclaredFields()) {
            String configKey;
            String defaultValue;

            ConfigProperty configProperty = field.getAnnotation(ConfigProperty.class);
            if (configProperty == null) {
                configKey = field.getName();
                defaultValue = null;
            } else {
                configKey = configProperty.name();
                defaultValue = configProperty.defaultValue();
                if (ConfigProperty.UNCONFIGURED_VALUE.equals(defaultValue)
                        || defaultValue.isEmpty()) {
                    defaultValue = null;
                }
            }

            fieldSetters.add(new FieldSetter(field, configKey, defaultValue));
        }

        String prefix = findPrefix(configProperties.prefix(), null);

        ConfigBeanDescriptor descriptor = new ConfigBeanDescriptor(type,
                                                                   prefix,
                                                                   instanceCreator,
                                                                   fieldSetters);
        descriptor.validate(ConfigProvider.getConfig(), prefix);
        return descriptor;
    }

    private static String findPrefix(String explicitPrefix, String defaultPrefix) {
        if (ConfigProperties.UNCONFIGURED_PREFIX.equals(explicitPrefix)) {
            // we need to use the prefix defined on class if unconfigured is used
            return defaultPrefix;
        }
        if ("".equals(explicitPrefix)) {
            return null;
        }
        return explicitPrefix;
    }

    void validate(Config config, String prefix) {
        fieldSetters.forEach(it -> it.validate(instanceCreator, config, prefix));
    }

    Object produce(InjectionPoint injectionPoint, Config config) {
        Set<Annotation> qualifiers = injectionPoint.getQualifiers();
        ConfigProperties annotation = null;
        for (Annotation qualifier : qualifiers) {
            if (qualifier.annotationType().equals(ConfigProperties.class)) {
                annotation = (ConfigProperties) qualifier;
                break;
            }
        }

        if (annotation == null) {
            // use the prefix as defined on the bean descriptor
            return produce(config, prefix);
        }
        // use prefix from injection point
        Type type = injectionPoint.getType();
        FieldTypes fieldTypes = FieldTypes.create(type);

        Object value = produce(config, findPrefix(annotation.prefix(), prefix));

        if (fieldTypes.field0().rawType().equals(Optional.class)) {
            return Optional.of(value);
        }

        return value;
    }

    Object produce(Config config, String prefix) {
        Object instance = instanceCreator.get();
        for (FieldSetter fieldSetter : fieldSetters) {
            fieldSetter.set(config, prefix, instance);
        }
        return instance;
    }

    Type type() {
        return type;
    }
}
