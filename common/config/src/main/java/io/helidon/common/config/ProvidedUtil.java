/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.Services;

final class ProvidedUtil {
    private static final System.Logger PROVIDER_LOGGER = System.getLogger(Prototype.class.getName() + ".provider");
    /**
     * Special configuration key that can be defined on provided options (loaded through ServiceLoader) that defines
     * the mapping to a provider.
     * Type of service, used to map to {@link io.helidon.common.config.ConfiguredProvider#configKey()}, which is the
     * default "type" of a configured provider. It is then used in {@link io.helidon.common.config.NamedService#type()},
     * to allow multiple instances of the same "type" with different "name".
     */
    private static final String KEY_SERVICE_TYPE = "type";
    /**
     * Special configuration key that can be defined on provided options (loaded through ServiceLoader) that defines
     * the name of an instance.
     * It is then used in {@link io.helidon.common.config.NamedService#name()}
     * to allow multiple instances of the same "type" with different "name".
     * In case of object type configurations, name is taken from the configuration node name.
     */
    private static final String KEY_SERVICE_NAME = "name";
    /**
     * Special configuration key that can be defined on provided options (loaded through ServiceLoader) that defines
     * whether a service provider of the type is enabled.
     * Each service from a {@link io.helidon.common.config.ConfiguredProvider} can be enabled/disabled through
     * configuration. If marked as {@code enabled = false}, the service will be ignored and not added.
     */
    private static final String KEY_SERVICE_ENABLED = "enabled";

    private ProvidedUtil() {
    }

    /**
     * Discover service from configuration.
     * This method looks for a single provider only.
     *
     * @param config           configuration located at the parent node of the service providers
     * @param configKey        configuration key of the provider list
     *                         (either a list node, or object, where each child is one service)
     * @param serviceLoader    helidon service loader for the expected type
     * @param providerType     service provider interface type
     * @param configType       configured service type
     * @param discoverServices whether all services from service loader should be used, or only the ones with configured
     *                         node
     * @param existingValue    value already configured, if the name is same as discovered from configuration
     * @param <T>              type of the expected service
     * @return first service by {@link io.helidon.common.Weight}, or empty optional
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static <T extends NamedService> Optional<T>
    discoverService(Config config,
                    String configKey,
                    HelidonServiceLoader<? extends ConfiguredProvider<T>> serviceLoader,
                    Class<? extends ConfiguredProvider<T>> providerType,
                    Class<T> configType,
                    boolean discoverServices,
                    Optional<T> existingValue) {

        // there is an explicit configuration for this service, ignore configuration
        if (existingValue.isPresent()) {
            return Optional.empty();
        }

        // all child nodes of the current node
        List<Config> serviceConfigList = config.get(configKey).asNodeList()
                .orElseGet(List::of);

        // if more than one is configured in config, fail
        // if more than one exists in service loader, use the first one
        if (serviceConfigList.size() > 1) {
            throw new ConfigException("There can only be one provider configured for " + config.key());
        }

        List<T> services = discoverServices(config,
                                            configKey,
                                            serviceLoader,
                                            providerType,
                                            configType,
                                            discoverServices,
                                            List.of());

        return services.isEmpty() ? Optional.empty() : Optional.of(services.getFirst());
    }

    /**
     * Discover services from configuration.
     *
     * @param config               configuration located at the parent node of the service providers
     * @param configKey            configuration key of the provider list
     *                             (either a list node, or object, where each child is one service)
     * @param serviceLoader        helidon service loader for the expected type
     * @param providerType         service provider interface type
     * @param configType           configured service type
     * @param allFromServiceLoader whether all services from service loader should be used, or only the ones with configured
     *                             node
     * @param existingInstances    already configured instances
     * @param <T>                  type of the expected service
     * @return list of discovered services, ordered by {@link io.helidon.common.Weight} (highest weight first)
     */
    static <T extends NamedService> List<T> discoverServices(Config config,
                                                             String configKey,
                                                             HelidonServiceLoader<? extends ConfiguredProvider<T>> serviceLoader,
                                                             Class<? extends ConfiguredProvider<T>> providerType,
                                                             Class<T> configType,
                                                             boolean allFromServiceLoader,
                                                             List<T> existingInstances) {

        // type and name is a unique identification of a service - for services already defined on the builder
        // do not add them from configuration (as this would duplicate service instances)
        Set<TypeAndName> ignoredServices = new HashSet<>();
        existingInstances.forEach(it -> ignoredServices.add(new TypeAndName(it.type(), it.name())));

        boolean discoverServices = config.get(configKey + "-discover-services").asBoolean().orElse(allFromServiceLoader);
        Config providersConfig = config.get(configKey);

        List<ConfiguredService> configuredServices = new ArrayList<>();

        // all child nodes of the current node
        List<Config> serviceConfigList = providersConfig.asNodeList()
                .orElseGet(List::of);
        boolean isList = providersConfig.isList();

        for (Config serviceConfig : serviceConfigList) {
            configuredServices.add(configuredService(serviceConfig, isList));
        }

        // now we have all service configurations, we can start building up instances
        if (providersConfig.isList()) {
            // driven by order of declaration in config
            return servicesFromList(serviceLoader,
                                    providerType,
                                    configType,
                                    configuredServices,
                                    discoverServices,
                                    ignoredServices);
        } else {
            // driven by service loader order
            return servicesFromObject(providersConfig,
                                      serviceLoader,
                                      providerType,
                                      configType,
                                      configuredServices,
                                      discoverServices,
                                      ignoredServices);
        }
    }

