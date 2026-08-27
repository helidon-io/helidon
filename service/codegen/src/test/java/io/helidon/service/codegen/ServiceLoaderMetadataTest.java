/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.service.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.metadata.MetadataConstants;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ServiceLoaderMetadataTest {
    private static final String HEADER =
            "# List of service contracts we want to support either from service registry, or from service loader";
    private static final String ANNOTATION_SOURCE = """
            package io.helidon.service.registry;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            public final class Service {
                private Service() {
                }

                @Retention(RetentionPolicy.CLASS)
                @Target(ElementType.MODULE)
                public @interface DiscoverFromServiceLoader {
                }
            }
            """;

    @Test
    void replacesServiceLoaderContractsFromModuleUses() throws IOException {
        var first = compiler("""
                import io.helidon.service.registry.Service;

                @Service.DiscoverFromServiceLoader
                module test.module {
                    uses test.spi.Second;
                    uses test.spi.First;
                }
                """, "Second")
                .build()
                .compile();
        assertSuccess(first);
        assertServiceLoader(first.classOutput(), "test.spi.First", "test.spi.Second");

        Path workDir = first.classOutput().getParent();
        var second = compiler("""
                import io.helidon.service.registry.Service;

                @Service.DiscoverFromServiceLoader
                module test.module {
                    uses test.spi.Third;
                    uses test.spi.First;
                }
                """, "Third")
                .workDir(workDir)
                .build()
                .compile();
        assertSuccess(second);
        assertServiceLoader(second.classOutput(), "test.spi.First", "test.spi.Third");
    }

    private static TestCompiler.Builder compiler(String moduleInfo, String additionalContract) {
        return TestCompiler.builder()
                .currentRelease()
                .addProcessor(AptProcessor::new)
                .printDiagnostics(false)
                .addSource("module-info.java", moduleInfo)
                .addSource("io/helidon/service/registry/Service.java", ANNOTATION_SOURCE)
                .addSource("test/spi/First.java", "package test.spi; public interface First {}")
                .addSource("test/spi/" + additionalContract + ".java",
                           "package test.spi; public interface " + additionalContract + " {}");
    }

    private static void assertSuccess(TestCompiler.Result result) {
        assertThat("Compilation diagnostics: " + String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    private static void assertServiceLoader(Path classOutput, String... contracts) throws IOException {
        Path serviceLoader = classOutput.resolve(MetadataConstants.LOCATION)
                .resolve("test.module")
                .resolve(MetadataConstants.SERVICE_LOADER_FILE);
        List<String> expected = new ArrayList<>();
        expected.add(HEADER);
        expected.addAll(List.of(contracts));
        assertThat(Files.readAllLines(serviceLoader), is(expected));
    }
}
