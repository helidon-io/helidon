/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.config;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.service.registry.ServiceRegistry;

/**
 * Methods used from generated code in builders when
 * {@link io.helidon.builder.api.Prototype.Configured} is used.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class ConfigBuilderSupport {
    private ConfigBuilderSupport() {
    }

    /**
     * Used to discover services from {@link io.helidon.service.registry.ServiceRegistry} for builder options annotated
     * with {@link io.helidon.builder.api.Option.Provider}, if the blueprint is annotated with
     * {@link io.helidon.builder.api.Prototype.RegistrySupport}.
     *
     * @param config          configuration of the option
     * @param configKey       configuration key associated with this option
     * @param serviceRegistry service registry instance
     * @param providerType    type of the service provider (contract)
     * @param configType      type of the configuration
     * @param allFromRegistry whether to use all services from the registry
     * @param existingValues  existing values that was explicitly configured by the user
     * @param <T>             type of the service
     * @return instances from the user augmented with instances from the registry
     */
    public static <T extends NamedService> List<T> discoverServices(Config config,
                                                                    String configKey,
                                                                    Optional<ServiceRegistry> serviceRegistry,
                                                                    Class<? extends ConfiguredProvider<T>> providerType,
                                                                    Class<T> configType,
                                                                    boolean allFromRegistry,
                                                                    List<T> existingValues) {

        return ProvidedUtil.discoverServices(config,
                                             configKey,
                                             serviceRegistry,
                                             providerType,
                                             configType,
                                             allFromRegistry,
                                             existingValues);
    }

    /**
     * Used to discover service from {@link io.helidon.service.registry.ServiceRegistry} for builder options annotated
     * with {@link io.helidon.builder.api.Option.Provider}, if the blueprint is annotated with
     * {@link io.helidon.builder.api.Prototype.RegistrySupport}.
     *
     * @param config           configuration of the option
     * @param configKey        configuration key associated with this option
     * @param serviceRegistry  service registry instance
     * @param providerType     type of the service provider (contract)
     * @param configType       type of the configuration
     * @param discoverServices whether to discover services from registry
     * @param existingValue    existing value that was explicitly configured by the user
     * @param <T>              type of the service
     * @return an instance, if available in the registry, or if provided by the user (user's value wins)
     */
    public static <T extends NamedService> Optional<T>
    discoverService(Config config,
                    String configKey,
                    Optional<ServiceRegistry> serviceRegistry,
                    Class<? extends ConfiguredProvider<T>> providerType,
                    Class<T> configType,
                    boolean discoverServices,
                    Optional<T> existingValue) {

        return ProvidedUtil.discoverService(config,
                                            configKey,
                                            serviceRegistry,
                                            providerType,
                                            configType,
                                            discoverServices,
                                            existingValue);
    }


    /**
     * Discover services from configuration.
     * If already configured instances already contain a service of the same type and name that would be added from
     * configuration, the configuration would be ignored (e.g. the user must make a choice whether to configure, or
     * set using an API).
     *
     * @param config               configuration located at the parent node of the service providers
     * @param configKey            configuration key of the provider list
     *                             (either a list node, or object, where each child is one service)
     * @param serviceLoader        helidon service loader for the expected type
     * @param providerType         type of the service provider interface
     * @param configType           type of the configured service
     * @param allFromServiceLoader whether all services from service loader should be used, or only the ones with configured
     *                             node
     * @param existingInstances    already configured instances
     * @param <S>                  type of the expected service
     * @param <T>                  type of the configured service provider that creates instances of S
     * @return list of discovered services, ordered by {@link io.helidon.common.Weight} (highest weight is first in the list)
     * @deprecated use {@link #discoverServices(Config, String, Class, Class, boolean, java.util.List)} instead
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    public static <S extends NamedService, T extends ConfiguredProvider<S>> List<S>
    discoverServices(Config config,
                     String configKey,
                     HelidonServiceLoader<T> serviceLoader,
                     Class<T> providerType,
                     Class<S> configType,
                     boolean allFromServiceLoader,
                     List<S> existingInstances) {
        return ProvidedUtil.discoverServices(config,
                                             configKey,
                                             serviceLoader,
                                             providerType,
                                             configType,
                                             allFromServiceLoader,
                                             existingInstances);
    }

    /**
     * Discover services from configuration.
     * If already configured instances contain a service of the same type and name that would be added from
     * configuration, the configuration would be ignored (e.g. the user must make a choice whether to configure, or
     * set using an API).
     *
     * @param config               configuration located at the parent node of the service providers
     * @param configKey            configuration key of the provider list
     *                             (either a list node, or object, where each child is one service)
     * @param providerType         type of the service provider interface, used to lookup from {@link java.util.ServiceLoader}
     * @param configType           type of the configured service
     * @param allFromServiceLoader whether all services from service loader should be used, or only the ones with configured
     *                             node
     * @param existingInstances    already configured instances
     * @param <S>                  type of the expected service
     * @param <T>                  type of the configured service provider that creates instances of S
     * @return list of discovered services, ordered by {@link io.helidon.common.Weight} (highest weight is first in the list)
     */
    public static <S extends NamedService, T extends ConfiguredProvider<S>> List<S>
    discoverServices(Config config,
                     String configKey,
                     Class<T> providerType,
                     Class<S> configType,
                     boolean allFromServiceLoader,
                     List<S> existingInstances) {
        return ProvidedUtil.discoverServices(config,
                                             configKey,
                                             HelidonServiceLoader.create(providerType),
                                             providerType,
                                             configType,
                                             allFromServiceLoader,
                                             existingInstances);
    }


    /**
     * Discover service from configuration. If an instance is already configured using a builder, it will not be
     * discovered from configuration (e.g. the user must make a choice whether to configure, or set using API).
     *
     * @param config               configuration located at the parent node of the service providers
     * @param configKey            configuration key of the provider list
     *                             (either a list node, or object, where each child is one service - this method requires
     *                             *                             zero to one configured services)
     * @param serviceLoader        helidon service loader for the expected type
     * @param providerType         type of the service provider interface
     * @param configType           type of the configured service
     * @param allFromServiceLoader whether all services from service loader should be used, or only the ones with configured
     *                             node
     * @param existingValue        value already configured, if the name is same as discovered from configuration
     * @param <S>                  type of the expected service
     * @param <T>                  type of the configured service provider that creates instances of S
     * @return the first service (ordered by {@link io.helidon.common.Weight} that is discovered, or empty optional if none
     *         is found
     * @deprecated use {@link #discoverService(Config, String, Class, Class, boolean, java.util.Optional)}
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    public static <S extends NamedService, T extends ConfiguredProvider<S>> Optional<S>
    discoverService(Config config,
                    String configKey,
                    HelidonServiceLoader<T> serviceLoader,
                    Class<T> providerType,
                    Class<S> configType,
                    boolean allFromServiceLoader,
                    Optional<S> existingValue) {
        return ProvidedUtil.discoverService(config,
                                            configKey,
                                            serviceLoader,
                                            providerType,
                                            configType,
                                            allFromServiceLoader,
                                            existingValue);
    }

    /**
     * Discover service from configuration. If an instance is already configured using a builder, it will not be
     * discovered from configuration (e.g. the user must make a choice whether to configure, or set using API).
     *
     * @param config               configuration located at the parent node of the service providers
     * @param configKey            configuration key of the provider list
     *                             (either a list node, or object, where each child is one service - this method requires
     *                             *                             zero to one configured services)
     * @param providerType         type of the service provider interface, used to lookup from {@link java.util.ServiceLoader}
     * @param configType           type of the configured service
     * @param allFromServiceLoader whether all services from service loader should be used, or only the ones with configured
     *                             node
     * @param existingValue        value already configured, if the name is same as discovered from configuration
     * @param <S>                  type of the expected service
     * @param <T>                  type of the configured service provider that creates instances of S
     * @return the first service (ordered by {@link io.helidon.common.Weight} that is discovered, or empty optional if none
     *         is found
     */
    public static <S extends NamedService, T extends ConfiguredProvider<S>> Optional<S>
    discoverService(Config config,
                    String configKey,
                    Class<T> providerType,
                    Class<S> configType,
                    boolean allFromServiceLoader,
                    Optional<S> existingValue) {
        return ProvidedUtil.discoverService(config,
                                            configKey,
                                            HelidonServiceLoader.create(providerType),
                                            providerType,
                                            configType,
                                            allFromServiceLoader,
                                            existingValue);
    }

    /**
     * Extension of {@link io.helidon.builder.api.Prototype.Builder} that supports configuration.
     * If a blueprint is marked as {@code @Configured}, build will accept configuration.
     *
     * @param <BUILDER>   type of the builder
     * @param <PROTOTYPE> type of the prototype to be built
     */
    public interface ConfiguredBuilder<BUILDER, PROTOTYPE> extends Prototype.Builder<BUILDER, PROTOTYPE> {
        /**
         * Update builder from configuration.
         * Any configured option that is defined on this prototype will be checked in configuration, and if it exists,
         * it will override current value for that option on this builder.
         * Options that do not exist in the provided config will not impact current values.
         * The config instance is kept and may be used in builder decorator, it is not available in prototype implementation.
         *
         * @param config configuration to use
         * @return updated builder instance
         */
        BUILDER config(Config config);
    }
}
