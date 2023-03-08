/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.configdriven.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.config.spi.BasicConfigBeanRegistry;
import io.helidon.builder.config.spi.ConfigBeanInfo;

/**
 * The highest ranked/weighted implementor of this contract is responsible for managing the set of
 * {@link io.helidon.builder.config.ConfigBean}'s that are active, along with whether the application is configured to
 * support dynamic aspects (i.e., dynamic in content, dynamic in lifecycle, etc.).
 */
public interface ConfigBeanRegistry extends BasicConfigBeanRegistry {

    /**
     * The config bean registry is initialized as part of Pico's initialization, which happens when the service registry
     * is initialized and bound.
     *
     * @return true if the config bean registry has been initialized
     */
    boolean ready();

    /**
     * These are the services that are configurable, mapping to the configuration beans each expects.
     * Each entry in the returned map is the master/root for the config beans it manages. The result, therefore, is
     * not associated with config beans. Use {@link #configuredServiceProviders()} for configured service instances.
     *
     * @param <T>  the service type
     * @param <CB> the config bean type
     * @return the map of configurable services to the meta config beans each expects
     */
    <T, CB> Map<ConfiguredServiceProvider<T, CB>, ConfigBeanInfo> configurableServiceProviders();

    /**
     * These are the managed/slave service providers that are associated with config bean instances.
     *
     * @return the list of configured services
     */
    List<ConfiguredServiceProvider<?, ?>> configuredServiceProviders();

    /**
     * These are the managed/slave service providers that are associated with config bean instances with the config key provided.
     *
     * @param key           the config options key - note that this is a partial key - and not relative to the parent - the same
     *                      key used by {@link io.helidon.builder.config.ConfigBean#value()}.
     * @return the list of configured services
     */
    List<ConfiguredServiceProvider<?, ?>> configuredServiceProvidersConfiguredBy(String key);

    /**
     * Returns all the known config beans in order of rank given the short config key / alias. Callers should understand
     * that this list might be incomplete until ready state is reached (see {@link #ready()}).
     *
     * @param key           the config options key - note that this is a partial key - and not relative to the parent - the same
     *                      key used by {@link io.helidon.builder.config.ConfigBean#value()}.
     * @param fullConfigKey optionally, the full config key - if not passed will return the list of all matches
     *                      using just the key
     * @param <CB>          the config bean type
     * @return the list of known config keys
     */
    <CB> Set<CB> configBeansByConfigKey(String key,
                                        Optional<String> fullConfigKey);

    /**
     * Similar to {@link #configBeansByConfigKey(String, Optional<String>)}, but instead returns all the known config beans in a
     * map where the key of the map is the config key.
     *
     * @param key           the config options key - note that this is a partial key - and not relative to the parent - the same
     *                      key used by {@link io.helidon.builder.config.ConfigBean#value()}.
     * @param fullConfigKey optionally, the full config key - if not passed will return the list of all matches
     *                      using just the key
     * @param <CB>          the config bean type
     * @return the list of known config keys
     */
    <CB> Map<String, CB> configBeanMapByConfigKey(String key,
                                                  Optional<String> fullConfigKey);

}
