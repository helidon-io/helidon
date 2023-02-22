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

package io.helidon.pico.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.ActivationRequest;
import io.helidon.pico.ActivationResult;
import io.helidon.pico.Application;
import io.helidon.pico.DeActivationRequest;
import io.helidon.pico.DefaultActivationRequest;
import io.helidon.pico.DefaultBootstrap;
import io.helidon.pico.Injector;
import io.helidon.pico.InjectorOptions;
import io.helidon.pico.Phase;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.services.testsubjects.HelloPicoApplication;
import io.helidon.pico.services.testsubjects.HelloPicoImpl$$picoActivator;
import io.helidon.pico.services.testsubjects.HelloPicoWorld;
import io.helidon.pico.services.testsubjects.HelloPicoWorldImpl;
import io.helidon.pico.services.testsubjects.PicoWorld;
import io.helidon.pico.services.testsubjects.PicoWorldImpl;
import io.helidon.pico.services.testsubjects.PicoWorldImpl$$picoActivator;
import io.helidon.pico.spi.BasicInjectionPlan;

import jakarta.inject.Singleton;
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
    private static final int EXPECTED_MODULES = 2;

    @BeforeEach
    void setUp() {
        tearDown();
        Config config = Config.create(
                ConfigSources.create(
                        Map.of(PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_PERMITS_DYNAMIC, "true"), "config-1"));
        PicoServices.globalBootstrap(DefaultBootstrap.builder().config(config).build());
    }

    @AfterEach
    void tearDown() {
        HelloPicoApplication.ENABLED = true;
        SimplePicoTestingSupport.resetAll();
    }

    @Test
    void sanity() {
        Optional<PicoServices> picoServices = PicoServices.picoServices();
        Services services = picoServices.orElseThrow().services();

        List<ServiceProvider<io.helidon.pico.Module>> moduleProviders = services.lookupAll(io.helidon.pico.Module.class);
        assertThat(moduleProviders.size(),
                   equalTo(EXPECTED_MODULES));
        List<String> descriptions = Utils.toDescriptions(moduleProviders);
        assertThat(descriptions,
                   containsInAnyOrder("EmptyModule:ACTIVE", "HelloPicoModule:ACTIVE"));

        List<ServiceProvider<Application>> applications = services.lookupAll(Application.class);
        assertThat(applications.size(),
                   equalTo(1));
        assertThat(Utils.toDescriptions(applications),
                   containsInAnyOrder("HelloPicoApplication:ACTIVE"));
    }

    @Test
    void standardActivationWithNoApplicationEnabled() {
        HelloPicoApplication.ENABLED = false;
        Optional<PicoServices> picoServices = PicoServices.picoServices();
        ((DefaultPicoServices) picoServices.orElseThrow()).reset(true);

        standardActivation();
    }

    @Test
    void standardActivation() {
        Optional<PicoServices> picoServices = PicoServices.picoServices();
        Services services = picoServices.orElseThrow().services();

        ServiceProvider<HelloPicoWorld> helloProvider1 = services.lookup(HelloPicoWorld.class);
        assertThat(helloProvider1, notNullValue());

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
                   equalTo(HelloPicoWorldImpl.class.getName()));
        assertThat(serviceInfo.contractsImplemented(),
                   containsInAnyOrder(HelloPicoWorld.class.getName()));
        assertThat(serviceInfo.externalContractsImplemented().size(),
                   equalTo(0));
        assertThat(serviceInfo.scopeTypeNames(),
                   containsInAnyOrder(Singleton.class.getName()));
        assertThat(serviceInfo.qualifiers().size(),
                   equalTo(0));
        assertThat(serviceInfo.activatorTypeName().get(),
                   equalTo(HelloPicoImpl$$picoActivator.class.getName()));
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
        assertThat(hello1.sayHello(),
                   equalTo("Hello pico"));
        assertThat(helloProvider1.currentActivationPhase(),
                   equalTo(Phase.ACTIVE));
        assertThat(helloProvider1.description(),
                   equalTo("HelloPicoWorldImpl:ACTIVE"));

        // world should be active now too, since Hello should have implicitly activated it
        assertThat(worldProvider1.description(),
                   equalTo("PicoWorldImpl:ACTIVE"));

        // check the post construct counts
        assertThat(((HelloPicoWorldImpl) helloProvider1.get()).postConstructCallCount(),
                   equalTo(1));
        assertThat(((HelloPicoWorldImpl) helloProvider1.get()).preDestroyCallCount(),
                   equalTo(0));

        // deactivate just the Hello service
        ActivationResult result = helloProvider1.deActivator().orElseThrow()
                .deactivate(DeActivationRequest.DEFAULT.get());
        assertThat(result.finished(), is(true));
        assertThat(result.success(), is(true));
        assertThat(result.serviceProvider(), sameInstance(helloProvider2));
        assertThat(result.finishingActivationPhase(),
                   is(Phase.DESTROYED));
        assertThat(result.startingActivationPhase(),
                   is(Phase.ACTIVE));
        assertThat(helloProvider1.description(),
                   equalTo("HelloPicoWorldImpl:DESTROYED"));
        assertThat(((HelloPicoWorldImpl) hello1).postConstructCallCount(),
                   equalTo(1));
        assertThat(((HelloPicoWorldImpl) hello1).preDestroyCallCount(),
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

        ActivationResult result = injector.activateInject(subversiveWay, InjectorOptions.DEFAULT.get());
        assertThat(result.finished(), is(true));
        assertThat(result.success(), is(true));

        HelloPicoWorld hello1 = subversiveWay.serviceRef().orElseThrow();
        assertThat(hello1.sayHello(),
                   equalTo("Hello pico"));
        assertThat(subversiveWay.currentActivationPhase(),
                   equalTo(Phase.ACTIVE));

        assertThat(hello1,
                   sameInstance(subversiveWay.get()));
        assertThat(subversiveWay, sameInstance(result.serviceProvider()));

        // the above is subversive because it is disconnected from the "real" activator...
        Services services = picoServices.services();
        ServiceProvider<?> realHelloProvider = ((DefaultServices) services).serviceProviderFor(HelloPicoWorldImpl.class.getName());
        assertThat(subversiveWay, not(sameInstance(realHelloProvider)));

        assertThat(realHelloProvider.currentActivationPhase(),
                   equalTo(Phase.INIT));

        result = injector.deactivate(subversiveWay, InjectorOptions.DEFAULT.get());
        assertThat(result.success(), is(true));
    }

    @Test
    void injectionPlanResolved() {
        HelloPicoImpl$$picoActivator activator = new HelloPicoImpl$$picoActivator();
        activator.picoServices(PicoServices.picoServices());

        ActivationResult result = activator.activate(ActivationRequest.create(Phase.INJECTING));
        assertThat(result.success(), is(true));

        assertThat(activator.currentActivationPhase(), is(Phase.INJECTING));
        assertThat(activator.serviceRef().orElseThrow().postConstructCallCount(), equalTo(0));
        assertThat(activator.serviceRef().orElseThrow().preDestroyCallCount(), equalTo(0));

        Map<String, ? extends BasicInjectionPlan> injectionPlan = result.injectionPlans();
        assertThat(injectionPlan.keySet(), containsInAnyOrder(
                "io.helidon.pico.services.testsubjects.world",
                "io.helidon.pico.services.testsubjects.worldRef",
                "io.helidon.pico.services.testsubjects.redWorld",
                "io.helidon.pico.services.testsubjects.listOfWorlds",
                "io.helidon.pico.services.testsubjects.listOfWorldRefs",
                "io.helidon.pico.services.testsubjects.world|1(1)"));
        BasicInjectionPlan plan = injectionPlan.get("io.helidon.pico.services.testsubjects.world");
        assertThat(plan.wasResolved(), is(true));
        assertThat(plan.resolved().orElseThrow().getClass(), equalTo(PicoWorldImpl.class));
        plan = injectionPlan.get("io.helidon.pico.services.testsubjects.redWorld");
        assertThat(plan.wasResolved(), is(true));
        assertThat(plan.resolved().orElseThrow().getClass(), equalTo(Optional.class));
        plan = injectionPlan.get("io.helidon.pico.services.testsubjects.worldRef");
        assertThat(plan.wasResolved(), is(true));
        assertThat(plan.resolved().orElseThrow().getClass(), equalTo(PicoWorldImpl$$picoActivator.class));
        plan = injectionPlan.get("io.helidon.pico.services.testsubjects.listOfWorlds");
        assertThat(plan.wasResolved(), is(true));
        assertThat(plan.resolved().orElseThrow().getClass(), equalTo(ArrayList.class));

        Map<String, Object> resolutions = result.resolvedDependencies();
        assertThat(resolutions.size(), equalTo(injectionPlan.size()));

        // now take us through activation
        result = activator.activate(DefaultActivationRequest.builder()
                                            .startingPhase(activator.currentActivationPhase())
                                            .build());
        assertThat(result.success(), is(true));
        assertThat(activator.currentActivationPhase(), is(Phase.ACTIVE));
        assertThat(activator.serviceRef().orElseThrow().postConstructCallCount(), equalTo(1));
        assertThat(activator.serviceRef().orElseThrow().preDestroyCallCount(), equalTo(0));

        // these should have happened prior, so not there any longer
        assertThat(result.injectionPlans(), equalTo(Map.of()));
        assertThat(result.resolvedDependencies(), equalTo(Map.of()));
    }

}
