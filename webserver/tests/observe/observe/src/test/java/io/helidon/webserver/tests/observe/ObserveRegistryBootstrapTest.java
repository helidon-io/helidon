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

package io.helidon.webserver.tests.observe;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.info.InfoObserver;
import io.helidon.webserver.observe.spi.ObserveProvider;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.spi.ServerFeature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static java.util.Map.entry;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

@Isolated
class ObserveRegistryBootstrapTest {
    private static final String ENDPOINT = "/registry-observe";

    @Test
    void managerStartDiscoversFeatureAndObservers() {
        ServiceRegistry previousGlobal = GlobalServiceRegistry.registry();
        ServiceRegistryManager manager = null;

        try {
            manager = ServiceRegistryManager.start(registryConfig(automaticConfig("bootstrap")));
            ServiceRegistry registry = manager.registry();
            WebServer server = registry.get(WebServer.class);
            WebClient client = client(server);

            assertThat(server.isRunning(), is(true));

            var features = registry.all(ObserveFeature.class);
            var observers = registry.all(Observer.class);
            assertThat(features, hasSize(1));
            assertThat(observers, hasSize(6));
            assertThat(observerTypes(observers),
                       containsInAnyOrder("config", "health", "info", "log", "metrics", "tracing"));

            ObserveFeature feature = features.getFirst();
            assertThat(feature.prototype().observers(), hasSize(6));
            for (Observer featureObserver : feature.prototype().observers()) {
                assertThat("Registry does not expose the feature's " + featureObserver.type() + " observer",
                           observers,
                           hasItem(sameInstance(featureObserver)));
            }

            assertConfigEndpoint(client, "bootstrap");
            assertStatus(client.get(ENDPOINT + "/health").request(String.class), Status.NO_CONTENT_204);
            assertStatus(client.get(ENDPOINT + "/metrics").request(String.class), Status.OK_200);
            assertStatus(client.get(ENDPOINT + "/info").request(String.class), Status.OK_200);
            assertStatus(client.get(ENDPOINT + "/log/loggers").request(String.class), Status.OK_200);

        } finally {
            if (manager != null) {
                manager.shutdown();
            }
            GlobalServiceRegistry.registry(previousGlobal);
        }
    }

