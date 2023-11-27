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
import io.helidon.inject.Application;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceInfo;
import io.helidon.inject.testing.InjectionTestingSupport;
import io.helidon.inject.tests.inject.ASerialProviderImpl;
import io.helidon.inject.tests.inject.ClassNamedY;
import io.helidon.inject.tests.inject.TestingSingleton;
import io.helidon.inject.tests.inject.provider.FakeConfig;
import io.helidon.inject.tests.inject.provider.FakeServer;
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
import static io.helidon.inject.testing.InjectionTestingSupport.toSimpleTypes;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Expectation here is that the annotation processor ran, and we can use standard injection and di registry services, etc.
 */
class ToolBoxTest {
    private static final MeterRegistry METER_REGISTRY = Metrics.globalRegistry();

    private InjectionServices injectionServices;
    private Services services;

    @BeforeEach
    void setUp() {
        TestingSingleton.reset();
        this.injectionServices = InjectionServices.create();
        this.services = injectionServices.services();
    }

    @AfterEach
    void tearDown() {
        InjectionTestingSupport.shutdown(injectionServices);
    }

    @Test
    void sanity() {
        assertNotNull(injectionServices);
        assertNotNull(services);
    }

    @Test
    void toolbox() {
        List<ServiceInfo> blanks = services.lookupServices(Lookup.create(Awl.class));
        assertThat(blanks, hasSize(1));

        List<ServiceInfo> allToolBoxes = services.lookupServices(Lookup.create(ToolBox.class));
        assertThat(allToolBoxes, hasSize(1));

        ToolBox toolBox = services.get(ToolBox.class);
        assertThat(toolBox.getClass(), equalTo(MainToolBox.class));
        MainToolBox mtb = (MainToolBox) toolBox;
        assertThat(mtb.postConstructCallCount, equalTo(1));
        assertThat(mtb.preDestroyCallCount, equalTo(0));
        assertThat(mtb.setterCallCount, equalTo(1));

        List<Supplier<Tool>> allTools = mtb.toolsInBox();
        assertThat(allTools, hasSize(7));
        assertThat(mtb.screwdriver(), notNullValue());

        Supplier<Hammer> hammer = Objects.requireNonNull(toolBox.preferredHammer());
        assertThat(hammer.get(), notNullValue());
        assertThat(hammer.get(), sameInstance(hammer.get()));
        assertThat(hammer.get(), instanceOf(BigHammer.class));

        List<String> toolTypes = allTools.stream()
                .map(Supplier::get)
                .map(Object::getClass)
                .map(Class::getSimpleName)
                .toList();
        assertThat(toolTypes, contains("SledgeHammer", // weight + 2, tbox.impl.SledgeHammer
                                       "BigHammer", // weight + 1, tbox.impl.BigHammer
                                       "TableSaw",  // tbox.TableSaw
                                       "AwlImpl", // tbox.impl.AwlImpl
                                       "HandSaw", // tbox.impl.HandSaw
                                       "Screwdriver", // tbox.impl.Screwdriver
                                       "LittleHammer" // tbox.impl.LittleHammer, has qualifier Named
        ));

        List<String> hammers = mtb.allHammers()
                .stream()
                .map(Supplier::get)
                .map(Object::getClass)
                .map(Class::getSimpleName)
                .toList();

        assertThat(hammers,
                   contains("SledgeHammer",
                            "BigHammer",
                            "LittleHammer"));
    }

    @Test
    void testClasses() {
        assertThat(services.first(TestingSingleton.class),
                   notNullValue());
        assertThat(services.first(TestingSingleton.class),
                   optionalPresent());
    }

    /**
     * This assumes {@code io.helidon.inject.codegen.InjectOptions#AUTO_ADD_NON_CONTRACT_INTERFACES} has
     * been enabled - see pom.xml
     */
    @Test
    void autoExternalContracts() {
        List<ServiceInfo> allSerializable = services.lookupServices(Lookup.create(Serializable.class));
        List<String> found = toSimpleTypes(allSerializable);

        // note that order matters here
        assertThat(found,
                   contains("ASerialProviderImpl", "Screwdriver"));
    }

