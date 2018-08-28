/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.logging.Logger;

import io.helidon.config.GenericConfigMapper.PropertyAccessor;
import io.helidon.config.GenericConfigMapper.SingleValueConfigImpl;

/**
 * Generic config mapping utils.
 * <p>
 * Analyzes class and finds all Java bean properties that can be set during
 * deserialization.
 */
final class GenericConfigMapperUtils {

    private static final Logger LOGGER = Logger.getLogger(GenericConfigMapperUtils.class.getName());

    private GenericConfigMapperUtils() {
        throw new AssertionError("Instantiation not allowed.");
    }

    public static <T> Collection<PropertyAccessor> getBeanProperties(ConfigMapperManager mapperManager,
                                                                     Class<T> type) {
        return getPropertyAccessors(mapperManager, type).values();
    }

    static <T> Map<String, PropertyAccessor> getPropertyAccessors(ConfigMapperManager mapperManager,
                                                                  Class<T> type) {
        Set<String> transientProps = new HashSet<>();
        Map<String, PropertyAccessor> propertyAccessors = new HashMap<>();

        initMethods(mapperManager, type, transientProps, propertyAccessors);
        initFields(mapperManager, type, transientProps, propertyAccessors);

        return propertyAccessors;
    }

    static <T> void initMethods(ConfigMapperManager mapperManager, Class<T> type, Set<String> transientProps,
                                Map<String, PropertyAccessor> propertyAccessors) {
        for (Method method : type.getMethods()) {
            if (!isSetter(method)) {
                continue;
            }

            String name = propertyName(method);
            if (isTransient(method)) {
                transientProps.add(name);
                continue;
            }

            propertyAccessors.put(name, createPropertyAccessor(mapperManager, type, name, method));
        }
    }

    static <T> void initFields(ConfigMapperManager mapperManager, Class<T> type, Set<String> transientProps,
                               Map<String, PropertyAccessor> propertyAccessors) {
        for (Field field : type.getFields()) {
            if (!isAccessible(field)) {
                continue;
            }

            String name = propertyName(field);
            if (isTransient(field)) {
                if (propertyAccessors.containsKey(name)) {
                    throw new ConfigException("Illegal use of both @Config.Value (method) and @Config.Transient (field) "
                                                      + "annotations on single '" + name + "' property.");
                }
                continue;
            } else if (transientProps.contains(name)) {
                if (field.isAnnotationPresent(Config.Value.class)) {
                    throw new ConfigException("Illegal use of both @Config.Value (field) and @Config.Transient (method) "
                                                      + "annotations on single '" + name + "' property.");
                }
                continue;
            }

            if (propertyAccessors.containsKey(name)) {
                //just try to use @Value on field (if not already used from method)
                if (field.getAnnotation(Config.Value.class) != null) {
                    PropertyAccessor propertyAccessor = propertyAccessors.get(name);
                    if (!propertyAccessor.hasValueAnnotation()) {
                        propertyAccessor.setValueAnnotation(field.getAnnotation(Config.Value.class));
                    } else {
                        LOGGER.fine(() -> "Annotation @Config.Value on '" + name
                                + "' field is ignored because setter method already has one.");
                    }
                }
            } else {
                propertyAccessors.put(name, createPropertyAccessor(mapperManager, type, name, field));
            }
        }
    }

    static <T> PropertyAccessor<T> createPropertyAccessor(ConfigMapperManager mapperManager,
                                                          Class<T> type,
                                                          String name,
                                                          Method method) {
        try {
            final Class<T> propertyType = (Class<T>) method.getParameterTypes()[0];
            Class<?> configAsType = propertyType;

            boolean list = configAsType.isAssignableFrom(List.class);
            if (list) {
                Type genType = method.getGenericParameterTypes()[0];
                if (genType instanceof ParameterizedType) {
                    configAsType = (Class<?>) ((ParameterizedType) genType).getActualTypeArguments()[0];
                } else {
                    throw new ConfigException("Unable to find generic type of List on setter parameter: " + method);
                }
            }

            MethodHandle handle = MethodHandles.publicLookup()
                    .findVirtual(type,
                                 method.getName(),
                                 MethodType.methodType(method.getReturnType(), method.getParameterTypes()));

            return new PropertyAccessor<>(mapperManager, name, propertyType, configAsType, list, handle,
                                          method.getAnnotation(Config.Value.class));
        } catch (NoSuchMethodException | IllegalAccessException | ClassCastException ex) {
            throw new ConfigException("Cannot access setter: " + method, ex);
        }
    }

    static <T> PropertyAccessor<T> createPropertyAccessor(ConfigMapperManager mapperManager,
                                                          Class<T> type,
                                                          String name,
                                                          Field field) {
        try {
            final Class<T> propertyType = (Class<T>) field.getType();
            Class<?> configAsType = propertyType;

            boolean list = configAsType.isAssignableFrom(List.class);
            if (list) {
                Type genType = field.getGenericType();
                if (genType instanceof ParameterizedType) {
                    configAsType = (Class<?>) ((ParameterizedType) genType).getActualTypeArguments()[0];
                } else {
                    throw new ConfigException("Unable to find generic type of List on field type: " + field);
                }
            }

            MethodHandle handle = MethodHandles.publicLookup()
                    .findSetter(type, field.getName(), field.getType());

            return new PropertyAccessor<>(mapperManager, name, propertyType, configAsType, list, handle,
                                          field.getAnnotation(Config.Value.class));
        } catch (NoSuchFieldException | IllegalAccessException | ClassCastException ex) {
            throw new ConfigException("Cannot access field: " + field, ex);
        }
    }

