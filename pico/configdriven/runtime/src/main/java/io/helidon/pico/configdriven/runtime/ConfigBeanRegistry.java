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

package io.helidon.pico.configdriven.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.builder.config.spi.ConfigBeanInfo;
import io.helidon.builder.config.spi.HelidonConfigBeanRegistry;

/**
 * The highest ranked/weighted implementor of this contract is responsible for managing the set of
 * {@link io.helidon.builder.config.ConfigBean}'s that are active, along with whether the application is configured to
 * support dynamic aspects (i.e., dynamic in content, dynamic in lifecycle, etc.).
 */
public interface ConfigBeanRegistry extends HelidonConfigBeanRegistry {

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
     * @return the map of configurable services to the meta config beans each expects
     */
    Map<ConfiguredServiceProvider<?, ?>, ConfigBeanInfo> configurableServiceProviders();

    /**
     * These are the managed/slave service providers that are associated with config bean instances.
     *
     * @return the list of configured services
     */
    List<ConfiguredServiceProvider<?, ?>> configuredServiceProviders();

    /**
     * These are the managed/slave service providers that are associated with config bean instances with the config {@code key}
     * provided.
     *
     * @param key the config options key - note that this is a partial key - and not relative to the parent - the same
     *            key used by {@link io.helidon.builder.config.ConfigBean#value()}.
     * @return the list of configured services
     */
    List<ConfiguredServiceProvider<?, ?>> configuredServiceProvidersConfiguredBy(String key);

    /**
     * Returns all the known config beans in order of rank given the {@code key}. Callers should understand
     * that this list might be incomplete until ready state is reached (see {@link #ready()}). Note also that callers should
     * attempt to use {@link #configBeansByConfigKey(String)} whenever possible since it will generate more precise matches.
     *
     * @param key           the config options key - note that this is a partial key - and not relative to the parent - the same
     *                      key used by {@link io.helidon.builder.config.ConfigBean#value()}.
     * @return the set of known config keys
     */
    Set<?> configBeansByConfigKey(String key);

    /**
     * Returns all the known config beans in order of rank matching the {@code key} and {@code fullConfigKey}. Callers should
     * understand that this list might be incomplete until ready state is reached (see {@link #ready()}).
     *
     * @param key           the config options key - note that this is a partial key - and not relative to the parent - the same
     *                      key used by {@link io.helidon.builder.config.ConfigBean#value()}.
     * @param fullConfigKey the full config key
     * @return the set of known config keys matching the provided criteria
     */
    Set<?> configBeansByConfigKey(String key,
                                  String fullConfigKey);

    /**
     * Similar to {@link #configBeansByConfigKey}, but instead returns all the known config beans in a
     * map where the key of the map is the config key.
     *
     * @param key           the config options key - note that this is a partial key - and not relative to the parent - the same
     *                      key used by {@link io.helidon.builder.config.ConfigBean#value()}.
     * @param fullConfigKey the full config key
     * @return the map of known config keys to config beans
     */
    Map<String, ?> configBeanMapByConfigKey(String key,
                                            String fullConfigKey);

}