    static <T extends NamedService> List<T> discoverServices(Config config,
                                                             String configKey,
                                                             Optional<ServiceRegistry> serviceRegistry,
                                                             Class<? extends ConfiguredProvider<T>> providerType,
                                                             Class<T> configType,
                                                             boolean allFromRegistry,
                                                             List<T> existingValues) {

        // type and name is a unique identification of a service - for services already defined on the builder
        // do not add them from configuration (as this would duplicate service instances)
        Set<TypeAndName> ignoredServices = new HashSet<>();
        existingValues.forEach(it -> ignoredServices.add(new TypeAndName(it.type(), it.name())));

        boolean discoverServices = config.get(configKey + "-discover-services").asBoolean().orElse(allFromRegistry);
        Config providersConfig = config.get(configKey);

        List<ConfiguredService> configuredServices = new ArrayList<>();

        // all child nodes of the current node
        List<Config> serviceConfigList = providersConfig.asNodeList()
                .orElseGet(List::of);
        boolean isList = providersConfig.isList();

        for (Config serviceConfig : serviceConfigList) {
            configuredServices.add(configuredService(serviceConfig, isList));
        }
        RegistryWrap wrap = serviceRegistry.isPresent()
                ? new RealRegistry(serviceRegistry.get())
                : StaticAccessRegistry.INSTANCE;

        // now we have all service configurations, we can start building up instances
        if (providersConfig.isList()) {
            // driven by order of declaration in config
            return servicesFromList(wrap,
                                    providerType,
                                    configType,
                                    configuredServices,
                                    discoverServices,
                                    ignoredServices);
        } else {
            // driven by service loader order
            return servicesFromObject(providersConfig,
                                      wrap,
                                      providerType,
                                      configType,
                                      configuredServices,
                                      discoverServices,
                                      ignoredServices);
        }
    }

    static <T extends NamedService> Optional<T>
    discoverService(Config config,
                    String configKey,
                    Optional<ServiceRegistry> serviceRegistry,
                    Class<? extends ConfiguredProvider<T>> providerType,
                    Class<T> configType,
                    boolean discoverServices,
                    Optional<T> existingValue) {

        // there is an explicit configuration for this service, ignore configuration
        if (existingValue.isPresent()) {
            return Optional.empty();
        }

        // all child nodes of the current node
        List<Config> serviceConfigList = config.get(configKey).asNodeList()
                .orElseGet(List::of);

        // if more than one is configured in config, fail
        // if more than one exists in service loader, use the first one
        if (serviceConfigList.size() > 1) {
            throw new ConfigException("There can only be one provider configured for " + config.key());
        }

        List<T> services = discoverServices(config,
                                            configKey,
                                            serviceRegistry,
                                            providerType,
                                            configType,
                                            discoverServices,
                                            List.of());

        return services.isEmpty() ? Optional.empty() : Optional.of(services.getFirst());
    }

