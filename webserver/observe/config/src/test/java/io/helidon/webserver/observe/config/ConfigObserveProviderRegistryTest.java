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

package io.helidon.webserver.observe.config;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.spi.Observer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ConfigObserveProviderRegistryTest {
    private static final AtomicBoolean DISABLED_CONFIG_RESOLVED = new AtomicBoolean();

    @Test
    void managedObserverUsesOwningRegistryConfig() {
        Config config = Config.just(ConfigSources.create(Map.of("registry.source", "owning-registry")));
        ServiceRegistryManager registryManager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                       .discoverServices(false)
                                                                                       .discoverServicesFromServiceLoader(false)
                                                                                       .putContractInstance(Config.class, config)
                                                                                       .build());

        Config observerConfig = Config.just(ConfigSources.create(Map.of("endpoint", "managed-config",
                                                                         "permit-all", "true",
                                                                         "unsafe-values", "true")));
        Observer managedObserver = new ConfigObserveProvider().create(observerConfig,
                                                                      "managed",
                                                                      registryManager.registry());
        WebServer server = WebServer.builder()
                .port(0)
                .featuresDiscoverServices(false)
                .addFeature(ObserveFeature.just(managedObserver))
                .build();
        try {
            server.start();
            WebClient client = WebClient.builder()
                    .baseUri(URI.create("http://localhost:" + server.port()))
                    .build();
            ClientResponseTyped<JsonObject> response = client.get("/observe/managed-config/values/registry.source")
                    .request(JsonObject.class);

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().stringValue("value").orElseThrow(), is("owning-registry"));
        } finally {
            server.stop();
            registryManager.shutdown();
        }
    }

    @Test
    void disabledObserverDoesNotResolveConfig() {
        var disabledConfig = ConfigObserver.builder()
                .enabled(false)
                .endpoint("disabled")
                .name("disabled")
                .buildPrototype();
        Observer disabledObserver = ConfigObserver.create(disabledConfig, () -> {
            DISABLED_CONFIG_RESOLVED.set(true);
            return Config.empty();
        });

        disabledObserver.register(null, List.of(), endpoint -> endpoint);

        assertThat(DISABLED_CONFIG_RESOLVED.get(), is(false));
    }
}
