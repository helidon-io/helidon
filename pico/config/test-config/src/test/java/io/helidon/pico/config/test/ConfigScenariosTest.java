/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.config.test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.config.services.ConfigBeanRegistry;
import io.helidon.pico.config.services.ConfigBeanRegistryProvider;
import io.helidon.pico.config.services.ConfigProvider;
import io.helidon.pico.config.testsubjects.ASingletonConfiguredService;
import io.helidon.pico.config.testsubjects.NonConfiguredServiceWithOptionals;
import io.helidon.pico.config.testsubjects.NonConfiguredServiceWithProviders;
import io.helidon.pico.config.testsubjects.SchemaRequiredConfiguredService;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.PicoServiceProviderException;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.testsupport.TestablePicoServices;
import io.helidon.pico.testsupport.TestableServices;

import jakarta.inject.Named;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests more advanced scenarios.
 */
public class ConfigScenariosTest {

    TestablePicoServices picoServices;
    TestableServices services;

    @BeforeEach
    public void setup() {
        picoServices = new TestablePicoServices();
        // we intentionally don't initialize services here since that will bootstrap config-driven-services
//        services = getServices();
    }

    protected TestableServices getServices() {
        if (Objects.isNull(services)) {
            services = picoServices.services();
        }
        return services;
    }

    @AfterEach
    public void tearDown() {
        NonConfiguredServiceWithProviders.ACTIVATED = false;
        NonConfiguredServiceWithOptionals.ACTIVATED = false;
        picoServices.reset();
        ConfigProvider.reset();
        ConfigBeanRegistryProvider.reset();
    }

    @Test
    public void emptyConfig() {
        Config cfg = Config.create();
        ConfigProvider.setConfigInstance(cfg);
        ConfigBeanRegistry cbr = ConfigBeanRegistryProvider.getInstance();
        assertNotNull(cbr);
        assertFalse(cbr.isReady());

        assertNotNull(getServices(), "this should initialize the config subsystem");
        assertTrue(cbr.isReady());
    }

    @Test
    public void noConfig() {
        ConfigBeanRegistry cbr = ConfigBeanRegistryProvider.getInstance();
        assertNotNull(cbr);
        assertFalse(cbr.isReady());

        assertNotNull(getServices(), "should initialize since config is available");
        assertTrue(cbr.isReady(), "default config in use");
        Config config = ConfigProvider.getConfigInstance();
        assertTrue(config.exists(), config.toString());
    }

    @Test
    public void schemaConfigPolicyEnforcement() {
        ConfigBeanRegistry cbr = ConfigBeanRegistryProvider.getInstance();
        assertNotNull(cbr);
        assertFalse(cbr.isReady());

        Config cfg = Config.create(
                ConfigSources.create(Map.of("schema-required-config.other-than-port-key", "8085"), "a")
        );
        ConfigProvider.setConfigInstance(cfg);

        PicoServiceProviderException e = assertThrows(PicoServiceProviderException.class, () -> getServices());
        assertEquals(
                "Error while initializing config bean registry: service provider: "
                        + "SchemaRequiredConfiguredService$$picoActivator{root}:io.helidon.pico.config.testsubjects"
                        + ".SchemaRequiredConfiguredService:INIT",
                e.getMessage());
        e = (PicoServiceProviderException) e.getCause();
        assertEquals("validation rules violated for interface io.helidon.pico.config.testsubjects"
                             + ".SchemaRequiredConfig with config key 'schema-required-config':\n"
                             + "'port-integer' is a required configuration for attribute 'portInteger', "
                             + "'my-simple-config' is a required configuration for attribute 'mySimpleConfig', "
                             + "'my-singleton-config' is a required configuration for attribute 'mySingletonConfig': "
                             + "service provider: SchemaRequiredConfiguredService$$picoActivator{root}:io.helidon"
                             + ".pico.config.testsubjects.SchemaRequiredConfiguredService:INIT",
                     e.getMessage());
        assertFalse(cbr.isReady(), "failed to initialize due to policy errors");
    }

