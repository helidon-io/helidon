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

package io.helidon.webserver.observe.health;

import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.spi.HealthCheckProvider;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

class HealthObserveProviderRegistryTest {
    @Test
    void providerUsesServicesFromOwningRegistryWhenDiscoveryIsDisabled() {
        Config rootConfig = Config.just(ConfigSources.create(Map.of("health-check-name", "provider-check")));
        HealthCheckProvider healthCheckProvider = config -> List.of(healthCheck(config.get("health-check-name")
                                                                                       .asString()
                                                                                       .orElseThrow()));
        HealthCheck healthCheck = healthCheck("registry-check");
        var registryConfig = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .putContractInstance(Config.class, rootConfig)
                .putContractInstance(HealthCheckProvider.class, healthCheckProvider)
                .putContractInstance(HealthCheck.class, healthCheck)
                .build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(registryConfig);
        try {
            HealthObserver observer = (HealthObserver) new HealthObserveProvider()
                    .create(Config.empty(), "test", manager.registry());

            assertThat(observer.all().stream().map(HealthCheck::name).toList(),
                       contains("provider-check", "registry-check"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void managedObserverPreservesDiscoveryOrder() {
        HealthObserverConfig config = HealthObserverConfig.builder()
                .addCheck(healthCheck("explicit-check"))
                .buildPrototype();
        HealthObserver observer = new HealthObserver(config,
                                                     Config::empty,
                                                     () -> List.of(_ -> List.of(healthCheck("provider-check"))),
                                                     () -> List.of(healthCheck("registry-check")));

        assertThat(observer.all().stream().map(HealthCheck::name).toList(),
                   contains("explicit-check", "provider-check", "registry-check"));
    }

    @Test
    void systemServiceDiscoveryCanBeDisabledPerObserver() {
        HealthObserverConfig config = HealthObserverConfig.builder()
                .useSystemServices(false)
                .addCheck(healthCheck("explicit-check"))
                .buildPrototype();
        HealthObserver observer = new HealthObserver(config,
                                                     () -> {
                                                         throw new AssertionError("Root config must not be resolved");
                                                     },
                                                     () -> {
                                                         throw new AssertionError("Providers must not be resolved");
                                                     },
                                                     () -> {
                                                         throw new AssertionError("Checks must not be resolved");
                                                     });

        assertThat(observer.all().stream().map(HealthCheck::name).toList(), contains("explicit-check"));
    }

    @Test
    void legacyHealthCheckProviderIsBridgedIntoRegistry() {
        var registryConfig = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(registryConfig);
        try {
            assertThat(manager.registry()
                               .all(HealthCheckProvider.class)
                               .stream()
                               .map(provider -> provider.getClass().getName())
                               .toList(),
                       hasItem("io.helidon.health.checks.BuiltInHealthCheckProvider"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void legacyHealthCheckProviderDiscoveryCanBeDisabled() {
        var registryConfig = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(registryConfig);
        try {
            assertThat(manager.registry().all(HealthCheckProvider.class), empty());
        } finally {
            manager.shutdown();
        }
    }

    private static HealthCheck healthCheck(String name) {
        return new HealthCheck() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public HealthCheckResponse call() {
                return HealthCheckResponse.builder().status(HealthCheckResponse.Status.UP).build();
            }
        };
    }
}
