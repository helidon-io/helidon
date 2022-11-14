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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.config.api.ConfiguredBy;
import io.helidon.pico.config.services.AbstractConfiguredServiceProvider;
import io.helidon.pico.config.services.ConfigBeanRegistry;
import io.helidon.pico.config.services.ConfigBeanRegistryProvider;
import io.helidon.pico.config.services.ConfigProvider;
import io.helidon.pico.config.services.ConfiguredServiceProvider;
import io.helidon.pico.config.spi.MetaConfigBeanInfo;
import io.helidon.pico.config.testsubjects.ASingletonConfig;
import io.helidon.pico.config.testsubjects.ASingletonConfiguredService;
import io.helidon.pico.config.testsubjects.ContractA;
import io.helidon.pico.config.testsubjects.ContractB;
import io.helidon.pico.config.testsubjects.MySimpleConfig;
import io.helidon.pico.config.testsubjects.MySimpleConfiguredService;
import io.helidon.pico.config.testsubjects.NonConfiguredServiceWithOptionals;
import io.helidon.pico.config.testsubjects.NonConfiguredServiceWithProviders;
import io.helidon.pico.ActivationPhase;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.spi.ext.ServiceProviderComparator;
import io.helidon.pico.testsupport.TestablePicoServices;
import io.helidon.pico.testsupport.TestableServices;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the basics.
 */
public class ConfigDrivenServicesTest {

    TestablePicoServices picoServices;
    TestableServices services;

