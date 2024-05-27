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

package io.helidon.inject.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.inject.api.ActivationRequest;
import io.helidon.inject.api.ActivationResult;
import io.helidon.inject.api.Application;
import io.helidon.inject.api.Bootstrap;
import io.helidon.inject.api.DeActivationRequest;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.Injector;
import io.helidon.inject.api.InjectorOptions;
import io.helidon.inject.api.ModuleComponent;
import io.helidon.inject.api.Phase;
import io.helidon.inject.api.ServiceInfo;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Services;
import io.helidon.inject.runtime.testsubjects.HelloInjection$$Application;
import io.helidon.inject.runtime.testsubjects.HelloInjectionImpl$$injectionActivator;
import io.helidon.inject.runtime.testsubjects.HelloInjectionWorld;
import io.helidon.inject.runtime.testsubjects.HelloInjectionWorldImpl;
import io.helidon.inject.runtime.testsubjects.InjectionWorld;
import io.helidon.inject.runtime.testsubjects.InjectionWorldImpl;
import io.helidon.inject.runtime.testsubjects.InjectionWorldImpl$$injectionActivator;
import io.helidon.inject.spi.InjectionPlan;

import jakarta.inject.Singleton;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Sanity type tests only. The "real" testing is in the tests submodules.
 */
class HelloInjectionWorldSanityTest {
    private static final int EXPECTED_MODULES = 2;

