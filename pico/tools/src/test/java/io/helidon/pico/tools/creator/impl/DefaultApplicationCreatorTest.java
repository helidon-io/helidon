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

package io.helidon.pico.tools.creator.impl;

import java.util.List;
import java.util.stream.Collectors;

import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.tools.creator.ApplicationCreator;
import io.helidon.pico.tools.creator.ApplicationCreatorResponse;
import io.helidon.pico.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link io.helidon.pico.tools.creator.impl.DefaultActivatorCreator}.
 */
public class DefaultApplicationCreatorTest extends AbstractBaseCreator {

    private final ApplicationCreator applicationCreator = loadAndCreate(ApplicationCreator.class);

    @Test
    public void sanity() {
        assertNotNull(applicationCreator);
        assertEquals(DefaultApplicationCreator.class, applicationCreator.getClass());
    }

    /**
     * Basic tests for {@link io.helidon.pico.tools.creator.impl.DefaultApplicationCreator}.
     * Most of the testing will need to occur downstream from this module.
     */
    @Test
    public void codegenHelloWorldApplication() {
        DefaultApplicationCreator creator = (DefaultApplicationCreator) this.applicationCreator;
        ServiceInfo allServices = DefaultServiceInfo.builder().build();

        PicoServices picoServices = PicoServices.picoServices().get();
        Services services = picoServices.services();
        List<ServiceProvider<Object>> serviceProviders = services.lookup(allServices);

        List<TypeName> serviceTypeNames = serviceProviders.stream()
                .map(sp -> DefaultTypeName.createFromTypeName(sp.serviceInfo().serviceTypeName()))
                .collect(Collectors.toList());

        String classpath = System.getProperty("java.class.path");
        String separator = System.getProperty("path.separator");
        String[] cp = classpath.split(separator);
        DefaultApplicationCreatorRequest req = (DefaultApplicationCreatorRequest) DefaultApplicationCreatorRequest.builder()
                .codeGenRequest(DefaultApplicationCreatorCodeGen.builder()
                                        .className(DefaultApplicationCreator.toApplicationClassName("test"))
                                        .classPrefixName("test")
                                        .build())
                .codeGenPaths(DefaultGeneralCodeGenPaths.builder().build())
                .serviceTypeNames(serviceTypeNames)
                .build();
        assertTrue(req.isFailOnError());

        ApplicationCreatorResponse res = creator.createApplication(req);
        assertNotNull(res);
        assertNull(res.getError());
        assertTrue(res.isSuccess());
        assertNull(res.getError());
        assertEquals("[pico.picotestApplication]",
                     res.getServiceTypeNames().toString());
        assertEquals("default", res.getTemplateName());
        assertEquals("unnamed", res.getModuleName());
        assertEquals("DefaultApplicationCreatorCodeGen(packageName=pico, className=picotestApplication, classPrefixName=test)",
                     res.getApplicationCodeGenResponse().toString());
        assertEquals("{pico.picotestApplication=DefaultGeneralCodeGenDetail(serviceTypeName=pico.picotestApplication,"
                             + " body=/**\n"
                             + " * \n"
                             + " */\n"
                             + "package pico;\n"
                             + "\n"
                             + "import java.util.Optional;\n"
                             + "\n"
                             + "import io.helidon.pico.spi.Application;\n"
                             + "import io.helidon.pico.spi.ServiceInjectionPlanBinder;\n"
                             + "\n"
                             + "import jakarta.annotation.Generated;\n"
                             + "import jakarta.inject.Named;\n"
                             + "import jakarta.inject.Singleton;\n"
                             + "\n"
                             + "/**\n"
                             + " * \n"
                             + " */\n"
                             + "@Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.creator.impl"
                             + ".DefaultApplicationCreator\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "@Singleton @Named(picotestApplication.NAME)\n"
                             + "public class picotestApplication implements Application {\n"
                             + "\n"
                             + "    static final String NAME = \"unnamed\";\n"
                             + "\n"
                             + "    static boolean enabled = true;\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public Optional<String> getName() {\n"
                             + "        return Optional.of(NAME);\n"
                             + "    }\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public String toString() {\n"
                             + "        return NAME + \":\" + getClass().getName();\n"
                             + "    }\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public void configure(ServiceInjectionPlanBinder binder) {\n"
                             + "        if (!enabled) {\n"
                             + "            return;\n"
                             + "        }\n"
                             + "\n"
                             + "    }\n"
                             + "\n"
                             + "})}",
                     res.getServiceTypeDetails().toString());
    }

}
