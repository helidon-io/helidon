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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JakartaPersistenceCompatibilityTest {

    // Confirms that entity repositories use the Jakarta Persistence generator by default.
    @Test
    void remainsTheDefaultForEntityRepositories() {
        TestCompiler.Result result = compiler()
                .addSource("DefaultRepository.java", entityRepository("DefaultRepository", ""))
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("example/DefaultRepository__Jpa.java")), is(true));
    }

    // Confirms that an explicit jakarta provider still selects the Jakarta Persistence generator.
    @Test
    void supportsExplicitSelectionForEntityRepositories() {
        TestCompiler.Result result = compiler()
                .addSource("ExplicitRepository.java",
                           entityRepository("ExplicitRepository", "@Data.Provider(\"jakarta\")"))
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("example/ExplicitRepository__Jpa.java")), is(true));
    }

    // Rejects an annotation-only repository because no provider is available to generate it.
    @Test
    void rejectsAnnotationOnlyRepositoryWithoutProvider() {
        TestCompiler.Result result = compiler()
                .addSource("MissingProviderRepository.java", """
                        package example;

                        import io.helidon.data.Data;

                        @Data.Repository
                        interface MissingProviderRepository {
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        assertThat(result.diagnostics(), hasItem(containsString("must declare @Data.Provider")));
        assertThat(Files.exists(result.sourceOutput()
                                        .resolve("example/MissingProviderRepository__Jpa.java")), is(false));
    }

    // Rejects a JDBC repository with no provider instead of treating it as a Jakarta Persistence repository.
    @Test
    void doesNotRouteJdbcRepositoryWithoutProviderToJpa() {
        TestCompiler.Result result = compiler()
                .addSource("MissingJdbcProviderRepository.java", """
                        package example;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;

                        @Data.Repository
                        interface MissingJdbcProviderRepository {
                            @Jdbc.Statement("select VALUE from TEST_VALUE")
                            String find();
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        assertThat(result.diagnostics(), hasItem(containsString("must declare @Data.Provider")));
        assertThat(Files.exists(result.sourceOutput()
                                        .resolve("example/MissingJdbcProviderRepository__Jpa.java")), is(false));
    }

    // Confirms that Jakarta Persistence ignores an explicitly selected JDBC repository when JDBC codegen is absent.
    @Test
    void doesNotRouteExplicitJdbcRepositoryToJpaWhenJdbcGeneratorIsUnavailable() {
        TestCompiler.Result result = compiler()
                .addSource("UnavailableProviderRepository.java", """
                        package example;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface UnavailableProviderRepository {
                            @Jdbc.Statement("select VALUE from TEST_VALUE")
                            String find();
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput()
                                        .resolve("example/UnavailableProviderRepository__Jpa.java")), is(false));
    }

    private static String entityRepository(String repositoryName, String providerAnnotation) {
        return """
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
                %s
                interface %s extends Data.GenericRepository<Pokemon, Long> {
                }
                """.formatted(providerAnnotation, repositoryName);
    }

    private static TestCompiler.Builder compiler() {
        return TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(load("jakarta.persistence.Entity"),
                                      load("io.helidon.data.Data"),
                                      load("io.helidon.data.jdbc.Jdbc"),
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
