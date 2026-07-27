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
package io.helidon.data.jakarta.persistence.codegen;

import java.nio.file.Files;
import java.util.List;

import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.data.codegen.DataGeneratorProvider;
import io.helidon.data.codegen.common.RepositoryCodegenProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JakartaPersistenceCompatibilityTest {

    @Test
    void remainsTheDefaultAndSupportsExplicitSelection() {
        TestCompiler.Result result = compiler()
                .addSource("Repositories.java", """
                        package example;

                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import io.helidon.data.Data;

                        @Entity
                        class Pokemon {
                            @Id
                            Long id;
                        }

                        @Data.Repository
                        interface DefaultRepository extends Data.GenericRepository<Pokemon, Long> {
                        }

                        @Data.Repository
                        @Data.Provider("jakarta")
                        interface ExplicitRepository extends Data.GenericRepository<Pokemon, Long> {
                        }

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface OtherProviderRepository extends Data.GenericRepository<Pokemon, Long> {
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("example/DefaultRepository__Jpa.java")), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("example/ExplicitRepository__Jpa.java")), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("example/OtherProviderRepository__Jpa.java")), is(false));
    }

    private static TestCompiler.Builder compiler() {
        return TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(load("jakarta.persistence.Entity"),
                                      load("io.helidon.data.Data"),
                                      load("io.helidon.data.jakarta.persistence.JpaRepositoryExecutor"),
                                      load("io.helidon.service.registry.Service"),
                                      load("io.helidon.transaction.Tx"),
                                      load("io.helidon.common.Generated"),
                                      load("io.helidon.common.types.TypeName"),
                                      DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class,
                                      JakartaPersistenceGeneratorProvider.class));
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing test classpath entry " + className, e);
        }
    }
}
