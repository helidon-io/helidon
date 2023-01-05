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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.ActivationResult;
import io.helidon.pico.DeActivationRequest;
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
import io.helidon.pico.services.testsubjects.PicoWorldImpl$$picoActivator;

import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static io.helidon.pico.services.DefaultPicoServicesConfig.realizedBootStrapConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

/**
 * ExampleTest.
 */
class HelloPicoWorldSanityTest {

    private static final int EXPECTED_MODULES = 2;

    @BeforeEach
    void setUp() {
        tearDown();
        Config config = Config.create(
                ConfigSources.create(
                        Map.of(PicoServicesConfig.NAME + "." + PicoServicesConfig.KEY_PERMITS_DYNAMIC, "true"), "config-1"));
        realizedBootStrapConfig(Optional.of(config));
    }

    @AfterEach
    void tearDown() {
        HelloPicoApplication.ENABLED = true;
        PicoTestingSupport.resetAll();
        HelloPicoImpl$$picoActivator.INSTANCE.reset(true);
        PicoWorldImpl$$picoActivator.INSTANCE.reset(true);
    }

    @Test
    void sanity() {
        Optional<PicoServices> picoServices = PicoServices.picoServices();
        Services services = picoServices.orElseThrow().services();

        List<ServiceProvider<io.helidon.pico.Module>> moduleProviders = services.lookupAll(io.helidon.pico.Module.class);
        assertThat(moduleProviders.size(),
                   equalTo(EXPECTED_MODULES));
        List<String> descriptions = DefaultServices.toDescriptions(moduleProviders);
        assertThat(descriptions,
                   containsInAnyOrder("EmptyModule:ACTIVE", "HelloPicoModule:ACTIVE"));

        // TODO:
        //        List<ServiceProvider<Application>> applications = services.lookupAll(Application.class);
        //        assertThat(applications.size(),
        //                   equalTo(1));
        //        assertThat(DefaultServices.toDescriptions(applications),
        //                   containsInAnyOrder("BasicModule:HelloPicoApplication"));
    }

    // TODO:
    @Disabled
    @Test
    void standardActivationWithNoApplicationEnabled() {
        HelloPicoApplication.ENABLED = false;
        Optional<PicoServices> picoServices = PicoServices.picoServices();
        ((DefaultPicoServices) picoServices.get()).reset(true);

        standardActivation();
    }

    // TODO:
    @Disabled
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
        assertThat(serviceInfo.activatorTypeName(),
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
                   equalTo("hello pico"));
        assertThat(helloProvider1.currentActivationPhase(),
                   equalTo(Phase.ACTIVE));
        assertThat(helloProvider1.description(),
                   equalTo("HelloPicoWorldImpl$$picoActivator:ACTIVE"));

        // world should be active now too, since Hello should have implicitly activated it
        assertThat(worldProvider1.description(),
                   equalTo("PicoWorldImpl$$picoActivator:ACTIVE"));

        // deactivate just that one service ...
        ActivationResult result = helloProvider1.deActivator().orElseThrow()
                .deactivate(DeActivationRequest.create(helloProvider1));
        assertThat(result.finished(), is(true));
        assertThat(result.success(), is(true));
        assertThat(result.serviceProvider(), sameInstance(helloProvider2));
        assertThat(helloProvider1.description(),
                   equalTo("HelloPicoWorldImpl$$picoActivator:DESTROYED"));
        assertThat(worldProvider1.description(),
                   equalTo("PicoWorldImpl$$picoActivator:ACTIVE"));
    }

