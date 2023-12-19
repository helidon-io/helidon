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

package io.helidon.inject.tests.inject.tbox;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;
import io.helidon.inject.ActivationResult;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Lookup;
import io.helidon.inject.ServiceProvider;
import io.helidon.inject.ServiceProviderRegistry;
import io.helidon.inject.Services;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.testing.InjectionTestingSupport;
import io.helidon.inject.tests.inject.ASerialProviderImpl;
import io.helidon.inject.tests.inject.ClassNamedY;
import io.helidon.inject.tests.inject.TestingSingleton;
import io.helidon.inject.tests.inject.provider.FakeConfig;
import io.helidon.inject.tests.inject.provider.FakeServer;
import io.helidon.inject.tests.inject.stacking.CommonContract;
import io.helidon.inject.tests.inject.tbox.impl.BigHammer;
import io.helidon.inject.tests.inject.tbox.impl.HandSaw;
import io.helidon.inject.tests.inject.tbox.impl.MainToolBox;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.types.TypeName.create;
import static io.helidon.inject.testing.InjectionTestingSupport.resetAll;
import static io.helidon.inject.testing.InjectionTestingSupport.testableServices;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Expectation here is that the annotation processor ran, and we can use standard injection and di registry services, etc.
 */
class ToolBoxTest {
    private static final MeterRegistry METER_REGISTRY = Metrics.globalRegistry();

    private final InjectionConfig config = InjectionTestingSupport.basicTestableConfig();
    private InjectionServices injectionServices;
    private Services serviceRegistry;
    private ServiceProviderRegistry services;

    @BeforeEach
    void setUp() {
        setUp(config);
    }

    void setUp(InjectionConfig config) {
        this.injectionServices = testableServices(config);
        this.serviceRegistry = injectionServices.services();
        this.services = serviceRegistry.serviceProviders();
    }

    @AfterEach
    void tearDown() {
        resetAll();
    }

    @Test
    void sanity() {
        assertNotNull(injectionServices);
        assertNotNull(services);
    }

    @Test
    void toolbox() {
        List<ServiceProvider<Awl>> blanks = services.all(Awl.class);
        List<String> desc = blanks.stream().map(ServiceProvider::description).collect(Collectors.toList());
        // note that order matters here
        assertThat(desc,
                   contains("AwlImpl:INIT"));

        List<ServiceProvider<ToolBox>> allToolBoxes = services.all(ToolBox.class);
        desc = allToolBoxes.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat(desc,
                   contains("MainToolBox:INIT"));

        ToolBox toolBox = allToolBoxes.get(0).get();
        assertThat(toolBox.getClass(), equalTo(MainToolBox.class));
        MainToolBox mtb = (MainToolBox) toolBox;
        assertThat(mtb.postConstructCallCount, equalTo(1));
        assertThat(mtb.preDestroyCallCount, equalTo(0));
        assertThat(mtb.setterCallCount, equalTo(1));
        List<Supplier<Tool>> allTools = mtb.toolsInBox();
        desc = allTools.stream().map(Object::toString).collect(Collectors.toList());
        assertThat(desc,
                   contains("SledgeHammer:INIT",
                            "TableSaw:INIT",
                            "AwlImpl:INIT",
                            "HandSaw:INIT",
                            "Screwdriver:ACTIVE",
                            "BigHammer:INIT",
                            "LittleHammer:INIT"));
        assertThat(mtb.screwdriver(), notNullValue());

        Supplier<Hammer> hammer = Objects.requireNonNull(toolBox.preferredHammer());
        assertThat(hammer.get(), notNullValue());
        assertThat(hammer.get(), is(hammer.get()));
        assertThat(BigHammer.class, equalTo(hammer.get().getClass()));
        desc = allTools.stream().map(Object::toString).collect(Collectors.toList());
        assertThat(desc,
                   contains("SledgeHammer:INIT",
                            "TableSaw:INIT",
                            "AwlImpl:INIT",
                            "HandSaw:INIT",
                            "Screwdriver:ACTIVE",
                            "BigHammer:ACTIVE",
                            "LittleHammer:INIT"));

        desc = (((MainToolBox) toolBox).allHammers()).stream().map(Object::toString).collect(Collectors.toList());
        assertThat(desc,
                   contains("SledgeHammer:INIT",
                            "BigHammer:ACTIVE",
                            "LittleHammer:INIT"));
        assertThat(((ServiceProvider<?>) ((MainToolBox) toolBox).bigHammer()).description(),
                   equalTo("BigHammer:ACTIVE"));
    }

    @Test
    void testClasses() {
        assertThat(services.first(TestingSingleton.class),
                   notNullValue());
        assertThat(services.first(TestingSingleton.class),
                   optionalPresent());
    }

