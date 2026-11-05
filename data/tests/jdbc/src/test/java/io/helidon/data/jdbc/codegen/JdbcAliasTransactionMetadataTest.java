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
package io.helidon.data.jdbc.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import io.helidon.builder.api.RuntimeType;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.types.TypeName;
import io.helidon.data.Data;
import io.helidon.data.codegen.DataGeneratorProvider;
import io.helidon.data.codegen.common.RepositoryCodegenProvider;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.codegen.ServiceRegistryCodegenProvider;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcAliasTransactionMetadataTest {

    /**
     * Compares service interception metadata from a source parent and a compiled parent without parameter metadata.
     * The alias survives both compilations while the SOURCE retained transaction annotation survives only source processing.
     */
    @Test
    void separatesRetainedAliasesFromSourceTransactionMetadata() throws IOException {
        String parentSource = """
                package example;
                import java.util.List;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                import io.helidon.transaction.Tx;
                public interface Parent {
                    @Tx.Required
                    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key OR NAME = :key")
                    List<String> find(@Data.Param("key") String renamed);
                }
                """;
        String childSource = """
                package example;
                import io.helidon.data.Data;
                @Data.Repository @Data.Provider("jdbc")
                public interface Child extends Parent {
                }
                """;
        TestCompiler.Result parent = compiler().processors(List.of())
                .addSource("Parent.java", parentSource)
                .build().compile();
        assertThat(parent.diagnostics().toString(), parent.success(), is(true));

        for (boolean sourceParent : List.of(true, false)) {
            TestCompiler.Builder compilation = compiler().addSource("Child.java", childSource);
            if (sourceParent) {
                compilation.addSource("Parent.java", parentSource);
            } else {
                compilation.addClasspathEntry(parent.classOutput());
            }
            TestCompiler.Result child = compilation.build().compile();
            assertThat(child.diagnostics().toString(), child.success(), is(true));
            assertThat(Files.exists(child.classOutput().resolve("example/Child__Jdbc.class")), is(true));
            String repository = Files.readString(child.sourceOutput().resolve("example/Child__Jdbc.java"));
            assertThat(repository, containsString("SQL_FIND, 2)"));
            String descriptor = Files.readString(child.sourceOutput().resolve("example/Child__Jdbc__ServiceDescriptor.java"));
            assertThat(descriptor.contains("io.helidon.transaction.Tx.Required"), is(sourceParent));
            var intercepted = child.sourceOutput().resolve("example/Child__Jdbc__Intercepted.java");
            assertThat(Files.exists(intercepted), is(sourceParent));
            if (sourceParent) {
                assertThat(Files.readString(intercepted), containsString("Child__Jdbc__ServiceDescriptor.METHOD_FIND"));
            }
            assertThat(descriptor, not(containsString("CTOR_ELEMENT,")));
        }
    }

    /**
     * Characterizes the existing constructor interception caused by copying a repository type transaction annotation.
     * Retained aliases do not restrict this type annotation to repository operations.
     */
    @Test
    void characterizesTypeTransactionConstructorInterception() throws IOException {
        TestCompiler.Result result = compiler().addSource("TypedRepository.java", """
                package example;
                import java.util.List;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                import io.helidon.transaction.Tx;
                @Data.Repository @Data.Provider("jdbc") @Tx.Required
                public interface TypedRepository {
                    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :key")
                    List<String> find(@Data.Param("key") String renamed);
                }
                """).build().compile();
        assertThat(result.diagnostics().toString(), result.success(), is(true));
        String descriptor = Files.readString(result.sourceOutput()
                                                    .resolve("example/TypedRepository__Jdbc__ServiceDescriptor.java"));
        assertThat(descriptor, containsString("io.helidon.transaction.Tx.Required"));
        assertThat(descriptor, containsString(".createInvoker(this,"));
        assertThat(descriptor, containsString("CTOR_ELEMENT,"));
        assertThat(Files.exists(result.classOutput().resolve("example/TypedRepository__Jdbc__Intercepted.class")), is(true));
    }

    private static TestCompiler.Builder compiler() {
        return TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addOption("-Ahelidon.registry.interceptionStrategy=EXPLICIT")
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class, Data.class, JdbcClient.class, Service.class, Tx.class,
                                      Generated.class, TypeName.class, DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class, JdbcRepositoryGeneratorProvider.class,
                                      ServiceRegistryCodegenProvider.class));
    }
}
