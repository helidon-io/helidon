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
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.Config;

/**
 * Provides mapping functions that convert a {@code Config}
 * subtree to specific Java types.
 * <p>
 * The config system automatically loads {@code ConfigMapperProvider}s using the
 * Java {@link java.util.ServiceLoader} mechanism, and by default the config
 * system automatically registers all {@code ConfigMapper}s from all such
 * providers with every {@code Config.Builder}. The application can suppress
 * auto-registration of loaded mappers by invoking
 * {@link Config.Builder#disableMapperServices()}.
 * <p>
 * Each {@code ConfigMapperProvider} can specify a
 * {@link javax.annotation.Priority}. The default priority is {@value PRIORITY}.
 *
 * @see Config.Builder#addStringMapper(Class, Function)
 * @see Config.Builder#addMapper(ConfigMapperProvider)
 * @see Config.Builder#disableMapperServices()
 */
@FunctionalInterface
public interface ConfigMapperProvider {
    /**
     * Default priority of the mapper provider if registered by {@link Config.Builder} automatically.
     */
    int PRIORITY = 100;

    /**
     * Returns a map of mapper functions associated with appropriate target type ({@code Class<?>}.
     * <p>
     * Mappers will by automatically registered by {@link Config.Builder} during
     * bootstrapping of {@link Config} unless
     * {@link Config.Builder#disableMapperServices() disableld}.
     *
     * @return a map of config mapper functions, never {@code null}, though this may return an empty map if
     * {@link #mapper(Class)} is used instead
     */
    Map<Class<?>, Function<Config, ?>> mappers();

    /**
     * Mapper for a specific type, if that type is supported by this provider.
     * This can be used for implementations that support types not known at the time this provider is created,
     * such as java beans, JsonObject etc.
     *
     * @param type class of the type we should map the config node to
     * @param <T>  type
     * @return mapping function or empty if the type is not supported by this mapper
     */
    default <T> Optional<Function<Config, T>> mapper(Class<T> type) {
        return Optional.empty();
    }

}
