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

package io.helidon.pico.testsubjects.ext.tbox.impl;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.helidon.pico.RunLevel;
import io.helidon.pico.ActivationResult;
import io.helidon.pico.ActivationStatus;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.Module;
import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.spi.ext.ExtendedServices;
import io.helidon.pico.spi.ext.Resetable;
import io.helidon.pico.test.utils.JsonUtils;
import io.helidon.pico.testsubjects.ext.stacking.Intercepted;
import io.helidon.pico.testsubjects.ext.tbox.AbstractSaw;
import io.helidon.pico.testsubjects.ext.tbox.Blank;
import io.helidon.pico.testsubjects.ext.tbox.Hammer;
import io.helidon.pico.testsubjects.ext.tbox.TestingSingleton;
import io.helidon.pico.testsubjects.ext.tbox.Tool;
import io.helidon.pico.testsubjects.ext.tbox.ToolBox;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.utils.CommonUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expectation here is that the annotation processor ran, and we can use standard injection and pico-di registry services, etc.
 */
public class ToolBoxTest {

    private PicoServices picoServices;
    private Services services;
    private ExtendedServices extendedServices;

    @BeforeEach
    public void init() {
        PicoServices picoServices = PicoServices.picoServices().get();
        ((Resetable) picoServices).reset();
        this.picoServices = picoServices;
        this.services = picoServices.services();
        assert (services instanceof ExtendedServices);
        this.extendedServices = (ExtendedServices) services;
    }

    @Test
    public void sanity() {
        assertNotNull(picoServices);
        assertNotNull(services);
    }

    @Test
    public void toolbox() {
        List<ServiceProvider<Blank>> blanks = services.lookup(Blank.class);
        assertEquals("[BlankImpl$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl.BlankImpl:INIT]",
                     String.valueOf(ServiceProvider.toDescriptions(blanks)));

        List<ServiceProvider<ToolBox>> allToolBoxes = services.lookup(ToolBox.class);
        assertEquals("[MainToolBox$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl.MainToolBox:INIT]",
                     String.valueOf(ServiceProvider.toDescriptions(allToolBoxes)));

        ToolBox toolBox = allToolBoxes.get(0).get();
        assertNotNull(toolBox);
        assertEquals(MainToolBox.class, toolBox.getClass());
        MainToolBox mtb = (MainToolBox) toolBox;
        assertEquals(1, mtb.postConstructCallCount);
        assertEquals(0, mtb.preDestroyCallCount);
        assertEquals(1, mtb.setterCallCount);
        List<Provider<Tool>> allTools = mtb.getToolsInBox();
        assertEquals(
                "[SledgeHammer$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl.SledgeHammer:INIT, "
                        + "BigHammer$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl.BigHammer:INIT, "
                        + "TableSaw$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.TableSaw:INIT, "
                        + "HandSaw$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl.HandSaw:INIT, "
                        + "LittleHammer$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl"
                        + ".LittleHammer:INIT, Screwdriver$$picoActivator:io.helidon.pico.testsubjects.ext"
                        + ".tbox.impl.Screwdriver:ACTIVE]",
                     String.valueOf(ServiceProvider.toDescriptions(allTools)));
        assertNotNull(mtb.getScrewdriver());

        Provider<Hammer> hammer = toolBox.getPreferredHammer();
        assertNotNull(hammer);
        assertNotNull(hammer.get());
        assertSame(hammer.get(), hammer.get());
        assertEquals(BigHammer.class, hammer.get().getClass());
        assertEquals(
                "[SledgeHammer$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl.SledgeHammer:INIT, "
                        + "BigHammer$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl.BigHammer:ACTIVE, "
                        + "TableSaw$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.TableSaw:INIT, "
                        + "HandSaw$$picoActivator:io.helidon.pico.testsubjects.ext"
                        + ".tbox.impl.HandSaw:INIT, "
                        + "LittleHammer$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl"
                        + ".LittleHammer:ACTIVE, Screwdriver$$picoActivator:io.helidon.pico.testsubjects.ext"
                        + ".tbox.impl.Screwdriver:ACTIVE]",
                     String.valueOf(ServiceProvider.toDescriptions(allTools)));
        assertEquals(LittleHammer.class, ((BigHammer) hammer.get()).littleHammer.get().getClass());

        assertEquals(
                "[SledgeHammer$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl.SledgeHammer:INIT, "
                        + "BigHammer$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl.BigHammer:ACTIVE, "
                        + "LittleHammer$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl"
                        + ".LittleHammer:ACTIVE]",
                     String.valueOf(ServiceProvider.toDescriptions(((MainToolBox) toolBox).getAllHammers())));
        assertEquals("BigHammer$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl.BigHammer:ACTIVE",
                     ((ServiceProvider) ((MainToolBox) toolBox).getBigHammer()).description());
    }