    @Test
    void providerTest() {
        Serializable s1 = services.get(Serializable.class);
        assertThat(s1, notNullValue());
        assertThat(ASerialProviderImpl.class + " is a higher weight and should have been returned for " + String.class,
                   s1,
                   instanceOf(String.class));

        assertThat(services.get(Serializable.class), not(s1));
    }

    @Test
    void modules() {
        List<ModuleComponent> allModules = services.all(ModuleComponent.class);
        List<String> names = allModules.stream()
                .map(ModuleComponent::name)
                .toList();

        // note that order matters here
        assertThat(names,
                   contains("io.helidon.config",
                            "io.helidon.inject",
                            "io.helidon.inject.tests.inject",
                            "unnamed/io.helidon.inject.tests.inject/test"));
    }

    @Test
    void innerClassesCanBeGenerated() {
        FakeServer.Builder s1 = services.get(FakeServer.Builder.class);
        assertThat(s1, notNullValue());
        assertThat(services.get(FakeServer.Builder.class), is(s1));

        FakeConfig.Builder c1 = services.get(FakeConfig.Builder.class);
        assertThat(c1, notNullValue());
        assertThat(services.get(FakeConfig.Builder.class), is(c1));
    }

    /**
     * Targets {@link AbstractSaw} with derived classes of
     * {@link HandSaw} and {@link TableSaw} found in different packages.
     */
    @Test
    void hierarchyOfInjections() {
        List<AbstractSaw> saws = services.all(AbstractSaw.class);
        assertThat(saws, hasSize(2));
        List<String> desc = toSimpleTypes(saws);
        // note that order matters here
        assertThat(desc,
                   contains("TableSaw", "HandSaw"));

        for (AbstractSaw saw : saws) {
            saw.verifyState();
        }
    }

    /**
     * This tests the presence of module(s) + application(s) to handle all bindings, with effectively no lookups.
     */
    @Test
    void runlevel() {
        Counter lookupCounter = lookupCounter();
        long initialCount = lookupCounter.count();

        Lookup lookup = Lookup.builder()
                .runLevel(Injection.RunLevel.STARTUP)
                .build();

        List<ServiceInfo> runLevelServices = services
                .lookupServices(lookup);
        List<String> desc = toSimpleTypes(runLevelServices);
        assertThat(desc,
                   contains("TestingSingleton"));

        services.supplyAll(lookup)
                .get()
                .forEach(it -> assertThat(it, notNullValue()));

        long count = lookupCounter.count() - initialCount;
        assertThat("We have invoked lookup twice",
                   count,
                   equalTo(2L));
    }

    /**
     * This assumes the presence of module(s) + application(s) to handle all bindings, with effectively no lookups!
     */
    @Test
    void noServiceActivationRequiresLookupWhenApplicationIsPresent() {
        Counter counter = lookupCounter();
        long initialCount = counter.count();

        List<ServiceInfo> allServices = services.lookupServices(Lookup.EMPTY);

        // from this point, there should be no additional lookups
        long postLookupCount = counter.count();
        assertThat((postLookupCount - initialCount), is(1L));
        allServices.forEach(sp -> {
                    try {
                        services.supply(sp)
                                .get();
                    } catch (Exception ignored) {
                        // injection point providers will throw an exception, as they cannot resolve injection point
                        // still should not lookup
                    }
                    assertThat("activation should not have triggered any lookups (for singletons): "
                                       + sp + " triggered lookups", counter.count(),
                               equalTo(postLookupCount));
                });
    }

