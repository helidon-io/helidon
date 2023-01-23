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

package io.helidon.pico.tools;

import java.io.File;
import java.util.List;

import io.helidon.pico.Contract;
import io.helidon.pico.ExternalContracts;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleInfoDescriptorTest {

    @Test
    void programmatic() {
        DefaultModuleInfoDescriptor.Builder builder = DefaultModuleInfoDescriptor.builder();
        assertThat(builder.build().contents(),
                   equalTo("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools"
                                   + ".DefaultModuleInfoDescriptor\", \"version=1\"})\n"
                                   + "module unnamed {\n"
                                   + "}"));
        builder.name("my.module");
        builder.descriptionComment("comments here.");
        builder.addItem(ModuleInfoDescriptor.requiresModuleName("their.module", true, false, List.of()));
        assertThat(builder.build().contents(),
                   equalTo("/**\n"
                                   + " * comments here.\n"
                                   + " */\n"
                                   + "// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools"
                                           + ".DefaultModuleInfoDescriptor\", \"version=1\"})\n"
                                   + "module my.module {\n"
                                   + "    requires transitive their.module;\n"
                                   + "}"));

        builder.addItem(ModuleInfoDescriptor.usesExternalContract(ExternalContracts.class));
        assertThat(builder.build().contents(),
                   equalTo("/**\n"
                                   + " * comments here.\n"
                                   + " */\n"
                                   + "// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools"
                                           + ".DefaultModuleInfoDescriptor\", \"version=1\"})\n"
                                   + "module my.module {\n"
                                   + "    requires transitive their.module;\n"
                                   + "    uses " + ExternalContracts.class.getName() + ";\n"
                                   + "}"));

        builder.addItem(ModuleInfoDescriptor.providesContract(Contract.class.getName(), "some.impl"));
        assertThat(builder.build().contents(),
                   equalTo("/**\n"
                                   + " * comments here.\n"
                                   + " */\n"
                                   + "// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools"
                                   + ".DefaultModuleInfoDescriptor\", \"version=1\"})\n"
                                   + "module my.module {\n"
                                   + "    requires transitive their.module;\n"
                                   + "    uses " + ExternalContracts.class.getName() + ";\n"
                                   + "    provides " + Contract.class.getName() + " with some.impl;\n"
                                   + "}"));
    }

    @Test
    void firstUnqualifiedExport() {
        ModuleInfoDescriptor descriptor = DefaultModuleInfoDescriptor.builder()
                .name("test")
                .addItem(ModuleInfoDescriptor.providesContract("cn2", "impl2"))
                .addItem(ModuleInfoDescriptor.providesContract("cn1"))
                .addItem(ModuleInfoDescriptor.exportsPackage("export1", "private.module.name"))
                .addItem(ModuleInfoDescriptor.exportsPackage("export2"))
                .build();
        assertThat(descriptor.contents(),
                   equalTo("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools"
                                   + ".DefaultModuleInfoDescriptor\", \"version=1\"})\n"
                                   + "module test {\n"
                                   + "    provides cn2 with impl2;\n"
                                   + "    provides cn1;\n"
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
        ModuleInfoDescriptor descriptor = DefaultModuleInfoDescriptor.builder()
                .ordering(ModuleInfoDescriptor.Ordering.SORTED)
                .name("test")
                .addItem(ModuleInfoDescriptor.providesContract("cn2", "impl2"))
                .addItem(ModuleInfoDescriptor.providesContract("cn1"))
                .addItem(ModuleInfoDescriptor.exportsPackage("export2"))
                .addItem(DefaultModuleInfoItem.builder()
                                 .exports(true)
                                 .target("export1")
                                 .addWithOrTo("private.module.name")
                                 .addWithOrTo("another.private.module.name")
                                 .addPrecomment("// this is an export1 comment.")
                                 .build())
                .build();
        assertThat(descriptor.contents(),
                   equalTo("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools"
                                   + ".DefaultModuleInfoDescriptor\", \"version=1\"})\n"
                                   + "module test {\n"
                                   + "    provides cn1;\n"
                                   + "    provides cn2 with impl2;\n"
                                   + "    // this is an export1 comment.\n"
                                   + "    exports export1 to another.private.module.name,\n"
                                   + "\t\t\tprivate.module.name;\n"
                                   + "    exports export2;\n"
                                   + "}"));
    }

    @Test
    void innerCommentsNotSupported() {
        String moduleInfo = "module test {\nprovides /* inner comment */ cn;\n}";
        ToolsException te = assertThrows(ToolsException.class, () -> ModuleInfoDescriptor.create(moduleInfo));
        assertThat(te.getMessage(),
                   equalTo("unable to parse lines that have inner comments: 'provides /* inner comment */ cn'"));
    }

    @Test
    void loadCreateAndSave() throws Exception {
        ModuleInfoDescriptor descriptor = ModuleInfoDescriptor
                        .create(CommonUtils.loadStringFromResource("testsubjects/m0._java_"),
                              ModuleInfoDescriptor.Ordering.NATURAL);
        assertThat(descriptor.contents(false),
                   equalTo("module io.helidon.pico {\n"
                                   + "    requires transitive io.helidon.pico.api;\n"
                                   + "    requires static com.fasterxml.jackson.annotation;\n"
                                   + "    requires static lombok;\n"
                                   + "    requires io.helidon.common;\n"
                                   + "    exports io.helidon.pico.spi.impl;\n"
                                   + "    provides io.helidon.pico.PicoServices with io.helidon.pico.spi.impl"
                                        + ".DefaultPicoServices;\n"
                                   + "    uses io.helidon.pico.Module;\n"
                                   + "    uses io.helidon.pico.Application;\n"
                                   + "}"));

        String contents = CommonUtils.loadStringFromFile("target/test-classes/testsubjects/m0._java_").trim();
        descriptor = ModuleInfoDescriptor.create(contents, ModuleInfoDescriptor.Ordering.NATURAL_PRESERVE_COMMENTS);
        assertThat(descriptor.contents(false),
                   equalTo(contents));

        File tempFile = null;
        try {
            tempFile = File.createTempFile("module-info", "");
            descriptor.save(tempFile.toPath());

            String contents2 = CommonUtils.loadStringFromFile("target/test-classes/testsubjects/m0._java_").trim();
            assertThat(contents, equalTo(contents2));
            ModuleInfoDescriptor descriptor2 =
                    ModuleInfoDescriptor.create(contents, ModuleInfoDescriptor.Ordering.NATURAL_PRESERVE_COMMENTS);
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
                        ModuleInfoDescriptor.Ordering.NATURAL);
        assertThat(descriptor.contents(false),
                   equalTo("module io.helidon.pico {\n"
                                   + "    requires transitive io.helidon.pico.api;\n"
                                   + "    requires static com.fasterxml.jackson.annotation;\n"
                                   + "    requires static lombok;\n"
                                   + "    requires io.helidon.common;\n"
                                   + "    exports io.helidon.pico.spi.impl;\n"
                                   + "    provides io.helidon.pico.PicoServices with io.helidon.pico.spi.impl"
                                   + ".DefaultPicoServices;\n"
                                   + "    uses io.helidon.pico.Module;\n"
                                   + "    uses io.helidon.pico.Application;\n"
                                   + "}"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> descriptor.mergeCreate(descriptor));
        assertThat(e.getMessage(), equalTo("can't merge with self"));

        ModuleInfoDescriptor mergeCreated = descriptor.mergeCreate(DefaultModuleInfoDescriptor.toBuilder(descriptor));
        assertThat(descriptor.contents(), equalTo(mergeCreated.contents()));

        ModuleInfoDescriptor descriptor1 = DefaultModuleInfoDescriptor.builder()
                .addItem(ModuleInfoDescriptor.exportsPackage("one"))
                .build();
        ModuleInfoDescriptor descriptor2 = DefaultModuleInfoDescriptor.builder()
                .addItem(ModuleInfoDescriptor.exportsPackage("two"))
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
        DefaultModuleInfoDescriptor.Builder builder = DefaultModuleInfoDescriptor.builder();
        ModuleInfoDescriptor.addIfAbsent(builder, "external",
                                         () -> DefaultModuleInfoItem.builder()
                                                 .uses(true)
                                                 .target("external")
                                                 .addPrecomment("// 1")
                                                 .build());
        ModuleInfoDescriptor.addIfAbsent(builder, "external",
                                                   () -> DefaultModuleInfoItem.builder()
                                                           .uses(true)
                                                           .target("external")
                                                           .addPrecomment("// 2")
                                                           .build());
        ModuleInfoDescriptor descriptor = builder.build();
        assertThat(descriptor.contents(false),
                   equalTo("module unnamed {\n"
                                   + "    // 1\n"
                                   + "    uses external;\n"
                                   + "}"));
    }

}