    @Test
    public void testClasses() {
        assertNotNull(services.lookupFirst(TestingSingleton.class));
    }

    /**
     * This assumes {@link io.helidon.pico.tools.processor.Options#TAG_AUTO_ADD_NON_CONTRACT_INTERFACES} has
     * been enabled - see pom.xml
     */
    @Test
    public void autoExternalContracts() {
        List<ServiceProvider<Serializable>> allSerializable = services.lookup(Serializable.class);
        assertEquals(
                "[ASerialProviderImpl$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl"
                        + ".ASerialProviderImpl:INIT, "
                        + "Screwdriver$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl.Screwdriver:INIT, "
                        + "BasicSingletonServiceProvider:io"
                        + ".helidon.pico.testsubjects.hello.WorldImpl:INIT]",
                     ServiceProvider.toDescriptions(allSerializable).toString());
    }

    @Test
    public void providerTest() {
        Serializable s1 = services.lookupFirst(Serializable.class).get();
        assertNotNull(s1);
        assertEquals(String.class, s1.getClass(),
                     ASerialProviderImpl.class + " is a higher weight and should have been returned for " + String.class);
        assertNotEquals(s1, services.lookupFirst(Serializable.class).get());
    }

    @Test
    public void modules() {
        List<ServiceProvider<Module>> allModules = services.lookup(Module.class);
        assertNotNull(allModules);
        assertEquals("[\n"
                             + "    \"BasicModule:io.helidon.pico.example.EmptyModule:ACTIVE\",\n"
                             + "    \"BasicModule:io.helidon.pico.example.ExampleHelloModule:ACTIVE\",\n"
                             + "    \"BasicModule:io.helidon.pico.testsubjects.ext.tbox.picoModule:ACTIVE\",\n"
                             + "    \"BasicModule:io.helidon.pico.testsubjects.ext.tbox.picotestModule:ACTIVE\",\n"
                             + "    \"BasicModule:io.helidon.pico.testsubjects.hello.MyCustomModule:ACTIVE\"\n"
                             + "]",
                     JsonUtils.prettyPrintJson(ServiceProvider.toDescriptions(allModules)),
                     "ensure that Annotation Processors are enabled in the tools module meta-inf/services");
        List<String> names = allModules.stream()
                .sorted()
                .map(m -> m.get().name().orElse(m.get().getClass() + ":null")).collect(Collectors.toList());
        assertEquals("[\n"
                             + "    \"class io.helidon.pico.example.EmptyModule:null\",\n"
                             + "    \"example\",\n"
                             + "    \"io.helidon.pico.test.pico\",\n"
                             + "    \"io.helidon.pico.test.pico/test\",\n"
                             + "    \"MyCustomModule\"\n"
                             + "]",
                JsonUtils.prettyPrintJson(names));
    }