    @BeforeEach
    void setUp() {
        tearDown();
        Config config = Config.builder(
                        ConfigSources.create(
                                Map.of("inject.permits-dynamic", "true"), "config-1"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        InjectionServices.globalBootstrap(Bootstrap.builder().config(config).build());
    }

    @AfterEach
    void tearDown() {
        HelloInjection$$Application.ENABLED = true;
        SimpleInjectionTestingSupport.resetAll();
    }

    @Test
    void sanity() {
        Services services = InjectionServices.realizedServices();

        List<ServiceProvider<ModuleComponent>> moduleProviders = services.lookupAll(ModuleComponent.class);
        assertThat(moduleProviders.size(),
                   equalTo(EXPECTED_MODULES));
        List<String> descriptions = ServiceUtils.toDescriptions(moduleProviders);
        assertThat(descriptions,
                   containsInAnyOrder("EmptyModule:ACTIVE", "HelloInjection$$Module:ACTIVE"));

        List<ServiceProvider<Application>> applications = services.lookupAll(Application.class);
        assertThat(applications.size(),
                   equalTo(1));
        assertThat(ServiceUtils.toDescriptions(applications),
                   containsInAnyOrder("HelloInjection$$Application:ACTIVE"));
    }

    @Test
    void standardActivationWithNoApplicationEnabled() {
        HelloInjection$$Application.ENABLED = false;
        Optional<InjectionServices> injectionServices = InjectionServices.injectionServices();
        ((DefaultInjectionServices) injectionServices.orElseThrow()).reset(true);

        standardActivation();
    }

    @Test
    void standardActivation() {
        Services services = InjectionServices.realizedServices();

        ServiceProvider<HelloInjectionWorld> helloProvider1 = services.lookup(HelloInjectionWorld.class);
        assertThat(helloProvider1,
                   notNullValue());

        ServiceProvider<HelloInjectionWorldImpl> helloProvider2 = services.lookup(HelloInjectionWorldImpl.class);
        assertThat(helloProvider1,
                   sameInstance(helloProvider2));
        assertThat(helloProvider1.id(),
                   equalTo(HelloInjectionWorldImpl.class.getName()));
        assertThat(helloProvider1.currentActivationPhase(),
                   equalTo(Phase.INIT));
        assertThat(helloProvider1.description(),
                   equalTo(HelloInjectionWorldImpl.class.getSimpleName() + ":" + Phase.INIT));

        ServiceInfo serviceInfo = helloProvider1.serviceInfo();
        assertThat(serviceInfo.serviceTypeName(),
                   equalTo(TypeName.create(HelloInjectionWorldImpl.class)));
        assertThat(serviceInfo.contractsImplemented(),
                   containsInAnyOrder(TypeName.create(HelloInjectionWorld.class)));
        assertThat(serviceInfo.externalContractsImplemented().size(),
                   equalTo(0));
        assertThat(serviceInfo.scopeTypeNames(),
                   containsInAnyOrder(TypeName.create(Singleton.class)));
        assertThat(serviceInfo.qualifiers().size(),
                   equalTo(0));
        assertThat(serviceInfo.activatorTypeName().orElseThrow(),
                   equalTo(TypeName.create(HelloInjectionImpl$$injectionActivator.class)));
        assertThat(serviceInfo.declaredRunLevel(),
                   optionalValue(equalTo(0)));
        assertThat(serviceInfo.realizedRunLevel(),
                   equalTo(0));
        assertThat(serviceInfo.moduleName(),
                   optionalValue(equalTo("example")));
        assertThat(serviceInfo.declaredWeight(),
                   optionalEmpty());
        assertThat(serviceInfo.realizedWeight(),
                   equalTo(Weighted.DEFAULT_WEIGHT));

        ServiceProvider<InjectionWorld> worldProvider1 = services.lookup(InjectionWorld.class);
        assertThat(worldProvider1, notNullValue());
        assertThat(worldProvider1.description(),
                   equalTo("InjectionWorldImpl:INIT"));

        // now activate
        HelloInjectionWorld hello1 = helloProvider1.get();
        MatcherAssert.assertThat(hello1.sayHello(),
                                 equalTo("Hello inject"));
        assertThat(helloProvider1.currentActivationPhase(),
                   equalTo(Phase.ACTIVE));
        assertThat(helloProvider1.description(),
                   equalTo("HelloInjectionWorldImpl:ACTIVE"));

        // world should be active now too, since Hello should have implicitly activated it
        assertThat(worldProvider1.description(),
                   equalTo("InjectionWorldImpl:ACTIVE"));

        // check the post construct counts
        MatcherAssert.assertThat(((HelloInjectionWorldImpl) helloProvider1.get()).postConstructCallCount(),
                                 equalTo(1));
        MatcherAssert.assertThat(((HelloInjectionWorldImpl) helloProvider1.get()).preDestroyCallCount(),
                                 equalTo(0));

        // deactivate just the Hello service
        ActivationResult result = helloProvider1.deActivator().orElseThrow()
                .deactivate(DeActivationRequest.create());
        assertThat(result.finished(),
                   is(true));
        assertThat(result.success(),
                   is(true));
        assertThat(result.serviceProvider(),
                   sameInstance(helloProvider2));
        assertThat(result.finishingActivationPhase(),
                   is(Phase.DESTROYED));
        assertThat(result.startingActivationPhase(),
                   is(Phase.ACTIVE));
        assertThat(helloProvider1.description(),
                   equalTo("HelloInjectionWorldImpl:DESTROYED"));
        MatcherAssert.assertThat(((HelloInjectionWorldImpl) hello1).postConstructCallCount(),
                                 equalTo(1));
        MatcherAssert.assertThat(((HelloInjectionWorldImpl) hello1).preDestroyCallCount(),
                                 equalTo(1));
        assertThat(worldProvider1.description(),
                   equalTo("InjectionWorldImpl:ACTIVE"));
    }

    @Test
    void viaInjector() {
        InjectionServices injectionServices = InjectionServices.injectionServices().orElseThrow();
        Injector injector = injectionServices.injector().orElseThrow();

        HelloInjectionImpl$$injectionActivator subversiveWay = new HelloInjectionImpl$$injectionActivator();
        subversiveWay.injectionServices(Optional.of(injectionServices));

        ActivationResult result = injector.activateInject(subversiveWay, InjectorOptions.builder().build());
        assertThat(result.finished(), is(true));
        assertThat(result.success(), is(true));

        HelloInjectionWorld hello1 = subversiveWay.serviceRef().orElseThrow();
        MatcherAssert.assertThat(hello1.sayHello(),
                                 equalTo("Hello inject"));
        MatcherAssert.assertThat(subversiveWay.currentActivationPhase(),
                                 equalTo(Phase.ACTIVE));

        MatcherAssert.assertThat(hello1,
                                 sameInstance(subversiveWay.get()));
        assertThat(subversiveWay, sameInstance(result.serviceProvider()));

        // the above is subversive because it is disconnected from the "real" activator
        Services services = injectionServices.services();
        ServiceProvider<?> realHelloProvider =
                ((DefaultServices) services).serviceProviderFor(TypeName.create(HelloInjectionWorldImpl.class));
        assertThat(subversiveWay, not(sameInstance(realHelloProvider)));

        assertThat(realHelloProvider.currentActivationPhase(),
                   equalTo(Phase.INIT));

        result = injector.deactivate(subversiveWay, InjectorOptions.builder().build());
        assertThat(result.success(), is(true));
    }

    @Test
    void injectionPlanResolved() {
        HelloInjectionImpl$$injectionActivator activator = new HelloInjectionImpl$$injectionActivator();
        activator.injectionServices(InjectionServices.injectionServices());

        ActivationResult result = activator.activate(ActivationRequest.builder().targetPhase(Phase.INJECTING).build());
        assertThat(result.success(), is(true));

        MatcherAssert.assertThat(activator.currentActivationPhase(), is(Phase.INJECTING));
        MatcherAssert.assertThat(activator.serviceRef().orElseThrow().postConstructCallCount(), equalTo(0));
        MatcherAssert.assertThat(activator.serviceRef().orElseThrow().preDestroyCallCount(), equalTo(0));

        Map<String, ? extends InjectionPlan> injectionPlan = result.injectionPlans();
        assertThat(injectionPlan.keySet(), containsInAnyOrder(
                "io.helidon.inject.runtime.testsubjects.world",
                "io.helidon.inject.runtime.testsubjects.worldRef",
                "io.helidon.inject.runtime.testsubjects.redWorld",
                "io.helidon.inject.runtime.testsubjects.listOfWorlds",
                "io.helidon.inject.runtime.testsubjects.listOfWorldRefs",
                "io.helidon.inject.runtime.testsubjects.world|1(1)"));
        InjectionPlan plan = injectionPlan.get("io.helidon.inject.runtime.testsubjects.world");
        assertThat(plan.wasResolved(), is(true));
        assertThat(plan.resolved().orElseThrow().getClass(), equalTo(InjectionWorldImpl.class));
        plan = injectionPlan.get("io.helidon.inject.runtime.testsubjects.redWorld");
        assertThat(plan.wasResolved(), is(true));
        assertThat(plan.resolved().orElseThrow().getClass(), equalTo(Optional.class));
        plan = injectionPlan.get("io.helidon.inject.runtime.testsubjects.worldRef");
        assertThat(plan.wasResolved(), is(true));
        assertThat(plan.resolved().orElseThrow().getClass(), equalTo(InjectionWorldImpl$$injectionActivator.class));
        plan = injectionPlan.get("io.helidon.inject.runtime.testsubjects.listOfWorlds");
        assertThat(plan.wasResolved(), is(true));
        assertThat(plan.resolved().orElseThrow().getClass(), equalTo(ArrayList.class));

        Map<String, Object> resolutions = result.resolvedDependencies();
        assertThat(resolutions.size(), equalTo(injectionPlan.size()));

        // now take us through activation
        result = activator.activate(ActivationRequest.builder()
                                            .startingPhase(activator.currentActivationPhase())
                                            .build());
        assertThat(result.success(), is(true));
        MatcherAssert.assertThat(activator.currentActivationPhase(), is(Phase.ACTIVE));
        MatcherAssert.assertThat(activator.serviceRef().orElseThrow().postConstructCallCount(), equalTo(1));
        MatcherAssert.assertThat(activator.serviceRef().orElseThrow().preDestroyCallCount(), equalTo(0));

        // these should have happened prior, so not there any longer
        assertThat(result.injectionPlans(), equalTo(Map.of()));
        assertThat(result.resolvedDependencies(), equalTo(Map.of()));
    }

}