//    @Test
//    public void viaInjector() {
//        Optional<PicoServices> picoServices = PicoServices.picoServices();
//        assertNotNull(picoServices);
//        Injector injector = picoServices.get().injector().get();
//        assertNotNull(injector);
//
//        HelloPicoImpl$$picoActivator subversiveWay = new HelloPicoImpl$$picoActivator();
//        Object hello = injector.activateInject(subversiveWay, null);
//        assertNotNull(hello);
//        assertEquals(ActivationPhase.ACTIVE, subversiveWay.currentActivationPhase());
//        assertSame(hello, subversiveWay.get());
//
//        // the above is subversive because it is disconnected from the "real" activator...
//        Services services = picoServices.get().services();
//        ServiceProvider<HelloPicoWorld> realHelloProvider = services.lookupFirst(HelloPicoWorld.class);
//        assertEquals(ActivationPhase.INIT, realHelloProvider.currentActivationPhase());
//        assertNotSame(hello, realHelloProvider.get());
//
//        ActivationResult<?> result = injector.deactivate(subversiveWay);
//        assertTrue(DefaultActivationResult.isSuccess(result));
//    }
//
//    @Test
//    public void dependencies() throws Exception  {
//        HelloPicoImpl$$picoActivator activator = new HelloPicoImpl$$picoActivator();
//        Dependencies deps = activator.dependencies();
//        assertEquals("{\n"
//                             + "  \"dependencies\" : [ {\n"
//                             + "    \"ipDependencies\" : [ {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"world\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.world\",\n"
//                             + "      \"listWrapped\" : false,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : false,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : null\n"
//                             + "  }, {\n"
//                             + "    \"ipDependencies\" : [ {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"worldRef\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.worldRef\",\n"
//                             + "      \"listWrapped\" : false,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : true,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : null\n"
//                             + "  }, {\n"
//                             + "    \"ipDependencies\" : [ {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"listOfWorlds\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.listOfWorlds\",\n"
//                             + "      \"listWrapped\" : true,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : false,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : null\n"
//                             + "  }, {\n"
//                             + "    \"ipDependencies\" : [ {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"listOfWorldRefs\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.listOfWorldRefs\",\n"
//                             + "      \"listWrapped\" : true,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : true,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : null\n"
//                             + "  }, {\n"
//                             + "    \"ipDependencies\" : [ {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"redWorld\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.redWorld\",\n"
//                             + "      \"listWrapped\" : false,\n"
//                             + "      \"optionalWrapped\" : true,\n"
//                             + "      \"providerWrapped\" : false,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : null\n"
//                             + "  }, {\n"
//                             + "    \"ipDependencies\" : [ {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 1,\n"
//                             + "      \"elementKind\" : \"METHOD\",\n"
//                             + "      \"elementName\" : \"world\",\n"
//                             + "      \"elementOffset\" : 1,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.world|1(1)\",\n"
//                             + "      \"listWrapped\" : false,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : false,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : null\n"
//                             + "  } ],\n"
//                             + "  \"forServiceTypeName\" : \"io.helidon.pico.example.HelloImpl\"\n"
//                             + "}", prettyPrintJson(deps));
//    }
//
//    @Test
//    public void injectionPlanResolved() throws Exception  {
//        HelloPicoImpl$$picoActivator activator = new HelloPicoImpl$$picoActivator();
//        activator.picoServices(PicoServices.picoServices().get());
//        assertEquals("{\n"
//                             + "  \"io.helidon.pico.example.listOfWorldRefs\" : {\n"
//                             + "    \"identity\" : \"io.helidon.pico.example.listOfWorldRefs\",\n"
//                             + "    \"ipInfo\" : {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"listOfWorldRefs\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.listOfWorldRefs\",\n"
//                             + "      \"listWrapped\" : true,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : true,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    },\n"
//                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
//                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
//                             + ".WorldImpl:ACTIVE\"\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : [ {\n"
//                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
//                             + ".WorldImpl:ACTIVE\"\n"
//                             + "    } ],\n"
//                             + "    \"wasResolved\" : true\n"
//                             + "  },\n"
//                             + "  \"io.helidon.pico.example.listOfWorlds\" : {\n"
//                             + "    \"identity\" : \"io.helidon.pico.example.listOfWorlds\",\n"
//                             + "    \"ipInfo\" : {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"listOfWorlds\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.listOfWorlds\",\n"
//                             + "      \"listWrapped\" : true,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : false,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    },\n"
//                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
//                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
//                             + ".WorldImpl:ACTIVE\"\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : [ {\n"
//                             + "      \"name\" : \"unknown\"\n"
//                             + "    } ],\n"
//                             + "    \"wasResolved\" : true\n"
//                             + "  },\n"
//                             + "  \"io.helidon.pico.example.redWorld\" : {\n"
//                             + "    \"identity\" : \"io.helidon.pico.example.redWorld\",\n"
//                             + "    \"ipInfo\" : {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"redWorld\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.redWorld\",\n"
//                             + "      \"listWrapped\" : false,\n"
//                             + "      \"optionalWrapped\" : true,\n"
//                             + "      \"providerWrapped\" : false,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    },\n"
//                             + "    \"ipQualifiedServiceProviders\" : [ ],\n"
//                             + "    \"resolved\" : null,\n"
//                             + "    \"wasResolved\" : true\n"
//                             + "  },\n"
//                             + "  \"io.helidon.pico.example.world\" : {\n"
//                             + "    \"identity\" : \"io.helidon.pico.example.world\",\n"
//                             + "    \"ipInfo\" : {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"world\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.world\",\n"
//                             + "      \"listWrapped\" : false,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : false,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    },\n"
//                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
//                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
//                             + ".WorldImpl:ACTIVE\"\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : {\n"
//                             + "      \"name\" : \"unknown\"\n"
//                             + "    },\n"
//                             + "    \"wasResolved\" : true\n"
//                             + "  },\n"
//                             + "  \"io.helidon.pico.example.worldRef\" : {\n"
//                             + "    \"identity\" : \"io.helidon.pico.example.worldRef\",\n"
//                             + "    \"ipInfo\" : {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"worldRef\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.worldRef\",\n"
//                             + "      \"listWrapped\" : false,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : true,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    },\n"
//                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
//                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
//                             + ".WorldImpl:ACTIVE\"\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : {\n"
//                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
//                             + ".WorldImpl:ACTIVE\"\n"
//                             + "    },\n"
//                             + "    \"wasResolved\" : true\n"
//                             + "  },\n"
//                             + "  \"io.helidon.pico.example.world|1(1)\" : {\n"
//                             + "    \"identity\" : \"io.helidon.pico.example.world|1(1)\",\n"
//                             + "    \"ipInfo\" : {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 1,\n"
//                             + "      \"elementKind\" : \"METHOD\",\n"
//                             + "      \"elementName\" : \"world\",\n"
//                             + "      \"elementOffset\" : 1,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.world|1(1)\",\n"
//                             + "      \"listWrapped\" : false,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : false,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    },\n"
//                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
//                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
//                             + ".WorldImpl:ACTIVE\"\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : {\n"
//                             + "      \"name\" : \"unknown\"\n"
//                             + "    },\n"
//                             + "    \"wasResolved\" : true\n"
//                             + "  }\n"
//                             + "}", prettyPrintJson(activator.getOrCreateInjectionPlan(true)));
//    }
//
//    @Test
//    public void injectionPlanUnresolved() throws Exception  {
//        HelloPicoImpl$$picoActivator activator = new HelloPicoImpl$$picoActivator();
//        activator.picoServices(PicoServices.picoServices().get());
//        assertEquals("{\n"
//                             + "  \"io.helidon.pico.example.listOfWorldRefs\" : {\n"
//                             + "    \"identity\" : \"io.helidon.pico.example.listOfWorldRefs\",\n"
//                             + "    \"ipInfo\" : {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"listOfWorldRefs\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.listOfWorldRefs\",\n"
//                             + "      \"listWrapped\" : true,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : true,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    },\n"
//                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
//                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
//                             + ".WorldImpl:INIT\"\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : null,\n"
//                             + "    \"wasResolved\" : false\n"
//                             + "  },\n"
//                             + "  \"io.helidon.pico.example.listOfWorlds\" : {\n"
//                             + "    \"identity\" : \"io.helidon.pico.example.listOfWorlds\",\n"
//                             + "    \"ipInfo\" : {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"listOfWorlds\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.listOfWorlds\",\n"
//                             + "      \"listWrapped\" : true,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : false,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    },\n"
//                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
//                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
//                             + ".WorldImpl:INIT\"\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : null,\n"
//                             + "    \"wasResolved\" : false\n"
//                             + "  },\n"
//                             + "  \"io.helidon.pico.example.redWorld\" : {\n"
//                             + "    \"identity\" : \"io.helidon.pico.example.redWorld\",\n"
//                             + "    \"ipInfo\" : {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"redWorld\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.redWorld\",\n"
//                             + "      \"listWrapped\" : false,\n"
//                             + "      \"optionalWrapped\" : true,\n"
//                             + "      \"providerWrapped\" : false,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    },\n"
//                             + "    \"ipQualifiedServiceProviders\" : [ ],\n"
//                             + "    \"resolved\" : null,\n"
//                             + "    \"wasResolved\" : false\n"
//                             + "  },\n"
//                             + "  \"io.helidon.pico.example.world\" : {\n"
//                             + "    \"identity\" : \"io.helidon.pico.example.world\",\n"
//                             + "    \"ipInfo\" : {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"world\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.world\",\n"
//                             + "      \"listWrapped\" : false,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : false,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    },\n"
//                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
//                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
//                             + ".WorldImpl:INIT\"\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : null,\n"
//                             + "    \"wasResolved\" : false\n"
//                             + "  },\n"
//                             + "  \"io.helidon.pico.example.worldRef\" : {\n"
//                             + "    \"identity\" : \"io.helidon.pico.example.worldRef\",\n"
//                             + "    \"ipInfo\" : {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 0,\n"
//                             + "      \"elementKind\" : \"FIELD\",\n"
//                             + "      \"elementName\" : \"worldRef\",\n"
//                             + "      \"elementOffset\" : null,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.worldRef\",\n"
//                             + "      \"listWrapped\" : false,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : true,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    },\n"
//                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
//                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
//                             + ".WorldImpl:INIT\"\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : null,\n"
//                             + "    \"wasResolved\" : false\n"
//                             + "  },\n"
//                             + "  \"io.helidon.pico.example.world|1(1)\" : {\n"
//                             + "    \"identity\" : \"io.helidon.pico.example.world|1(1)\",\n"
//                             + "    \"ipInfo\" : {\n"
//                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
//                             + "      \"elementArgs\" : 1,\n"
//                             + "      \"elementKind\" : \"METHOD\",\n"
//                             + "      \"elementName\" : \"world\",\n"
//                             + "      \"elementOffset\" : 1,\n"
//                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
//                             + "      \"identity\" : \"io.helidon.pico.example.world|1(1)\",\n"
//                             + "      \"listWrapped\" : false,\n"
//                             + "      \"optionalWrapped\" : false,\n"
//                             + "      \"providerWrapped\" : false,\n"
//                             + "      \"staticDecl\" : false\n"
//                             + "    },\n"
//                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
//                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
//                             + ".WorldImpl:INIT\"\n"
//                             + "    } ],\n"
//                             + "    \"resolved\" : null,\n"
//                             + "    \"wasResolved\" : false\n"
//                             + "  }\n"
//                             + "}", prettyPrintJson(activator.getOrCreateInjectionPlan(false)));
//    }
//
//    static String prettyPrintJson(Object obj) throws Exception {
//        ObjectMapper mapper = new ObjectMapper()
//                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
//                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
//        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
//    }
//
}
