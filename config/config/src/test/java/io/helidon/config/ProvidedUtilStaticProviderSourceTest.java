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

package io.helidon.config;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Weight;
import io.helidon.service.registry.ExistingInstanceDescriptor;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@Testing.Test
@Isolated
public class ProvidedUtilStaticProviderSourceTest {
    @Test
    void mergesServiceLoaderAndRegistryProvidersByEffectiveWeight() {
        ServiceRegistry previousGlobal = GlobalServiceRegistry.registry();
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .addServiceDescriptor(descriptor(
                                                                                        new RegistryProvider(
                                                                                                "registry-high",
                                                                                                "registry-high"),
                                                                                        400))
                                                                                .addServiceDescriptor(descriptor(
                                                                                        new RegistryProvider(
                                                                                                "replaceable",
                                                                                                "registry-replacement"),
                                                                                        100))
                                                                                .addServiceDescriptor(descriptor(
                                                                                        new RegistryProvider(
                                                                                                "replaceable",
                                                                                                "registry-lower-priority"),
                                                                                        25))
                                                                                .addServiceDescriptor(descriptor(
                                                                                        new RegistryProvider(
                                                                                                "registry-low",
                                                                                                "registry-low"),
                                                                                        50))
                                                                                .build());
        try {
            GlobalServiceRegistry.registry(manager.registry());

            List<OrderingService> services = ProvidedUtil.discoverServices(Config.empty(),
                                                                            "services",
                                                                            Optional.empty(),
                                                                            OrderingProvider.class,
                                                                            OrderingService.class,
                                                                            true,
                                                                            List.of());

            assertThat(services.stream().map(OrderingService::source).toList(),
                       contains("registry-high", "loader-high", "registry-replacement", "registry-low"));
        } finally {
            manager.shutdown();
            GlobalServiceRegistry.registry(previousGlobal);
        }
    }

    private static ServiceDescriptor<?> descriptor(OrderingProvider provider, double weight) {
        return ExistingInstanceDescriptor.create(provider, Set.of(OrderingProvider.class), weight);
    }

    public interface OrderingProvider extends ConfiguredProvider<OrderingService> {
    }

    private record OrderingService(String name, String type, String source) implements NamedService {
    }

    private abstract static class BaseProvider implements OrderingProvider {
        private final String key;
        private final String source;

        private BaseProvider(String key, String source) {
            this.key = key;
            this.source = source;
        }

        @Override
        public String configKey() {
            return key;
        }

        @Override
        public OrderingService create(Config config, String name) {
            return new OrderingService(name, key, source);
        }
    }

    @Weight(300)
    public static final class LoaderHighProvider extends BaseProvider {
        public LoaderHighProvider() {
            super("loader-high", "loader-high");
        }
    }

    @Weight(200)
    public static final class LoaderReplaceableProvider extends BaseProvider {
        public LoaderReplaceableProvider() {
            super("replaceable", "loader-replaceable");
        }
    }

    private static final class RegistryProvider extends BaseProvider {
        private RegistryProvider(String key, String source) {
            super(key, source);
        }
    }
}
