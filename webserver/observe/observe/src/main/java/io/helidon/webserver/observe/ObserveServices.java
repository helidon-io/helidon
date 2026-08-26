/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.spi.ServerFeatureProvider;

@Service.Singleton
class ObserveServices {
    private static final String OBSERVE = ObserveFeature.OBSERVE_ID;

    private final LazyValue<Graph> graph;

    @Service.Inject
    ObserveServices(Supplier<Config> config,
                    Supplier<List<ServerFeatureProvider<ObserveFeature>>> featureProviders,
                    ServiceRegistry serviceRegistry) {
        this.graph = LazyValue.create(() -> createGraph(config.get(), featureProviders, serviceRegistry));
    }

    List<ObserveFeature> features() {
        return graph.get().features();
    }

    List<Service.QualifiedInstance<Observer>> observers() {
        return graph.get().observers();
    }

    private static Graph createGraph(Config rootConfig,
                                     Supplier<List<ServerFeatureProvider<ObserveFeature>>> featureProviders,
                                     ServiceRegistry serviceRegistry) {
        Config serverConfig = rootConfig.get("server");
        Config featuresConfig = serverConfig.get("features");
        List<ConfiguredFeature> configuredFeatures = configuredFeatures(featuresConfig);
        boolean discoverFeatures = serverConfig.get("features-discover-services")
                .asBoolean()
                .orElse(true);
        List<ObserveFeature> activeFeatures = serviceRegistry.allActive(ObserveFeature.class);

        LazyValue<ServerFeatureProvider<ObserveFeature>> featureProvider = LazyValue.create(() -> featureProviders.get()
                .stream()
                .filter(provider -> OBSERVE.equals(provider.configKey()))
                .findFirst()
                .orElseThrow(() -> new ConfigException("No server feature provider is available for type \""
                                                               + OBSERVE + "\"")));
        var features = new ArrayList<ObserveFeature>();
        boolean observeConfigured = false;
        Set<String> names = new HashSet<>();

        for (ConfiguredFeature configuredFeature : configuredFeatures) {
            if (!OBSERVE.equals(configuredFeature.type())) {
                continue;
            }
            observeConfigured = true;
            if (!names.add(configuredFeature.name())) {
                throw new ConfigException("Duplicate configured provider identity at " + featuresConfig.key()
                                                  + ": type \"" + OBSERVE
                                                  + "\", name \"" + configuredFeature.name() + "\"");
            }
            if (activeFeatures.stream().anyMatch(feature -> sameIdentity(feature, configuredFeature.name()))) {
                continue;
            }
            if (configuredFeature.enabled()) {
                features.add(featureProvider.get().create(configuredFeature.config(),
                                                          configuredFeature.name(),
                                                          serviceRegistry));
            }
        }

        if (discoverFeatures
                && !observeConfigured
                && activeFeatures.stream().noneMatch(feature -> sameIdentity(feature, OBSERVE))) {
            features.add(featureProvider.get().create(featuresConfig.get(OBSERVE), OBSERVE, serviceRegistry));
        }

        var observers = new ArrayList<Service.QualifiedInstance<Observer>>();
        for (ObserveFeature feature : features) {
            int ordinal = 0;
            for (Observer observer : feature.observers()) {
                String identity = lengthPrefixed(feature.name())
                        + lengthPrefixed(observer.type())
                        + lengthPrefixed(observer.name())
                        + lengthPrefixed(Integer.toString(ordinal++));
                observers.add(Service.QualifiedInstance.create(observer,
                                                               Qualifier.createNamed(identity)));
            }
        }
        return new Graph(List.copyOf(features), List.copyOf(observers));
    }

    private static boolean sameIdentity(ObserveFeature feature, String name) {
        return OBSERVE.equals(feature.type()) && name.equals(feature.name());
    }

    private static List<ConfiguredFeature> configuredFeatures(Config featuresConfig) {
        List<Config> featureConfigs = featuresConfig.asNodeList().orElseGet(List::of);
        var result = new ArrayList<ConfiguredFeature>(featureConfigs.size());
        boolean isList = featuresConfig.isList();

        for (Config featureConfig : featureConfigs) {
            if (isList) {
                result.add(configuredListFeature(featureConfig));
            } else {
                String name = featureConfig.name();
                String type = featureConfig.get("type").asString().orElse(name);
                boolean enabled = !featureConfig.isObject()
                        || featureConfig.get("enabled").asBoolean().orElse(true);
                result.add(new ConfiguredFeature(type, name, featureConfig, enabled));
            }
        }
        return result;
    }

    private static ConfiguredFeature configuredListFeature(Config featureConfig) {
        String type = featureConfig.get("type").asString().orElse(null);
        String name = featureConfig.get("name").asString().orElse(type);
        boolean enabled = featureConfig.get("enabled").asBoolean().orElse(true);
        Config usedConfig = featureConfig;

        if (type == null) {
            List<Config> nested = featureConfig.asNodeList().orElseGet(List::of);
            if (nested.size() != 1) {
                throw new ConfigException("Service provider configuration defined as a list must have a single node that is "
                                                  + "the type, with children containing the provider configuration. Failed on: "
                                                  + featureConfig.key());
            }
            usedConfig = nested.getFirst();
            name = usedConfig.name();
            type = usedConfig.get("type").asString().orElse(name);
            enabled = usedConfig.get("enabled").asBoolean().orElse(enabled);
        }
        return new ConfiguredFeature(type, name, usedConfig, enabled);
    }

    private static String lengthPrefixed(String value) {
        return value.length() + ":" + value;
    }

    private record Graph(List<ObserveFeature> features, List<Service.QualifiedInstance<Observer>> observers) {
    }

    private record ConfiguredFeature(String type, String name, Config config, boolean enabled) {
    }
}