    /**
     * This assumes {@link io.helidon.inject.codegen.InjectOptions#AUTO_ADD_NON_CONTRACT_INTERFACES} has
     * been enabled - see pom.xml
     */
    @Test
    void autoExternalContracts() {
        List<ServiceProvider<Serializable>> allSerializable = services.all(Serializable.class);
        List<String> desc = allSerializable.stream().map(ServiceProvider::description).collect(Collectors.toList());
        // note that order matters here
        assertThat(desc,
                   contains("ASerialProviderImpl:INIT", "Screwdriver:INIT"));
    }

    @Test
    void providerTest() {
        Serializable s1 = services.get(Serializable.class).get();
        assertThat(s1, notNullValue());
        assertThat(ASerialProviderImpl.class + " is a higher weight and should have been returned for " + String.class,
                   String.class, equalTo(s1.getClass()));
        assertThat(services.get(Serializable.class).get(), not(s1));
    }

    @Test
    void modules() {
        List<ServiceProvider<ModuleComponent>> allModules = services.all(ModuleComponent.class);
        List<String> desc = allModules.stream()
                .map(it -> it.id() + ":" + it.currentActivationPhase())
                .toList();
        // note that order matters here
        // there is now config module as active as well
        assertThat("ensure that Annotation Processors are enabled in the tools module meta-inf/services",
                   desc,
                   contains("io.helidon.config.Injection__Module:ACTIVE",
                            "io.helidon.inject.configdriven.ConfigDrivenInjectModule:ACTIVE",
                            "io.helidon.inject.tests.inject.Injection__Module:ACTIVE",
                            "io.helidon.inject.tests.inject.TestInjection__Module:ACTIVE"));
        List<String> names = allModules.stream()
                .sorted()
                .map(ServiceProvider::get)
                .map(ModuleComponent::name)
                .toList();
        assertThat(names,
                   contains("io.helidon.config",
                            "io.helidon.inject.configdriven",
                            "io.helidon.inject.tests.inject",
                            "unnamed/io.helidon.inject.tests.inject/test"));
    }

    @Test
    void innerClassesCanBeGenerated() {
        FakeServer.Builder s1 = services.get(FakeServer.Builder.class).get();
        assertThat(s1, notNullValue());
        assertThat(services.get(FakeServer.Builder.class).get(), is(s1));

        FakeConfig.Builder c1 = services.get(FakeConfig.Builder.class).get();
        assertThat(c1, notNullValue());
        assertThat(services.get(FakeConfig.Builder.class).get(), is(c1));
    }

    /**
     * Targets {@link AbstractSaw} with derived classes of
     * {@link HandSaw} and {@link TableSaw} found in different packages.
     */
    @Test
    void hierarchyOfInjections() {
        List<ServiceProvider<AbstractSaw>> saws = services.all(AbstractSaw.class);
        List<String> desc = saws.stream().map(ServiceProvider::description).collect(Collectors.toList());
        // note that order matters here
        assertThat(desc,
                   contains("TableSaw:INIT", "HandSaw:INIT"));
        for (ServiceProvider<AbstractSaw> saw : saws) {
            saw.get().verifyState();
        }
    }

    /**
     * This tests the presence of module(s) + application(s) to handle all bindings, with effectively no lookups.
     */
    @Test
    void runlevel() {
        Counter lookupCounter = lookupCounter();
        long initialCount = lookupCounter.count();

        List<ServiceProvider<Object>> runLevelServices = services
                .all(Lookup.builder()
                             .runLevel(Injection.RunLevel.STARTUP)
                             .build());
        List<String> desc = runLevelServices.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat(desc,
                   contains("TestingSingleton:INIT"));

        runLevelServices.forEach(sp -> Objects.requireNonNull(sp.get(), sp + " failed on get()"));

        long count = lookupCounter.count() - initialCount;
        assertThat("activation should triggered one new lookup from startup",
                   count,
                   equalTo(1L));
        desc = runLevelServices.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat(desc,
                   contains("TestingSingleton:ACTIVE"));
    }

    /**
     * This assumes the presence of module(s) + application(s) to handle all bindings, with effectively no lookups!
     */
    @Test
    void noServiceActivationRequiresLookupWhenApplicationIsPresent() {
        Counter counter = lookupCounter();

        List<ServiceProvider<Object>> allServices = services
                .all(Lookup.EMPTY);

        // from this point, there should be no additional lookups
        long initialCount = counter.count();
        allServices.stream()
                .filter(it -> !it.isProvider())
                .forEach(sp -> {
                    sp.get();
                    assertThat("activation should not have triggered any lookups (for singletons): "
                                       + sp + " triggered lookups", counter.count(),
                               equalTo(initialCount));
                });
    }

