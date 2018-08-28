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
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import io.helidon.config.GenericConfigMapperUtils.PropertyWrapper;

/**
 * Generic {@link ConfigMapper} implementation to support factory method (including "factory" constructor).
 * <p>
 * Factory method pattern:
 * <pre>{@code
 * public class T {
 *     public static T from(prop1, prop2, prop3, ...) {
 *         return new T(prop1, prop2, prop3, ...);
 *     }
 * }
 * }</pre>
 * Class {@code T} contains public static method {@code from(...)} with list of config properties
 * that returns instance of the class {@code T}.
 * <p>
 * "Factory" constructor pattern:
 * <pre>{@code
 * public class T {
 *     public T(prop1, prop2, prop3, ...) {
 *         //set props
 *     }
 * }
 * }</pre>
 * Class {@code T} contains public constructor with list of config properties.
 * <p>
 * Method or constructor parameters can contain {@link Config.Value} annotations to customize property name (recommended)
 * and/or default value.
 *
 * @param <T> type of target java bean
 * @see ConfigMapperManager
 */
class FactoryMethodConfigMapper<T> implements ConfigMapper<T> {

    private final Class<T> type;
    private final FactoryAccessor<T> factoryAccessor;

    FactoryMethodConfigMapper(Class<T> type, FactoryAccessor<T> factoryAccessor) {
        this.type = type;
        this.factoryAccessor = factoryAccessor;
    }

    @Override
    public T apply(Config config) throws ConfigMappingException, MissingValueException {
        return factoryAccessor.create(config);
    }

    /**
     * The class covers work with factory method.
     *
     * @param <T> type of target java bean
     */
    static class FactoryAccessor<T> {
        private final Class<T> type;
        private final MethodHandle handle;
        private final LinkedHashMap<String, PropertyWrapper<?>> parameterValueProviders;

        FactoryAccessor(ConfigMapperManager mapperManager,
                        Class<T> type,
                        MethodHandle handle,
                        Parameter[] parameters) {
            this.type = type;
            this.handle = handle;

            this.parameterValueProviders = initParameterValueProviders(mapperManager, parameters);
        }

        public T create(Config configNode) {
            List<Object> args = createArguments(configNode);

            try {
                Object obj = handle.invokeWithArguments(args);
                return type.cast(obj);
            } catch (ConfigException ex) {
                throw ex;
            } catch (Throwable throwable) {
                throw new ConfigException("Unable to create '" + type.getName() + "' instance.", throwable);
            }
        }

        private List<Object> createArguments(Config configNode) {
            List<Object> arguments = new ArrayList<>(parameterValueProviders.size());

            parameterValueProviders.forEach((name, propertyWrapper) -> {
                Config subConfig = configNode.get(name);
                Object argument = propertyWrapper
                        .get(subConfig)
                        .orElseThrow(() -> new ConfigMappingException(configNode.key(),
                                                                      type,
                                                                      "Missing value for parameter '" + name + "'."));
                arguments.add(argument);
            });

            return arguments;
        }

        private static LinkedHashMap<String, PropertyWrapper<?>> initParameterValueProviders(
                ConfigMapperManager mapperManager, Parameter[] parameters) {
            LinkedHashMap<String, PropertyWrapper<?>> parameterValueProvider = new LinkedHashMap<>();

            for (Parameter parameter : parameters) {
                String name = GenericConfigMapperUtils.propertyName(parameter);
                parameterValueProvider.put(name, createPropertyWrapper(mapperManager, name, parameter));
            }

            return parameterValueProvider;
        }

        private static PropertyWrapper<?> createPropertyWrapper(ConfigMapperManager mapperManager,
                                                                String name,
                                                                Parameter parameter) {
            Config.Value value = parameter.getAnnotation(Config.Value.class);

            final Class<?> propertyType = parameter.getType();
            Class<?> configAsType = propertyType;
            boolean list = configAsType.isAssignableFrom(List.class);
            if (list) {
                Type genType = parameter.getParameterizedType();
                if (genType instanceof ParameterizedType) {
                    configAsType = (Class<?>) ((ParameterizedType) genType).getActualTypeArguments()[0];
                } else {
                    throw new ConfigException("Unable to find generic type of List on parameter type: " + parameter);
                }
            }

            return new PropertyWrapper<>(mapperManager,
                                         name,
                                         propertyType,
                                         configAsType,
                                         list,
                                         GenericConfigMapperUtils.createDefaultSupplier(name, value));
        }
    }

}
