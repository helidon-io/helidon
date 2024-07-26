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

package io.helidon.service.tests.inject.toolbox;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Injection.InjectionPointProvider;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.tests.inject.toolbox.impl.BigHammer;
import io.helidon.service.tests.inject.toolbox.impl.MainToolBox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Expectation here is that the annotation processor ran, and we can use standard injection and di registry services, etc.
 */
class ToolBoxTest {
    private static final MeterRegistry METER_REGISTRY = Metrics.globalRegistry();

    private InjectRegistryManager registryManager;
    private InjectRegistry registry;

    @BeforeEach
    void setUp() {
        this.registryManager = InjectRegistryManager.create();
        this.registry = registryManager.registry();
    }

    @AfterEach
    void tearDown() {
        if (registryManager != null) {
            registryManager.shutdown();
        }
    }

    @Test
    void sanity() {
        assertNotNull(registryManager);
        assertNotNull(registry);
    }

    @Test
    void toolbox() {
        List<InjectServiceInfo> blanks = registry.lookupServices(Lookup.create(Awl.class));
        assertThat(blanks, hasSize(1));

        List<InjectServiceInfo> allToolBoxes = registry.lookupServices(Lookup.create(ToolBox.class));
        assertThat(allToolBoxes, hasSize(1));

        ToolBox toolBox = registry.get(ToolBox.class);
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

    /**
     * Targets {@link AbstractSaw} with derived classes of
     * {@link io.helidon.service.tests.inject.toolbox.impl.HandSaw} and {@link TableSaw} found in different packages.
     */
    @Test
    void hierarchyOfInjections() {
        List<AbstractSaw> saws = registry.all(AbstractSaw.class);
        assertThat(saws, hasSize(2));
        List<String> desc = toSimpleClassNames(saws);
        // note that order matters here
        assertThat(desc,
                   contains("TableSaw", "HandSaw"));

        for (AbstractSaw saw : saws) {
            saw.verifyState();
        }
    }

    /**
     * This assumes the presence of module(s) + application(s) to handle all bindings, with effectively no lookups!
     */
    @Test
    void noServiceActivationRequiresLookupWhenApplicationIsPresent() {
        Counter counter = lookupCounter();
        long initialCount = counter.count();

        List<InjectServiceInfo> allServices = registry.lookupServices(Lookup.EMPTY);
        // one lookup on previous line
        long postLookupCount = counter.count();
        assertThat((postLookupCount - initialCount), is(1L));

        // now lookup of each service should increase the counter by exactly one (i.e. no lookups for injection points)
        for (InjectServiceInfo service : allServices) {
            postLookupCount++;
            try {
                registry.supply(service.serviceType())
                        .get();
            } catch (Exception ignored) {
                // injection point providers will throw an exception, as they cannot resolve injection point
                // still should not lookup
            }
            assertThat("Activation should not have triggered any lookups (for singletons): "
                               + service.descriptorType().fqName() + " triggered lookups", counter.count(),
                       equalTo(postLookupCount));
        }
    }

    @Test
    void knownIpProviders() {
        List<InjectServiceInfo> services = this.registry.lookupServices(Lookup.create(InjectionPointProvider.class));
        List<String> desc = toSimpleTypes(services);

        // this list must only InjectionPointProviders
        assertThat(desc,
                   contains("BladeProvider"));
    }

    private List<String> toSimpleClassNames(List<?> listOfObjects) {
        return listOfObjects.stream()
                .map(Object::getClass)
                .map(Class::getSimpleName)
                .toList();
    }

    private List<String> toSimpleTypes(List<InjectServiceInfo> providers) {
        return providers.stream()
                .map(InjectServiceInfo::serviceType)
                .map(TypeName::className)
                .toList();
    }

    private Counter lookupCounter() {
        Optional<Counter> counterMeter = METER_REGISTRY.counter("io.helidon.inject.lookups", List.of());
        assertThat(counterMeter, optionalPresent());
        return counterMeter.get();
    }
}
