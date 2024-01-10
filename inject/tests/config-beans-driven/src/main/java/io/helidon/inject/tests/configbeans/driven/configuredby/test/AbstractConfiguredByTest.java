/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.configbeans.driven.configuredby.test;

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.common.config.GlobalConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MapConfigSource;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.testing.InjectionTestingSupport;
import io.helidon.inject.tests.configbeans.FakeWebServer;
import io.helidon.inject.tests.configbeans.FakeWebServer__ServiceDescriptor;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link io.helidon.inject.service.ConfigDriven}.
 */
public abstract class AbstractConfiguredByTest {
    protected static final String FAKE_SERVER_CONFIG = "fake-server";

    protected static InjectionServices injectionServices;
    protected static Services services;

    @AfterAll
    static void tearDown() {
        InjectionTestingSupport.shutdown(injectionServices);
    }

    protected MapConfigSource.Builder createRootDefault8080TestingConfigSource() {
        return ConfigSources.create(
                Map.of(
                        "server.name", "fake-server",
                        "server.port", "8080",
                        "server.worker-count", "1"
                ), "config-root-default-8080");
    }

    protected void shutDown() {
        tearDown();
    }

    protected void resetWith(Config config) {
        GlobalConfig.config(() -> config, true);

        injectionServices = InjectionServices.create(InjectionConfig.builder()
                                                             .serviceLookupCaching(true)
                                                             .config(config.get("inject"))
                                                             .serviceConfig(config)
                                                             .build());
        services = injectionServices.services();
    }

    @Test
    void testItAll() {
        shutDown();
        resetWith(io.helidon.config.Config.builder(createRootDefault8080TestingConfigSource())
                          .disableEnvironmentVariablesSource()
                          .disableSystemPropertiesSource()
                          .build());

        // verify the services registry
        testRegistry();

        Supplier<FakeWebServer> fakeWebServer = services.supply(FakeWebServer__ServiceDescriptor.INSTANCE);
        assertThat(fakeWebServer.get().isRunning(), is(true));

        Supplier<ASingletonService> singletonService =
                services.supply(ASingletonService__ServiceDescriptor.INSTANCE);
        assertThat(singletonService.get().isRunning(), is(true));

        // shutdown has to come next
        testShutdown(fakeWebServer.get());
    }

    //    @Test
    void testRegistry() {
        //        Lookup criteria = Lookup.builder()
        //                .addQualifier(Qualifier.create(ConfigDriven.class))
        //                .build();
        //        List<RegistryServiceProvider<Object>> list = services.all(criteria);
        //        List<String> desc = list.stream()
        //                .filter(it -> !it.serviceType().resolvedName().contains(".yaml."))
        //                .map(RegistryServiceProvider::description)
        //                .toList();
        //        // order matters here since it should be based upon weight
        //        assertThat("root providers are config-driven, auto-started services unless overridden to not be driven",
        //                   desc,
        //                   containsInAnyOrder("ASingletonService{root}:ACTIVE",
        //                                      "FakeTlsWSNotDrivenByCB{root}:PENDING",
        //                                      "FakeWebServer{root}:ACTIVE",
        //                                      "FakeWebServerNotDrivenAndHavingConfiguredByOverrides{root}:PENDING",
        //                                      "SomeConfiguredServiceWithAnAbstractBase{root}:PENDING"
        //                   ));
        //
        //        criteria = Lookup.builder()
        //                .addContract(FakeWebServerContract.class)
        //                .build();
        //        list = services.all(criteria);
        //        desc = list.stream().map(RegistryServiceProvider::description).collect(Collectors.toList());
        //        assertThat("no root providers expected in result, but all are auto-started unless overridden", desc,
        //                   contains("FakeWebServer{@default}:ACTIVE",
        //                            "FakeWebServerNotDrivenAndHavingConfiguredByOverrides{@default}:INIT"));
        //
        //        criteria = Lookup.builder()
        //                .serviceType(TypeName.create(FakeTlsWSNotDrivenByCB.class))
        //                .build();
        //        list = services.all(criteria);
        //        desc = list.stream().map(RegistryServiceProvider::description).collect(Collectors.toList());
        //        assertThat("root providers expected here since we looked up by service type name", desc,
        //                   contains("FakeTlsWSNotDrivenByCB{root}:PENDING"));
        //
        //        criteria = Lookup.builder()
        //                .addContract(FakeTlsWSNotDrivenByCB.class)
        //                .addQualifier(Qualifier.createNamed("*"))
        //                .build();
        //        list = services.all(criteria);
        //        desc = list.stream().map(RegistryServiceProvider::description).collect(Collectors.toList());
        //        assertThat("root providers expected here since no configuration for this service", desc,
        //                   contains("FakeTlsWSNotDrivenByCB{root}:PENDING"));
        //
        //        RegistryServiceProvider<?> fakeTlsProvider = list.getFirst();
        //        InjectionServiceProviderException e = assertThrows(InjectionServiceProviderException.class,
        //        fakeTlsProvider::get);
        //        assertThat("There is no configuration, so cannot activate this service",
        //                   e.getMessage(),
        //                   equalTo("Expected to find a match: service provider: FakeTlsWSNotDrivenByCB{root}:PENDING"));
        //
        //        criteria = Lookup.builder()
        //                .addContract(ASingletonService.class)
        //                .addQualifier(Qualifier.createNamed("jane"))
        //                .build();
        //        list = services.all(criteria);
        //        desc = list.stream().map(RegistryServiceProvider::description).collect(Collectors.toList());
        //        assertThat("Even though there is a @default, it cannot match named", list, empty());
        //
        //        criteria = Lookup.builder()
        //                .addContract(ASingletonService.class)
        //                .build();
        //        list = services.all(criteria);
        //        desc = list.stream().map(RegistryServiceProvider::description).collect(Collectors.toList());
        //        assertThat("Managed providers expected here since we have default configuration for this service", desc,
        //                   contains("ASingletonService{@default}:ACTIVE"));
    }

    //    @Test
    void testShutdown(FakeWebServer fakeWebServer) {
        assertThat(fakeWebServer.isRunning(), is(true));

        injectionServices.shutdown();

        assertThat(fakeWebServer.isRunning(), is(false));
    }
}