    @Test
    void startupAndShutdownCallsPostConstructAndPreDestroy() {
        assertThat(TestingSingleton.postConstructCount(), equalTo(0));
        assertThat(TestingSingleton.preDestroyCount(), equalTo(0));

        List<ServiceProvider<CommonContract>> allInterceptedBefore = services.all(CommonContract.class);
        assertThat(allInterceptedBefore.size(), greaterThan(0));
        assertThat(TestingSingleton.postConstructCount(), equalTo(0));
        assertThat(TestingSingleton.preDestroyCount(), equalTo(0));
        allInterceptedBefore.forEach(ServiceProvider::get);

        TestingSingleton testingSingletonFromLookup = services.get(TestingSingleton.class).get();
        assertThat(testingSingletonFromLookup, notNullValue());
        assertThat(TestingSingleton.postConstructCount(), equalTo(1));
        assertThat(TestingSingleton.preDestroyCount(), equalTo(0));

        Map<TypeName, ActivationResult> map = injectionServices.shutdown();
        Map<TypeName, String> report = map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                                          e -> e.getValue().startingActivationPhase().toString()
                                                  + "->" + e.getValue().finishingActivationPhase()));

        assertThat(report, hasEntry(create("io.helidon.inject.tests.inject.Injection__Application"), "ACTIVE->DESTROYED"));
        assertThat(report,
                   hasEntry(create("io.helidon.inject.tests.inject.Injection__Module"), "ACTIVE->DESTROYED"));
        assertThat(report,
                   hasEntry(create("io.helidon.inject.tests.inject.TestInjection__Application"), "ACTIVE->DESTROYED"));
        assertThat(report,
                   hasEntry(create("io.helidon.inject.tests.inject.TestInjection__Module"), "ACTIVE->DESTROYED"));
        assertThat(report,
                   hasEntry(create("io.helidon.inject.tests.inject.stacking.MostOuterCommonContractImpl"), "ACTIVE->DESTROYED"));
        assertThat(report,
                   hasEntry(create("io.helidon.inject.tests.inject.stacking.OuterCommonContractImpl"), "ACTIVE->DESTROYED"));
        assertThat(report, hasEntry(create("io.helidon.inject.tests.inject.stacking.CommonContractImpl"), "ACTIVE->DESTROYED"));
        assertThat(report, hasEntry(create("io.helidon.inject.tests.inject.TestingSingleton"), "ACTIVE->DESTROYED"));
        assertThat(report,
                   hasEntry(create("io.helidon.inject.configdriven.ConfigDrivenInjectModule"), "ACTIVE->DESTROYED"));
        assertThat(report, hasEntry(create("io.helidon.config.Injection__Module"), "ACTIVE->DESTROYED"));
        assertThat(report, hasEntry(create("io.helidon.config.ConfigProducer"), "ACTIVE->DESTROYED"));
        assertThat(report, hasEntry(create("io.helidon.inject.Services"), "ACTIVE->DESTROYED"));

        assertThat(report + " : expected 10 services to be present", report.size(), equalTo(12));

        assertThat(TestingSingleton.postConstructCount(), equalTo(1));
        assertThat(TestingSingleton.preDestroyCount(), equalTo(1));

        tearDown();
        setUp();
        TestingSingleton testingSingletonFromLookup2 = injectionServices.services().get(TestingSingleton.class).get();
        assertThat(testingSingletonFromLookup2, not(testingSingletonFromLookup));

        map = injectionServices.shutdown();
        report = map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                                          e2 -> e2.getValue().startingActivationPhase().toString()
                                                  + "->" + e2.getValue().finishingActivationPhase()));
        // now contains config as well
        assertThat(report.toString(), report.size(), is(12));

        tearDown();
        map = injectionServices.shutdown();
        assertThat(map.toString(), map.size(), is(0));
    }

    @Test
    void knownProviders() {
        List<ServiceProvider<Object>> providers = services.all(
                Lookup.builder().addContract(Supplier.class).build());
        List<String> desc = providers.stream().map(ServiceProvider::description).collect(Collectors.toList());
        // note that order matters here (weight ranked)
        assertThat(desc,
                   contains("ASerialProviderImpl:INIT",
                            "MyServices.MyConcreteClassContractPerRequestIPProvider:INIT",
                            "MyServices.MyConcreteClassContractPerRequestProvider:INIT",
                            "BladeProvider:INIT"));
    }

    @Test
    void classNamed() {
        List<ServiceProvider<Object>> providers = services.all(
                Lookup.builder()
                        .addQualifier(Qualifier.createNamed(ClassNamedY.class))
                        .build());
        List<String> desc = providers.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat(desc,
                   contains("YImpl:INIT",
                            "BladeProvider:INIT"));

        providers = services.all(
                Lookup.builder()
                        .addQualifier(Qualifier.createNamed(ClassNamedY.class.getName()))
                        .build());
        List<String> desc2 = providers.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat(desc2,
                   equalTo(desc));
    }

    private Counter lookupCounter() {
        Optional<Counter> counterMeter = METER_REGISTRY.counter("io.helidon.inject.lookups", List.of());
        assertThat(counterMeter, optionalPresent());
        return counterMeter.get();
    }
}