    @Test
    public void fullyLoadedSchemaRequiredConfiguredService() {
        ConfigBeanRegistry cbr = ConfigBeanRegistryProvider.getInstance();
        assertNotNull(cbr);
        assertFalse(cbr.isReady());

        /**
         * Based upon this config, we expect the following config-beans to be generated:
         * 1. ASingletonConfig("@default") - since there was no default one found at the top level.
         * 2. MySchemaRequiredConfig("a.schema-required-config") with the following sub-injections:
         * 3. MySchemaRequiredConfig("a.schema-required-config.0")
         * 4. MySimpleConfig("a.schema-required-config.0.my-simple-config")
         * 5. ASingletonConfig("a.schema-required-config.0.my-singleton-config")
         * 6. MySimpleConfig("a.schema-required-config.my-simple-config") w/ port 99
         * 7. ASingletonConfig("a.schema-required-config.my-singleton-config") w/ port 99
         */
        Config cfg = Config.create(
                ConfigSources.create(Map.of("a.schema-required-config.port", "1",
                                            "a.schema-required-config.port-integer", "2",
                                            "a.schema-required-config.strings.0", "hello",
                                            "a.schema-required-config.my-simple-config.port", "10",
                                            "a.schema-required-config.my-singleton-config.port", "20",
                                            "a.schema-required-config.my-singleton-config.password", "pwd",
                                            "a.schema-required-config.0.port", "30",
                                            "a.schema-required-config.0.port-integer", "31",
                                            "a.schema-required-config.0.my-simple-config.port", "40",
                                            "a.schema-required-config.0.my-singleton-config.port", "41"
                                            ),
                                     "a")
        );
        ConfigProvider.setConfigInstance(cfg);

        assertNotNull(getServices(), "this should initialize the config subsystem");
        assertTrue(cbr.isReady());

        Map<String, ?> allConfigBeans = cbr.getAllConfigBeans();
        Iterator<? extends Map.Entry<String, ?>> iter = allConfigBeans.entrySet().iterator();
        Map.Entry<String, ?> e = iter.next();

        // 1..
        assertEquals("@default", e.getKey(), allConfigBeans.toString());
        assertEquals(
                "DefaultASingletonConfig{@default}(port=8080, hostAddress=127.0.0.1, password=null, "
                        + "theSimpleConfig=null, listOfSimpleConfig=[], setOfSimpleConfig=[], mapOfSimpleConfig={}, "
                        + "theSingletonConfig=null, listOfSingletonConfigConfig=[], setOfSingletonConfig=[], "
                        + "mapOfSingletonConfig={})",
                     e.getValue().toString(), e.toString());

        // 2..
        assertTrue(iter.hasNext());
        e = iter.next();
        assertEquals("a.schema-required-config", e.getKey(), allConfigBeans.toString());
        assertEquals(
                "DefaultSchemaRequiredConfig{a.schema-required-config}(port=1, optionalPort=Optional[2], "
                        + "portInteger=2, mySimpleConfig=DefaultMySimpleConfig{a.schema-required-config"
                        + ".my-simple-config}(port=10), mySimpleConfigSet=[DefaultMySimpleConfig{a"
                        + ".schema-required-config.my-simple-config}(port=10)], "
                        + "mySimpleConfigList=[DefaultMySimpleConfig{a.schema-required-config.my-simple-config}"
                        + "(port=10)], mySimpleConfigMap={a.schema-required-config"
                        + ".my-simple-config=DefaultMySimpleConfig{a.schema-required-config.my-simple-config}"
                        + "(port=10)}, mySingletonConfigSet=[DefaultASingletonConfig{a.schema-required-config"
                        + ".my-singleton-config}(port=20, hostAddress=127.0.0.1, password=not-null, "
                        + "theSimpleConfig=null, listOfSimpleConfig=[], setOfSimpleConfig=[], mapOfSimpleConfig={}, "
                        + "theSingletonConfig=null, listOfSingletonConfigConfig=[], setOfSingletonConfig=[], "
                        + "mapOfSingletonConfig={})], mySingletonConfig=Optional[DefaultASingletonConfig{a"
                        + ".schema-required-config.my-singleton-config}(port=20, hostAddress=127.0.0.1, "
                        + "password=not-null, theSimpleConfig=null, listOfSimpleConfig=[], setOfSimpleConfig=[], "
                        + "mapOfSimpleConfig={}, theSingletonConfig=null, listOfSingletonConfigConfig=[], "
                        + "setOfSingletonConfig=[], mapOfSingletonConfig={})], arrayOfStrings=[hello])",
                e.getValue().toString(), e.toString());

        // 3..
        assertTrue(iter.hasNext());
        e = iter.next();
        assertEquals("a.schema-required-config.0", e.getKey(), allConfigBeans.toString());
        assertEquals(
                "DefaultSchemaRequiredConfig{a.schema-required-config.0}(port=30, optionalPort=Optional[31], "
                        + "portInteger=31, mySimpleConfig=DefaultMySimpleConfig{a.schema-required-config.0"
                        + ".my-simple-config}(port=40), mySimpleConfigSet=[DefaultMySimpleConfig{a"
                        + ".schema-required-config.0.my-simple-config}(port=40)], "
                        + "mySimpleConfigList=[DefaultMySimpleConfig{a.schema-required-config.0.my-simple-config}"
                        + "(port=40)], mySimpleConfigMap={a.schema-required-config.0"
                        + ".my-simple-config=DefaultMySimpleConfig{a.schema-required-config.0.my-simple-config}"
                        + "(port=40)}, mySingletonConfigSet=[DefaultASingletonConfig{a.schema-required-config.0"
                        + ".my-singleton-config}(port=41, hostAddress=127.0.0.1, password=null, theSimpleConfig=null,"
                        + " listOfSimpleConfig=[], setOfSimpleConfig=[], mapOfSimpleConfig={}, "
                        + "theSingletonConfig=null, listOfSingletonConfigConfig=[], setOfSingletonConfig=[], "
                        + "mapOfSingletonConfig={})], mySingletonConfig=Optional[DefaultASingletonConfig{a"
                        + ".schema-required-config.0.my-singleton-config}(port=41, hostAddress=127.0.0.1, "
                        + "password=null, theSimpleConfig=null, listOfSimpleConfig=[], setOfSimpleConfig=[], "
                        + "mapOfSimpleConfig={}, theSingletonConfig=null, listOfSingletonConfigConfig=[], "
                        + "setOfSingletonConfig=[], mapOfSingletonConfig={})], arrayOfStrings=null)",
                e.getValue().toString(), e.toString());

        // 4..
        assertTrue(iter.hasNext());
        e = iter.next();
        assertEquals("a.schema-required-config.0.my-simple-config", e.getKey(), allConfigBeans.toString());
        assertEquals(
                "DefaultMySimpleConfig{a.schema-required-config.0.my-simple-config}(port=40)",
                e.getValue().toString(), e.toString());

        // 5..
        assertTrue(iter.hasNext());
        e = iter.next();
        assertEquals("a.schema-required-config.0.my-singleton-config", e.getKey(), allConfigBeans.toString());
        assertEquals(
                "DefaultASingletonConfig{a.schema-required-config.0.my-singleton-config}(port=41, hostAddress=127.0"
                        + ".0.1, password=null, theSimpleConfig=null, listOfSimpleConfig=[], setOfSimpleConfig=[], "
                        + "mapOfSimpleConfig={}, theSingletonConfig=null, listOfSingletonConfigConfig=[], "
                        + "setOfSingletonConfig=[], mapOfSingletonConfig={})",
                e.getValue().toString(), e.toString());

        // 6..
        assertTrue(iter.hasNext());
        e = iter.next();
        assertEquals("a.schema-required-config.my-simple-config", e.getKey(), allConfigBeans.toString());
        assertEquals(
                "DefaultMySimpleConfig{a.schema-required-config.my-simple-config}(port=10)",
                e.getValue().toString(), e.toString());

        // 7..
        assertTrue(iter.hasNext());
        e = iter.next();
        assertEquals("a.schema-required-config.my-singleton-config", e.getKey(), allConfigBeans.toString());
        assertEquals(
                "DefaultASingletonConfig{a.schema-required-config.my-singleton-config}(port=20, hostAddress=127.0.0"
                        + ".1, password=not-null, theSimpleConfig=null, listOfSimpleConfig=[], setOfSimpleConfig=[], "
                        + "mapOfSimpleConfig={}, theSingletonConfig=null, listOfSingletonConfigConfig=[], "
                        + "setOfSingletonConfig=[], mapOfSingletonConfig={})",
                e.getValue().toString(), e.toString());

        assertFalse(iter.hasNext());

        // the previous was verifying all the config beans created.
        // below we will now look at the service registry side of it

        List<ServiceProvider<SchemaRequiredConfiguredService>> services = getServices()
                .lookup(SchemaRequiredConfiguredService.class);
        assertEquals(
                "[SchemaRequiredConfiguredService$$picoActivator{a.schema-required-config}:io.helidon.pico.config"
                        + ".testsubjects.SchemaRequiredConfiguredService:PENDING, "
                        + "SchemaRequiredConfiguredService$$picoActivator{a.schema-required-config.0}:io.helidon.pico"
                        + ".config.testsubjects.SchemaRequiredConfiguredService:PENDING]",
                String.valueOf(ServiceProvider.toDescriptions(services)));

        TypeName named = DefaultTypeName.create(Named.class);

        ServiceProvider<SchemaRequiredConfiguredService> sp1 = services.get(0);
        assertEquals("a.schema-required-config",
             DefaultQualifierAndValue.findFirst(named, sp1.serviceInfo().qualifiers()).orElseThrow().value().get());
        ServiceProvider<SchemaRequiredConfiguredService> sp2 = services.get(1);
        assertEquals("a.schema-required-config.0",
             DefaultQualifierAndValue.findFirst(named, sp2.serviceInfo().qualifiers()).orElseThrow().value().get());
    }

