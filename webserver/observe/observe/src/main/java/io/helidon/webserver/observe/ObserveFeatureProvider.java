/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.config.ConfigBuilderSupport;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.webserver.observe.spi.ObserveProvider;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * Provider implementation for the observe feature for {@link io.helidon.webserver.WebServer}, discoverable through
 * {@link io.helidon.service.registry.ServiceRegistry} and {@link java.util.ServiceLoader}.
 */
@Weight(ObserveFeature.WEIGHT)
@Service.Singleton
public class ObserveFeatureProvider implements ServerFeatureProvider<ObserveFeature> {
    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public ObserveFeatureProvider() {
    }

    @Override
    public String configKey() {
        return ObserveFeature.OBSERVE_ID;
    }

    @Override
    public ObserveFeature create(Config config, String name) {
        return ObserveFeature.builder()
                .config(config)
                .name(name)
                .build();
    }

    @Override
    public ObserveFeature create(Config config, String name, ServiceRegistry serviceRegistry) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(name);
        Objects.requireNonNull(serviceRegistry);
        boolean discoverServices = config.get("observers-discover-services")
                .asBoolean()
                .orElse(true);
        List<Observer> observers = ConfigBuilderSupport.discoverServices(config,
                                                                         "observers",
                                                                         Optional.of(serviceRegistry),
                                                                         ObserveProvider.class,
                                                                         Observer.class,
                                                                         discoverServices,
                                                                         List.of());
        return ObserveFeature.create(new RegistryObserveFeatureConfig(config, name, observers));
    }

    private static final class RegistryObserveFeatureConfig implements ObserveFeatureConfig {
        private final boolean enabled;
        private final double weight;
        private final List<Observer> observers;
        private final List<String> sockets;
        private final Config config;
        private final String endpoint;
        private final String name;

        private RegistryObserveFeatureConfig(Config config, String name, List<Observer> observers) {
            this.config = config;
            this.name = name;
            this.observers = List.copyOf(observers);
            this.enabled = config.get("enabled").asBoolean().orElse(true);
            this.endpoint = config.get("endpoint").asString().orElse("/observe");
            this.weight = config.get("weight").asDouble().orElse(ObserveFeature.WEIGHT);
            this.sockets = config.get("sockets").asList(String.class).orElseGet(List::of);
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public String endpoint() {
            return endpoint;
        }

        @Override
        public double weight() {
            return weight;
        }

        @Override
        public List<Observer> observers() {
            return observers;
        }

        @Override
        public Optional<Config> config() {
            return Optional.of(config);
        }

        @Override
        public List<String> sockets() {
            return sockets;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ObserveFeature build() {
            return ObserveFeature.create(this);
        }
    }
}
