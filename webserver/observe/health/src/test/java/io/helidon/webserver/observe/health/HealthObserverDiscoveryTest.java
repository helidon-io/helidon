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

import io.helidon.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.spi.HealthCheckProvider;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@Testing.Test(perMethod = true)
@Execution(ExecutionMode.SAME_THREAD)
class HealthObserverDiscoveryTest {
    private ServiceRegistryManager globalRegistry;

    static HealthCheck healthCheck(String name) {
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

    @AfterEach
    void shutdownRegistry() {
        if (globalRegistry != null) {
            globalRegistry.shutdown();
        }
    }

    @Test
    void directObserverCombinesRegistryAndJavaProvidersWithExplicitChecks() {
        HealthCheckProvider provider = _ -> List.of(healthCheck("registry-provider-check"));
        installGlobalRegistry(provider);
        HealthObserver observer = HealthObserver.builder()
                .addCheck(healthCheck("explicit-check"))
                .build();

        assertThat(names(observer), hasItems("registry-provider-check", "registry-check", "explicit-check",
                                            "java-provider-check", "diskSpace", "heapMemory"));
    }

    @Test
    void registryProviderReplacesSameJavaProviderWithoutDuplicateChecks() {
        DiscoveryHealthCheckProvider provider = new DiscoveryHealthCheckProvider("registry-provider-check");
        installGlobalRegistry(provider);
        HealthObserver observer = HealthObserver.builder().build();

        assertThat(names(observer), hasItem("registry-provider-check"));
        assertThat(names(observer), not(hasItem("java-provider-check")));
        assertThat(names(observer).stream().filter("registry-provider-check"::equals).count(), is(1L));
        assertThat(provider.calls(), is(1));
    }

    @Test
    void directObserverCanDisableDiscoveryAndKeepExplicitChecks() {
        HealthCheckProvider provider = _ -> {
            throw new AssertionError("Provider must not be called when discovery is disabled");
        };
        installGlobalRegistry(provider);
        HealthObserver observer = HealthObserver.builder()
                .useSystemServices(false)
                .addCheck(healthCheck("explicit-check"))
                .build();

        assertThat(names(observer), contains("explicit-check"));
    }

    @Test
    void directObserverExcludesChecksFromEveryDiscoveryPath() {
        installGlobalRegistry(_ -> List.of(healthCheck("registry-provider-check")));
        HealthObserver observer = HealthObserver.builder()
                .addCheck(healthCheck("explicit-check"))
                .addExcluded("registry-provider-check")
                .addExcluded("registry-check")
                .addExcluded("java-provider-check")
                .addExcluded("explicit-check")
                .build();

        assertThat(names(observer), not(hasItem("registry-provider-check")));
        assertThat(names(observer), not(hasItem("registry-check")));
        assertThat(names(observer), not(hasItem("java-provider-check")));
        assertThat(names(observer), not(hasItem("explicit-check")));
        assertThat(names(observer), hasItems("diskSpace", "heapMemory"));
    }

    @Test
    void managedObserverDoesNotUseGlobalProviders() {
        installGlobalRegistry(_ -> List.of(healthCheck("global-provider-check")));
        ServiceRegistryManager owner = registry(_ -> List.of(healthCheck("owner-provider-check")));
        try {
            HealthObserver observer = (HealthObserver) new HealthObserveProvider()
                    .create(Config.empty(), "owned", owner.registry());

            assertThat(names(observer), contains("owner-provider-check", "registry-check"));
        } finally {
            owner.shutdown();
        }
    }

    private static ServiceRegistryManager registry(HealthCheckProvider provider) {
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .putContractInstance(Config.class, Config.empty())
                .putContractInstance(HealthCheckProvider.class, provider)
                .putContractInstance(HealthCheck.class, healthCheck("registry-check"))
                .build());
    }

    private static List<String> names(HealthObserver observer) {
        return observer.all().stream().map(HealthCheck::name).toList();
    }

    private void installGlobalRegistry(HealthCheckProvider provider) {
        globalRegistry = registry(provider);
        Services.registry(globalRegistry.registry());
    }
}
