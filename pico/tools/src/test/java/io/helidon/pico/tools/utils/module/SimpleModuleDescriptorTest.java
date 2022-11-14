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

package io.helidon.pico.tools.utils.module;

import io.helidon.pico.Contract;
import io.helidon.pico.ExternalContracts;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.types.SimpleModuleDescriptor;
import io.helidon.pico.tools.utils.CommonUtils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SimpleModuleDescriptorTest {

    @Test
    public void moduleInfo() {
        SimpleModuleDescriptor descriptor = new SimpleModuleDescriptor();
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module unnamed {\n"
                             + "}", descriptor.toString());

        descriptor.add(new SimpleModuleDescriptor.Item("TheirModule").requires(true).isTransitive(true));
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module unnamed {\n"
                             + "    requires transitive TheirModule;\n"
                             + "}", descriptor.toString());

        descriptor.add(SimpleModuleDescriptor.Item.usesExternalContract(ExternalContracts.class));
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module unnamed {\n"
                             + "    requires transitive TheirModule;\n"
                             + "    uses io.helidon.pico.api.ExternalContracts;\n"
                             + "}", descriptor.toString());

        descriptor.add(SimpleModuleDescriptor.Item.providesContract(ExternalContracts.class, "pkg.to.MyServiceInThisModuleImpl"));
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module unnamed {\n"
                             + "    requires transitive TheirModule;\n"
                             + "    uses io.helidon.pico.api.ExternalContracts;\n"
                             + "    provides io.helidon.pico.api.ExternalContracts with pkg.to.MyServiceInThisModuleImpl;\n"
                             + "}", descriptor.toString());

        descriptor.add(SimpleModuleDescriptor.Item.providesContract(Contract.class, "pkg.to.MyServiceInThisModuleImpl"));
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module unnamed {\n"
                             + "    requires transitive TheirModule;\n"
                             + "    uses io.helidon.pico.api.ExternalContracts;\n"
                             + "    provides io.helidon.pico.api.ExternalContracts with pkg.to.MyServiceInThisModuleImpl;\n"
                             + "    provides io.helidon.pico.api.Contract with pkg.to.MyServiceInThisModuleImpl;\n"
                             + "}", descriptor.toString());

        descriptor.add(SimpleModuleDescriptor.Item.providesContract(Contract.class, "pkg.to.AnotherServiceInThisModuleImpl"));
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module unnamed {\n"
                             + "    requires transitive TheirModule;\n"
                             + "    uses io.helidon.pico.api.ExternalContracts;\n"
                             + "    provides io.helidon.pico.api.ExternalContracts with pkg.to.MyServiceInThisModuleImpl;\n"
                             + "    provides io.helidon.pico.api.Contract with pkg.to.MyServiceInThisModuleImpl,\n"
                             + "\t\t\tpkg.to.AnotherServiceInThisModuleImpl;\n"
                             + "}", descriptor.toString());
    }

    @Test
    public void getFirstUnqualifiedExport() {
        SimpleModuleDescriptor descriptor = new SimpleModuleDescriptor("test");
        descriptor.add(SimpleModuleDescriptor.Item.providesContract("cn"));
        descriptor.add(SimpleModuleDescriptor.Item.providesContract("cn2").with("impl2"));
        descriptor.add(SimpleModuleDescriptor.Item.providesContract("cn3").with("impl3"));
        descriptor.add(SimpleModuleDescriptor.Item.providesContract("uses"));
        descriptor.add(SimpleModuleDescriptor.Item.exportsPackage("export1").to("to"));
        descriptor.add(SimpleModuleDescriptor.Item.exportsPackage("export2"));
        descriptor.add(SimpleModuleDescriptor.Item.exportsPackage("export3"));

        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module test {\n"
                             + "    provides cn;\n"
                             + "    provides cn2 with impl2;\n"
                             + "    provides cn3 with impl3;\n"
                             + "    provides uses;\n"
                             + "    exports export1 to to;\n"
                             + "    exports export2;\n"
                             + "    exports export3;\n"
                             + "}",
                     descriptor.getContents());
        assertEquals("export2", descriptor.getFirstUnqualifiedPackageExport());
    }

    @Test
    public void setOrReplace() {
        SimpleModuleDescriptor descriptor = new SimpleModuleDescriptor("test", SimpleModuleDescriptor.Ordering.SORTED);
        descriptor.add(SimpleModuleDescriptor.Item.providesContract("cn"));
        descriptor.add(SimpleModuleDescriptor.Item.providesContract("cn2").with("impl2"));
        descriptor.add(SimpleModuleDescriptor.Item.providesContract("cn3").with("impl3"));
        descriptor.add(SimpleModuleDescriptor.Item.usesExternalContract("uses"));
        descriptor.add(SimpleModuleDescriptor.Item.exportsPackage("export1").to("to"));
        descriptor.add(SimpleModuleDescriptor.Item.exportsPackage("export2"));
        descriptor.add(SimpleModuleDescriptor.Item.exportsPackage("export3"));

        descriptor.setOrReplace("cn2","impl2b");
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module test {\n"
                             + "    provides cn;\n"
                             + "    provides cn2 with impl2b;\n"
                             + "    provides cn3 with impl3;\n"
                             + "    exports export1 to to;\n"
                             + "    exports export2;\n"
                             + "    exports export3;\n"
                             + "    uses uses;\n"
                             + "}",
                     descriptor.getContents());

        descriptor.setOrReplace("cn3","impl3b");
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module test {\n"
                             + "    provides cn;\n"
                             + "    provides cn2 with impl2b;\n"
                             + "    provides cn3 with impl3b;\n"
                             + "    exports export1 to to;\n"
                             + "    exports export2;\n"
                             + "    exports export3;\n"
                             + "    uses uses;\n"
                             + "}",
                     descriptor.getContents());

        descriptor.setOrReplace("cn4","impl");
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module test {\n"
                             + "    provides cn;\n"
                             + "    provides cn2 with impl2b;\n"
                             + "    provides cn3 with impl3b;\n"
                             + "    provides cn4 with impl;\n"
                             + "    exports export1 to to;\n"
                             + "    exports export2;\n"
                             + "    exports export3;\n"
                             + "    uses uses;\n"
                             + "}",
                     descriptor.getContents());
    }

    @Test
    public void innerCommentsNotSupported() {
        String moduleInfo = "module test {\nprovides /* inner comment */ cn;\n}";
        ToolsException te = assertThrows(ToolsException.class, () -> SimpleModuleDescriptor
                .uncheckedLoad(moduleInfo));
        assertEquals("unable to parse lines that have inner comments: 'provides /* inner comment */ cn'", te.getMessage());
    }

    @Test
    public void m0() throws Exception {
        SimpleModuleDescriptor descriptor =
                SimpleModuleDescriptor
                        .load(CommonUtils.loadStringFromResource("module-info-testsubjects/m0.java"),
                              SimpleModuleDescriptor.Ordering.NATURAL);
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module io.helidon.pico {\n"
                             + "    requires transitive io.helidon.pico.api;\n"
                             + "    requires static com.fasterxml.jackson.annotation;\n"
                             + "    requires static lombok;\n"
                             + "    requires io.helidon.common;\n"
                             + "    exports io.helidon.pico.spi.impl;\n"
                             + "    provides io.helidon.pico.spi.PicoServices with io.helidon.pico.spi.impl"
                             + ".DefaultPicoServices;\n"
                             + "    uses io.helidon.pico.spi.Module;\n"
                             + "    uses io.helidon.pico.spi.Application;\n"
                             + "}",
                     descriptor.toString());

        String contents = CommonUtils.loadStringFromFile("target/test-classes/module-info-testsubjects/m0.java").trim();
        descriptor = SimpleModuleDescriptor.load(contents, SimpleModuleDescriptor.Ordering.NATURAL_PRESERVE_COMMENTS);
        assertEquals(contents, descriptor.getContents());
    }

    @Test
    public void m1() throws Exception {
        SimpleModuleDescriptor descriptor = SimpleModuleDescriptor
                .load(CommonUtils.loadStringFromFile("target/test-classes/module-info-testsubjects/m1.java"),
                      SimpleModuleDescriptor.Ordering.NATURAL);
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module io.helidon.pico.tools {\n"
                             + "    requires io.helidon.common;\n"
                             + "    requires java.compiler;\n"
                             + "    requires io.helidon.pico.api;\n"
                             + "    requires io.helidon.pico;\n"
                             + "    requires static lombok;\n"
                             + "    requires static com.fasterxml.jackson.annotation;\n"
                             + "    requires handlebars;\n"
                             + "    exports io.helidon.pico.tools;\n"
                             + "    exports io.helidon.pico.tools.impl to io.helidon.pico.processor;\n"
                             + "    exports io.helidon.pico.tools.utils to io.helidon.pico.processor;\n"
                             + "    exports io.helidon.pico.tools.utils.module to io.helidon.pico.processor, "
                             + "handlebars;\n"
                             + "    exports io.helidon.pico.tools.utils.template to io.helidon.pico.processor;\n"
                             + "    provides io.helidon.pico.tools.creator.ActivatorCreator with io.helidon.pico"
                             + ".tools.creator.impl.DefaultActivatorCreator;\n"
                             + "    provides io.helidon.pico.tools.creator.ApplicationCreator with io.helidon.pico"
                             + ".tools.creator.impl.DefaultApplicationCreator;\n"
                             + "}",
                     descriptor.toString());

        String contents = CommonUtils.loadStringFromFile("target/test-classes/module-info-testsubjects/m1.java").trim();
        descriptor = SimpleModuleDescriptor.load(contents, SimpleModuleDescriptor.Ordering.NATURAL_PRESERVE_COMMENTS);
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
                             + "\n"
                             + "module io.helidon.pico.tools {\n"
                             + "    requires io.helidon.common;\n"
                             + "    requires java.compiler;\n"
                             + "    requires io.helidon.pico.api;\n"
                             + "    requires io.helidon.pico;\n"
                             + "    requires static lombok;\n"
                             + "    requires static com.fasterxml.jackson.annotation;\n"
                             + "    requires handlebars;\n"
                             + "\n"
                             + "    exports io.helidon.pico.tools;\n"
                             + "    exports io.helidon.pico.tools.impl to io.helidon.pico.processor;\n"
                             + "    exports io.helidon.pico.tools.utils to io.helidon.pico.processor;\n"
                             + "    exports io.helidon.pico.tools.utils.module to io.helidon.pico.processor, "
                             + "handlebars;\n"
                             + "    exports io.helidon.pico.tools.utils.template to io.helidon.pico.processor;\n"
                             + "\n"
                             + "    provides io.helidon.pico.tools.creator.ActivatorCreator with io.helidon.pico"
                             + ".tools.creator.impl.DefaultActivatorCreator;\n"
                             + "    provides io.helidon.pico.tools.creator.ApplicationCreator with io.helidon.pico"
                             + ".tools.creator.impl.DefaultApplicationCreator;\n"
                             + "}",
                     descriptor.getContents());
    }

    @Test
    public void m2() throws Exception {
        SimpleModuleDescriptor descriptor = SimpleModuleDescriptor
                .load(CommonUtils.loadStringFromFile("target/test-classes/module-info-testsubjects/m2.java"),
                      SimpleModuleDescriptor.Ordering.NATURAL);
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module io.helidon.config.etcd {\n"
                             + "    requires java.logging;\n"
                             + "    requires transitive io.helidon.config;\n"
                             + "    requires etcd4j;\n"
                             + "    requires grpc.api;\n"
                             + "    requires grpc.protobuf;\n"
                             + "    requires grpc.stub;\n"
                             + "    requires com.google.protobuf;\n"
                             + "    requires com.google.common;\n"
                             + "    requires io.helidon.common;\n"
                             + "    requires io.helidon.common.media.type;\n"
                             + "    requires static java.annotation;\n"
                             + "    exports io.helidon.config.etcd;\n"
                             + "    provides io.helidon.config.spi.ConfigSourceProvider with io.helidon.config.etcd"
                             + ".EtcdConfigSourceProvider;\n"
                             + "    provides io.helidon.config.spi.ChangeWatcherProvider with io.helidon.config.etcd"
                             + ".EtcdWatcherProvider;\n"
                             + "}",
                     descriptor.toString());

        String contents = CommonUtils.loadStringFromFile("target/test-classes/module-info-testsubjects/m2.java").trim();
        descriptor = SimpleModuleDescriptor.load(contents, SimpleModuleDescriptor.Ordering.NATURAL_PRESERVE_COMMENTS);
        assertNotEquals(contents, descriptor.getContents(), "this is because we fold out the includes, see below");
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
                             + "\n"
                             + "/**\n"
                             + " * Etcd config source implementation.\n"
                             + " */\n"
                             + "module io.helidon.config.etcd {\n"
                             + "\n"
                             + "    requires java.logging;\n"
                             + "    requires transitive io.helidon.config;\n"
                             + "    requires etcd4j;\n"
                             + "    requires grpc.api;\n"
                             + "    requires grpc.protobuf;\n"
                             + "    requires grpc.stub;\n"
                             + "    requires com.google.protobuf;\n"
                             + "    requires com.google.common;\n"
                             + "    requires io.helidon.common;\n"
                             + "    requires io.helidon.common.media.type;\n"
                             + "    // used only for compilation of generated classes\n"
                             + "    requires static java.annotation;\n"
                             + "\n"
                             + "    exports io.helidon.config.etcd;\n"
                             + "\n"
                             + "    provides io.helidon.config.spi.ConfigSourceProvider with io.helidon.config.etcd"
                             + ".EtcdConfigSourceProvider;\n"
                             + "    provides io.helidon.config.spi.ChangeWatcherProvider with io.helidon.config.etcd"
                             + ".EtcdWatcherProvider;\n"
                             + "}", descriptor.getContents());
    }

    @Test
    public void m3() throws Exception {
        SimpleModuleDescriptor descriptor = SimpleModuleDescriptor
                .load(CommonUtils.loadStringFromFile("target/test-classes/module-info-testsubjects/m3.java"),
                      SimpleModuleDescriptor.Ordering.NATURAL);
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module io.helidon.pico.processor {\n"
                             + "    requires io.helidon.common;\n"
                             + "    requires java.compiler;\n"
                             + "    requires io.helidon.pico.api;\n"
                             + "    requires io.helidon.pico;\n"
                             + "    requires io.helidon.pico.tools;\n"
                             + "    requires jdk.compiler;\n"
                             + "    exports io.helidon.pico.processor;\n"
                             + "    exports io.helidon.pico.processor.spi;\n"
                             + "    uses io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer;\n"
                             + "    provides javax.annotation.processing.Processor with io.helidon.pico.processor"
                             + ".ContractAnnotationProcessor,\n"
                             + "\t\t\tio.helidon.pico.processor.InjectAnnotationProcessor,\n"
                             + "\t\t\tio.helidon.pico.processor.PostConstructPreDestroyAnnotationProcessor,\n"
                             + "\t\t\tio.helidon.pico.processor.ServiceAnnotationProcessor,\n"
                             + "\t\t\tio.helidon.pico.processor.CustomAnnotationProcessor;\n"
                             + "}",
                     descriptor.toString());

        String contents = CommonUtils.loadStringFromFile("target/test-classes/module-info-testsubjects/m3.java").trim();
        descriptor = SimpleModuleDescriptor.load(contents, SimpleModuleDescriptor.Ordering.NATURAL_PRESERVE_COMMENTS);
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
                             + "module io.helidon.pico.processor {\n"
                             + "    requires io.helidon.common;\n"
                             + "    requires java.compiler;\n"
                             + "    requires io.helidon.pico.api;\n"
                             + "    requires io.helidon.pico;\n"
                             + "    requires io.helidon.pico.tools;\n"
                             + "    requires jdk.compiler;\n"
                             + "\n"
                             + "    exports io.helidon.pico.processor;\n"
                             + "    exports io.helidon.pico.processor.spi;\n"
                             + "\n"
                             + "    uses io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer;\n"
                             + "\n"
                             + "    provides javax.annotation.processing.Processor with io.helidon.pico.processor"
                             + ".ContractAnnotationProcessor,\n"
                             + "\t\t\tio.helidon.pico.processor.InjectAnnotationProcessor,\n"
                             + "\t\t\tio.helidon.pico.processor.PostConstructPreDestroyAnnotationProcessor,\n"
                             + "\t\t\tio.helidon.pico.processor.ServiceAnnotationProcessor,\n"
                             + "\t\t\tio.helidon.pico.processor.CustomAnnotationProcessor;\n"
                             + "}",
                     descriptor.getContents());
    }

    @Test
    public void m4() throws Exception {
        SimpleModuleDescriptor descriptor = SimpleModuleDescriptor
                .load(CommonUtils.loadStringFromFile("target/test-classes/module-info-testsubjects/m4.java"),
                      SimpleModuleDescriptor.Ordering.NATURAL);
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module io.helidon.nima.http.api {\n"
                             + "    requires io.helidon.common.http;\n"
                             + "    requires static io.helidon.pico.api;\n"
                             + "    requires static io.helidon.pico;\n"
                             + "    requires static io.helidon.pico.tools;\n"
                             + "    requires static io.helidon.pico.processor;\n"
                             + "    exports io.helidon.nima.http.api;\n"
                             + "    provides io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer with io"
                             + ".helidon.nima.http.api.pico.processor.GetProducer,\n"
                             + "\t\t\tio.helidon.nima.http.api.pico.processor.HttpEndpointInterceptorProducer;\n"
                             + "}",
                     descriptor.toString());

        String contents = CommonUtils.loadStringFromFile("target/test-classes/module-info-testsubjects/m4.java").trim();
        descriptor = SimpleModuleDescriptor.load(contents, SimpleModuleDescriptor.Ordering.NATURAL_PRESERVE_COMMENTS);
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
                             + "/**\n"
                             + " * Níma HTTP API, mostly annotations to use to create HTTP endpoints.\n"
                             + " */\n"
                             + "module io.helidon.nima.http.api {\n"
                             + "    requires io.helidon.common.http;\n"
                             + "    requires static io.helidon.pico.api;\n"
                             + "    requires static io.helidon.pico;\n"
                             + "    requires static io.helidon.pico.tools;\n"
                             + "    requires static io.helidon.pico.processor;\n"
                             + "\n"
                             + "    exports io.helidon.nima.http.api;\n"
                             + "\n"
                             + "    provides io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer with io"
                             + ".helidon.nima.http.api.pico.processor.GetProducer,\n"
                             + "\t\t\tio.helidon.nima.http.api.pico.processor.HttpEndpointInterceptorProducer;\n"
                             + "}",
                     descriptor.getContents());
    }

    @Test
    public void m5() throws Exception {
        String contents = CommonUtils.loadStringFromFile("target/test-classes/module-info-testsubjects/m5.java").trim();
        SimpleModuleDescriptor descriptor = SimpleModuleDescriptor.load(contents, SimpleModuleDescriptor.Ordering.NATURAL);
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module unnamed {\n"
                             + "    provides io.helidon.pico.spi.Module with io.helidon.pico.testsubjects.ext.tbox"
                             + ".pico.pico.picoModule;\n"
                             + "    exports io.helidon.pico.spi;\n"
                             + "    requires transitive io.helidon.pico;\n"
                             + "}", descriptor.toString());
        assertEquals(contents, descriptor.getContents());
        assertEquals(contents, descriptor.toString());

        descriptor = SimpleModuleDescriptor.load(contents, SimpleModuleDescriptor.Ordering.NATURAL_PRESERVE_COMMENTS);
        assertEquals(contents, descriptor.getContents());
    }

    @Test
    public void cloningAndOrdering() throws Exception {
        String contents = CommonUtils.loadStringFromFile("target/test-classes/module-info-testsubjects/m5.java").trim();
        SimpleModuleDescriptor descriptor = SimpleModuleDescriptor.load(contents, SimpleModuleDescriptor.Ordering.NATURAL);
        descriptor = descriptor.cloneToOrdering(SimpleModuleDescriptor.Ordering.SORTED);
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module unnamed {\n"
                             + "    requires transitive io.helidon.pico;\n"
                             + "    exports io.helidon.pico.spi;\n"
                             + "    provides io.helidon.pico.spi.Module with io.helidon.pico.testsubjects.ext.tbox"
                             + ".pico.pico.picoModule;\n"
                             + "}", descriptor.toString());
        descriptor = descriptor.clone();
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module unnamed {\n"
                             + "    requires transitive io.helidon.pico;\n"
                             + "    exports io.helidon.pico.spi;\n"
                             + "    provides io.helidon.pico.spi.Module with io.helidon.pico.testsubjects.ext.tbox"
                             + ".pico.pico.picoModule;\n"
                             + "}", descriptor.toString());

        descriptor = SimpleModuleDescriptor.load(contents, SimpleModuleDescriptor.Ordering.SORTED);
        assertEquals("// @Generated({\"provider=oracle\", \"generator=io.helidon.pico.tools.types"
                             + ".SimpleModuleDescriptor\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "module unnamed {\n"
                             + "    requires transitive io.helidon.pico;\n"
                             + "    exports io.helidon.pico.spi;\n"
                             + "    provides io.helidon.pico.spi.Module with io.helidon.pico.testsubjects.ext.tbox"
                             + ".pico.pico.picoModule;\n"
                             + "}", descriptor.toString());
    }

}
