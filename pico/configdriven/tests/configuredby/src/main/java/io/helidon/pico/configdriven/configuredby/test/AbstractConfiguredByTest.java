/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.configdriven.configuredby.test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.builder.config.spi.ConfigBeanRegistryHolder;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MapConfigSource;
import io.helidon.pico.api.Phase;
import io.helidon.pico.api.PicoServiceProviderException;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.PicoServicesConfig;
import io.helidon.pico.api.QualifierAndValueDefault;
import io.helidon.pico.api.ServiceInfoCriteriaDefault;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.Services;
import io.helidon.pico.configdriven.api.ConfiguredBy;
import io.helidon.pico.configdriven.runtime.ConfigBeanRegistry;
import io.helidon.pico.testing.PicoTestingSupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.testing.PicoTestingSupport.testableServices;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link io.helidon.pico.configdriven.api.ConfiguredBy}.
 */
public abstract class AbstractConfiguredByTest {
    protected static final String FAKE_SOCKET_CONFIG = "sockets";
    protected static final String FAKE_SERVER_CONFIG = "fake-server";

    protected PicoServices picoServices;
    protected Services services;

    @BeforeAll
    static void initialStateChecks() {
        ConfigBeanRegistry cbr = (ConfigBeanRegistry) ConfigBeanRegistryHolder.configBeanRegistry().orElseThrow();
        assertThat(cbr.ready(), is(false));
    }

    @AfterAll
    static void tearDown() {
        PicoTestingSupport.resetAll();
    }

    protected void resetWith(Config config) {
        PicoTestingSupport.resetAll();
        this.picoServices = testableServices(config);
        this.services = picoServices.services();
    }

    public MapConfigSource.Builder createBasicTestingConfigSource() {
        return ConfigSources.create(
                Map.of(
                        PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_PERMITS_DYNAMIC, "true",
                        PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_ACTIVATION_LOGS, "true",
                        PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_SERVICE_LOOKUP_CACHING, "true"
                ), "config-basic");
    }

    public MapConfigSource.Builder createRootDefault8080TestingConfigSource() {
        return ConfigSources.create(
                Map.of(
                        FAKE_SERVER_CONFIG + ".name", "fake-server",
                        FAKE_SERVER_CONFIG + ".port", "8080",
                        FAKE_SERVER_CONFIG + ".worker-count", "1"
                ), "config-root-default-8080");
    }

    @Test
    void testItAll() {
        resetWith(io.helidon.config.Config.builder(createBasicTestingConfigSource(), createRootDefault8080TestingConfigSource())
                          .disableEnvironmentVariablesSource()
                          .disableSystemPropertiesSource()
                          .build());

        // verify the services registry
        testRegistry();

        ServiceProvider<FakeWebServer> fakeWebServer = services.lookup(FakeWebServer.class);
        assertThat(fakeWebServer.currentActivationPhase(), is(Phase.ACTIVE));
        assertThat(fakeWebServer.get().isRunning(), is(true));

        ServiceProvider<ASingletonService> singletonService = services.lookup(ASingletonService.class);
        assertThat(singletonService.currentActivationPhase(), is(Phase.ACTIVE));
        assertThat(singletonService.get().isRunning(), is(true));

        // verify the bean registry
        testBeanRegistry();

        // shutdown has to come next
        testShutdown(fakeWebServer.get());
    }

    //    @Test
    void testRegistry() {
        ServiceInfoCriteriaDefault criteria = ServiceInfoCriteriaDefault.builder()
                .addQualifier(QualifierAndValueDefault.create(ConfiguredBy.class))
                .build();
        List<ServiceProvider<?>> list = services.lookupAll(criteria);
        List<String> desc = list.stream()
                .filter(it -> !it.serviceInfo().serviceTypeName().contains(".yaml."))
                .map(ServiceProvider::description)
                .collect(Collectors.toList());
        // order matters here since it should be based upon weight
        assertThat("root providers are config-driven, auto-started services unless overridden to not be driven", desc,
                   contains("ASingletonService{root}:ACTIVE",
                            "FakeTlsWSNotDrivenByCB{root}:PENDING",
                            "FakeWebServer{root}:ACTIVE",
                            "FakeWebServerNotDrivenAndHavingConfiguredByOverrides{root}:PENDING",
                            "SomeConfiguredServiceWithAnAbstractBase{root}:PENDING"
                   ));

        criteria = ServiceInfoCriteriaDefault.builder()
                .addContractImplemented(FakeWebServerContract.class.getName())
                .build();
        list = services.lookupAll(criteria);
        desc = list.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat("no root providers expected in result, but all are auto-started unless overridden", desc,
                   contains("FakeWebServer{fake-server}:ACTIVE",
                            "FakeWebServerNotDrivenAndHavingConfiguredByOverrides{fake-server}:PENDING"));

        criteria = ServiceInfoCriteriaDefault.builder()
                .serviceTypeName(FakeTlsWSNotDrivenByCB.class.getName())
                .build();
        list = services.lookupAll(criteria);
        desc = list.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat("root providers expected here since we looked up by service type name", desc,
                   contains("FakeTlsWSNotDrivenByCB{root}:PENDING"));

        criteria = ServiceInfoCriteriaDefault.builder()
                .addContractImplemented(FakeTlsWSNotDrivenByCB.class.getName())
                .addQualifier(QualifierAndValueDefault.createNamed("*"))
                .build();
        list = services.lookupAll(criteria);
        desc = list.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat("root providers expected here since no configuration for this service", desc,
                   contains("FakeTlsWSNotDrivenByCB{root}:PENDING"));

        ServiceProvider<?> fakeTlsProvider = list.get(0);
        PicoServiceProviderException e = assertThrows(PicoServiceProviderException.class, fakeTlsProvider::get);
        assertThat("There is no configuration, so cannot activate this service", e.getMessage(),
                   equalTo("Expected to find a match: service provider: FakeTlsWSNotDrivenByCB{root}:PENDING"));

        criteria = ServiceInfoCriteriaDefault.builder()
                .addContractImplemented(ASingletonService.class.getName())
                .addQualifier(QualifierAndValueDefault.createNamed("jane"))
                .build();
        list = services.lookupAll(criteria);
        desc = list.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat("Slave providers expected here since we have default configuration for this service", desc,
                   contains("ASingletonService{@default}:ACTIVE"));
    }

    //    @Test
    void testShutdown(FakeWebServer fakeWebServer) {
        assertThat(fakeWebServer.isRunning(), is(true));

        picoServices.shutdown();

        assertThat(fakeWebServer.isRunning(), is(false));
    }

    //    @Test
    void testBeanRegistry() {
        ConfigBeanRegistry cbr = (ConfigBeanRegistry) ConfigBeanRegistryHolder.configBeanRegistry().orElseThrow();
        assertThat(cbr.ready(), is(true));

        Set<String> set = cbr.allConfigBeans().keySet();
        assertThat(set, containsInAnyOrder(
                "@default",
                "fake-server"
        ));
    }

}
