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

package io.helidon.pico.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.api.ActivationRequest;
import io.helidon.pico.api.ActivationResult;
import io.helidon.pico.api.Application;
import io.helidon.pico.api.Bootstrap;
import io.helidon.pico.api.DeActivationRequest;
import io.helidon.pico.api.Injector;
import io.helidon.pico.api.InjectorOptions;
import io.helidon.pico.api.ModuleComponent;
import io.helidon.pico.api.Phase;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.ServiceInfo;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.Services;
import io.helidon.pico.runtime.testsubjects.HelloPico$$Application;
import io.helidon.pico.runtime.testsubjects.HelloPicoImpl$$picoActivator;
import io.helidon.pico.runtime.testsubjects.HelloPicoWorld;
import io.helidon.pico.runtime.testsubjects.HelloPicoWorldImpl;
import io.helidon.pico.runtime.testsubjects.PicoWorld;
import io.helidon.pico.runtime.testsubjects.PicoWorldImpl;
import io.helidon.pico.runtime.testsubjects.PicoWorldImpl$$picoActivator;
import io.helidon.pico.spi.InjectionPlan;

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
class HelloPicoWorldSanityTest {
    // helidon-config is now one of the modules
    private static final int EXPECTED_MODULES = 3;

    @BeforeEach
    void setUp() {
        tearDown();
        Config config = Config.builder(
                        ConfigSources.create(
                                Map.of("pico.permits-dynamic", "true"), "config-1"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        PicoServices.globalBootstrap(Bootstrap.builder().config(config).build());
    }

    @AfterEach
    void tearDown() {
        HelloPico$$Application.ENABLED = true;
        SimplePicoTestingSupport.resetAll();
    }

    @Test
    void sanity() {
        Services services = PicoServices.realizedServices();

        List<ServiceProvider<ModuleComponent>> moduleProviders = services.lookupAll(ModuleComponent.class);
        assertThat(moduleProviders.size(),
                   equalTo(EXPECTED_MODULES));
        List<String> descriptions = ServiceUtils.toDescriptions(moduleProviders);
        // helidon-config is now first
        assertThat(descriptions,
                   containsInAnyOrder("Pico$$Module:ACTIVE", "EmptyModule:ACTIVE", "HelloPico$$Module:ACTIVE"));

        List<ServiceProvider<Application>> applications = services.lookupAll(Application.class);
        assertThat(applications.size(),
                   equalTo(1));
        assertThat(ServiceUtils.toDescriptions(applications),
                   containsInAnyOrder("HelloPico$$Application:ACTIVE"));
    }

    @Test
    void standardActivationWithNoApplicationEnabled() {
        HelloPico$$Application.ENABLED = false;
        Optional<PicoServices> picoServices = PicoServices.picoServices();
        ((DefaultPicoServices) picoServices.orElseThrow()).reset(true);

        standardActivation();
    }

    @Test
    void standardActivation() {
        Services services = PicoServices.realizedServices();

        ServiceProvider<HelloPicoWorld> helloProvider1 = services.lookup(HelloPicoWorld.class);
        assertThat(helloProvider1,
                   notNullValue());

        ServiceProvider<HelloPicoWorldImpl> helloProvider2 = services.lookup(HelloPicoWorldImpl.class);
        assertThat(helloProvider1,
                   sameInstance(helloProvider2));
        assertThat(helloProvider1.id(),
                   equalTo(HelloPicoWorldImpl.class.getName()));
        assertThat(helloProvider1.currentActivationPhase(),
                   equalTo(Phase.INIT));
        assertThat(helloProvider1.description(),
                   equalTo(HelloPicoWorldImpl.class.getSimpleName() + ":" + Phase.INIT));

        ServiceInfo serviceInfo = helloProvider1.serviceInfo();
        assertThat(serviceInfo.serviceTypeName(),
                   equalTo(TypeName.create(HelloPicoWorldImpl.class)));
        assertThat(serviceInfo.contractsImplemented(),
                   containsInAnyOrder(TypeName.create(HelloPicoWorld.class)));
        assertThat(serviceInfo.externalContractsImplemented().size(),
                   equalTo(0));
        assertThat(serviceInfo.scopeTypeNames(),
                   containsInAnyOrder(TypeName.create(Singleton.class)));
        assertThat(serviceInfo.qualifiers().size(),
                   equalTo(0));
        assertThat(serviceInfo.activatorTypeName().orElseThrow(),
                   equalTo(TypeName.create(HelloPicoImpl$$picoActivator.class)));
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

        ServiceProvider<PicoWorld> worldProvider1 = services.lookup(PicoWorld.class);
        assertThat(worldProvider1, notNullValue());
        assertThat(worldProvider1.description(),
                   equalTo("PicoWorldImpl:INIT"));

        // now activate
        HelloPicoWorld hello1 = helloProvider1.get();
        MatcherAssert.assertThat(hello1.sayHello(),
                                 equalTo("Hello pico"));
        assertThat(helloProvider1.currentActivationPhase(),
                   equalTo(Phase.ACTIVE));
        assertThat(helloProvider1.description(),
                   equalTo("HelloPicoWorldImpl:ACTIVE"));

        // world should be active now too, since Hello should have implicitly activated it
        assertThat(worldProvider1.description(),
                   equalTo("PicoWorldImpl:ACTIVE"));

        // check the post construct counts
        MatcherAssert.assertThat(((HelloPicoWorldImpl) helloProvider1.get()).postConstructCallCount(),
                                 equalTo(1));
        MatcherAssert.assertThat(((HelloPicoWorldImpl) helloProvider1.get()).preDestroyCallCount(),
                                 equalTo(0));

        // deactivate just the Hello service
        ActivationResult result = helloProvider1.deActivator().orElseThrow()
                .deactivate(DeActivationRequest.create());
        assertThat(result.finished(), is(true));
        assertThat(result.success(), is(true));
        assertThat(result.serviceProvider(), sameInstance(helloProvider2));
        assertThat(result.finishingActivationPhase(),
                   is(Phase.DESTROYED));
        assertThat(result.startingActivationPhase(),
                   is(Phase.ACTIVE));
        assertThat(helloProvider1.description(),
                   equalTo("HelloPicoWorldImpl:DESTROYED"));
        MatcherAssert.assertThat(((HelloPicoWorldImpl) hello1).postConstructCallCount(),
                                 equalTo(1));
        MatcherAssert.assertThat(((HelloPicoWorldImpl) hello1).preDestroyCallCount(),
                                 equalTo(1));
        assertThat(worldProvider1.description(),
                   equalTo("PicoWorldImpl:ACTIVE"));
    }

    @Test
    void viaInjector() {
        PicoServices picoServices = PicoServices.picoServices().orElseThrow();
        Injector injector = picoServices.injector().orElseThrow();

        HelloPicoImpl$$picoActivator subversiveWay = new HelloPicoImpl$$picoActivator();
        subversiveWay.picoServices(Optional.of(picoServices));

        ActivationResult result = injector.activateInject(subversiveWay, InjectorOptions.builder().build());
        assertThat(result.finished(), is(true));
        assertThat(result.success(), is(true));

        HelloPicoWorld hello1 = subversiveWay.serviceRef().orElseThrow();
        MatcherAssert.assertThat(hello1.sayHello(),
                                 equalTo("Hello pico"));
        MatcherAssert.assertThat(subversiveWay.currentActivationPhase(),
                                 equalTo(Phase.ACTIVE));

        MatcherAssert.assertThat(hello1,
                                 sameInstance(subversiveWay.get()));
        assertThat(subversiveWay, sameInstance(result.serviceProvider()));

        // the above is subversive because it is disconnected from the "real" activator
        Services services = picoServices.services();
        ServiceProvider<?> realHelloProvider =
                ((DefaultServices) services).serviceProviderFor(TypeName.create(HelloPicoWorldImpl.class));
        assertThat(subversiveWay, not(sameInstance(realHelloProvider)));

        assertThat(realHelloProvider.currentActivationPhase(),
                   equalTo(Phase.INIT));

        result = injector.deactivate(subversiveWay, InjectorOptions.builder().build());
        assertThat(result.success(), is(true));
    }

    @Test
    void injectionPlanResolved() {
        HelloPicoImpl$$picoActivator activator = new HelloPicoImpl$$picoActivator();
        activator.picoServices(PicoServices.picoServices());

        ActivationResult result = activator.activate(ActivationRequest.builder().targetPhase(Phase.INJECTING).build());
        assertThat(result.success(), is(true));

        MatcherAssert.assertThat(activator.currentActivationPhase(), is(Phase.INJECTING));
        MatcherAssert.assertThat(activator.serviceRef().orElseThrow().postConstructCallCount(), equalTo(0));
        MatcherAssert.assertThat(activator.serviceRef().orElseThrow().preDestroyCallCount(), equalTo(0));

        Map<String, ? extends InjectionPlan> injectionPlan = result.injectionPlans();
        assertThat(injectionPlan.keySet(), containsInAnyOrder(
                "io.helidon.pico.runtime.testsubjects.world",
                "io.helidon.pico.runtime.testsubjects.worldRef",
                "io.helidon.pico.runtime.testsubjects.redWorld",
                "io.helidon.pico.runtime.testsubjects.listOfWorlds",
                "io.helidon.pico.runtime.testsubjects.listOfWorldRefs",
                "io.helidon.pico.runtime.testsubjects.world|1(1)"));
        InjectionPlan plan = injectionPlan.get("io.helidon.pico.runtime.testsubjects.world");
        assertThat(plan.wasResolved(), is(true));
        assertThat(plan.resolved().orElseThrow().getClass(), equalTo(PicoWorldImpl.class));
        plan = injectionPlan.get("io.helidon.pico.runtime.testsubjects.redWorld");
        assertThat(plan.wasResolved(), is(true));
        assertThat(plan.resolved().orElseThrow().getClass(), equalTo(Optional.class));
        plan = injectionPlan.get("io.helidon.pico.runtime.testsubjects.worldRef");
        assertThat(plan.wasResolved(), is(true));
        assertThat(plan.resolved().orElseThrow().getClass(), equalTo(PicoWorldImpl$$picoActivator.class));
        plan = injectionPlan.get("io.helidon.pico.runtime.testsubjects.listOfWorlds");
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