    private static <T extends NamedService> List<T>
    servicesFromObject(Config providersConfig,
                       HelidonServiceLoader<? extends ConfiguredProvider<T>> serviceLoader,
                       Class<? extends ConfiguredProvider<T>> providerType,
                       Class<T> configType,
                       List<ConfiguredService> configuredServices,
                       boolean allFromServiceLoader,
                       Set<TypeAndName> ignoredServices) {
        // order is determined by service loader
        Set<String> availableProviders = new HashSet<>();
        Map<String, ConfiguredService> allConfigs = new HashMap<>();
        configuredServices.forEach(it -> allConfigs.put(it.typeAndName().type, it));
        Set<String> unusedConfigs = new HashSet<>(allConfigs.keySet());

        List<T> result = new ArrayList<>();

        serviceLoader.forEach(provider -> {
            ConfiguredService configuredService = allConfigs.get(provider.configKey());
            availableProviders.add(provider.configKey());
            unusedConfigs.remove(provider.configKey());
            if (configuredService == null) {
                if (allFromServiceLoader) {
                    // even though the specific key does not exist, we want to have the real config tree, so we can get to the
                    // root of it
                    // when there is no configuration, the name defaults to the type
                    String type = provider.configKey();
                    if (ignoredServices.add(new TypeAndName(type, type))) {
                        result.add(provider.create(providersConfig.get(type), type));
                    } else {
                        if (PROVIDER_LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            PROVIDER_LOGGER.log(System.Logger.Level.DEBUG, "Service: " + new TypeAndName(type, type)
                                    + " is already added in builder, ignoring configured one.");
                        }
                    }
                }
            } else {
                if (configuredService.enabled()) {
                    if (ignoredServices.add(configuredService.typeAndName())) {
                        result.add(provider.create(configuredService.serviceConfig(),
                                                   configuredService.typeAndName().name()));
                    } else {
                        if (PROVIDER_LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            PROVIDER_LOGGER.log(System.Logger.Level.DEBUG, "Service: " + configuredService.typeAndName()
                                    + " is already added in builder, ignoring configured one.");
                        }
                    }
                }
            }
        });
        if (!unusedConfigs.isEmpty()) {
            throw new ConfigException("Unknown provider configured. Expected providers with types: " + unusedConfigs
                                              + ", but only the following providers are supported: " + availableProviders
                                              + ", provider interface: " + providerType.getName()
                                              + ", configured service: " + configType.getName());
        }
        return result;
    }

    private static <T extends NamedService> List<T>
    servicesFromList(HelidonServiceLoader<? extends ConfiguredProvider<T>> serviceLoader,
                     Class<? extends ConfiguredProvider<T>> providerType,
                     Class<T> configType,
                     List<ConfiguredService> configuredServices,
                     boolean allFromServiceLoader,
                     Set<TypeAndName> ignoredServices) {
        Map<String, ConfiguredProvider<T>> allProvidersByType = new HashMap<>();
        Map<String, ConfiguredProvider<T>> unusedProvidersByType = new LinkedHashMap<>();
        serviceLoader.forEach(it -> {
            allProvidersByType.put(it.configKey(), it);
            unusedProvidersByType.put(it.configKey(), it);
        });

        List<T> result = new ArrayList<>();

        // first add all configured
        for (ConfiguredService service : configuredServices) {
            TypeAndName typeAndName = service.typeAndName();
            if (!ignoredServices.add(typeAndName)) {
                unusedProvidersByType.remove(typeAndName.type());

                if (PROVIDER_LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    PROVIDER_LOGGER.log(System.Logger.Level.DEBUG, "Service: " + typeAndName
                            + " is already added in builder, ignoring configured one.");
                }

                continue;
            }
            ConfiguredProvider<T> provider = allProvidersByType.get(typeAndName.type());
            if (provider == null) {
                throw new ConfigException("Unknown provider configured. Expecting a provider with type \"" + typeAndName.type()
                                                  + "\", but only the following providers are supported: "
                                                  + allProvidersByType.keySet() + ", "
                                                  + "provider interface: " + providerType.getName()
                                                  + ", configured service: " + configType.getName());
            }
            unusedProvidersByType.remove(typeAndName.type());
            if (service.enabled()) {
                result.add(provider.create(service.serviceConfig(), typeAndName.name()));
            }
        }

        // then (if desired) add the rest
        if (allFromServiceLoader) {
            unusedProvidersByType.forEach((type, provider) -> {
                if (ignoredServices.add(new TypeAndName(type, type))) {
                    result.add(provider.create(Config.empty(), type));
                }
            });
        }

        return result;
    }

