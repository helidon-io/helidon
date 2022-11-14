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

package io.helidon.pico.example;

import java.util.List;
import java.util.Optional;

import io.helidon.pico.ActivationPhase;
import io.helidon.pico.ActivationResult;
import io.helidon.pico.Injector;
import io.helidon.pico.Module;
import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.spi.ext.Dependencies;
import io.helidon.pico.spi.impl.DefaultActivationResult;
import io.helidon.pico.spi.impl.DefaultPicoServices;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExampleTest.
 */
public class ExampleTest {

    private static final int EXPECTED_MODULES = 2;

    @BeforeEach
    public void init() {
        ExampleHelloApplication.ENABLED = true;
        HelloImpl$$picodiActivator.INSTANCE.reset();
        WorldImpl$$picodiActivator.INSTANCE.reset();
        Optional<PicoServices> picoServices = PicoServices.picoServices();
        ((DefaultPicoServices) picoServices.get()).reset();
    }

    @Test
    public void standardActivationWithNoApplication() {
        ExampleHelloApplication.ENABLED = false;
        Optional<PicoServices> picoServices = PicoServices.picoServices();
        ((DefaultPicoServices) picoServices.get()).reset();

        standardActivation();
    }

    @Test
    public void standardActivation() {
        Optional<PicoServices> picoServices = PicoServices.picoServices();

        Services services = picoServices.get().services();
        assertNotNull(services);

        ServiceProvider<Hello> helloProvider1 = services.lookupFirst(Hello.class);
        assertNotNull(helloProvider1);

        ServiceProvider<HelloImpl> helloProvider2 = services.lookupFirst(HelloImpl.class);
        assertSame(helloProvider1, helloProvider2);

        assertEquals("DefaultServiceInfo(serviceTypeName=io.helidon.pico.example.HelloImpl, contractsImplemented=[io"
                        + ".helidon.pico.example.Hello], externalContractsImplemented=[], scopeTypeNames=[jakarta"
                        + ".inject.Singleton], qualifiers=[], activatorTypeName=io.helidon.pico.example"
                        + ".HelloImpl$$picodiActivator, runLevel=0, moduleName=example, weight=null)",
                     helloProvider1.serviceInfo().toString());

        String toString = String.valueOf(services.lookupFirst(Hello.class));
        assertEquals("HelloImpl$$picodiActivator:INIT:[io.helidon.pico.example.Hello]", toString);

        ServiceProvider<World> worldProvider1 = services.lookupFirst(World.class);
        assertNotNull(worldProvider1);
        assertSame(worldProvider1, services.lookupFirst(WorldImpl.class));

        toString = String.valueOf(worldProvider1);
        assertEquals("WorldImpl$$picodiActivator:INIT:[io.helidon.pico.example.World]", toString);

        assertEquals("DefaultServiceInfo(serviceTypeName=io.helidon.pico.example.WorldImpl, contractsImplemented=[io.helidon.pico"
                        + ".example.World], externalContractsImplemented=[io.helidon.pico.example.World], "
                        + "scopeTypeNames=[jakarta.inject.Singleton], qualifiers=[], activatorTypeName=io.helidon.pico"
                        + ".example.WorldImpl$$picodiActivator, runLevel=null, moduleName=example, weight=100.0)",
                worldProvider1.serviceInfo().toString());

        List<ServiceProvider<Module>> moduleProviders1 = services.lookup(Module.class);
        assertNotNull(moduleProviders1);
        assertEquals(EXPECTED_MODULES, moduleProviders1.size(), moduleProviders1.toString());
        assertEquals("BasicModule:io.helidon.pico.example.EmptyModule:ACTIVE",
                     moduleProviders1.get(0).description());
        assertEquals("BasicModule:io.helidon.pico.example.ExampleHelloModule:ACTIVE",
                     moduleProviders1.get(1).description());
        assertEquals(moduleProviders1, services.lookup(Module.class));
        assertSame(moduleProviders1.get(1).get(), services.lookup(Module.class).get(1).get());

        // now activate HelloImpl...
        assertSame(helloProvider1.get(), helloProvider2.get());
        assertEquals("HelloImpl$$picodiActivator:io.helidon.pico.example.HelloImpl:ACTIVE",
                     services.lookupFirst(Hello.class).description());

        assertEquals("WorldImpl$$picodiActivator:io.helidon.pico.example.WorldImpl:ACTIVE",
                     services.lookupFirst(World.class).description());

        Hello hello1 = helloProvider1.get();
        hello1.sayHello();

        // deactivate just that one service ...
        ActivationResult<?> result = helloProvider1.deActivator().deactivate(helloProvider1);
        assertNotNull(result);

        assertEquals("HelloImpl$$picodiActivator:io.helidon.pico.example.HelloImpl:DESTROYED",
                     services.lookupFirst(Hello.class).description());

        Hello newHello = helloProvider1.get();
        assertNotNull(newHello);
        assertNotSame(hello1, newHello);
        newHello.sayHello();

        ServiceProvider<WorldImpl> worldProvider = services.lookupFirst(WorldImpl.class);
        result = worldProvider.deActivator().deactivate(worldProvider);
        assertNotNull(result);
        assertThrows(AssertionError.class, newHello::sayHello, "world != worldRef");

        List<ServiceProvider<Module>> moduleProviders = services.lookup(Module.class);
        assertEquals(EXPECTED_MODULES, moduleProviders.size(), moduleProviders.toString());
    }