    /**
     * The pico module-info that was created.
     */
    @Test
    public void moduleInfo() {
        assertEquals("/*\n"
                             + " * Copyright (c) 2022 Oracle and/or its affiliates.\n"
                             + " *\n"
                             + " * Licensed under the Apache License, Version 2.0 (the \"License\");\n"
                             + " * you may not use this file except in compliance with the License.\n"
                             + " * You may obtain a copy of the License at\n"
                             + " *\n"
                             + " *     http://www.apache.org/licenses/LICENSE-2.0\n"
                             + " *\n"
                             + " * Unless required by applicable law or agreed to in writing, software\n"
                             + " * distributed under the License is distributed on an \"AS IS\" BASIS,\n"
                             + " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
                             + " * See the License for the specific language governing permissions and\n"
                             + " * limitations under the License.\n"
                             + " */\n"
                             + "\n"
                             + "module io.helidon.pico.test.pico {\n"
                             + "    requires transitive io.helidon.pico.processor;\n"
                             + "    requires transitive io.helidon.pico.test.extended.resources;\n"
                             + "    requires static jakarta.inject;\n"
                             + "    requires static jakarta.annotation;\n"
                             + "\n"
                             + "    exports io.helidon.pico.testsubjects.ext.tbox;\n"
                             + "\n"
                             + "    requires io.helidon.common;\n"
                             + "    requires java.compiler;\n"
                             + "    requires io.helidon.pico;\n"
                             + "    requires jdk.compiler;\n"
                             + "    requires io.helidon.pico.api;\n"
                             + "    requires io.helidon.pico.test.resources;\n"
                             + "    // pico module - generated by io.helidon.pico.tools.creator.impl"
                             + ".DefaultActivatorCreator\n"
                             + "    provides io.helidon.pico.spi.Module with io.helidon.pico.testsubjects.ext.tbox"
                             + ".picoModule;\n"
                             + "    // pico application - generated by io.helidon.pico.tools.creator.impl"
                             + ".DefaultActivatorCreator\n"
                             + "    provides io.helidon.pico.spi.Application with io.helidon.pico.testsubjects.ext"
                             + ".tbox.picoApplication;\n"
                             + "    // pico external contract usage - generated by io.helidon.pico.tools.creator.impl"
                             + ".DefaultActivatorCreator\n"
                             + "    uses io.helidon.pico.testsubjects.interceptor.IA;\n"
                             + "    uses io.helidon.pico.testsubjects.interceptor.IB;\n"
                             + "    uses io.helidon.pico.spi.InjectionPointProvider;\n"
                             + "    uses jakarta.inject.Provider;\n"
                             + "    uses io.helidon.pico.spi.ext.Resetable;\n"
                             + "    // pico contract usage - generated by io.helidon.pico.tools.creator.impl"
                             + ".DefaultActivatorCreator\n"
                             + "    exports io.helidon.pico.testsubjects.ext.interceptor;\n"
                             + "    exports io.helidon.pico.testsubjects.ext.provider;\n"
                             + "    exports io.helidon.pico.testsubjects.ext.stacking;\n"
                             + "}",
                     CommonUtils.loadStringFromFile("target/classes/module-info.java.pico"));
    }