    @Test
    void startupAndShutdownCallsPostConstructAndPreDestroy() {
        assertThat(TestingSingleton.postConstructCount(), equalTo(0));
        assertThat(TestingSingleton.preDestroyCount(), equalTo(0));

        TestingSingleton testingSingletonFromLookup = services.get(TestingSingleton.class);
        assertThat(testingSingletonFromLookup, notNullValue());
        assertThat(TestingSingleton.postConstructCount(), equalTo(1));
        assertThat(TestingSingleton.preDestroyCount(), equalTo(0));

        services.all(Application.class);
        services.all(ModuleComponent.class);
        services.get(Services.class);

        Map<TypeName, ActivationResult> map = injectionServices.shutdown();
        Map<TypeName, String> report = map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                                          e -> e.getValue().startingActivationPhase().toString()
                                                  + "->" + e.getValue().finishingActivationPhase()));

        int expected = 0;
        assertThat(report,
                   hasEntry(create("io.helidon.inject.tests.inject.Injection__Application"), "ACTIVE->DESTROYED"));
        expected++;
        assertThat(report,
                   hasEntry(create("io.helidon.inject.tests.inject.Injection__Module"), "ACTIVE->DESTROYED"));
        expected++;
        assertThat(report,
                   hasEntry(create("io.helidon.inject.tests.inject.TestInjection__Application"), "ACTIVE->DESTROYED"));
        expected++;
        assertThat(report,
                   hasEntry(create("io.helidon.inject.tests.inject.TestInjection__Module"), "ACTIVE->DESTROYED"));
        expected++;
        assertThat(report,
                   hasEntry(create("io.helidon.inject.tests.inject.TestingSingleton"), "ACTIVE->DESTROYED"));
        expected++;

        assertThat(report,
                   hasEntry(create("io.helidon.config.Injection__Module"), "ACTIVE->DESTROYED"));
        expected++;
        assertThat(report,
                   hasEntry(create("io.helidon.inject.Services"), "ACTIVE->DESTROYED"));
        expected++;
        assertThat(report,
                   hasEntry(create("io.helidon.inject.Injection__Module"), "ACTIVE->DESTROYED"));
        expected++;
        assertThat(report + " : expected " + expected + " services to be present", report.size(), equalTo(expected));

        assertThat(TestingSingleton.postConstructCount(), equalTo(1));
        assertThat(TestingSingleton.preDestroyCount(), equalTo(1));

        tearDown();
        setUp();
        TestingSingleton testingSingletonFromLookup2 = injectionServices.services().get(TestingSingleton.class);
        assertThat(testingSingletonFromLookup2, not(testingSingletonFromLookup));

        map = injectionServices.shutdown();
        report = map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                                          e2 -> e2.getValue().startingActivationPhase().toString()
                                                  + "->" + e2.getValue().finishingActivationPhase()));
        // we only used testing singleton, so other service providers that require lifecycle management
        // are not activated
        assertThat(report.toString(), report.size(), is(1));

        tearDown();
        map = injectionServices.shutdown();
        assertThat(map.toString(), map.size(), is(0));
    }

    @Test
    void knownSuppliers() {
        List<ServiceInfo> providers = services.lookupServices(Lookup.create(Supplier.class));
        List<String> desc = toSimpleTypes(providers);

        // this list must not contain InjectionPointProviders, as these do not implement Supplier (anymore)
        // so both MyServices.My..IPProvider and BladeProvider are not listed!
        // note that order matters here (weight ranked)
        assertThat(desc,
                   contains("ASerialProviderImpl",
                            "MyServices.MyConcreteClassContractPerRequestProvider"));
    }

    @Test
    void knownIpProviders() {
        List<ServiceInfo> services = this.services.lookupServices(Lookup.create(InjectionPointProvider.class));
        List<String> desc = toSimpleTypes(services);

        // this list must only InjectionPointProviders
        // so both MyServices.My..IPProvider and BladeProvider are listed
        // note that order matters here (weight ranked)
        assertThat(desc,
                   contains("MyServices.MyConcreteClassContractPerRequestIPProvider",
                            "BladeProvider"));
    }

    @Test
    void classNamed() {
        // as we need to get both normal services and injection point providers, we must use contextual
        // lookup. To be able to invoke injection point providers, we must search for them using contract
        List<ServiceInfo> descriptors = services.lookupServices(
                Lookup.builder()
                        .addQualifier(Qualifier.createNamed(ClassNamedY.class))
                        .build());
        List<String> desc = toSimpleTypes(descriptors);
        assertThat(desc,
                   contains("YImpl",
                            "BladeProvider"));

        descriptors = services.lookupServices(Lookup.builder()
                                                      .addQualifier(Qualifier.createNamed(ClassNamedY.class.getName()))
                                                      .build());
        List<String> desc2 = toSimpleTypes(descriptors);
        assertThat(desc2,
                   equalTo(desc));
    }

    private Counter lookupCounter() {
        Optional<Counter> counterMeter = METER_REGISTRY.counter("io.helidon.inject.lookups", List.of());
        assertThat(counterMeter, optionalPresent());
        return counterMeter.get();
    }
}
