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

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.spi.ObserveProvider;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.spi.ServerFeature;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.sameInstance;

class ObserveServicesTest {
    @Test
    void factoriesExposeTheSameConfiguredGraph() {
        Config config = Config.just(ConfigSources.create(Map.of("server.features.observe.observers.test.value", "configured")));
        var provider = new TestObserveProvider();
        ServiceRegistryManager manager = registry(provider);
        try {
            ServiceRegistry serviceRegistry = manager.registry();
            var services = new ObserveServices(() -> config, serviceRegistry);
            var featureProducts = new ObserveFeatureServicesFactory(services).services();
            var observerProducts = new ObserverServicesFactory(services).services();

            assertThat(featureProducts, hasSize(1));
            assertThat(observerProducts, hasSize(1));
            ObserveFeature feature = featureProducts.getFirst().get();
            Observer observer = observerProducts.getFirst().get();
            assertThat(feature, instanceOf(ServerFeature.class));
            assertThat(feature.observers(), contains(sameInstance(observer)));
            assertThat(provider.registry, sameInstance(serviceRegistry));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void discoveryFlagsAreHonored() {
        var provider = new TestObserveProvider();
        ServiceRegistryManager manager = registry(provider);
        try {
            ServiceRegistry serviceRegistry = manager.registry();
            Config noFeatureDiscovery = Config.just(ConfigSources.create(Map.of("server.features-discover-services", "false")));
            var noFeatures = new ObserveServices(() -> noFeatureDiscovery, serviceRegistry);
            assertThat(new ObserveFeatureServicesFactory(noFeatures).services(), empty());
            assertThat(new ObserverServicesFactory(noFeatures).services(), empty());

            Config noObserverDiscovery = Config.just(
                    ConfigSources.create(Map.of("server.features.observe.observers-discover-services", "false")));
            var noObservers = new ObserveServices(() -> noObserverDiscovery, serviceRegistry);
            assertThat(new ObserveFeatureServicesFactory(noObservers).services(), hasSize(1));
            assertThat(new ObserverServicesFactory(noObservers).services(), empty());
            assertThat(provider.createCount, is(0));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void featureListOrderAndObserverIdentityArePreserved() {
        ConfigNode.ListNode featureNodes = ConfigNode.ListNode.builder()
                .addObject(ConfigNode.ObjectNode.builder()
                                   .addValue("type", "observe")
                                   .addValue("name", "first")
                                   .build())
                .addObject(ConfigNode.ObjectNode.builder()
                                   .addValue("type", "observe")
                                   .addValue("name", "second")
                                   .build())
                .build();
        ConfigNode.ObjectNode configNode = ConfigNode.ObjectNode.builder()
                .addObject("server", ConfigNode.ObjectNode.builder()
                        .addList("features", featureNodes)
                        .build())
                .build();
        Config config = Config.just(ConfigSources.create(configNode));
        var provider = new TestObserveProvider();
        ServiceRegistryManager manager = registry(provider);
        try {
            var services = new ObserveServices(() -> config, manager.registry());
            List<ObserveFeature> features = new ObserveFeatureServicesFactory(services)
                    .services()
                    .stream()
                    .map(it -> it.get())
                    .toList();
            var observerProducts = new ObserverServicesFactory(services).services();
            List<Observer> observers = observerProducts
                    .stream()
                    .map(it -> it.get())
                    .toList();

            assertThat(features.stream().map(ObserveFeature::name).toList(), contains("first", "second"));
            assertThat(observers, hasSize(2));
            assertThat(features.get(0).observers().getFirst(), sameInstance(observers.get(0)));
            assertThat(features.get(1).observers().getFirst(), sameInstance(observers.get(1)));
            assertThat(observers.get(0) == observers.get(1), is(false));
            assertThat(observerProducts.get(0).qualifiers(), not(is(observerProducts.get(1).qualifiers())));
        } finally {
            manager.shutdown();
        }
    }

    private static ServiceRegistryManager registry(ObserveProvider provider) {
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                     .discoverServices(false)
                                                     .discoverServicesFromServiceLoader(false)
                                                     .putContractInstance(ObserveProvider.class, provider)
                                                     .build());
    }

    private static final class TestObserveProvider implements ObserveProvider {
        private ServiceRegistry registry;
        private int createCount;

        @Override
        public String configKey() {
            return "test";
        }

        @Override
        public Observer create(Config config, String name) {
            throw new AssertionError("Managed observer creation must use the owning registry");
        }

        @Override
        public Observer create(Config config, String name, ServiceRegistry serviceRegistry) {
            registry = serviceRegistry;
            createCount++;
            return new TestObserver(name);
        }
    }

    private static final class TestObserver implements Observer {
        private final ObserverConfigBase config;

        private TestObserver(String name) {
            config = ObserverConfigBase.builder()
                    .name(name)
                    .buildPrototype();
        }

        @Override
        public ObserverConfigBase prototype() {
            return config;
        }

        @Override
        public String type() {
            return "test";
        }

        @Override
        public void register(ServerFeature.ServerFeatureContext featureContext,
                             List<HttpRouting.Builder> observeEndpointRouting,
                             UnaryOperator<String> endpointFunction) {
        }
    }
}
