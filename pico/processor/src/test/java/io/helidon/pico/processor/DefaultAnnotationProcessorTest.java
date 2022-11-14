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

package io.helidon.pico.processor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.testsubjects.hello.HelloImpl;
import io.helidon.pico.testsubjects.hello.WorldImpl;
import io.helidon.pico.tools.creator.impl.CodeGenFiler;
import io.helidon.pico.tools.processor.Options;
import io.helidon.pico.tools.processor.ServicesToProcess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.tools.reflect.CompileOptions;
import org.jooq.tools.reflect.Reflect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the common annotation processing scenarios.
 */
@SuppressWarnings("checkstyle:TodoComment")
public class DefaultAnnotationProcessorTest {
    private final System.Logger logger = System.getLogger(getClass().getName());

    /**
     * before each.
     */
    @BeforeEach
    public void init() {
        CodeGenFiler.setFilerEnabled(false);
    }

    /**
     * tear down.
     */
    @AfterEach
    public void reset() {
        CodeGenFiler.setFilerEnabled(true);
    }

    /**
     * Compile with annotation processing enabled.
     *
     * @throws Exception
     */
    @SuppressWarnings("checkstyle:MethodLength")
    @Test
    @Disabled("Fails in maven, works in IDE")
    // TODO: identify why this fails with "id the annotation processor quietly crash??"
    public void compileWithAnnotationProcessors() throws Exception {
        List<String> diagMessages = new LinkedList<>();

        // time to say hello...
        logger.log(System.Logger.Level.INFO, "time to say hello...");
        ServiceAnnotationProcessor serviceProcessor = new ServiceAnnotationProcessor();
        ContractAnnotationProcessor contractProcessor = new ContractAnnotationProcessor();
        InjectAnnotationProcessor injectProcessor = new InjectAnnotationProcessor();
        PostConstructPreDestroyAnnotationProcessor lcmProcessor = new PostConstructPreDestroyAnnotationProcessor();
        NameAndContents namedContents = loadTestSourceAsString(HelloImpl.class);
        Object hello;
        try {
            hello = Reflect.compile(namedContents.name, namedContents.contents,
                                    new CompileOptions()
                                            .options("-Xlint:all", "-proc:only")
                                            .processors(serviceProcessor,
                                                        contractProcessor,
                                                        injectProcessor,
                                                        lcmProcessor)
                            /*.withDiagnosticListener((diagnostic) -> {
                                String message = diagnostic.getMessage(Locale.ENGLISH);
                                diagMessages.add(message);
                                logger.info(message);
                            })*/);
        } catch (Throwable t) {
            // expected
            logger.log(System.Logger.Level.WARNING, t.getMessage());
        }

        assertThat("Service processor called", serviceProcessor.processed, is(true));
        assertThat(
                "ifaces are not compiled here since we only renamed the class for the impl - the interfaces are just "
                        + "being "
                        + "loaded out of the classpath",
                contractProcessor.processed,
                is(false));
        assertThat("Inject processor called", injectProcessor.processed, is(true));
        assertThat("LCM processor called", lcmProcessor.processed, is(true));
        // TODO:
        //        assertNotNull(lastResult, "https://github.com/jOOQ/jOOR/pull/120");

        assertEquals("{xio.helidon.pico.testsubjects.hello.HelloImpl=[io.helidon.pico.testsubjects.hello.Hello]}",
                     ServicesToProcess.getServicesInstance().getServicesToContracts().toString());
        if (Options.isOptionEnabled(Options.TAG_AUTO_ADD_NON_CONTRACT_INTERFACES)) {
            assertEquals("{xio.helidon.pico.testsubjects.hello.HelloImpl=[io.helidon.pico.testsubjects.hello.Hello]}",
                         ServicesToProcess.getServicesInstance().getServicesToExternalContracts().toString());
        } else {
            assertEquals("{}", ServicesToProcess.getServicesInstance().getServicesToExternalContracts().toString());
        }

        //        assertEquals("{}", ServicesToProcess.getContract().getServicesToContracts().toString());
        //        assertEquals("{}", ServicesToProcess.getContract().getServicesToExternalContracts().toString());

        String json = prettyPrintJson(ServicesToProcess.getServicesInstance().getServicesToInjectionPointDependencies());
        assertEquals("{\n"
                             + "  \"xio.helidon.pico.testsubjects.hello.HelloImpl\" : {\n"
                             + "    \"forServiceTypeName\" : \"xio.helidon.pico.testsubjects.hello.HelloImpl\",\n"
                             + "    \"dependencies\" : [ {\n"
                             + "      \"ipDependencies\" : [ {\n"
                             + "        \"serviceTypeName\" : \"xio.helidon.pico.testsubjects.hello.HelloImpl\",\n"
                             + "        \"scopeTypeNames\" : [ ],\n"
                             + "        \"qualifiers\" : [ ],\n"
                             + "        \"elementName\" : \"world\",\n"
                             + "        \"elementOffset\" : null,\n"
                             + "        \"elementTypeName\" : \"io.helidon.pico.testsubjects.hello.World\",\n"
                             + "        \"elementKind\" : \"FIELD\",\n"
                             + "        \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "        \"listWrapped\" : false,\n"
                             + "        \"optionalWrapped\" : false,\n"
                             + "        \"providerWrapped\" : false,\n"
                             + "        \"staticDecl\" : false\n"
                             + "      } ],\n"
                             + "      \"resolved\" : null\n"
                             + "    }, {\n"
                             + "      \"ipDependencies\" : [ {\n"
                             + "        \"serviceTypeName\" : \"xio.helidon.pico.testsubjects.hello.HelloImpl\",\n"
                             + "        \"scopeTypeNames\" : [ ],\n"
                             + "        \"qualifiers\" : [ ],\n"
                             + "        \"elementName\" : \"worldRef\",\n"
                             + "        \"elementOffset\" : null,\n"
                             + "        \"elementTypeName\" : \"io.helidon.pico.testsubjects.hello.World\",\n"
                             + "        \"elementKind\" : \"FIELD\",\n"
                             + "        \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "        \"listWrapped\" : false,\n"
                             + "        \"optionalWrapped\" : false,\n"
                             + "        \"providerWrapped\" : true,\n"
                             + "        \"staticDecl\" : false\n"
                             + "      } ],\n"
                             + "      \"resolved\" : null\n"
                             + "    }, {\n"
                             + "      \"ipDependencies\" : [ {\n"
                             + "        \"serviceTypeName\" : \"xio.helidon.pico.testsubjects.hello.HelloImpl\",\n"
                             + "        \"scopeTypeNames\" : [ ],\n"
                             + "        \"qualifiers\" : [ ],\n"
                             + "        \"elementName\" : \"listOfWorldRefs\",\n"
                             + "        \"elementOffset\" : null,\n"
                             + "        \"elementTypeName\" : \"io.helidon.pico.testsubjects.hello.World\",\n"
                             + "        \"elementKind\" : \"FIELD\",\n"
                             + "        \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "        \"listWrapped\" : true,\n"
                             + "        \"optionalWrapped\" : false,\n"
                             + "        \"providerWrapped\" : true,\n"
                             + "        \"staticDecl\" : false\n"
                             + "      } ],\n"
                             + "      \"resolved\" : null\n"
                             + "    }, {\n"
                             + "      \"ipDependencies\" : [ {\n"
                             + "        \"serviceTypeName\" : \"xio.helidon.pico.testsubjects.hello.HelloImpl\",\n"
                             + "        \"scopeTypeNames\" : [ ],\n"
                             + "        \"qualifiers\" : [ ],\n"
                             + "        \"elementName\" : \"listOfWorlds\",\n"
                             + "        \"elementOffset\" : null,\n"
                             + "        \"elementTypeName\" : \"io.helidon.pico.testsubjects.hello.World\",\n"
                             + "        \"elementKind\" : \"FIELD\",\n"
                             + "        \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "        \"listWrapped\" : true,\n"
                             + "        \"optionalWrapped\" : false,\n"
                             + "        \"providerWrapped\" : false,\n"
                             + "        \"staticDecl\" : false\n"
                             + "      } ],\n"
                             + "      \"resolved\" : null\n"
                             + "    }, {\n"
                             + "      \"ipDependencies\" : [ {\n"
                             + "        \"serviceTypeName\" : \"xio.helidon.pico.testsubjects.hello.HelloImpl\",\n"
                             + "        \"scopeTypeNames\" : [ ],\n"
                             + "        \"qualifiers\" : [ {\n"
                             + "          \"qualifierTypeName\" : \"jakarta.inject.Named\",\n"
                             + "          \"value\" : \"red\"\n"
                             + "        } ],\n"
                             + "        \"elementName\" : \"redWorld\",\n"
                             + "        \"elementOffset\" : null,\n"
                             + "        \"elementTypeName\" : \"io.helidon.pico.testsubjects.hello.World\",\n"
                             + "        \"elementKind\" : \"FIELD\",\n"
                             + "        \"access\" : \"PACKAGE_PRIVATE\",\n"
                             + "        \"listWrapped\" : false,\n"
                             + "        \"optionalWrapped\" : true,\n"
                             + "        \"providerWrapped\" : false,\n"
                             + "        \"staticDecl\" : false\n"
                             + "      } ],\n"
                             + "      \"resolved\" : null\n"
                             + "    }, {\n"
                             + "      \"ipDependencies\" : [ {\n"
                             + "        \"serviceTypeName\" : \"xio.helidon.pico.testsubjects.hello.HelloImpl\",\n"
                             + "        \"scopeTypeNames\" : [ ],\n"
                             + "        \"qualifiers\" : [ ],\n"
                             + "        \"elementName\" : \"privateWorld\",\n"
                             + "        \"elementOffset\" : null,\n"
                             + "        \"elementTypeName\" : \"io.helidon.pico.testsubjects.hello.World\",\n"
                             + "        \"elementKind\" : \"FIELD\",\n"
                             + "        \"access\" : \"PRIVATE\",\n"
                             + "        \"listWrapped\" : false,\n"
                             + "        \"optionalWrapped\" : true,\n"
                             + "        \"providerWrapped\" : false,\n"
                             + "        \"staticDecl\" : false\n"
                             + "      } ],\n"
                             + "      \"resolved\" : null\n"
                             + "    }, {\n"
                             + "      \"ipDependencies\" : [ {\n"
                             + "        \"serviceTypeName\" : \"xio.helidon.pico.testsubjects.hello.HelloImpl\",\n"
                             + "        \"scopeTypeNames\" : [ ],\n"
                             + "        \"qualifiers\" : [ ],\n"
                             + "        \"elementName\" : \"<init>\",\n"
                             + "        \"elementOffset\" : 0,\n"
                             + "        \"elementTypeName\" : \"io.helidon.pico.testsubjects.hello.World\",\n"
                             + "        \"elementKind\" : \"CTOR\",\n"
                             + "        \"access\" : \"PUBLIC\",\n"
                             + "        \"listWrapped\" : false,\n"
                             + "        \"optionalWrapped\" : false,\n"
                             + "        \"providerWrapped\" : false,\n"
                             + "        \"staticDecl\" : false\n"
                             + "      } ],\n"
                             + "      \"resolved\" : null\n"
                             + "    }, {\n"
                             + "      \"ipDependencies\" : [ {\n"
                             + "        \"serviceTypeName\" : \"xio.helidon.pico.testsubjects.hello.HelloImpl\",\n"
                             + "        \"scopeTypeNames\" : [ ],\n"
                             + "        \"qualifiers\" : [ ],\n"
                             + "        \"elementName\" : \"world\",\n"
                             + "        \"elementOffset\" : 0,\n"
                             + "        \"elementTypeName\" : \"io.helidon.pico.testsubjects.hello.World\",\n"
                             + "        \"elementKind\" : \"METHOD\",\n"
                             + "        \"access\" : \"PUBLIC\",\n"
                             + "        \"listWrapped\" : false,\n"
                             + "        \"optionalWrapped\" : false,\n"
                             + "        \"providerWrapped\" : false,\n"
                             + "        \"staticDecl\" : false\n"
                             + "      } ],\n"
                             + "      \"resolved\" : null\n"
                             + "    }, {\n"
                             + "      \"ipDependencies\" : [ {\n"
                             + "        \"serviceTypeName\" : \"xio.helidon.pico.testsubjects.hello.HelloImpl\",\n"
                             + "        \"scopeTypeNames\" : [ ],\n"
                             + "        \"qualifiers\" : [ {\n"
                             + "          \"qualifierTypeName\" : \"jakarta.inject.Named\",\n"
                             + "          \"value\" : \"red\"\n"
                             + "        } ],\n"
                             + "        \"elementName\" : \"setRedWorld\",\n"
                             + "        \"elementOffset\" : 0,\n"
                             + "        \"elementTypeName\" : \"io.helidon.pico.testsubjects.hello.World\",\n"
                             + "        \"elementKind\" : \"METHOD\",\n"
                             + "        \"access\" : \"PUBLIC\",\n"
                             + "        \"listWrapped\" : false,\n"
                             + "        \"optionalWrapped\" : true,\n"
                             + "        \"providerWrapped\" : false,\n"
                             + "        \"staticDecl\" : false\n"
                             + "      } ],\n"
                             + "      \"resolved\" : null\n"
                             + "    } ]\n"
                             + "  }\n"
                             + "}", json);
        //        assertEquals("{}", ServicesToProcess.getContract().getServicesToInjectionPointDependencies()
        //        .toString());
        assertEquals("{xio.helidon.pico.testsubjects.hello.HelloImpl=postConstruct}",
                     ServicesToProcess.getServicesInstance().getServicesToPostConstructMethodNames().toString());
        assertEquals("{xio.helidon.pico.testsubjects.hello.HelloImpl=preDestroy}",
                     ServicesToProcess.getServicesInstance().getServicesToPreDestroyMethodNames().toString());

        // time for the world...
        logger.log(System.Logger.Level.INFO, "time to see the world...");
        serviceProcessor = new ServiceAnnotationProcessor();
        contractProcessor = new ContractAnnotationProcessor();
        injectProcessor = new InjectAnnotationProcessor();
        lcmProcessor = new PostConstructPreDestroyAnnotationProcessor();
        namedContents = loadTestSourceAsString(WorldImpl.class);
        Object world = null;
        try {
            Reflect.compile(namedContents.name, namedContents.contents,
                            new CompileOptions()
                                    .options("-Xlint:all", "-proc:only")
                                    .processors(serviceProcessor, contractProcessor, injectProcessor, lcmProcessor)
                            /*.withDiagnosticListener((diagnostic) -> {
                                String message = diagnostic.getMessage(Locale.ENGLISH);
                                diagMessages.add(message);
                                logger.info(message);
                            })*/);
        } catch (Throwable t) {
            // expected
            logger.log(System.Logger.Level.WARNING, t.getMessage());
        }
        assertTrue(serviceProcessor.processed);
        assertFalse(contractProcessor.processed,
                    "ifaces are not compiled here since we only renamed the class for the impl - the interfaces are "
                            + "just being loaded out of the classpath");
        assertFalse(injectProcessor.processed, "WorldImpl has no injection points");
        assertFalse(lcmProcessor.processed, "WorldImpl has no apropos methods");
        assertNull(world, "https://github.com/jOOQ/jOOR/pull/120");
        // TODO:
        //        assertNotNull(lastResult, "https://github.com/jOOQ/jOOR/pull/120");

        // these SHOULD BE showing cumulative results...
        if (Options.isOptionEnabled(Options.TAG_AUTO_ADD_NON_CONTRACT_INTERFACES)) {
            assertEquals(
                    "{xio.helidon.pico.testsubjects.hello.HelloImpl=[io.helidon.pico.testsubjects.hello.Hello], xio"
                            + ".helidon.pico.testsubjects.hello.WorldImpl=[io.helidon.pico.testsubjects.hello"
                            + ".SomeOtherLocalNonContractInterface, io.helidon.pico.testsubjects.hello.World, java.io"
                            + ".Serializable]}",
                    ServicesToProcess.getServicesInstance().getServicesToContracts().toString());
            assertEquals("{xio.helidon.pico.testsubjects.hello.WorldImpl=[io.helidon.pico.testsubjects.hello.World]}",
                         ServicesToProcess.getServicesInstance().getServicesToExternalContracts().toString());
        } else {
            assertEquals(
                    "{xio.helidon.pico.testsubjects.hello.HelloImpl=[io.helidon.pico.testsubjects.hello.Hello], xio"
                            + ".helidon.pico.testsubjects.hello.WorldImpl=[io.helidon.pico.testsubjects.hello.World]}",
                    ServicesToProcess.getServicesInstance().getServicesToContracts().toString());
            assertEquals("{xio.helidon.pico.testsubjects.hello.WorldImpl=[io.helidon.pico.testsubjects.hello.World]}",
                         ServicesToProcess.getServicesInstance().getServicesToExternalContracts().toString());
        }

        //        assertEquals("{}", ServicesToProcess.getContract().getServicesToContracts().toString());
        //        assertEquals("{}", ServicesToProcess.getContract().getServicesToExternalContracts().toString());

        assertEquals(json,
                     prettyPrintJson(ServicesToProcess.getServicesInstance().getServicesToInjectionPointDependencies()));
        //        assertEquals("{}", ServicesToProcess.getContract().getServicesToInjectionPointDependencies()
        //        .toString());
        assertEquals("{xio.helidon.pico.testsubjects.hello.HelloImpl=postConstruct}",
                     ServicesToProcess.getServicesInstance().getServicesToPostConstructMethodNames().toString());
        assertEquals("{xio.helidon.pico.testsubjects.hello.HelloImpl=preDestroy}",
                     ServicesToProcess.getServicesInstance().getServicesToPreDestroyMethodNames().toString());

        assertFalse(ServicesToProcess.isRunning(), "did the annotation processor quietly crash?? likely.");
        assertNotNull(serviceProcessor.result);
        assertEquals("[xio.helidon.pico.testsubjects.hello.HelloImpl, xio.helidon.pico.testsubjects.hello.WorldImpl]",
                     serviceProcessor.result.getServiceTypeNames().toString());
        assertEquals("/**\n"
                             + " * \n"
                             + "*/\n"
                             + "package xio.helidon.pico.testsubjects.hello;\n"
                             + "\n"
                             + "import io.helidon.common.Weight;\n"
                             + "import io.helidon.common.Weighted;\n"
                             + "\n"
                             + "import io.helidon.pico.RunLevel;\n"
                             + "import io.helidon.pico.spi.PostConstructMethod;\n"
                             + "import io.helidon.pico.spi.PreDestroyMethod;\n"
                             + "import io.helidon.pico.spi.ext.AbstractServiceProvider;\n"
                             + "import io.helidon.pico.spi.DefaultServiceInfo;\n"
                             + "import io.helidon.pico.spi.ext.Dependencies;\n"
                             + "\n"
                             + "import jakarta.annotation.Generated;\n"
                             + "import jakarta.inject.Provider;\n"
                             + "import jakarta.inject.Singleton;\n"
                             + "import java.util.List;\n"
                             + "import java.util.Map;\n"
                             + "import java.util.Objects;\n"
                             + "import java.util.Optional;\n"
                             + "import java.util.Set;\n"
                             + "\n"
                             + "import static io.helidon.pico.spi.InjectionPointInfo.Access;\n"
                             + "import static io.helidon.pico.spi.InjectionPointInfo.ElementKind;\n"
                             + "\n"
                             + "/**\n"
                             + " * Activator for xio.helidon.pico.testsubjects.hello.HelloImpl\n"
                             + " * \n"
                             + "*/\n"
                             + "@Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.impl"
                             + ".DefaultActivatorCreator\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "// @Singleton  \n"
                             + "public class HelloImpl$$picoActivator extends AbstractServiceProvider<HelloImpl> {\n"
                             + "    private static final DefaultServiceInfo serviceInfo =\n"
                             + "        DefaultServiceInfo.builder()\n"
                             + "            .serviceTypeName(getServiceTypeName())\n"
                             + "            .contractImplemented(io.helidon.pico.testsubjects.hello.Hello.class"
                             + ".getName())\n"
                             + "            \n"
                             + "            \n"
                             + "            .activatorTypeName(HelloImpl$$picoActivator.class.getName())\n"
                             + "\n"
                             + "            .scopeTypeName(jakarta.inject.Singleton.class.getName())\n"
                             + "\n"
                             + "            \n"
                             + "            \n"
                             + "            \n"
                             + "            .build();\n"
                             + "\n"
                             + "    public static final HelloImpl$$picoActivator INSTANCE = new "
                             + "HelloImpl$$picoActivator();\n"
                             + "\n"
                             + "    HelloImpl$$picoActivator() {\n"
                             + "        setServiceInfo(serviceInfo);\n"
                             + "    }\n"
                             + "\n"
                             + "    public static String getServiceTypeName() {\n"
                             + "        return xio.helidon.pico.testsubjects.hello.HelloImpl.class.getName();\n"
                             + "    }\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public Dependencies getDependencies() {\n"
                             + "        Dependencies deps = Dependencies.builder()\n"
                             + "                .forServiceTypeName(getServiceTypeName())\n"
                             + "                .add(\"world\", io.helidon.pico.testsubjects.hello.World.class, "
                             + "ElementKind"
                             + ".FIELD, Access.PACKAGE_PRIVATE)\n"
                             + "                .add(\"worldRef\", io.helidon.pico.testsubjects.hello.World.class, "
                             + "ElementKind.FIELD, Access.PACKAGE_PRIVATE).setIsProviderWrapped()\n"
                             + "                .add(\"listOfWorldRefs\", io.helidon.pico.testsubjects.hello.World"
                             + ".class, "
                             + "ElementKind.FIELD, Access.PACKAGE_PRIVATE).setIsListWrapped().setIsProviderWrapped()\n"
                             + "                .add(\"listOfWorlds\", io.helidon.pico.testsubjects.hello.World.class, "
                             + "ElementKind.FIELD, Access.PACKAGE_PRIVATE).setIsListWrapped()\n"
                             + "                .add(\"redWorld\", io.helidon.pico.testsubjects.hello.World.class, "
                             + "ElementKind.FIELD, Access.PACKAGE_PRIVATE).qualifier(io.helidon.pico.spi.impl"
                             + ".DefaultQualifierAndValue.toQualifierAndValue(jakarta.inject.Named.class, \"red\"))"
                             + ".setIsOptionalWrapped()\n"
                             + "                .add(\"privateWorld\", io.helidon.pico.testsubjects.hello.World.class, "
                             + "ElementKind.FIELD, Access.PRIVATE).setIsOptionalWrapped()\n"
                             + "                .add(\"<init>\", io.helidon.pico.testsubjects.hello.World.class, "
                             + "ElementKind"
                             + ".CTOR, Access.PUBLIC).elemOffset(0)\n"
                             + "                .add(\"world\", io.helidon.pico.testsubjects.hello.World.class, "
                             + "ElementKind"
                             + ".METHOD, Access.PUBLIC).elemOffset(0)\n"
                             + "                .add(\"setRedWorld\", io.helidon.pico.testsubjects.hello.World.class, "
                             + "ElementKind.METHOD, Access.PUBLIC).elemOffset(0).qualifier(io.helidon.pico.spi.impl"
                             + ".DefaultQualifierAndValue.toQualifierAndValue(jakarta.inject.Named.class, \"red\"))"
                             + ".setIsOptionalWrapped()\n"
                             + "                \n"
                             + "                .build().build();\n"
                             + "        return Dependencies.combine(super.getDependencies(), deps);\n"
                             + "    }\n"
                             + "\n"
                             + "\n"
                             + "\n"
                             + "    @Override\n"
                             + "    protected HelloImpl createServiceProvider(Map<String, Object> deps) {\n"
                             + "        io.helidon.pico.testsubjects.hello.World c0 = (io.helidon.pico.testsubjects"
                             + ".hello"
                             + ".World)deps.get(\"<init>(0)\");\n"
                             + "        \n"
                             + "        return new xio.helidon.pico.testsubjects.hello.HelloImpl(c0);\n"
                             + "    }\n"
                             + "\n"
                             + "\n"
                             + "    @Override\n"
                             + "    protected void doInjectingFields(Object t, Map<String, Object> deps, Set<String> "
                             + "injections, String forServiceType) {\n"
                             + "                super.doInjectingFields(t, deps, injections, forServiceType);\n"
                             + "        HelloImpl target = (HelloImpl)t;\n"
                             + "        target.world = (io.helidon.pico.testsubjects.hello.World)deps.get(\"world\");\n"
                             + "        target.worldRef = (Provider<io.helidon.pico.testsubjects.hello.World>)deps.get"
                             + "(\"worldRef\");\n"
                             + "        target.listOfWorldRefs = (List<Provider<io.helidon.pico.testsubjects.hello"
                             + ".World>>)"
                             + "deps.get(\"listOfWorldRefs\");\n"
                             + "        target.listOfWorlds = (List<io.helidon.pico.testsubjects.hello.World>)deps.get"
                             + "(\"listOfWorlds\");\n"
                             + "        target.redWorld = (Optional<io.helidon.pico.testsubjects.hello.World>)deps.get"
                             + "(\"redWorld\");\n"
                             + "        target.privateWorld = (Optional<io.helidon.pico.testsubjects.hello.World>)"
                             + "deps.get"
                             + "(\"privateWorld\");\n"
                             + "        \n"
                             + "    }\n"
                             + "\n"
                             + "\n"
                             + "\n"
                             + "    @Override\n"
                             + "    protected void doInjectingMethods(Object t, Map<String, Object> deps, Set<String> "
                             + "injections, String forServiceType) {\n"
                             + "                super.doInjectingMethods(t, deps, injections, forServiceType);\n"
                             + "        HelloImpl target = (HelloImpl)t;\n"
                             + "        target.world((io.helidon.pico.testsubjects.hello.World)deps.get(\"world(0)\")"
                             + ");\n"
                             + "        target.setRedWorld((Optional<io.helidon.pico.testsubjects.hello.World>)deps.get"
                             + "(\"setRedWorld(1)\"));\n"
                             + "        \n"
                             + "    }\n"
                             + "\n"
                             + "\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public PostConstructMethod getPostConstructMethod() {\n"
                             + "        xio.helidon.pico.testsubjects.hello.HelloImpl impl = serviceRef.get();\n"
                             + "        return impl::postConstruct;\n"
                             + "    }\n"
                             + "\n"
                             + "\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public PreDestroyMethod getPreDestroyMethod() {\n"
                             + "        xio.helidon.pico.testsubjects.hello.HelloImpl impl = serviceRef.get();\n"
                             + "        return impl::preDestroy;\n"
                             + "    }\n"
                             + "\n"
                             + "}",
                     serviceProcessor.result.getServiceTypeDetails()
                             .get(DefaultTypeName.createFromTypeName("xio.helidon.pico.testsubjects.hello.HelloImpl"))
                             .getBody().trim());

        assertNotNull(serviceProcessor.result.getModuleDetail());
        assertEquals(
                "[xio.helidon.pico.testsubjects.hello.HelloImpl$$picoActivator, xio.helidon.pico.testsubjects.hello"
                        + ".WorldImpl$$picoActivator]",
                serviceProcessor.result.getModuleDetail().getServiceProviderActivatorTypeNames().toString());
        assertEquals("unnamed", serviceProcessor.result.getModuleDetail().getModuleName());
        assertEquals("xio.helidon.pico.testsubjects.hello.pico.picoModule",
                     serviceProcessor.result.getModuleDetail().getModuleTypeName().name());
        assertEquals("/**\n"
                             + " * \n"
                             + "*/\n"
                             + "package xio.helidon.pico.testsubjects.hello.pico;\n"
                             + "\n"
                             + "import io.helidon.pico.spi.Module;\n"
                             + "import io.helidon.pico.spi.ServicesBinder;\n"
                             + "\n"
                             + "import jakarta.annotation.Generated;\n"
                             + "import jakarta.inject.Named;\n"
                             + "import jakarta.inject.Singleton;\n"
                             + "import java.util.Optional;\n"
                             + "\n"
                             + "/**\n"
                             + " * \n"
                             + "*/\n"
                             + "@Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.impl"
                             + ".DefaultActivatorCreator\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "// @Singleton @Named(picoModule.NAME)\n"
                             + "public class picoModule implements Module {\n"
                             + "\n"
                             + "    static final String NAME = \"unnamed\";\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public Optional<String> getName() {\n"
                             + "        return Optional.of(NAME);\n"
                             + "    }\n"
                             + "\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public void configure(ServicesBinder binder) {\n"
                             + "        binder.bind(xio.helidon.pico.testsubjects.hello.HelloImpl$$picoActivator"
                             + ".INSTANCE);\n"
                             + "        binder.bind(xio.helidon.pico.testsubjects.hello.WorldImpl$$picoActivator"
                             + ".INSTANCE);\n"
                             + "\n"
                             + "    }\n"
                             + "\n"
                             + "}", serviceProcessor.result.getModuleDetail().getModuleBody().trim());

        assertEquals("\n"
                             + "/**\n"
                             + " * \n"
                             + "*/\n"
                             + "\n"
                             + "/**\n"
                             + " * \n"
                             + "*/\n"
                             + "module unnamed {\n"
                             + "    requires transitive AnotherModuleMaybe;\n"
                             + "    provides io.helidon.pico.spi.Module with xio.helidon.pico.testsubjects.hello.pico"
                             + ".picoModule;\n"
                             + "    exports io.helidon.pico.testsubjects.hello;\n"
                             + "    uses io.helidon.pico.testsubjects.hello.World;\n"
                             + "    requires transitive pico.default;\n"
                             + "\n"
                             + "}",
                     serviceProcessor.result.getModuleDetail().getModuleInfoBody());

        assertEquals("{io.helidon.pico.spi.Module=[xio.helidon.pico.testsubjects.hello.pico.picoModule]}",
                     serviceProcessor.result.getMetaInfServices().toString());
    }

    static NameAndContents loadTestSourceAsString(Class<?> original) throws Exception {
        String packageName = original.getPackage().getName();
        String serviceTypeName = original.getName();
        String fileLocation = serviceTypeName.replace(".", "/") + ".java";
        String contents = Files.readString(Paths.get("../test-resources/src/main/java", fileLocation),
                                           StandardCharsets.UTF_8);
        contents = contents.replace("package " + packageName + ";",
                                    "package x" + packageName + ";\n\nimport " + packageName + ".*;");
        return new NameAndContents("x" + serviceTypeName, contents);
    }

    static class NameAndContents {
        private final String name;
        private final String contents;

        NameAndContents(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }

    static String prettyPrintJson(Object obj) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        //                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        //                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }

}
