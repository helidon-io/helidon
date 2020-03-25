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

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Priority;

import io.helidon.common.HelidonFeatures;
import io.helidon.config.Config;
import io.helidon.config.objectmapping.ObjectConfigMappers.BuilderConfigMapper;
import io.helidon.config.objectmapping.ObjectConfigMappers.ConfigMethodHandleConfigMapper;
import io.helidon.config.objectmapping.ObjectConfigMappers.FactoryMethodConfigMapper;
import io.helidon.config.objectmapping.ObjectConfigMappers.GenericConfigMapper;
import io.helidon.config.objectmapping.ObjectConfigMappers.StringMethodHandleConfigMapper;
import io.helidon.config.spi.ConfigMapperProvider;

import static io.helidon.config.objectmapping.ReflectionUtil.findBuilderConstructor;
import static io.helidon.config.objectmapping.ReflectionUtil.findBuilderMethod;
import static io.helidon.config.objectmapping.ReflectionUtil.findConstructor;
import static io.helidon.config.objectmapping.ReflectionUtil.findConstructorWithParameters;
import static io.helidon.config.objectmapping.ReflectionUtil.findStaticMethod;
import static io.helidon.config.objectmapping.ReflectionUtil.findStaticMethodWithParameters;

/**
 * Java beans support for configuration.
 */
@Priority(1000) // priority should be low to be one of the last ones used
public class ObjectConfigMapperProvider implements ConfigMapperProvider {
    private static final String METHOD_FROM = "from";
    private static final String METHOD_OF = "of";
    private static final String METHOD_VALUE_OF = "valueOf";
    private static final String METHOD_FROM_CONFIG = "fromConfig";
    private static final String METHOD_FROM_STRING = "fromString";
    private static final String METHOD_PARSE = "parse";
    private static final String METHOD_CREATE = "create";

    static {
        HelidonFeatures.register("Config", "Object Mapping");
    }

    @Override
    public Map<Class<?>, Function<Config, ?>> mappers() {
        return Map.of();
    }

    @Override
    public <T> Optional<Function<Config, T>> mapper(Class<T> type) {
        return  // T create(Config)
                findStaticConfigMethodMapper(type, METHOD_CREATE)
                // T from(Config)
                .or(() -> findStaticConfigMethodMapper(type, METHOD_FROM))
                // Config constructor
                .or(() -> findConfigConstructorMapper(type))
                // T of(Config)
                .or(() -> findStaticConfigMethodMapper(type, METHOD_OF))
                // T valueOf(Config)
                .or(() -> findStaticConfigMethodMapper(type, METHOD_VALUE_OF))
                // T fromConfig(Config)
                .or(() -> findStaticConfigMethodMapper(type, METHOD_FROM_CONFIG))
                // T from(String)
                .or(() -> findStaticStringMethodMapper(type, METHOD_FROM))
                // T parse(String)
                .or(() -> findStaticStringMethodMapper(type, METHOD_PARSE))
                // T parse(CharSequence)
                .or(() -> findParseCharSequenceMethodMapper(type))
                // String constructor
                .or(() -> findStringConstructorMapper(type))
                // T of(String)
                .or(() -> findStaticStringMethodMapper(type, METHOD_OF))
                // T valueOf(String)
                .or(() -> findStaticStringMethodMapper(type, METHOD_VALUE_OF))
                // T fromString(String)
                .or(() -> findStaticStringMethodMapper(type, METHOD_FROM_STRING))
                // static Builder builder()
                .or(() -> findBuilderMethodMapper(type))
                // new T.Builder()
                .or(() -> findBuilderClassMapper(type))
                // static T from(param, params...)
                .or(() -> findStaticMethodWithParamsMapper(type, METHOD_FROM))
                // static T create(param, params...)
                .or(() -> findStaticMethodWithParamsMapper(type, METHOD_CREATE))
                // constructor(param, params...)
                .or(() -> findConstructorWithParamsMapper(type))
                // generic mapping support
                .or(() -> findGenericMapper(type));
                // we could not find anything, let config decide what to do
    }

    private static <T> Optional<Function<Config, T>> findStaticConfigMethodMapper(Class<T> type,
                                                                                  String methodName) {
        return findStaticMethod(type, methodName, Config.class)
                .map(handle -> new ConfigMethodHandleConfigMapper<>(
                        type,
                        methodName + "(Config) method",
                        handle));
    }

    private static <T> Optional<Function<Config, T>> findStaticStringMethodMapper(Class<T> type,
                                                                                  String methodName) {

        Optional<HelidonMethodHandle> method = findStaticMethod(type,
                                                         methodName,
                                                         String.class);

        if (method.isEmpty()) {
            method = findStaticMethod(type,
                                      methodName,
                                      CharSequence.class);
        }

        return method.map(handle -> new StringMethodHandleConfigMapper<>(
                type,
                methodName + "(String) method",
                handle));
    }

    private static <T> Optional<Function<Config, T>> findParseCharSequenceMethodMapper(Class<T> type) {
        return findStaticMethod(type, METHOD_PARSE, CharSequence.class)
                .map(handle -> new StringMethodHandleConfigMapper<>(
                        type,
                        "parse(CharSequence) method",
                        handle));
    }

    private static <T> Optional<Function<Config, T>> findConfigConstructorMapper(Class<T> type) {
        return findConstructor(type, Config.class)
                .map(handle -> new ConfigMethodHandleConfigMapper<>(type, "Config constructor", handle));
    }

    private static <T> Optional<Function<Config, T>> findStringConstructorMapper(Class<T> type) {
        return findConstructor(type, String.class)
                .map(handle -> new StringMethodHandleConfigMapper<>(type, "String constructor", handle));
    }

    private static <T> Optional<Function<Config, T>> findBuilderMethodMapper(Class<T> type) {
        return findBuilderMethod(type)
                .map(BuilderConfigMapper::new);
    }

    private static <T> Optional<Function<Config, T>> findBuilderClassMapper(Class<T> type) {
        return findBuilderConstructor(type)
                .map(BuilderConfigMapper::new);
    }

    private static <T> Optional<Function<Config, T>> findStaticMethodWithParamsMapper(Class<T> type, String methodName) {
        return findStaticMethodWithParameters(type, methodName)
                .map(FactoryMethodConfigMapper::new);
    }

    private static <T> Optional<Function<Config, T>> findConstructorWithParamsMapper(Class<T> type) {
        return findConstructorWithParameters(type)
                .map(FactoryMethodConfigMapper::new);
    }

    private static <T> Optional<Function<Config, T>> findGenericMapper(Class<T> type) {
        try {
            return findConstructor(type)
                    .map(methodHandle -> new GenericConfigMapper<>(type, methodHandle));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
