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

package io.helidon.webserver.testing.junit5;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.spi.ServerFeatureProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

@Isolated
class JunitExtensionBaseTest {
    @Test
    void registryFeatureTakesPrecedenceOverMatchingProvider() {
        ServiceRegistry previousGlobal = GlobalServiceRegistry.registry();
        AtomicInteger providerCreateCount = new AtomicInteger();
        TestFeature registryFeature = new TestFeature();
        ServerFeatureProvider<TestFeature> provider = new ServerFeatureProvider<>() {
            @Override
            public String configKey() {
                return TestFeature.TYPE;
            }

            @Override
            public TestFeature create(Config config, String name) {
                providerCreateCount.incrementAndGet();
                return new TestFeature();
            }
        };
        Config config = Config.just(ConfigSources.create(Map.of("server.features.registry-feature.enabled", "true")));
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Config.class, config)
                                                                                .putContractInstance(ServerFeature.class,
                                                                                                     registryFeature)
                                                                                .putContractInstance(ServerFeatureProvider.class,
                                                                                                     provider)
                                                                                .build());
        try {
            GlobalServiceRegistry.registry(manager.registry());
            WebServerConfig.Builder builder = WebServer.builder()
                    .config(config.get("server"));

            JunitExtensionBase.setupWebServerFromRegistry(builder);
            WebServerConfig serverConfig = builder.buildPrototype();

            assertThat(serverConfig.features(), hasItem(sameInstance(registryFeature)));
            assertThat(providerCreateCount.get(), is(0));
        } finally {
            manager.shutdown();
            GlobalServiceRegistry.registry(previousGlobal);
        }
    }

    private static final class TestFeature implements ServerFeature {
        private static final String TYPE = "registry-feature";

        @Override
        public void setup(ServerFeatureContext featureContext) {
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String name() {
            return TYPE;
        }
    }
}
