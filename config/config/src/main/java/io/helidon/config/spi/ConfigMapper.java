/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.config.spi;

import java.lang.reflect.Type;

import io.helidon.common.GenericType;
import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;

/**
 * Config mapper is provided to {@link ConfigMapperProvider} to help transformation of
 * complex structures.
 */
public interface ConfigMapper {
    /**
     * Convert the specified {@code Config} node into the target type specified by {@link GenericType}.
     * You can use {@link GenericType#create(Type)} if needed to wrap a parametrized type.
     *
     * @param config config node to convert
     * @param type   type to extract generics types from (to prevent type erasure)
     * @param <T>    type of the result
     * @return converted value, never null
     * @throws MissingValueException  in case a value is expected and not found (either on this config node or on a subnode)
     * @throws ConfigMappingException in case the config node cannot be converted into the expected type (e.g. value is
     *                                {@code "hello"} and expected type is {@code List<Integer>})
     */
    <T> T map(Config config, GenericType<T> type) throws MissingValueException, ConfigMappingException;

    /**
     * Converts the specified {@code Config} node to the target type.
     * <p>
     * The method uses the mapper function instance associated with the
     * specified {@code type} to convert the {@code Config} subtree. If there is
     * none it tries to find one from {@link io.helidon.config.spi.ConfigMapperProvider#mapper(Class)} from configured
     * mapper providers.
     * If none is found, mapping will throw a {@link ConfigMappingException}.
     *
     * @param config config node to be transformed
     * @param type   type to which the config node is to be transformed
     * @param <T>    type to which the config node is to be transformed
     * @return transformed value of type {@code T}; never returns {@code null}
     * @throws MissingValueException  in case the configuration node does not represent an existing configuration node
     * @throws ConfigMappingException in case the mapper fails to map the existing configuration value
     *                                to an instance of a given Java type
     */
    <T> T map(Config config, Class<T> type) throws MissingValueException, ConfigMappingException;

    /**
     * Converts the value to the target type.
     *
     * @param value String value to convert
     * @param type  type to convert to
     * @param key   configuration key to be used to create appropriate exceptions
     * @param <T>   type of the converted value
     * @return value converted to the correct type
     * @throws MissingValueException  iin case a value is expected and not found (e.g. when trying to convert to a complex object)
     * @throws ConfigMappingException in case the String cannot be converted to the expected type
     */
    <T> T map(String value, Class<T> type, String key) throws MissingValueException, ConfigMappingException;

    /**
     * Converts the value to the target generic type.
     *
     * @param value String value to convert
     * @param type  generic type to convert to
     * @param key   configuration key to be used to create appropriate exceptions
     * @param <T>   type of the converted value
     * @return value converted to the correct type
     * @throws MissingValueException  iin case a value is expected and not found (e.g. when trying to convert to a complex object)
     * @throws ConfigMappingException in case the String cannot be converted to the expected type
     */
    <T> T map(String value, GenericType<T> type, String key) throws MissingValueException, ConfigMappingException;

}
