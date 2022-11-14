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

package io.helidon.pico.tools.utils.template;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.spi.ext.Dependencies;
import io.helidon.pico.testsubjects.hello.World;
import io.helidon.pico.testsubjects.hello.WorldImpl;
import io.helidon.pico.tools.creator.impl.DefaultActivatorCreator;
import io.helidon.pico.tools.utils.TemplateHelper;

import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TemplateHelperTest {

    @Test
    public void bogusTemplateName() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> TemplateHelper.safeLoadTemplate("bogus"));
        assertEquals("templates/pico/default/bogus was not found", e.getMessage());
    }

    @Test
    public void getRequiredArguments() {
        Set<String> args = TemplateHelper.getRequiredArguments("this is {a} little {test}", "{", "}");
        assertEquals("[a, test]", args.toString());

        args = TemplateHelper.getRequiredArguments("this is a little test");
        assertEquals("[]", args.toString());

        args = TemplateHelper.getRequiredArguments("this is a {{little}} test");
        assertEquals("[little]", args.toString());
    }

    @Test
    public void applyMustacheSubstitutions() {
        Map<String, Object> props = Collections.singletonMap("little", "big");
        String val = TemplateHelper.applySubstitutions(null, "this is a {{little}} test", props);
        assertEquals("this is a big test", val);
    }

    @Test
    public void applyMustacheSubstitutionsOnNullTarget() {
        Map<String, Object> props = Collections.singletonMap("little", "big");
        String val = TemplateHelper.applySubstitutions(null, null, props);
        assertNull(val);
    }

    @Test
    public void serviceProviderTemplate() {
        String template = TemplateHelper.safeLoadTemplate("service-provider-activator.hbs");
        Set<String> args = TemplateHelper.getRequiredArguments(template);
        assertEquals("[activatorsuffix, classname, contracts, ctorarglist, ctorargs, dependencies, description, "
                        + "externalcontracts, extracodegen, flatclassname, header, injectedfields, injectedmethods, "
                        + "injectedmethodsskippedinparent, injectionorder, isconcrete, isprovider, isrunlevelset, "
                        + "issupportsjsr330instrictmode, isweightset, packagename, postconstruct, predestroy, "
                        + "qualifiers, runlevel, scopetypenames, weight]",
                args.toString());
        Map<String, Object> subst = new HashMap<>();
        subst.put("flatclassname", "HelloWorldImpl");
        subst.put("classname", "HelloWorldImpl");
        subst.put("parent", "ParentTypeName");
        subst.put("isconcrete", true);
        subst.put("packagename", "io.helidon.pico.tools.example.hello");
        subst.put("activatorsuffix", DefaultActivatorCreator.INNER_ACTIVATOR_CLASS_NAME);
        subst.put("contracts", Collections.singletonList("io.helidon.pico.tools.example.hello.Hello"));
        subst.put("externalcontracts", Collections.emptyList());
        subst.put("dependencies",
                  Dependencies.builder().forServiceTypeName("HI")
                          .add("world", World.class, InjectionPointInfo.ElementKind.FIELD, InjectionPointInfo.Access.PACKAGE_PRIVATE).build()
                          .build().getDependencies());
        subst.put("description", Arrays.asList("description1", "description2"));
        subst.put("priority", "PriorityProvider.DEFAULT");
        subst.put("generatedanno", "\"test\"");
        subst.put("scopetypenames", Collections.singleton(Singleton.class.getName()));
        subst.put("qualifiers", null);
        subst.put("ctorarglist", Collections.emptyList());
        subst.put("ctorargs", Collections.emptyList());
        subst.put("injectedfields", Collections.emptyList());
        subst.put("injectedmethods", Collections.emptyList());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(new BufferedOutputStream(baos));
        String codegen = TemplateHelper.applySubstitutions(ps, template, subst);
        assertEquals("/**\n"
                             + " * \n"
                             + " */\n"
                             + "package io.helidon.pico.tools.example.hello;\n"
                             + "\n"
                             + "import io.helidon.common.Weight;\n"
                             + "import io.helidon.common.Weighted;\n"
                             + "\n"
                             + "import io.helidon.pico.api.RunLevel;\n"
                             + "import io.helidon.pico.spi.DefaultServiceInfo;\n"
                             + "import io.helidon.pico.spi.PostConstructMethod;\n"
                             + "import io.helidon.pico.spi.PreDestroyMethod;\n"
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
                             + "import static io.helidon.pico.spi.InjectionPointInfo.CTOR;\n"
                             + "import static io.helidon.pico.spi.InjectionPointInfo.ElementKind;\n"
                             + "\n"
                             + "/**\n"
                             + " * description1\n"
                             + " * description2\n"
                             + " * \n"
                             + " */\n"
                             + "@Generated(\"test\")\n"
                             + "// @Singleton \n"
                             + "@SuppressWarnings(\"unchecked\")\n"
                             + "public class HelloWorldImpl$$picoActivator extends ParentTypeName {\n"
                             + "    private static final DefaultServiceInfo serviceInfo =\n"
                             + "        DefaultServiceInfo.builder()\n"
                             + "            .serviceTypeName(io.helidon.pico.tools.example.hello.HelloWorldImpl.class.getName())\n"
                             + "            .contractTypeImplemented(io.helidon.pico.tools.example.hello.Hello.class)\n"
                             + "            .activatorType(HelloWorldImpl$$picoActivator.class)\n"
                             + "            .scopeType(jakarta.inject.Singleton.class)\n"
                             + "            .build();\n"
                             + "\n"
                             + "    public static final HelloWorldImpl$$picoActivator INSTANCE = new "
                             + "HelloWorldImpl$$picoActivator();\n"
                             + "\n"
                             + "    protected HelloWorldImpl$$picoActivator() {\n"
                             + "        setServiceInfo(serviceInfo);\n"
                             + "    }\n"
                             + "\n"
                             + "    public Class<?> getServiceType() {\n"
                             + "        return io.helidon.pico.tools.example.hello.HelloWorldImpl.class;\n"
                             + "    }\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public Dependencies getDependencies() {\n"
                             + "        Dependencies deps = Dependencies.builder()\n"
                             + "                .forServiceTypeName(io.helidon.pico.tools.example.hello.HelloWorldImpl.class.getName())\n"
                             + "                [HI.world]:[io.helidon.pico.testsubjects.hello.World]\n"
                             + "                \n"
                             + "                .build().build();\n"
                             + "        return Dependencies.combine(super.getDependencies(), deps);\n"
                             + "    }\n"
                             + "\n"
                             + "    @Override\n"
                             + "    protected HelloWorldImpl createServiceProvider(Map<String, Object> deps) { \n"
                             + "        return new io.helidon.pico.tools.example.hello.HelloWorldImpl();\n"
                             + "    }\n"
                             + "\n"
                             + "}", codegen.trim());
    }

    @Test
    public void moduleTemplate() {
        String template = TemplateHelper.safeLoadTemplate("service-provider-module.hbs");
        Set<String> args = TemplateHelper.getRequiredArguments(template);
        assertEquals("[activators, classname, description, header, modulename, packagename]", args.toString());
        Map<String, Object> subst = new HashMap<>();
        subst.put("classname", "HelloWorldModule");
        subst.put("packagename", "io.helidon.pico.tools.example.hello");
        subst.put("generatedanno", "\"test\"");
        subst.put("activators", Collections.singleton("io.helidon.pico.tools.example.hello.HelloImpl" + DefaultActivatorCreator.INNER_ACTIVATOR_CLASS_NAME));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(new BufferedOutputStream(baos));
        String codegen = TemplateHelper.applySubstitutions(ps, template, subst);
        assertEquals("/**\n"
                             + " * \n"
                             + " */\n"
                             + "package io.helidon.pico.tools.example.hello;\n"
                             + "\n"
                             + "import io.helidon.pico.spi.Module;\n"
                             + "import io.helidon.pico.spi.ServiceBinder;\n"
                             + "\n"
                             + "import jakarta.annotation.Generated;\n"
                             + "import jakarta.inject.Named;\n"
                             + "import jakarta.inject.Singleton;\n"
                             + "import java.util.Optional;\n"
                             + "\n"
                             + "/**\n"
                             + " * \n"
                             + " */\n"
                             + "@Generated(\"test\")\n"
                             + "@Singleton \n"
                             + "public class HelloWorldModule implements Module {\n"
                             + "\n"
                             + "    static final String NAME = \"unnamed\";\n"
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
                             + "    public void configure(ServiceBinder binder) {\n"
                             + "        binder.bind(io.helidon.pico.tools.example.hello.HelloImpl$$picoActivator"
                             + ".INSTANCE);\n"
                             + "\n"
                             + "    }\n"
                             + "\n"
                             + "}", codegen.trim());
    }

    @Test
    public void applicationTemplate() {
        String template = TemplateHelper.safeLoadTemplate("service-provider-application.hbs");
        Set<String> args = TemplateHelper.getRequiredArguments(template);
        assertEquals("[classname, description, header, modulename, packagename, servicetypebindings]", args.toString());
        Map<String, Object> subst = new HashMap<>();
        subst.put("classname", "HelloWorldApplication");
        subst.put("packagename", "io.helidon.pico.tools.example.hello");
        subst.put("generatedanno", "\"test\"");
        subst.put("servicetypebindings",
                  Collections.singletonList(
                          "\n"
                                  + "        /**\n"
                                  + "         * See {@link io.helidon.pico.example.WorldImpl}\n"
                                  + "         */\n"
                                  + "        binder.bindTo(WorldImpl$$picodiActivator.INSTANCE)\n"
                                  + "                .commit();\n"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(new BufferedOutputStream(baos));
        String codegen = TemplateHelper.applySubstitutions(ps, template, subst);
        assertEquals("/**\n"
                             + " * \n"
                             + " */\n"
                             + "package io.helidon.pico.tools.example.hello;\n"
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
                             + "@Generated(\"test\")\n"
                             + "@Singleton \n"
                             + "public class HelloWorldApplication implements Application {\n"
                             + "\n"
                             + "    static final String NAME = \"unnamed\";\n"
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
                             + "        \n"
                             + "        /**\n"
                             + "         * See {@link io.helidon.pico.example.WorldImpl}\n"
                             + "         */\n"
                             + "        binder.bindTo(WorldImpl$$picodiActivator.INSTANCE)\n"
                             + "                .commit();\n"
                             + "\n"
                             + "    }\n"
                             + "\n"
                             + "}", codegen.trim());
    }

    @Test
    public void applicationServiceTypeBinding() {
        String template = TemplateHelper.safeLoadTemplate("service-provider-application-servicetypebinding.hbs");
        Set<String> args = TemplateHelper.getRequiredArguments(template);
        assertEquals("[activator, injectionplan, modulename, servicetypename]", args.toString());
        Map<String, Object> subst = new HashMap<>();
        subst.put("activator", "WorldImpl$$picoActivator");
        subst.put("injectionplan", Collections.singletonList("<bind methods here>"));
        subst.put("modulename", "module.name");
        subst.put("servicetypename", WorldImpl.class);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(new BufferedOutputStream(baos));
        String codegen = TemplateHelper.applySubstitutions(ps, template, subst);
        assertEquals("/**\n"
                             + "         * In module name \"module.name\".\n"
                             + "         * @see {@link class io.helidon.pico.testsubjects.hello.WorldImpl }\n"
                             + "         */\n"
                             + "        binder.bindTo(WorldImpl$$picoActivator)\n"
                             + "                <bind methods here>\n"
                             + "                .commit();", codegen.trim());
    }

}
