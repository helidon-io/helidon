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

/**
 * Contextual mapper of a configuration hierarchy to a Java type.
 * <p>
 * The application can register mappers on a {@code Builder} using the
 * {@link Config.Builder#addMapper(Class, ConfigMapper)} method. The config
 * system also locates mappers using the
 * {@link io.helidon.config.spi.ConfigMapperProvider} SPI that it discovers using
 * the Java {@link java.util.ServiceLoader} mechanism and automatically adds
 * them to every {@code Builder} unless the application disables this feature
 * for a given {@code Builder} by invoking
 * {@link Config.Builder#disableMapperServices()}.
 *
 * @param <T> Java type to which the {@code ConfigMapper} converts
 * {@code Config} subtree.
 * @see Config.Builder#addMapper(Class, ConfigMapper)
 * @see Config.Builder#addMapper(Class, java.util.function.Function)
 * @see Config.Builder#addMapper(io.helidon.config.spi.ConfigMapperProvider)
 * @see Config#as(Class)
 * @see Config#as(Class, Object)
 * @see Config#asOptional(Class)
 * @see Config#asList(Class)
 * @see Config#asList(Class, java.util.List)
 * @see Config#asOptionalList(Class)
 * @see Config#map(ConfigMapper)
 * @see Config#map(ConfigMapper, Object)
 * @see Config#mapOptional(ConfigMapper)
 * @see Config#mapList(ConfigMapper)
 * @see Config#mapList(ConfigMapper, java.util.List)
 * @see Config#mapOptionalList(ConfigMapper)
 * @see ConfigMappers
 * @see io.helidon.config.spi.ConfigMapperProvider
 * @see Config.Value
 */
@FunctionalInterface
public interface ConfigMapper<T> {

    /**
     * Maps a configuration hierarchy to a Java representation.
     *
     * @param config configuration node representing a configuration tree to be mapped to an instance of a Java type.
     * @return node mapped to a given java type.
     * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
     *                                required by the mapper implementation to provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
     *                                supplied configuration node to an instance of a given Java type.
     */
    T apply(Config config) throws ConfigMappingException, MissingValueException;

}
