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

package io.helidon.webserver.grpc;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.webserver.grpc.spi.GrpcServerService;
import io.helidon.webserver.grpc.spi.GrpcServerServiceProvider;

import io.grpc.ServerInterceptor;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class GrpcRoutingRegistryTest {
    @Test
    void configuredServiceUsesOwningRegistryProvider() {
        TestProvider provider = new TestProvider();
        ServiceRegistryManager manager = registry(provider);
        try {
            ServiceRegistry serviceRegistry = manager.registry();
            Config config = routingConfig(Map.of("grpc.grpc-services.owner.enabled", "true",
                                                 "grpc.grpc-services-discover-services", "false"));

            GrpcRouting.builder()
                    .config(config)
                    .serviceRegistry(serviceRegistry)
                    .build();

            assertThat(provider.createCount, is(1));
            assertThat(provider.name, is("owner"));
            assertThat(provider.serviceRegistry, sameInstance(serviceRegistry));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void discoveredServiceUsesOwningRegistryProvider() {
        TestProvider provider = new TestProvider();
        ServiceRegistryManager manager = registry(provider);
        try {
            ServiceRegistry serviceRegistry = manager.registry();
            Config config = routingConfig(Map.of("grpc.marker", "present"));

            GrpcRouting.builder()
                    .config(config)
                    .serviceRegistry(serviceRegistry)
                    .build();

            assertThat(provider.createCount, is(1));
            assertThat(provider.name, is("owner"));
            assertThat(provider.serviceRegistry, sameInstance(serviceRegistry));
        } finally {
            manager.shutdown();
        }
    }

    private static Config routingConfig(Map<String, String> values) {
        return Config.just(ConfigSources.create(values)).get("grpc");
    }

    private static ServiceRegistryManager registry(GrpcServerServiceProvider provider) {
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                     .discoverServices(false)
                                                     .discoverServicesFromServiceLoader(false)
                                                     .putContractInstance(GrpcServerServiceProvider.class, provider)
                                                     .build());
    }

    private static final class TestProvider implements GrpcServerServiceProvider {
        private ServiceRegistry serviceRegistry;
        private String name;
        private int createCount;

        @Override
        public String configKey() {
            return "owner";
        }

        @Override
        public GrpcServerService create(Config config, String name) {
            throw new AssertionError("Managed service creation must use the owning registry");
        }

        @Override
        public GrpcServerService create(Config config, String name, ServiceRegistry serviceRegistry) {
            this.serviceRegistry = serviceRegistry;
            this.name = name;
            createCount++;
            return new TestService(name);
        }
    }

    private record TestService(String name) implements GrpcServerService {
        @Override
        public String type() {
            return "owner";
        }

        @Override
        public WeightedBag<ServerInterceptor> interceptors() {
            return WeightedBag.create();
        }
    }
}