    @Test
    void legacyObserveProvidersAreBridgedIntoRegistry() {
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .discoverServices(false)
                                                                                .discoverServicesFromServiceLoader(true)
                                                                                .build());
        try {
            List<String> providerTypes = manager.registry()
                    .all(ObserveProvider.class)
                    .stream()
                    .map(ObserveProvider::configKey)
                    .toList();

            assertThat(providerTypes,
                       containsInAnyOrder("config", "health", "info", "log", "metrics", "tracing"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void managedConfigObserverUsesOwningRegistry() {
        ServiceRegistry previousGlobal = GlobalServiceRegistry.registry();
        ServiceRegistryManager globalManager = ServiceRegistryManager.create(registryConfig(isolatedConfig("global")));
        ServiceRegistryManager ownerManager = ServiceRegistryManager.create(registryConfig(isolatedConfig("owner")));
        WebServer server = null;

        try {
            GlobalServiceRegistry.registry(globalManager.registry());
            assertThat(Services.get(Config.class).get("registry.marker").asString().orElseThrow(), is("global"));

            ServiceRegistry ownerRegistry = ownerManager.registry();
            server = ownerRegistry.get(WebServer.class).start();
            var observers = ownerRegistry.all(Observer.class);

            assertThat(observerTypes(observers), contains("config"));
            assertConfigEndpoint(client(server), "owner");
        } finally {
            if (server != null) {
                server.stop();
            }
            ownerManager.shutdown();
            globalManager.shutdown();
            GlobalServiceRegistry.registry(previousGlobal);
        }
    }

    @Test
    void discoveryCanBeDisabledForManualFeatureAndObserver() {
        ServiceRegistry previousGlobal = GlobalServiceRegistry.registry();
        InfoObserver observer = InfoObserver.builder()
                .name("manual")
                .endpoint("manual")
                .putValue("source", "manual")
                .build();
        ObserveFeature feature = ObserveFeature.builder()
                .name("manual-observe")
                .endpoint("/manual-observe")
                .observersDiscoverServices(false)
                .addObserver(observer)
                .build();
        ServiceRegistryConfig registryConfig = ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, manualConfig())
                .putContractInstance(Observer.class, observer)
                .putContractInstance(ObserveFeature.class, feature)
                .putContractInstance(ServerFeature.class, feature)
                .discoverServicesFromServiceLoader(false)
                .build();
        ServiceRegistryManager manager = null;

        try {
            manager = ServiceRegistryManager.start(registryConfig);
            ServiceRegistry registry = manager.registry();
            WebServer server = registry.get(WebServer.class);

            assertThat(registry.all(ObserveFeature.class), contains(sameInstance(feature)));
            assertThat(registry.all(Observer.class), contains(sameInstance(observer)));

            ClientResponseTyped<JsonObject> response = client(server).get("/manual-observe/manual/source")
                    .request(JsonObject.class);
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().stringValue("value").orElseThrow(), is("manual"));
        } finally {
            if (manager != null) {
                manager.shutdown();
            }
            GlobalServiceRegistry.registry(previousGlobal);
        }
    }

    @Test
    void defaultManualFeatureSuppressesAutomaticGraph() {
        ServiceRegistry previousGlobal = GlobalServiceRegistry.registry();
        var provider = new CountingObserveProvider();
        InfoObserver observer = InfoObserver.builder()
                .name("manual")
                .endpoint("manual")
                .putValue("source", "manual")
                .build();
        ObserveFeature feature = ObserveFeature.builder()
                .name("observe")
                .endpoint("/manual-default-observe")
                .observersDiscoverServices(false)
                .addObserver(observer)
                .build();
        Config config = Config.just(ConfigSources.create(Map.of(
                "declarative.ignore-incubating", "true",
                "server.port", "0")));
        ServiceRegistryConfig registryConfig = ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, config)
                .putContractInstance(ObserveProvider.class, provider)
                .putContractInstance(Observer.class, observer)
                .putContractInstance(ObserveFeature.class, feature)
                .putContractInstance(ServerFeature.class, feature)
                .discoverServicesFromServiceLoader(false)
                .build();
        ServiceRegistryManager manager = null;

        try {
            manager = ServiceRegistryManager.start(registryConfig);
            ServiceRegistry registry = manager.registry();
            WebServer server = registry.get(WebServer.class);

            assertThat(provider.createCount, is(0));
            assertThat(registry.all(ObserveFeature.class), contains(sameInstance(feature)));
            assertThat(registry.all(Observer.class), contains(sameInstance(observer)));

            ClientResponseTyped<JsonObject> response = client(server).get("/manual-default-observe/manual/source")
                    .request(JsonObject.class);
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().stringValue("value").orElseThrow(), is("manual"));
        } finally {
            if (manager != null) {
                manager.shutdown();
            }
            GlobalServiceRegistry.registry(previousGlobal);
        }
    }

    @Test
    void matchingManualFeatureSuppressesAutomaticGraphWhenNotFirst() {
        ServiceRegistry previousGlobal = GlobalServiceRegistry.registry();
        var provider = new CountingObserveProvider();
        InfoObserver observer = InfoObserver.builder()
                .name("manual")
                .endpoint("manual")
                .putValue("source", "manual")
                .build();
        ObserveFeature customFeature = ObserveFeature.builder()
                .name("custom")
                .endpoint("/custom-observe")
                .observersDiscoverServices(false)
                .build();
        ObserveFeature defaultFeature = ObserveFeature.builder()
                .name("observe")
                .endpoint("/manual-default-observe")
                .observersDiscoverServices(false)
                .addObserver(observer)
                .build();
        Config config = Config.just(ConfigSources.create(Map.of(
                "declarative.ignore-incubating", "true",
                "server.port", "0")));
        ServiceRegistryConfig registryConfig = ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, config)
                .putContractInstance(ObserveProvider.class, provider)
                .putContractInstance(Observer.class, observer)
                .discoverServicesFromServiceLoader(false)
                .build();
        ServiceRegistryManager manager = null;
        WebServer server = null;

        try {
            manager = ServiceRegistryManager.create(registryConfig);
            ServiceRegistry registry = manager.registry();
            GlobalServiceRegistry.registry(registry);
            Services.set(ObserveFeature.class, customFeature, defaultFeature);
            Services.set(ServerFeature.class, customFeature, defaultFeature);
            server = registry.get(WebServer.class).start();

            assertThat(provider.createCount, is(0));
            List<ObserveFeature> features = registry.all(ObserveFeature.class);
            assertThat(features, hasSize(2));
            assertThat(features.get(0), sameInstance(customFeature));
            assertThat(features.get(1), sameInstance(defaultFeature));

            ClientResponseTyped<JsonObject> response = client(server).get("/manual-default-observe/manual/source")
                    .request(JsonObject.class);
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().stringValue("value").orElseThrow(), is("manual"));
        } finally {
            if (server != null) {
                server.stop();
            }
            if (manager != null) {
                manager.shutdown();
            }
            GlobalServiceRegistry.registry(previousGlobal);
        }
    }

    private static ServiceRegistryConfig registryConfig(Config config) {
        return ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, config)
                .build();
    }

    private static Config automaticConfig(String marker) {
        return Config.just(ConfigSources.create(Map.ofEntries(
                entry("declarative.ignore-incubating", "true"),
                entry("server.port", "0"),
                entry("server.features.observe.endpoint", ENDPOINT),
                entry("server.features.observe.observers.config.permit-all", "true"),
                entry("server.features.observe.observers.config.unsafe-values", "true"),
                entry("server.features.observe.observers.log.permit-all", "true"),
                entry("registry.marker", marker))));
    }

    private static Config isolatedConfig(String marker) {
        return Config.just(ConfigSources.create(Map.ofEntries(
                entry("declarative.ignore-incubating", "true"),
                entry("server.port", "0"),
                entry("server.features.observe.endpoint", ENDPOINT),
                entry("server.features.observe.observers-discover-services", "false"),
                entry("server.features.observe.observers.config.permit-all", "true"),
                entry("server.features.observe.observers.config.unsafe-values", "true"),
                entry("registry.marker", marker))));
    }

    private static Config manualConfig() {
        return Config.just(ConfigSources.create(Map.of(
                "declarative.ignore-incubating", "true",
                "server.port", "0",
                "server.features-discover-services", "false")));
    }

    private static WebClient client(WebServer server) {
        return WebClient.builder()
                .baseUri(URI.create("http://localhost:" + server.port()))
                .build();
    }

    private static Set<String> observerTypes(List<Observer> observers) {
        return observers.stream()
                .map(Observer::type)
                .collect(Collectors.toSet());
    }

    private static void assertConfigEndpoint(WebClient client, String expectedMarker) {
        ClientResponseTyped<JsonObject> response = client.get(ENDPOINT + "/config/values/registry.marker")
                .request(JsonObject.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity().stringValue("value").orElseThrow(), is(expectedMarker));
    }

    private static void assertStatus(ClientResponseTyped<String> response, Status expectedStatus) {
        assertThat(response.status(), is(expectedStatus));
    }

    private static final class CountingObserveProvider implements ObserveProvider {
        private int createCount;

        @Override
        public String configKey() {
            return "counting";
        }

        @Override
        public Observer create(Config config, String name) {
            throw new AssertionError("Managed observer creation must use the owning registry");
        }

        @Override
        public Observer create(Config config, String name, ServiceRegistry serviceRegistry) {
            createCount++;
            return InfoObserver.builder()
                    .name(name)
                    .endpoint(name)
                    .build();
        }
    }
}