    @Test
    public void nonConfiguredServiceWithConfiguredServiceInjectionPoints() {
        Config cfg = Config.create();
        ConfigProvider.setConfigInstance(cfg);

        TestableServices services = getServices();

        ConfigBeanRegistry cbr = ConfigBeanRegistryProvider.getInstance();
        assertNotNull(cbr);
        assertTrue(cbr.isReady());

        List<ServiceProvider<NonConfiguredServiceWithProviders>> providers =
                services.lookup(NonConfiguredServiceWithProviders.class);
        assertNotNull(providers);
        assertEquals("[NonConfiguredServiceWithProviders$$picoActivator:INIT:[]]",
                     String.valueOf(ServiceProvider.toDescription(providers)));
        assertFalse(NonConfiguredServiceWithProviders.ACTIVATED);

        NonConfiguredServiceWithProviders service = providers.get(0).get();
        assertNotNull(service);
        assertTrue(NonConfiguredServiceWithProviders.ACTIVATED);
        // notice this one binds to root since there is no simple config service created by default
        assertEquals(
                "UnconfiguredServiceProvider{MySimpleConfiguredService$$picoActivator{root}}:io.helidon.pico.config"
                        + ".testsubjects.MySimpleConfiguredService:PENDING",
                String.valueOf(ServiceProvider.toDescription(service.providerSimpleConfiguredService)));
        assertNull(service.providerSimpleConfiguredService.get(), "should not be able to resolve this provider");
        assertEquals(
                "ASingletonConfiguredService$$picoActivator{@default}:io.helidon.pico.config.testsubjects"
                        + ".ASingletonConfiguredService:PENDING",
                String.valueOf(ServiceProvider.toDescription(service.providerSingletonConfiguredService)));
        ASingletonConfiguredService activated = service.providerSingletonConfiguredService.get();
        assertNotNull(activated);
        assertSame(activated, service.providerSingletonConfiguredService.get());
        assertEquals(
                "ASingletonConfiguredService$$picoActivator{@default}:io.helidon.pico.config.testsubjects"
                        + ".ASingletonConfiguredService:ACTIVE",
                String.valueOf(ServiceProvider.toDescription(service.providerSingletonConfiguredService)));

        List<ServiceProvider<NonConfiguredServiceWithOptionals>> optionals =
                services.lookup(NonConfiguredServiceWithOptionals.class);
        assertNotNull(providers);
        assertEquals("[NonConfiguredServiceWithProviders$$picoActivator:ACTIVE:[]]",
                     String.valueOf(ServiceProvider.toDescription(providers)));
        assertFalse(NonConfiguredServiceWithOptionals.ACTIVATED);
        NonConfiguredServiceWithOptionals service2 = optionals.get(0).get();
        assertNotNull(service2);
        assertSame(service2, optionals.get(0).get());
        assertTrue(NonConfiguredServiceWithOptionals.ACTIVATED);
    }

    /**
     * The point of this is to ensure contextual configuration and service injection works as expected.
     *
     * The scenario is to create four different client-server configurations:
     * <li> 1. a config that has both client and server (named "both")
     * <li> 2. a config that has just server (named "server")
     * <li> 3. a config that has just client (named "client")
     * <li> 4. a config that has neither client nor server (named "none")
     *
     * Note also that client-and-server is a @default service type, so when no config is available we should have "none"
     */
    @Test
    public void clientAndServers() {

    }

}