    static boolean isTransient(Method method) throws ConfigException {
        if (method.isAnnotationPresent(Config.Transient.class)) {
            if (method.isAnnotationPresent(Config.Value.class)) {
                throw new ConfigException("Illegal use of both @Config.Value and @Config.Transient annotations on single '"
                                                  + method.getName() + "' setter.");
            } else {
                return true;
            }
        }
        return false;
    }

    static boolean isTransient(Field field) throws ConfigException {
        if (field.isAnnotationPresent(Config.Transient.class)) {
            if (field.isAnnotationPresent(Config.Value.class)) {
                throw new ConfigException("Illegal use of both @Config.Value and @Config.Transient annotations on single field '"
                                                  + field.getName() + "'.");
            } else {
                return true;
            }
        }
        return false;
    }

    static boolean isAccessible(Field field) {
        if (Modifier.isFinal(field.getModifiers())) {
            return false;
        }
        return true;
    }

    static boolean isSetter(Method method) {
        if (method.getParameterCount() != 1) {
            return false;
        }
        if (!method.isAnnotationPresent(Config.Value.class)) {
            if (method.getName().length() <= 3) {
                return false;
            }
            if (!method.getName().startsWith("set")) {
                return false;
            }
            if (!method.getReturnType().equals(void.class)) {
                return false;
            }
        }
        return true;
    }

    static String propertyName(Method method) {
        Config.Value value = method.getAnnotation(Config.Value.class);
        String name = Optional.ofNullable(value)
                .map(Config.Value::key)
                .filter(((Predicate<String>) String::isEmpty).negate())
                .orElse(null);
        if (name == null) {
            name = method.getName();
            if (name.startsWith("set") && name.length() > 3) {
                name = decapitalize(name.substring("set".length()));
            }
        }
        return name;
    }

    static String propertyName(Parameter parameter) {
        Config.Value value = parameter.getAnnotation(Config.Value.class);
        String name = Optional.ofNullable(value)
                .map(Config.Value::key)
                .filter(((Predicate<String>) String::isEmpty).negate())
                .orElse(null);
        if (name == null) {
            name = parameter.getName();
        }
        return name;
    }

    static String propertyName(Field field) {
        Config.Value value = field.getAnnotation(Config.Value.class);
        String name = Optional.ofNullable(value)
                .map(Config.Value::key)
                .filter(((Predicate<String>) String::isEmpty).negate())
                .orElse(null);
        if (name == null) {
            name = field.getName();
        }
        return name;
    }

    static String decapitalize(String name) {
        if (Character.isLowerCase(name.charAt(0))) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    static <T> BiFunction<Class<T>, ConfigMapperManager, T> createDefaultSupplier(String name, Config.Value annotation) {
        if (annotation != null) {
            if (annotation.withDefaultSupplier() != Config.Value.None.class) {
                return (type, mapperManager) -> {
                    try {
                        return type.cast(annotation
                                                 .withDefaultSupplier()
                                                 .getDeclaredConstructor()
                                                 .newInstance()
                                                 .get());
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                            | NoSuchMethodException ex) {
                        throw new ConfigException("Error creating default value supplier.", ex);
                    }
                };
            } else if (!annotation.withDefault().equals(Config.Value.None.VALUE)) {
                return (type, mapperManager) ->
                        mapperManager.map(type, new SingleValueConfigImpl(mapperManager, name, annotation.withDefault()));
            }
        }
        return null;
    }

    /**
     * The class covers work with a single property.
     *
     * @param <T> property type
     */
    static class PropertyWrapper<T> {
        private final ConfigMapperManager mapperManager;
        private final String name;
        private final Class<T> propertyType;
        private final Class<?> configAsType;
        private final boolean list;
        private BiFunction<Class<T>, ConfigMapperManager, T> defaultSupplier;

        PropertyWrapper(ConfigMapperManager mapperManager,
                        String name,
                        Class<T> propertyType,
                        Class<?> configAsType,
                        boolean list,
                        BiFunction<Class<T>, ConfigMapperManager, T> defaultSupplier) {
            this.mapperManager = mapperManager;
            this.name = name;
            this.propertyType = ConfigMapperManager.supportedType(propertyType);
            this.configAsType = configAsType;
            this.list = list;
            this.defaultSupplier = defaultSupplier;
        }

        Optional<T> get(Config configNode) {
            try {
                if (configNode.exists()) {
                    if (list) {
                        return Optional.of(propertyType.cast(configNode.asList(configAsType)));
                    } else {
                        return Optional.of(propertyType.cast(configNode.as(configAsType)));
                    }
                } else {
                    if (defaultSupplier != null) {
                        return Optional.ofNullable(defaultSupplier.apply(propertyType, mapperManager));
                    } else {
                        return Optional.empty();
                    }
                }
            } catch (ConfigException ex) {
                throw ex;
            } catch (Throwable throwable) {
                throw new ConfigException("Unable to set '" + name + "' property.", throwable);
            }
        }

        void setDefaultSupplier(BiFunction<Class<T>, ConfigMapperManager, T> defaultSupplier) {
            this.defaultSupplier = defaultSupplier;
        }
    }

}
