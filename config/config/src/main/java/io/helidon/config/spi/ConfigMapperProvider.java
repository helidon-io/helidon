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

package io.helidon.config.spi;

import java.util.Map;

import io.helidon.config.ConfigMapper;

/**
 * Provides {@link ConfigMapper}s each of which converts a {@code Config}
 * subtree to a specific Java type.
 * <p>
 * The config system automatically loads {@code ConfigMapperProvider}s using the
 * Java {@link java.util.ServiceLoader} mechanism, and by default the config
 * system automatically registers all {@code ConfigMapper}s from all such
 * providers with every {@code Config.Builder}. The application can suppress
 * auto-registration of loaded mappers by invoking
 * {@link io.helidon.config.Config.Builder#disableMapperServices()}.
 * <p>
 * Each {@code ConfigMapperProvider} can specify a
 * {@link javax.annotation.Priority}. The default priority is {@value PRIORITY}.
 *
 * @see ConfigMapper
 * @see io.helidon.config.Config.Builder#addMapper(Class, ConfigMapper)
 * @see io.helidon.config.Config.Builder#disableMapperServices()
 */
@FunctionalInterface
public interface ConfigMapperProvider {

    /**
     * Default priority of the mapper provider if registered by {@link io.helidon.config.Config.Builder} automatically.
     */
    int PRIORITY = 100;

    /**
     * Returns a map of {@link ConfigMapper} instances associated with appropriate target type ({@code Class<?>}.
     * <p>
     * Mappers will by automatically registered by {@link io.helidon.config.Config.Builder} during {@link io.helidon.config.Config}
     * bootstrap
     * if not {@link io.helidon.config.Config.Builder#disableMapperServices() disabled}.
     *
     * @return a map of {@link ConfigMapper}s, never {@code null}
     */
    Map<Class<?>, ConfigMapper<?>> getMappers();

}