    @Test
    public void viaInjector() {
        Optional<PicoServices> picoServices = PicoServices.picoServices();
        assertNotNull(picoServices);
        Injector injector = picoServices.get().injector().get();
        assertNotNull(injector);

        HelloImpl$$picodiActivator subversiveWay = new HelloImpl$$picodiActivator();
        Object hello = injector.activateInject(subversiveWay, null);
        assertNotNull(hello);
        assertEquals(ActivationPhase.ACTIVE, subversiveWay.currentActivationPhase());
        assertSame(hello, subversiveWay.get());

        // the above is subversive because it is disconnected from the "real" activator...
        Services services = picoServices.get().services();
        ServiceProvider<Hello> realHelloProvider = services.lookupFirst(Hello.class);
        assertEquals(ActivationPhase.INIT, realHelloProvider.currentActivationPhase());
        assertNotSame(hello, realHelloProvider.get());

        ActivationResult<?> result = injector.deactivate(subversiveWay);
        assertTrue(DefaultActivationResult.isSuccess(result));
    }

    @Test
    public void dependencies() throws Exception  {
        HelloImpl$$picodiActivator activator = new HelloImpl$$picodiActivator();
        Dependencies deps = activator.dependencies();
        assertEquals("{\n"
                             + "  \"dependencies\" : [ {\n"
                             + "    \"ipDependencies\" : [ {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"world\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.world\",\n"
                             + "      \"listWrapped\" : false,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : false,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    } ],\n"
                             + "    \"resolved\" : null\n"
                             + "  }, {\n"
                             + "    \"ipDependencies\" : [ {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"worldRef\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.worldRef\",\n"
                             + "      \"listWrapped\" : false,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : true,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    } ],\n"
                             + "    \"resolved\" : null\n"
                             + "  }, {\n"
                             + "    \"ipDependencies\" : [ {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"listOfWorlds\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.listOfWorlds\",\n"
                             + "      \"listWrapped\" : true,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : false,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    } ],\n"
                             + "    \"resolved\" : null\n"
                             + "  }, {\n"
                             + "    \"ipDependencies\" : [ {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"listOfWorldRefs\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.listOfWorldRefs\",\n"
                             + "      \"listWrapped\" : true,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : true,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    } ],\n"
                             + "    \"resolved\" : null\n"
                             + "  }, {\n"
                             + "    \"ipDependencies\" : [ {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"redWorld\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.redWorld\",\n"
                             + "      \"listWrapped\" : false,\n"
                             + "      \"optionalWrapped\" : true,\n"
                             + "      \"providerWrapped\" : false,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    } ],\n"
                             + "    \"resolved\" : null\n"
                             + "  }, {\n"
                             + "    \"ipDependencies\" : [ {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 1,\n"
                             + "      \"elementKind\" : \"METHOD\",\n"
                             + "      \"elementName\" : \"world\",\n"
                             + "      \"elementOffset\" : 1,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.world|1(1)\",\n"
                             + "      \"listWrapped\" : false,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : false,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    } ],\n"
                             + "    \"resolved\" : null\n"
                             + "  } ],\n"
                             + "  \"forServiceTypeName\" : \"io.helidon.pico.example.HelloImpl\"\n"
                             + "}", prettyPrintJson(deps));
    }

