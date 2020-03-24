/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
package io.helidon.config.objectmapping;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;
import io.helidon.config.objectmapping.ReflectionUtil.BuilderAccessor;
import io.helidon.config.objectmapping.ReflectionUtil.PropertyAccessor;

/**
 * Various mappers used in {@link ObjectConfigMapperProvider}.
 */
class ObjectConfigMappers {

    abstract static class MethodHandleConfigMapper<T, P> implements Function<Config, T> {
        private final Class<T> type;
        private final String methodName;
        private final HelidonMethodHandle methodHandle;

        MethodHandleConfigMapper(Class<T> type, String methodName, HelidonMethodHandle methodHandle) {
            this.type = type;
            this.methodName = methodName;
            this.methodHandle = methodHandle;
        }

        protected abstract P invokeParameter(Config config);

        @Override
        public T apply(Config config) throws ConfigMappingException, MissingValueException {
            try {
                return type.cast(methodHandle.invoke(List.of(invokeParameter(config))));
            } catch (ConfigMappingException ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new ConfigMappingException(config.key(), type,
                                                 "Invocation of " + methodName + " has failed with an exception.", ex);
            }
        }
    }

    static class ConfigMethodHandleConfigMapper<T> extends MethodHandleConfigMapper<T, Config> {
        ConfigMethodHandleConfigMapper(Class<T> type, String methodName, HelidonMethodHandle methodHandle) {
            super(type, methodName, methodHandle);
        }

        @Override
        protected Config invokeParameter(Config config) {
            return config;
        }
    }

    static class StringMethodHandleConfigMapper<T> extends MethodHandleConfigMapper<T, String> {
        StringMethodHandleConfigMapper(Class<T> type, String methodName, HelidonMethodHandle methodHandle) {
            super(type, methodName, methodHandle);
        }

        @Override
        protected String invokeParameter(Config config) {
            return config.asString().get();
        }
    }

    /**
     * Generic config mapper implementation supporting the builder pattern.
     * <p>
     * Implementations must provide both the bean class {@code T} and the builder
     * class {@code T_Builder}, as outlined below:
     * <pre>{@code
     * public class T {
     *     //getters
     *
     *     public static T_Builder builder() {
     *         return new T_Builder();
     *     }
     * }
     * public class T_Builder {
     *     //setters
     *
     *     public T build() {
     *         new T(...);
     *     }
     * }
     * }</pre>
     * Class {@code T} must contain the public static method {@code builder()} that returns an instance of the corresponding
     * Builder
     * class. The Config system deserializes properties into the Builder instance
     * (calling setters or setting fields; see {@link GenericConfigMapper}). The
     * Builder class must expose the public method {@code build()} that returns an
     * initialize instance of {@code T}.
     *
     * @param <T> type of target Java bean
     */
    static class BuilderConfigMapper<T> implements Function<Config, T> {

        private final BuilderAccessor<T> builderAccessor;

        BuilderConfigMapper(BuilderAccessor<T> builderAccessor) {
            this.builderAccessor = builderAccessor;
        }

        @Override
        public T apply(Config config) throws ConfigMappingException, MissingValueException {
            return builderAccessor.create(config);
        }
    }

    /**
     * Generic config mapper implementation to support factory method (including "factory" constructor).
     * <p>
     * Factory method pattern:
     * <pre>{@code
     * public class T {
     *     public static T create(prop1, prop2, prop3, ...) {
     *         return new T(prop1, prop2, prop3, ...);
     *     }
     * }
     * }</pre>
     * Class {@code T} contains public static method {@code create(...)} with list of config properties
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
     * Method or constructor parameters can contain {@link Value} annotations to customize property name (recommended)
     * and/or default value.
     *
     * @param <T> type of target java bean
     */
    static class FactoryMethodConfigMapper<T> implements Function<Config, T> {

        private final ReflectionUtil.FactoryAccessor<T> factoryAccessor;

        FactoryMethodConfigMapper(ReflectionUtil.FactoryAccessor<T> factoryAccessor) {
            this.factoryAccessor = factoryAccessor;
        }

        @Override
        public T apply(Config config) throws ConfigMappingException, MissingValueException {
            return factoryAccessor.create(config);
        }
    }

    /**
     * Implements generic mapping support based on public no-parameter constructor and public setters or public fields.
     */
    static class GenericConfigMapper<T> implements Function<Config, T> {

        private final Class<T> type;
        private final HelidonMethodHandle constructorHandle;
        private final Collection<PropertyAccessor<?>> propertyAccessors;

        GenericConfigMapper(Class<T> type, HelidonMethodHandle constructorHandle) {
            this.type = type;
            this.constructorHandle = constructorHandle;

            propertyAccessors = ReflectionUtil.getBeanProperties(type);
            if (propertyAccessors.isEmpty()) {
                throw new IllegalArgumentException(type + " has no bean properties");
            }
        }

        @Override
        public T apply(Config config) throws ConfigMappingException, MissingValueException {
            try {
                T instance = type.cast(constructorHandle.invoke(List.of()));

                for (PropertyAccessor<?> propertyAccessor : propertyAccessors) {
                    propertyAccessor.set(instance, config.get(propertyAccessor.name()));
                }
                return instance;
            } catch (ConfigMappingException ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new ConfigMappingException(
                        config.key(),
                        type,
                        "Generic java bean initialization has failed with an exception.",
                        ex);
            }
        }
    }
}
