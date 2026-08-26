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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.FactoryType;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.InterceptionMetadata;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
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
    private static final AtomicInteger FACTORY_CREATE_COUNT = new AtomicInteger();
    private static final AtomicInteger PROVIDER_CREATE_COUNT = new AtomicInteger();

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

    @Test
    void configBackedFeatureIsCreatedFromFinalConfigOnly() {
        ServiceRegistry previousGlobal = GlobalServiceRegistry.registry();
        FACTORY_CREATE_COUNT.set(0);
        PROVIDER_CREATE_COUNT.set(0);
        Config registryConfig = Config.just(ConfigSources.create(Map.of(
                "server.features.registry-feature.value", "registry")));
        Config finalConfig = Config.just(ConfigSources.create(Map.of(
                "server.features.registry-feature.value", "final")));
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Config.class,
                                                                                                     registryConfig)
                                                                                .addServiceDescriptor(
                                                                                        TestFeatureFactoryDescriptor.INSTANCE)
                                                                                .addServiceDescriptor(
                                                                                        TestFeatureProviderDescriptor.INSTANCE)
                                                                                .build());
        try {
            GlobalServiceRegistry.registry(manager.registry());
            WebServerConfig.Builder builder = WebServer.builder()
                    .config(finalConfig.get("server"));

            JunitExtensionBase.setupWebServerFromRegistry(builder);

            assertThat(FACTORY_CREATE_COUNT.get(), is(0));
            assertThat(PROVIDER_CREATE_COUNT.get(), is(0));

            WebServerConfig serverConfig = builder.buildPrototype();
            TestFeature feature = serverConfig.features()
                    .stream()
                    .filter(TestFeature.class::isInstance)
                    .map(TestFeature.class::cast)
                    .findFirst()
                    .orElseThrow();

            assertThat(FACTORY_CREATE_COUNT.get(), is(0));
            assertThat(PROVIDER_CREATE_COUNT.get(), is(1));
            assertThat(feature.value(), is("final"));
        } finally {
            manager.shutdown();
            GlobalServiceRegistry.registry(previousGlobal);
        }
    }

    private static final class TestFeature implements ServerFeature {
        private static final String TYPE = "registry-feature";
        private final String value;

        private TestFeature() {
            this("explicit");
        }

        private TestFeature(String value) {
            this.value = value;
        }

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

        private String value() {
            return value;
        }
    }

    private static final class TestFeatureFactory implements Service.ServicesFactory<TestFeature> {
        @Override
        public List<Service.QualifiedInstance<TestFeature>> services() {
            FACTORY_CREATE_COUNT.incrementAndGet();
            return List.of(Service.QualifiedInstance.create(new TestFeature("registry"),
                                                            Qualifier.createNamed(TestFeature.TYPE)));
        }
    }

    private static final class TestFeatureProvider implements ServerFeatureProvider<TestFeature> {
        @Override
        public String configKey() {
            return TestFeature.TYPE;
        }

        @Override
        public TestFeature create(Config config, String name) {
            PROVIDER_CREATE_COUNT.incrementAndGet();
            return new TestFeature(config.get("value").asString().orElse("missing"));
        }
    }

    private static final class TestFeatureFactoryDescriptor implements ServiceDescriptor<TestFeatureFactory> {
        private static final TestFeatureFactoryDescriptor INSTANCE = new TestFeatureFactoryDescriptor();
        private static final TypeName SERVICE_TYPE = TypeName.create(TestFeatureFactory.class);
        private static final TypeName PROVIDED_TYPE = TypeName.create(TestFeature.class);
        private static final TypeName DESCRIPTOR_TYPE = TypeName.create(TestFeatureFactoryDescriptor.class);
        private static final TypeName FACTORY_CONTRACT = TypeName.builder(TypeName.create(Service.ServicesFactory.class))
                .addTypeArgument(PROVIDED_TYPE)
                .build();
        private static final Set<ResolvedType> CONTRACTS = Set.of(ResolvedType.create(PROVIDED_TYPE),
                                                                  ResolvedType.create(ServerFeature.class));
        private static final Set<ResolvedType> FACTORY_CONTRACTS = Set.of(ResolvedType.create(FACTORY_CONTRACT));

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            return new TestFeatureFactory();
        }

        @Override
        public TypeName serviceType() {
            return SERVICE_TYPE;
        }

        @Override
        public TypeName providedType() {
            return PROVIDED_TYPE;
        }

        @Override
        public TypeName descriptorType() {
            return DESCRIPTOR_TYPE;
        }

        @Override
        public Set<ResolvedType> contracts() {
            return CONTRACTS;
        }

        @Override
        public Set<ResolvedType> factoryContracts() {
            return FACTORY_CONTRACTS;
        }

        @Override
        public FactoryType factoryType() {
            return FactoryType.SERVICES;
        }
    }

    private static final class TestFeatureProviderDescriptor implements ServiceDescriptor<TestFeatureProvider> {
        private static final TestFeatureProviderDescriptor INSTANCE = new TestFeatureProviderDescriptor();
        private static final TypeName SERVICE_TYPE = TypeName.create(TestFeatureProvider.class);
        private static final TypeName DESCRIPTOR_TYPE = TypeName.create(TestFeatureProviderDescriptor.class);
        private static final Set<ResolvedType> CONTRACTS = Set.of(
                ResolvedType.create(ServerFeatureProvider.class),
                ResolvedType.create(TypeName.builder(TypeName.create(ServerFeatureProvider.class))
                                            .addTypeArgument(TypeName.create(TestFeature.class))
                                            .build()));

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            return new TestFeatureProvider();
        }

        @Override
        public TypeName serviceType() {
            return SERVICE_TYPE;
        }

        @Override
        public TypeName descriptorType() {
            return DESCRIPTOR_TYPE;
        }

        @Override
        public Set<ResolvedType> contracts() {
            return CONTRACTS;
        }
    }
}