    @Test
    public void injectionPlanResolved() throws Exception  {
        HelloImpl$$picodiActivator activator = new HelloImpl$$picodiActivator();
        activator.picoServices(PicoServices.picoServices().get());
        assertEquals("{\n"
                             + "  \"io.helidon.pico.example.listOfWorldRefs\" : {\n"
                             + "    \"identity\" : \"io.helidon.pico.example.listOfWorldRefs\",\n"
                             + "    \"ipInfo\" : {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"listOfWorldRefs\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.listOfWorldRefs\",\n"
                             + "      \"listWrapped\" : true,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : true,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    },\n"
                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
                             + ".WorldImpl:ACTIVE\"\n"
                             + "    } ],\n"
                             + "    \"resolved\" : [ {\n"
                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
                             + ".WorldImpl:ACTIVE\"\n"
                             + "    } ],\n"
                             + "    \"wasResolved\" : true\n"
                             + "  },\n"
                             + "  \"io.helidon.pico.example.listOfWorlds\" : {\n"
                             + "    \"identity\" : \"io.helidon.pico.example.listOfWorlds\",\n"
                             + "    \"ipInfo\" : {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"listOfWorlds\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.listOfWorlds\",\n"
                             + "      \"listWrapped\" : true,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : false,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    },\n"
                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
                             + ".WorldImpl:ACTIVE\"\n"
                             + "    } ],\n"
                             + "    \"resolved\" : [ {\n"
                             + "      \"name\" : \"unknown\"\n"
                             + "    } ],\n"
                             + "    \"wasResolved\" : true\n"
                             + "  },\n"
                             + "  \"io.helidon.pico.example.redWorld\" : {\n"
                             + "    \"identity\" : \"io.helidon.pico.example.redWorld\",\n"
                             + "    \"ipInfo\" : {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"redWorld\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.redWorld\",\n"
                             + "      \"listWrapped\" : false,\n"
                             + "      \"optionalWrapped\" : true,\n"
                             + "      \"providerWrapped\" : false,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    },\n"
                             + "    \"ipQualifiedServiceProviders\" : [ ],\n"
                             + "    \"resolved\" : null,\n"
                             + "    \"wasResolved\" : true\n"
                             + "  },\n"
                             + "  \"io.helidon.pico.example.world\" : {\n"
                             + "    \"identity\" : \"io.helidon.pico.example.world\",\n"
                             + "    \"ipInfo\" : {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"world\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.world\",\n"
                             + "      \"listWrapped\" : false,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : false,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    },\n"
                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
                             + ".WorldImpl:ACTIVE\"\n"
                             + "    } ],\n"
                             + "    \"resolved\" : {\n"
                             + "      \"name\" : \"unknown\"\n"
                             + "    },\n"
                             + "    \"wasResolved\" : true\n"
                             + "  },\n"
                             + "  \"io.helidon.pico.example.worldRef\" : {\n"
                             + "    \"identity\" : \"io.helidon.pico.example.worldRef\",\n"
                             + "    \"ipInfo\" : {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"worldRef\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.worldRef\",\n"
                             + "      \"listWrapped\" : false,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : true,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    },\n"
                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
                             + ".WorldImpl:ACTIVE\"\n"
                             + "    } ],\n"
                             + "    \"resolved\" : {\n"
                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
                             + ".WorldImpl:ACTIVE\"\n"
                             + "    },\n"
                             + "    \"wasResolved\" : true\n"
                             + "  },\n"
                             + "  \"io.helidon.pico.example.world|1(1)\" : {\n"
                             + "    \"identity\" : \"io.helidon.pico.example.world|1(1)\",\n"
                             + "    \"ipInfo\" : {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 1,\n"
                             + "      \"elementKind\" : \"METHOD\",\n"
                             + "      \"elementName\" : \"world\",\n"
                             + "      \"elementOffset\" : 1,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.world|1(1)\",\n"
                             + "      \"listWrapped\" : false,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : false,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    },\n"
                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
                             + ".WorldImpl:ACTIVE\"\n"
                             + "    } ],\n"
                             + "    \"resolved\" : {\n"
                             + "      \"name\" : \"unknown\"\n"
                             + "    },\n"
                             + "    \"wasResolved\" : true\n"
                             + "  }\n"
                             + "}", prettyPrintJson(activator.getOrCreateInjectionPlan(true)));
    }