    private static ConfiguredService configuredService(Config serviceConfig, boolean isList) {
        if (isList) {
            // order is significant
            String type = serviceConfig.get(KEY_SERVICE_TYPE).asString().orElse(null);
            String name = serviceConfig.get(KEY_SERVICE_NAME).asString().orElse(type);
            boolean enabled = serviceConfig.get(KEY_SERVICE_ENABLED).asBoolean().orElse(true);

            Config usedConfig = serviceConfig;
            if (type == null) {
                // nested approach (we are on the first node of the list, we need to go deeper)
                List<Config> configs = serviceConfig.asNodeList().orElseGet(List::of);
                if (configs.size() != 1) {
                    throw new ConfigException(
                            "Service provider configuration defined as a list must have a single node that is the type, "
                                    + "with children containing the provider configuration. Failed on: " + serviceConfig.key());
                }
                usedConfig = configs.getFirst();
                name = usedConfig.name();
                type = usedConfig.get(KEY_SERVICE_TYPE).asString().orElse(name);
                enabled = usedConfig.get(KEY_SERVICE_ENABLED).asBoolean().orElse(enabled);
            }
            return new ConfiguredService(new TypeAndName(type, name), usedConfig, enabled);
        }
        // just collect each node, order will be determined by weight

        String name = serviceConfig.name(); // name is the config node name for object types
        String type = serviceConfig.get(KEY_SERVICE_TYPE).asString().orElse(name);
        boolean enabled = serviceConfig.get(KEY_SERVICE_ENABLED).asBoolean().orElse(true);

        return new ConfiguredService(new TypeAndName(type, name), serviceConfig, enabled);
    }

    private static <T extends NamedService> List<T>
    servicesFromList(RegistryWrap serviceRegistry,
                     Class<? extends ConfiguredProvider<T>> providerType,
                     Class<T> configType,
                     List<ConfiguredService> configuredServices,
                     boolean allFromServiceLoader,
                     Set<TypeAndName> ignoredServices) {
        Map<String, ConfiguredProvider<T>> allProvidersByType = new HashMap<>();
        Map<String, ConfiguredProvider<T>> unusedProvidersByType = new LinkedHashMap<>();

        serviceRegistry.all(providerType)
                .forEach(provider -> {
                    allProvidersByType.put(provider.configKey(), provider);
                    unusedProvidersByType.put(provider.configKey(), provider);
                });

        List<T> result = new ArrayList<>();

        // first add all configured
        for (ConfiguredService service : configuredServices) {
            TypeAndName typeAndName = service.typeAndName();
            if (!ignoredServices.add(typeAndName)) {
                unusedProvidersByType.remove(typeAndName.type());

                if (PROVIDER_LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    PROVIDER_LOGGER.log(System.Logger.Level.DEBUG, "Service: " + typeAndName
                            + " is already added in builder, ignoring configured one.");
                }

                continue;
            }
            ConfiguredProvider<T> provider = allProvidersByType.get(typeAndName.type());
            if (provider == null) {
                throw new ConfigException("Unknown provider configured. Expecting a provider with type \"" + typeAndName.type()
                                                  + "\", but only the following providers are supported: "
                                                  + allProvidersByType.keySet() + ", "
                                                  + "provider interface: " + providerType.getName()
                                                  + ", configured service: " + configType.getName());
            }
            unusedProvidersByType.remove(typeAndName.type());
            if (service.enabled()) {
                result.add(provider.create(service.serviceConfig(), typeAndName.name()));
            }
        }

        // then (if desired) add the rest
        if (allFromServiceLoader) {
            unusedProvidersByType.forEach((type, provider) -> {
                if (ignoredServices.add(new TypeAndName(type, type))) {
                    result.add(provider.create(Config.empty(), type));
                }
            });
        }

        return result;
    }

