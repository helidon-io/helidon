/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.health;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.health.spi.HealthCheckProvider;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.webserver.observe.spi.ObserveProvider;
import io.helidon.webserver.observe.spi.Observer;

/**
 * Health observe provider implementation, discoverable through
 * {@link io.helidon.service.registry.ServiceRegistry} and {@link java.util.ServiceLoader}.
 */
@Service.Singleton
@Api.Internal
public class HealthObserveProvider implements ObserveProvider {
    private final ServiceRegistry owner;
    private final Function<HealthObserverConfig, HealthObserver> observerFactory;

    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public HealthObserveProvider() {
        this.owner = null;
        this.observerFactory = HealthObserver::create;
    }

    @Service.Inject
    HealthObserveProvider(ServiceRegistry owner,
                          Supplier<Config> rootConfig,
                          Supplier<List<HealthCheckProvider>> providers,
                          Supplier<List<HealthCheck>> checks) {
        this.owner = owner;
        this.observerFactory = config -> new HealthObserver(config, rootConfig, providers, checks);
    }

    @Override
    public String configKey() {
        return "health";
    }

    @Override
    public Observer create(Config config, String name) {
        return observerFactory.apply(observerConfig(config, name));
    }

    @Override
    public Observer create(Config config, String name, ServiceRegistry serviceRegistry) {
        Objects.requireNonNull(serviceRegistry);
        if (serviceRegistry == owner) {
            return create(config, name);
        }
        return new HealthObserver(observerConfig(config, name),
                                  serviceRegistry.supply(Config.class),
                                  serviceRegistry.supplyAll(HealthCheckProvider.class),
                                  serviceRegistry.supplyAll(HealthCheck.class));
    }

    private static HealthObserverConfig observerConfig(Config config, String name) {
        return HealthObserverConfig.builder()
                .config(Objects.requireNonNull(config))
                .name(Objects.requireNonNull(name))
                .buildPrototype();
    }
}