    @Test
    public void injectionPlanUnresolved() throws Exception  {
        HelloImpl$$picodiActivator activator = new HelloImpl$$picodiActivator();
        activator.picoServices(PicoServices.picoServices().get());
        assertEquals("{\n"
                             + "  \"io.helidon.pico.example.listOfWorldRefs\" : {\n"
                             + "    \"identity\" : \"io.helidon.pico.example.listOfWorldRefs\",\n"
                             + "    \"ipInfo\" : {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"listOfWorldRefs\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.listOfWorldRefs\",\n"
                             + "      \"listWrapped\" : true,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : true,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    },\n"
                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
                             + ".WorldImpl:INIT\"\n"
                             + "    } ],\n"
                             + "    \"resolved\" : null,\n"
                             + "    \"wasResolved\" : false\n"
                             + "  },\n"
                             + "  \"io.helidon.pico.example.listOfWorlds\" : {\n"
                             + "    \"identity\" : \"io.helidon.pico.example.listOfWorlds\",\n"
                             + "    \"ipInfo\" : {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"listOfWorlds\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.listOfWorlds\",\n"
                             + "      \"listWrapped\" : true,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : false,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    },\n"
                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
                             + ".WorldImpl:INIT\"\n"
                             + "    } ],\n"
                             + "    \"resolved\" : null,\n"
                             + "    \"wasResolved\" : false\n"
                             + "  },\n"
                             + "  \"io.helidon.pico.example.redWorld\" : {\n"
                             + "    \"identity\" : \"io.helidon.pico.example.redWorld\",\n"
                             + "    \"ipInfo\" : {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"redWorld\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.redWorld\",\n"
                             + "      \"listWrapped\" : false,\n"
                             + "      \"optionalWrapped\" : true,\n"
                             + "      \"providerWrapped\" : false,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    },\n"
                             + "    \"ipQualifiedServiceProviders\" : [ ],\n"
                             + "    \"resolved\" : null,\n"
                             + "    \"wasResolved\" : false\n"
                             + "  },\n"
                             + "  \"io.helidon.pico.example.world\" : {\n"
                             + "    \"identity\" : \"io.helidon.pico.example.world\",\n"
                             + "    \"ipInfo\" : {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"world\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.world\",\n"
                             + "      \"listWrapped\" : false,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : false,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    },\n"
                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
                             + ".WorldImpl:INIT\"\n"
                             + "    } ],\n"
                             + "    \"resolved\" : null,\n"
                             + "    \"wasResolved\" : false\n"
                             + "  },\n"
                             + "  \"io.helidon.pico.example.worldRef\" : {\n"
                             + "    \"identity\" : \"io.helidon.pico.example.worldRef\",\n"
                             + "    \"ipInfo\" : {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 0,\n"
                             + "      \"elementKind\" : \"FIELD\",\n"
                             + "      \"elementName\" : \"worldRef\",\n"
                             + "      \"elementOffset\" : null,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.worldRef\",\n"
                             + "      \"listWrapped\" : false,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : true,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    },\n"
                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
                             + ".WorldImpl:INIT\"\n"
                             + "    } ],\n"
                             + "    \"resolved\" : null,\n"
                             + "    \"wasResolved\" : false\n"
                             + "  },\n"
                             + "  \"io.helidon.pico.example.world|1(1)\" : {\n"
                             + "    \"identity\" : \"io.helidon.pico.example.world|1(1)\",\n"
                             + "    \"ipInfo\" : {\n"
                             + "      \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "      \"elementArgs\" : 1,\n"
                             + "      \"elementKind\" : \"METHOD\",\n"
                             + "      \"elementName\" : \"world\",\n"
                             + "      \"elementOffset\" : 1,\n"
                             + "      \"elementTypeName\" : \"io.helidon.pico.example.World\",\n"
                             + "      \"identity\" : \"io.helidon.pico.example.world|1(1)\",\n"
                             + "      \"listWrapped\" : false,\n"
                             + "      \"optionalWrapped\" : false,\n"
                             + "      \"providerWrapped\" : false,\n"
                             + "      \"staticDecl\" : false\n"
                             + "    },\n"
                             + "    \"ipQualifiedServiceProviders\" : [ {\n"
                             + "      \"description\" : \"WorldImpl$$picodiActivator:io.helidon.pico.example"
                             + ".WorldImpl:INIT\"\n"
                             + "    } ],\n"
                             + "    \"resolved\" : null,\n"
                             + "    \"wasResolved\" : false\n"
                             + "  }\n"
                             + "}", prettyPrintJson(activator.getOrCreateInjectionPlan(false)));
    }

    static String prettyPrintJson(Object obj) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }

}