    private static <T extends NamedService> List<T>
    servicesFromObject(Config providersConfig,
                       RegistryWrap serviceRegistry,
                       Class<? extends ConfiguredProvider<T>> providerType,
                       Class<T> configType,
                       List<ConfiguredService> configuredServices,
                       boolean allFromServiceLoader,
                       Set<TypeAndName> ignoredServices) {
        // order is determined by service loader
        Set<String> availableProviders = new HashSet<>();
        Map<String, ConfiguredService> allConfigs = new HashMap<>();
        configuredServices.forEach(it -> allConfigs.put(it.typeAndName().type, it));
        Set<String> unusedConfigs = new HashSet<>(allConfigs.keySet());

        List<T> result = new ArrayList<>();

        List<? extends ConfiguredProvider<T>> all = serviceRegistry.all(providerType);
        for (ConfiguredProvider<T> provider : all) {
            ConfiguredService configuredService = allConfigs.get(provider.configKey());
            availableProviders.add(provider.configKey());
            unusedConfigs.remove(provider.configKey());
            if (configuredService == null) {
                if (allFromServiceLoader) {
                    // even though the specific key does not exist, we want to have the real config tree, so we can get to the
                    // root of it
                    // when there is no configuration, the name defaults to the type
                    String type = provider.configKey();
                    if (ignoredServices.add(new TypeAndName(type, type))) {
                        result.add(provider.create(providersConfig.get(type), type));
                    } else {
                        if (PROVIDER_LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            PROVIDER_LOGGER.log(System.Logger.Level.DEBUG, "Service: " + new TypeAndName(type, type)
                                    + " is already added in builder, ignoring configured one.");
                        }
                    }
                }
            } else {
                if (configuredService.enabled()) {
                    if (ignoredServices.add(configuredService.typeAndName())) {
                        result.add(provider.create(configuredService.serviceConfig(),
                                                   configuredService.typeAndName().name()));
                    } else {
                        if (PROVIDER_LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            PROVIDER_LOGGER.log(System.Logger.Level.DEBUG, "Service: " + configuredService.typeAndName()
                                    + " is already added in builder, ignoring configured one.");
                        }
                    }
                }
            }
        }

        if (!unusedConfigs.isEmpty()) {
            throw new ConfigException("Unknown provider configured. Expected providers with types: " + unusedConfigs
                                              + ", but only the following providers are supported: " + availableProviders
                                              + ", provider interface: " + providerType.getName()
                                              + ", configured service: " + configType.getName());
        }
        return result;
    }

    private interface RegistryWrap {
        <T> List<T> all(Class<T> type);
    }

    private record TypeAndName(String type, String name) {
    }

    private record ConfiguredService(TypeAndName typeAndName, Config serviceConfig, boolean enabled) {
    }

    private static final class StaticAccessRegistry implements RegistryWrap {
        public static final StaticAccessRegistry INSTANCE = new StaticAccessRegistry();

        @Override
        public <T> List<T> all(Class<T> type) {
            return Services.all(type);
        }
    }

    private static final class RealRegistry implements RegistryWrap {
        private final ServiceRegistry serviceRegistry;

        private RealRegistry(ServiceRegistry serviceRegistry) {
            this.serviceRegistry = serviceRegistry;
        }

        @Override
        public <T> List<T> all(Class<T> type) {
            return serviceRegistry.all(type);
        }
    }
}