    /**
     * The pico test version of module-info that was created.
     */
    @Test
    public void testModuleInfo() {
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.creator.impl"
                             + ".DefaultActivatorCreator\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module io.helidon.pico.test.pico/test {\n"
                             + "    requires io.helidon.pico.test.pico;\n"
                             + "    // pico module - generated by io.helidon.pico.tools.creator.impl"
                             + ".DefaultActivatorCreator\n"
                             + "    provides io.helidon.pico.spi.Module with io.helidon.pico.testsubjects.ext.tbox"
                             + ".picotestModule;\n"
                             + "    // pico application - generated by io.helidon.pico.tools.creator.impl"
                             + ".DefaultActivatorCreator\n"
                             + "    provides io.helidon.pico.spi.Application with io.helidon.pico.testsubjects.ext"
                             + ".tbox.picotestApplication;\n"
                             + "    // pico external contract usage - generated by io.helidon.pico.tools.creator.impl"
                             + ".DefaultActivatorCreator\n"
                             + "    uses io.helidon.pico.spi.ext.Resetable;\n"
                             + "    uses io.helidon.pico.testsubjects.ext.stacking.Intercepted;\n"
                             + "    // pico - generated by io.helidon.pico.tools.creator.impl.DefaultActivatorCreator\n"
                             + "    requires transitive io.helidon.pico;\n"
                             + "}",
                     CommonUtils.loadStringFromFile("target/test-classes/module-info.java.pico"));
    }

    @Test
    public void innerClasses() {
        Server.Builder s1 = services.lookupFirst(Server.Builder.class).get();
        assertNotNull(s1);
        assertSame(s1, services.lookupFirst(Server.Builder.class).get());

        Config.Builder c1 = services.lookupFirst(Config.Builder.class).get();
        assertNotNull(c1);
        assertSame(c1, services.lookupFirst(Config.Builder.class).get());
    }

    /**
     * Targets {@link io.helidon.pico.testsubjects.ext.tbox.AbstractSaw} with derived classes of
     * {@link io.helidon.pico.testsubjects.ext.tbox.impl.HandSaw} and {@link io.helidon.pico.testsubjects.ext.tbox.TableSaw}
     * found in different packages.
     */
    @Test
    public void hierarchyOfInjections() {
        List<ServiceProvider<AbstractSaw>> saws = services.lookup(AbstractSaw.class);
        assertEquals(
                "[TableSaw$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.TableSaw:INIT, "
                        + "HandSaw$$picoActivator:io.helidon.pico.testsubjects.ext.tbox"
                        + ".impl.HandSaw:INIT]",
                     String.valueOf(ServiceProvider.toDescriptions(saws)));
        for (ServiceProvider<AbstractSaw> saw : saws) {
            saw.get().verifyState();
        }
    }

    /**
     * This assumes the presence of module(s) + application(s) to handle all bindings, with effectively no lookups!
     */
    @Test
    public void runlevel() {
        assertEquals(1, extendedServices.lookupCount());
        List<ServiceProvider<Object>> runLevelServices = services
                .lookup(DefaultServiceInfo.builder().runLevel(RunLevel.STARTUP).build(), true);
        assertEquals(2, extendedServices.lookupCount());

        assertEquals(
                "[HelloImpl$$picodiActivator:io.helidon.pico.example.HelloImpl:INIT, StartupImpl$$picoActivator:io"
                        + ".helidon.pico.testsubjects.ext.tbox.impl.StartupImpl:INIT]",
                String.valueOf(ServiceProvider.toDescriptions(runLevelServices)));

        runLevelServices.forEach(sp -> assertNotNull(sp.get(), "activation"));
        assertEquals(2, extendedServices.lookupCount(), "activation should not have triggered any lookups");

        assertEquals(
                "[HelloImpl$$picodiActivator:io.helidon.pico.example.HelloImpl:ACTIVE, StartupImpl$$picoActivator:io"
                        + ".helidon.pico.testsubjects.ext.tbox.impl.StartupImpl:ACTIVE]",
                String.valueOf(ServiceProvider.toDescriptions(runLevelServices)));
    }

    /**
     * This assumes the presence of module(s) + application(s) to handle all bindings, with effectively no lookups!
     */
    @Test
    public void noServiceActivationRequiresLookupWhenApplicationIsPresent() {
        List<ServiceProvider<Object>> allServices = services
                .lookup(DefaultServiceInfo.builder().build(), true);
        allServices.stream()
                .filter((sp) -> !(sp instanceof Provider))
                .forEach(sp -> {
                    sp.get();
                    assertEquals(1, extendedServices.lookupCount(),
                                 "activation should not have triggered any lookups (for singletons): "
                                            + sp + " triggered lookups");
        });
    }

    @Test
    public void startupAndShutdownCallsPostConstructAndPreDestroy() throws Exception {
        assertEquals(0, TestingSingleton.getPostConstructCount());
        assertEquals(0, TestingSingleton.getPreDestroyCount());

        List<ServiceProvider<Intercepted>> allInterceptedBefore = services.lookup(Intercepted.class);

        assertEquals(0, TestingSingleton.getPostConstructCount());
        assertEquals(0, TestingSingleton.getPreDestroyCount());

        Future<Map<String, ActivationResult<?>>> future = picoServices.shutdown();
        assertNotNull(future);
        Map<String, ActivationResult<?>> map = future.get(10, TimeUnit.SECONDS);
        assertEquals(5, map.size(), map + " : expected 5 modules to be present");
        map.entrySet().forEach((e) -> {
            assertTrue(e.getValue().serviceProvider().serviceInfo().contractsImplemented().contains(Module.class.getName()));
        });

        List<ServiceProvider<Intercepted>> allInterceptedAfter = services.lookup(Intercepted.class);
        assertTrue(allInterceptedAfter.isEmpty(), "should be empty until after reset");
        List<ServiceProvider<Object>> allServices = services.lookup(DefaultServiceInfo.builder().build(), false);
        assertTrue(allServices.isEmpty(), "should be empty until after reset");

        // reset everything
        init();

        allInterceptedAfter = services.lookup(Intercepted.class);

        // things should look basically the same now
        assertEquals(ServiceProvider.toDescriptions(allInterceptedBefore), ServiceProvider.toDescriptions(allInterceptedAfter));

        TestingSingleton singleton = services.lookupFirst(TestingSingleton.class).get();
        assertNotNull(singleton);
        assertEquals(1, TestingSingleton.getPostConstructCount());
        assertEquals(0, TestingSingleton.getPreDestroyCount());

        future = picoServices.shutdown();
        assertNotNull(future);
        map = future.get(10, TimeUnit.SECONDS);
        assertTrue(map.size() > 1, prettyPrintJson(map) + " was expected to have just " + TestingSingleton.class);
        assertTrue(map.get(TestingSingleton.class.getName()).finishingStatus() == ActivationStatus.SUCCESS,
                   prettyPrintJson(map) + " was expected to have just " + TestingSingleton.class);
        assertEquals(1, TestingSingleton.getPostConstructCount());
        assertEquals(1, TestingSingleton.getPreDestroyCount());

        future = picoServices.shutdown();
        assertNotNull(future);
        map = future.get(10, TimeUnit.SECONDS);
        assertTrue(map.isEmpty(), prettyPrintJson(map));
    }

    @Test
    public void knownProviders() {
        List<ServiceProvider<Provider>> providers = services.lookup(DefaultServiceInfo.builder().contractImplemented(Provider.class.getName()).build());
        assertEquals(
                "[ASerialProviderImpl$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl"
                        + ".ASerialProviderImpl:INIT, "
                        + "MyServices$MyConcreteClassContractPerRequestIPProvider$$picoActivator:io.helidon.pico"
                        + ".testsubjects.ext.provider.MyServices$MyConcreteClassContractPerRequestIPProvider:INIT, "
                        + "MyServices$MyConcreteClassContractPerRequestProvider$$picoActivator:io.helidon.pico"
                        + ".testsubjects.ext.provider.MyServices$MyConcreteClassContractPerRequestProvider:INIT,"
                        + " BladeProvider$$picoActivator:io.helidon.pico.testsubjects.ext.tbox.impl"
                        + ".BladeProvider:INIT]",
                     ServiceProvider.toDescriptions(providers).toString());
    }

    static String prettyPrintJson(Object obj) {
        ObjectMapper mapper = new ObjectMapper();
        //                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        //                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            throw new ToolsException(e.getMessage(), e);
        }
    }

}