    @BeforeEach
    public void setup() {
        Config cfg = Config.create(
                ConfigSources.create(
                        Map.of("my-simple-config.1.port", "8085"),
                        "my-simple-config-1"),
                ConfigSources.create(
                        Map.of("my-simple-config.2.port", "8086", "my-simple-config.2.extra", "abc"),
                        "my-simple-config-2"),
                ConfigSources.create(
                        Map.of("my-simple-config.0.port", "8087"),
                        "my-simple-config-0"),
                ConfigSources.create(
                        Map.of("my-simple-config.port", "8084"),
                        "my-simple-config")
                );
        ConfigProvider.setConfigInstance(cfg);

//        DefaultMySimpleConfig.builder().build();
//        DefaultMySimpleConfig.toBuilder(cfg).build()
        picoServices = new TestablePicoServices();
        services = getServices();
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
    public void serviceRegistry() {
        // this does include configured-by and with no value, therefore we will only see the root in the result...
        List<ServiceProvider<Object>> list = services
                .lookup(DefaultServiceInfo.builder()
                                .qualifier(DefaultQualifierAndValue.create(ConfiguredBy.class))
                                .build())
                .stream()
                .filter(it -> it.serviceInfo().serviceTypeName().contains("Service"))
                .collect(Collectors.toList());
        assertEquals(
                "[MySimpleConfiguredService$$picoActivator{root}:io.helidon.pico.config.testsubjects"
                        + ".MySimpleConfiguredService:ACTIVE, ASingletonConfiguredService$$picoActivator{root}:io"
                        + ".helidon.pico.config.testsubjects.ASingletonConfiguredService:PENDING, "
                        + "UnconfiguredServiceProvider{SchemaRequiredConfiguredService$$picoActivator{root}}:io"
                        + ".helidon.pico.config.testsubjects.SchemaRequiredConfiguredService:PENDING]",
                String.valueOf(ServiceProvider.toDescriptions(list)),
                "config-driven services should be active when driveActivation is set, where others should be pending");
        ServiceProvider singletonConfiguredService = list.get(1);
        assertEquals(
                "[DefaultQualifierAndValue(typeName=io.helidon.pico.config.api.ConfiguredBy, value=io.helidon.pico"
                        + ".config.testsubjects.ASingletonConfig), DefaultQualifierAndValue(typeName=jakarta.inject"
                        + ".Named, value=*)]",
                singletonConfiguredService.serviceInfo().qualifiers().toString(), "name should be wildcard");

        // this does not include configured-by, therefore we will not see the root provider in the result...
        list = services
                .lookup(DefaultServiceInfo.builder()
                                .contractTypeImplemented(ContractB.class)
                                .build());
        assertEquals(
                "[MySimpleConfiguredService$$picoActivator{my-simple-config}:io.helidon.pico.config.testsubjects"
                        + ".MySimpleConfiguredService:ACTIVE, "
                        + "MySimpleConfiguredService$$picoActivator{my-simple-config.0}:io.helidon.pico.config"
                        + ".testsubjects.MySimpleConfiguredService:ACTIVE, "
                        + "MySimpleConfiguredService$$picoActivator{my-simple-config.1}:io.helidon.pico.config"
                        + ".testsubjects.MySimpleConfiguredService:ACTIVE, "
                        + "MySimpleConfiguredService$$picoActivator{my-simple-config.2}:io.helidon.pico.config"
                        + ".testsubjects.MySimpleConfiguredService:ACTIVE, "
                        + "ASingletonConfiguredService$$picoActivator{@default}:io.helidon.pico.config.testsubjects"
                        + ".ASingletonConfiguredService:PENDING]",
                String.valueOf(ServiceProvider.toDescriptions(list)),
         "config-driven services should be active when driveActivation is set, where others should be pending");
        ServiceProvider<?> sp = list.get(0);
        assertEquals(
                "[DefaultQualifierAndValue(typeName=io.helidon.pico.config.api.ConfiguredBy, value=io.helidon.pico"
                        + ".config.testsubjects.MySimpleConfig), DefaultQualifierAndValue(typeName=jakarta.inject"
                        + ".Named, value=my-simple-config)]",
                sp.serviceInfo().qualifiers().toString(), "name should not be wildcard");

        // search by name...
        list = services
                .lookup(DefaultServiceInfo.builder()
                                .contractImplemented(ContractA.class.getName())
                                .named("my-simple-config.1")
                                .build());
        assertEquals("[MySimpleConfiguredService$$picoActivator{my-simple-config.1}]",
                     String.valueOf(ServiceProvider.toIdentities(list)));

        // the config-driven services should already be active (i.e., MySimpleConfig instances but not ASingletonConfig)
        sp = list.get(0);
        assertEquals(ActivationPhase.ACTIVE, sp.currentActivationPhase());
        Object service = sp.get();
        assertNotNull(service);
        assertSame(MySimpleConfiguredService.class, service.getClass());
        assertEquals(1, ((MySimpleConfiguredService) service).getActivationCount());
        // calling get again should have no effect on PostConstruct count...
        Object service2 = sp.get();
        assertSame(service, service2);
        assertEquals(1, ((MySimpleConfiguredService) service).getActivationCount());

        // finally, activate singletonConfiguredService since this is still PENDING...
        assertEquals(ActivationPhase.PENDING, singletonConfiguredService.currentActivationPhase());
        ASingletonConfiguredService s1 = (ASingletonConfiguredService) singletonConfiguredService.get();
        assertNotNull(s1);
        assertEquals(ActivationPhase.ACTIVE, singletonConfiguredService.currentActivationPhase());
        assertEquals(1, s1.getActivationCount());
        service2 = singletonConfiguredService.get();
        assertSame(s1, service2);
        assertEquals(1, s1.getActivationCount());
        assertNotNull(s1.getConfig());
        assertEquals("DefaultASingletonConfig{@default}",
                     ConfigBeanTest.toSBeanDescription(s1.getConfig()));
    }

    @Test
    public void beanRegistry() {
        ConfigBeanRegistry cbr = ConfigBeanRegistryProvider.getInstance();
        assertNotNull(cbr);

        Map<ConfiguredServiceProvider<?, ?>, MetaConfigBeanInfo>
                configurableProviders = (Map) cbr.getConfigurableServiceProviders();
        assertNotNull(configurableProviders);
        assertEquals(6, configurableProviders.size(), configurableProviders.toString());

        Map<ConfiguredServiceProvider<?, ?>, MetaConfigBeanInfo> sorted
                = new TreeMap<>(new ServiceProviderComparator());
        sorted.putAll(configurableProviders);
        LinkedHashMap<String, MetaConfigBeanInfo<?>> orderedMap = new LinkedHashMap<>();
        sorted.entrySet().stream()
                .filter(entry -> {
                    String key = entry.getKey().getClass().getSimpleName();
                    return key.startsWith("My") || key.startsWith("ASingle");
                })
                .forEach(e -> orderedMap.put(e.getKey().serviceInfo().serviceTypeName(), e.getValue()));
        assertEquals(
                "{io.helidon.pico.config.testsubjects.MySimpleConfiguredService=MetaConfigBeanInfo"
                        + "(drivesActivation=true, defaultConfigBeanUsingDefaults=false, repeatable=true, "
                        + "key=my-simple-config), io.helidon.pico.config.testsubjects"
                        + ".ASingletonConfiguredService=MetaConfigBeanInfo(drivesActivation=false, "
                        + "defaultConfigBeanUsingDefaults=true, repeatable=false, key=my-singleton-config)}",
                     orderedMap.toString(), sorted.toString());
    }

    @Test
    public void mySimpleConfiguredServiceToConfigBean() {
        ConfiguredServiceProvider<MySimpleConfiguredService, MySimpleConfig> csp =
                (ConfiguredServiceProvider<MySimpleConfiguredService, MySimpleConfig>) picoServices.services()
                        .lookupFirst(MySimpleConfiguredService.class);
        assertNotNull(csp);
        ((AbstractConfiguredServiceProvider) csp).reset();

        assertSame(MySimpleConfig.class, csp.getConfigBeanType());
        Config cfg = Config.create(ConfigSources.create(
                        Map.of("my-simple-config.port", "8084"),
                        "my-simple-config"));
        MySimpleConfig cfgBean = csp.toConfigBean(cfg.get("my-simple-config"));
        assertNotNull(cfgBean);
        assertEquals(8084, cfgBean.port());
    }

    @Test
    public void singletonConfiguredServiceToConfigBean() {
        ConfiguredServiceProvider<ASingletonConfiguredService, ASingletonConfig> csp =
                (ConfiguredServiceProvider<ASingletonConfiguredService, ASingletonConfig>) picoServices.services()
                        .lookupFirst(ASingletonConfiguredService.class);
        assertNotNull(csp);
        ((AbstractConfiguredServiceProvider) csp).reset();

        assertSame(ASingletonConfig.class, csp.getConfigBeanType());
        Config cfg = Config.create(ConfigSources.create(
                Map.of("my-singleton-config.host-address", "hostaddress"),
                "my-singleton-config"));
        Config subCfg = Objects.requireNonNull(cfg.get("my-singleton-config"));
        assertTrue(subCfg.exists());
        ASingletonConfig cfgBean = csp.toConfigBean(subCfg);
        assertNotNull(cfgBean);
        assertEquals(8080, cfgBean.port());
        assertEquals("hostaddress", cfgBean.hostAddress());
        assertNull(cfgBean.password());
    }

    @Test
    public void rootConfiguredServiceActivation() {
        ServiceInfo criteria = DefaultServiceInfo.builder()
                .qualifier(DefaultQualifierAndValue.create(ConfiguredBy.class))
                .build();
        List<ServiceProvider<Object>> list = services.lookup(criteria).stream()
                .filter(it -> it.serviceInfo().serviceTypeName().contains("Service"))
                .collect(Collectors.toList());
        assertEquals(3, list.size(), list.toString());
        assertEquals(
                "[MySimpleConfiguredService$$picoActivator{root}, ASingletonConfiguredService$$picoActivator{root}, "
                        + "UnconfiguredServiceProvider{SchemaRequiredConfiguredService$$picoActivator{root}}]",
                     String.valueOf(ServiceProvider.toIdentities(list)));

        AbstractConfiguredServiceProvider<?, Object> sp0 = (AbstractConfiguredServiceProvider<?, Object>) list.get(0);
        assertEquals(ActivationPhase.ACTIVE, sp0.currentActivationPhase());

        Map<String, AbstractConfiguredServiceProvider<?, Object>> slaves = sp0.managedServiceProviders(criteria);
        assertEquals(
                "[MySimpleConfiguredService$$picoActivator{my-simple-config}, "
                        + "MySimpleConfiguredService$$picoActivator{my-simple-config.0}, "
                        + "MySimpleConfiguredService$$picoActivator{my-simple-config.1}, "
                        + "MySimpleConfiguredService$$picoActivator{my-simple-config.2}]",
                     String.valueOf(ServiceProvider.toIdentities(slaves.values())));

        final List<?> all = new ArrayList<>();
        list.stream()
                .map(ConfiguredServiceProvider.class::cast)
                .map(csp -> csp.getList(null, null, false))
                .forEach(all::addAll);
        assertEquals(
                "[MySimpleConfiguredService{DefaultMySimpleConfig{my-simple-config}(port=8084)}, "
                        + "MySimpleConfiguredService{DefaultMySimpleConfig{my-simple-config.0}(port=8087)}, "
                        + "MySimpleConfiguredService{DefaultMySimpleConfig{my-simple-config.1}(port=8085)}, "
                        + "MySimpleConfiguredService{DefaultMySimpleConfig{my-simple-config.2}(port=8086)}, "
                        + "ASingletonConfiguredService{DefaultASingletonConfig{@default}(port=8080, hostAddress=127"
                        + ".0.0.1, password=null, theSimpleConfig=null, listOfSimpleConfig=[], setOfSimpleConfig=[], "
                        + "mapOfSimpleConfig={}, theSingletonConfig=null, listOfSingletonConfigConfig=[], "
                        + "setOfSingletonConfig=[], mapOfSingletonConfig={})}]",
                     String.valueOf(ServiceProvider.toDescriptions(all)));
        assertFalse(all.stream().anyMatch(Objects::isNull),
                    slaves.toString());

        ConfiguredServiceProvider sp1 = (ConfiguredServiceProvider) services
                .lookupFirst(DefaultServiceInfo.builder()
                                     .qualifier(DefaultQualifierAndValue.create(ConfiguredBy.class))
                                     .serviceTypeName(MySimpleConfiguredService.class.getName())
                                .build());
        ConfiguredServiceProvider sp2 = (ConfiguredServiceProvider) services
                .lookupFirst(DefaultServiceInfo.builder()
                                     .qualifier(DefaultQualifierAndValue.create(ConfiguredBy.class))
                                     .serviceTypeName(ASingletonConfiguredService.class.getName())
                                     .build());
        assertNotEquals(sp1, sp2);
    }

    @Test
    public void nonConfiguredServiceWithConfiguredServiceInjectionPoints() {
        TestableServices services = picoServices.services();

        List<ServiceProvider<NonConfiguredServiceWithProviders>> providers =
                services.lookup(NonConfiguredServiceWithProviders.class);
        assertNotNull(providers);
        assertEquals("[NonConfiguredServiceWithProviders$$picoActivator:INIT:[]]",
                     String.valueOf(ServiceProvider.toDescription(providers)));
        assertFalse(NonConfiguredServiceWithProviders.ACTIVATED);
        NonConfiguredServiceWithProviders service = providers.get(0).get();
        assertNotNull(service);
        assertTrue(NonConfiguredServiceWithProviders.ACTIVATED);
        assertEquals(
                "MySimpleConfiguredService$$picoActivator{my-simple-config}:io.helidon.pico.config.testsubjects"
                        + ".MySimpleConfiguredService:ACTIVE",
                     String.valueOf(ServiceProvider.toDescription(service.providerSimpleConfiguredService)));
        assertEquals(
                "ASingletonConfiguredService$$picoActivator{@default}:io.helidon.pico.config.testsubjects"
                        + ".ASingletonConfiguredService:PENDING",
                     String.valueOf(ServiceProvider.toDescription(service.providerSingletonConfiguredService)));

        List<ServiceProvider<NonConfiguredServiceWithOptionals>> optionals =
                services.lookup(NonConfiguredServiceWithOptionals.class);
        assertNotNull(optionals);
        assertEquals("[NonConfiguredServiceWithOptionals$$picoActivator:INIT:[]]",
                     String.valueOf(ServiceProvider.toDescription(optionals)));
        assertFalse(NonConfiguredServiceWithOptionals.ACTIVATED);
        NonConfiguredServiceWithOptionals optional = optionals.get(0).get();
        assertNotNull(optional);
        assertTrue(NonConfiguredServiceWithOptionals.ACTIVATED);
        assertEquals(
                "MySimpleConfiguredService{DefaultMySimpleConfig{my-simple-config}(port=8084)}",
                String.valueOf(ServiceProvider.toDescription(optional.optionalSimpleConfiguredService)));
        assertEquals(
                "ASingletonConfiguredService{DefaultASingletonConfig{@default}(port=8080, hostAddress=127.0.0.1, "
                        + "password=null, theSimpleConfig=null, listOfSimpleConfig=[], setOfSimpleConfig=[], "
                        + "mapOfSimpleConfig={}, theSingletonConfig=null, listOfSingletonConfigConfig=[], "
                        + "setOfSingletonConfig=[], mapOfSingletonConfig={})}",
                String.valueOf(ServiceProvider.toDescription(optional.optionalSingletonConfiguredService)));
    }

}
