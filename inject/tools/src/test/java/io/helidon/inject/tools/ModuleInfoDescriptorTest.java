/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.tools;

import java.io.File;
import java.util.List;

import io.helidon.inject.api.Contract;
import io.helidon.inject.api.ExternalContracts;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleInfoDescriptorTest {

    @Test
    void programmatic() {
        ModuleInfoDescriptor.Builder builder = ModuleInfoDescriptor.builder();
        String typeName = "io.helidon.inject.tools.ModuleInfoDescriptor";
        assertThat(builder.build().contents(),
                   equalTo("// @io.helidon.common.Generated(value = \""
                                   + typeName + "\", trigger = \""
                                   + typeName + "\")\n"
                                   + "module unnamed {\n"
                                   + "}"));
        builder.name("my.module");
        builder.descriptionComment("comments here.");
        builder.addItem(ModuleInfoUtil.requiresModuleName("their.module", true, false, List.of()));
        assertThat(builder.build().contents(),
                   equalTo("/**\n"
                                   + " * comments here.\n"
                                   + " */\n"
                                   + "// @io.helidon.common.Generated(value = \""
                                   + typeName + "\", trigger = \""
                                   + typeName + "\")\n"
                                   + "module my.module {\n"
                                   + "    requires transitive their.module;\n"
                                   + "}"));

        builder.addItem(ModuleInfoUtil.usesExternalContract(ExternalContracts.class));
        assertThat(builder.build().contents(),
                   equalTo("/**\n"
                                   + " * comments here.\n"
                                   + " */\n"
                                   + "// @io.helidon.common.Generated(value = \""
                                   + typeName + "\", trigger = \""
                                   + typeName + "\")\n"
                                   + "module my.module {\n"
                                   + "    requires transitive their.module;\n"
                                   + "    uses " + ExternalContracts.class.getName() + ";\n"
                                   + "}"));

        builder.addItem(ModuleInfoUtil.providesContract(Contract.class.getName(), "some.impl"));
        assertThat(builder.build().contents(),
                   equalTo("/**\n"
                                   + " * comments here.\n"
                                   + " */\n"
                                   + "// @io.helidon.common.Generated(value = \""
                                   + typeName + "\", trigger = \""
                                   + typeName + "\")\n"
                                   + "module my.module {\n"
                                   + "    requires transitive their.module;\n"
                                   + "    uses " + ExternalContracts.class.getName() + ";\n"
                                   + "    provides " + Contract.class.getName() + " with some.impl;\n"
                                   + "}"));
    }

    @Test
    void firstUnqualifiedExport() {
        ModuleInfoDescriptor descriptor = ModuleInfoDescriptor.builder()
                .name("test")
                .addItem(ModuleInfoUtil.providesContract("cn2", "impl2"))
                .addItem(ModuleInfoUtil.providesContract("cn1", "impl1"))
                .addItem(ModuleInfoUtil.exportsPackage("export1", "private.module.name"))
                .addItem(ModuleInfoUtil.exportsPackage("export2"))
                .build();

        String typeName = "io.helidon.inject.tools.ModuleInfoDescriptor";
        assertThat(descriptor.contents(),
                   equalTo("// @io.helidon.common.Generated(value = \""
                                   + typeName + "\", trigger = \""
                                   + typeName + "\")\n"
                                   + "module test {\n"
                                   + "    provides cn2 with impl2;\n"
                                   + "    provides cn1 with impl1;\n"
                                   + "    exports export1 to private.module.name;\n"
                                   + "    exports export2;\n"
                                   + "}"));

        assertThat(descriptor.firstUnqualifiedPackageExport().orElseThrow(),
                   equalTo("export2"));
        assertThat(descriptor.first("cn1").orElseThrow().provides(),
                   is(true));
    }

    @Test
    void sortedWithComments() {
        ModuleInfoDescriptor descriptor = ModuleInfoDescriptor.builder()
                .ordering(ModuleInfoOrdering.SORTED)
                .name("test")
                .addItem(ModuleInfoUtil.providesContract("cn2", "impl2"))
                .addItem(ModuleInfoUtil.providesContract("cn1", "impl1"))
                .addItem(ModuleInfoUtil.exportsPackage("export2"))
                .addItem(ModuleInfoItem.builder()
                                 .exports(true)
                                 .target("export1")
                                 .addWithOrTo("private.module.name")
                                 .addWithOrTo("another.private.module.name")
                                 .addPrecomment("    // this is an export1 comment")
                                 .build())
                .build();

        String typeName = "io.helidon.inject.tools.ModuleInfoDescriptor";
        assertThat(descriptor.contents(),
                   equalTo("// @io.helidon.common.Generated(value = \""
                                   + typeName + "\", trigger = \""
                                   + typeName + "\")\n"
                                   + "module test {\n"
                                   + "    provides cn1 with impl1;\n"
                                   + "    provides cn2 with impl2;\n"
                                   + "    // this is an export1 comment\n"
                                   + "    exports export1 to another.private.module.name,\n"
                                   + "\t\t\tprivate.module.name;\n"
                                   + "    exports export2;\n"
                                   + "}"));
    }

    @Test
    void innerCommentsNotSupported() {
        String moduleInfo = "module test {\nprovides /* inner comment */ cn;\n}";
        ToolsException te = assertThrows(ToolsException.class,
                                         () -> ModuleInfoDescriptor
                                                 .create(moduleInfo, ModuleInfoOrdering.NATURAL_PRESERVE_COMMENTS, true));
        assertThat(te.getMessage(),
                   equalTo("Unable to load or parse module-info: module test {\nprovides /* inner comment */ cn;\n}"));

        ModuleInfoDescriptor descriptor = ModuleInfoDescriptor.create(moduleInfo);
        assertThat(descriptor.handled(),
                   is(false));
        assertThat(descriptor.unhandledLines(),
                   contains("module test {"));
        assertThat(descriptor.error().orElseThrow().getMessage(),
                   equalTo("Unable to load or parse module-info: module test {\nprovides /* inner comment */ cn;\n}"));
    }

    // filed https://github.com/helidon-io/helidon/issues/5697
    @Test
    void annotationsNotSupported() {
        String moduleInfo = "import io.helidon.common.features.api.Feature;\n"
                + "import io.helidon.common.features.api.HelidonFlavor;\n"
                + "\n"
                + "/**\n"
                + " * Helidon SE Config module.\n"
                + " *\n"
                + " * @see io.helidon.config\n"
                + " */\n"
                + "@Feature(value = \"Config\",\n"
                + "        description = \"Configuration module\",\n"
                + "        in = {HelidonFlavor.NIMA, HelidonFlavor.SE}\n"
                + ")\n"
                + "module io.helidon.config {\n}\n";
        ToolsException te = assertThrows(ToolsException.class,
                                         () -> ModuleInfoDescriptor
                                                 .create(moduleInfo, ModuleInfoOrdering.NATURAL_PRESERVE_COMMENTS, true));
        assertThat(te.getCause().getMessage(),
                   equalTo("Failed to parse line: @Feature(value = \"Config\", description = \"Configuration module\", "
                                   + "in = {HelidonFlavor.NIMA, HelidonFlavor.SE}"));

        ModuleInfoDescriptor descriptor = ModuleInfoDescriptor.create(moduleInfo);
        assertThat(descriptor.handled(),
                   is(false));
        assertThat(descriptor.unhandledLines(),
                   contains("@Feature(value = \"Config\", description = \"Configuration module\", in = {HelidonFlavor"
                                   + ".NIMA, HelidonFlavor.SE}"));
        assertThat(descriptor.error().orElseThrow().getCause().getMessage(),
                   equalTo("Failed to parse line: @Feature(value = \"Config\", description = \"Configuration module\", "
                                   + "in = {HelidonFlavor.NIMA, HelidonFlavor.SE}"));
    }

    @Test
    void loadCreateAndSave() throws Exception {
        ModuleInfoDescriptor descriptor = ModuleInfoDescriptor
                .create(CommonUtils.loadStringFromResource("testsubjects/m0._java_"),
                        ModuleInfoOrdering.NATURAL, true);
        assertThat(descriptor.contents(false),
                   equalTo("module io.helidon.inject {\n"
                                   + "    requires transitive io.helidon.inject.api;\n"
                                   + "    requires static com.fasterxml.jackson.annotation;\n"
                                   + "    requires static lombok;\n"
                                   + "    requires io.helidon.common;\n"
                                   + "    exports io.helidon.inject.spi.impl;\n"
                                   + "    provides io.helidon.inject.api.InjectionServices with io.helidon.inject.spi.impl"
                                   + ".DefaultInjectionServices;\n"
                                   + "    uses io.helidon.inject.api.ModuleComponent;\n"
                                   + "    uses io.helidon.inject.api.Application;\n"
                                   + "    opens io.helidon.config to weld.core.impl,\n"
                                   + "            io.helidon.microprofile.cdi;\n"
                                   + "}"));

        String contents = CommonUtils.loadStringFromFile("target/test-classes/testsubjects/m0._java_").trim();
        descriptor = ModuleInfoDescriptor.create(contents, ModuleInfoOrdering.NATURAL_PRESERVE_COMMENTS, true);
        assertThat(descriptor.contents(false),
                   equalTo(contents));

        File tempFile = null;
        try {
            tempFile = File.createTempFile("module-info", "");
            descriptor.save(tempFile.toPath());

            String contents2 = CommonUtils.loadStringFromFile("target/test-classes/testsubjects/m0._java_").trim();
            assertThat(contents, equalTo(contents2));
            ModuleInfoDescriptor descriptor2 =
                    ModuleInfoDescriptor.create(contents, ModuleInfoOrdering.NATURAL_PRESERVE_COMMENTS, true);
            assertThat(descriptor, equalTo(descriptor2));
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    @Test
    void mergeCreate() {
        ModuleInfoDescriptor descriptor = ModuleInfoDescriptor
                .create(CommonUtils.loadStringFromResource("testsubjects/m0._java_"),
                        ModuleInfoOrdering.NATURAL_PRESERVE_COMMENTS, true);
        assertThat(descriptor.contents(false),
                   equalTo("module io.helidon.inject {\n"
                                   + "\n"
                                   + "    requires transitive io.helidon.inject.api;\n"
                                   + "    requires static com.fasterxml.jackson.annotation;\n"
                                   + "    requires static lombok;\n"
                                   + "    requires io.helidon.common;\n"
                                   + "\n"
                                   + "    exports io.helidon.inject.spi.impl;\n"
                                   + "\n"
                                   + "    provides io.helidon.inject.api.InjectionServices with io.helidon.inject.spi.impl.DefaultInjectionServices;\n"
                                   + "\n"
                                   + "    uses io.helidon.inject.api.ModuleComponent;\n"
                                   + "    uses io.helidon.inject.api.Application;\n"
                                   + "\n"
                                   + "    // needed when running with modules - to make private methods accessible\n"
                                   + "    // another comment with a semicolon;\n"
                                   + "    opens io.helidon.config to weld.core.impl,\n"
                                   + "            io.helidon.microprofile.cdi;\n"
                                   + "}"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> descriptor.mergeCreate(descriptor));
        assertThat(e.getMessage(), equalTo("can't merge with self"));

        ModuleInfoDescriptor mergeCreated = descriptor.mergeCreate(ModuleInfoDescriptor.builder(descriptor));
        assertThat(descriptor.contents(), equalTo(mergeCreated.contents()));

        ModuleInfoDescriptor descriptor1 = ModuleInfoDescriptor.builder()
                .addItem(ModuleInfoUtil.exportsPackage("one"))
                .build();
        ModuleInfoDescriptor descriptor2 = ModuleInfoDescriptor.builder()
                .addItem(ModuleInfoUtil.exportsPackage("two"))
                .build();
        mergeCreated = descriptor1.mergeCreate(descriptor2);
        assertThat(mergeCreated.contents(false),
                   equalTo("module unnamed {\n"
                                   + "    exports one;\n"
                                   + "    exports two;\n"
                                   + "}"));
    }

    @Test
    void addIfAbsent() {
        ModuleInfoDescriptor.Builder builder = ModuleInfoDescriptor.builder();
        ModuleInfoUtil.addIfAbsent(builder, "external",
                                   () -> ModuleInfoItem.builder()
                                           .uses(true)
                                           .target("external")
                                           .addPrecomment("    // 1")
                                           .build());
        ModuleInfoUtil.addIfAbsent(builder, "external",
                                   () -> ModuleInfoItem.builder()
                                           .uses(true)
                                           .target("external")
                                           .addPrecomment("    // 2")
                                           .build());
        ModuleInfoDescriptor descriptor = builder.build();
        assertThat(descriptor.contents(false),
                   equalTo("module unnamed {\n"
                                   + "    // 1\n"
                                   + "    uses external;\n"
                                   + "}"));
    }

}
