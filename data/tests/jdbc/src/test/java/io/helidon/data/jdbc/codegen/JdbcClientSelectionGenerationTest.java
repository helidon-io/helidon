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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import io.helidon.builder.api.RuntimeType;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.types.TypeName;
import io.helidon.data.Data;
import io.helidon.data.codegen.DataGeneratorProvider;
import io.helidon.data.codegen.common.RepositoryCodegenProvider;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcClientSelectionGenerationTest {

    /**
     * Proves JDBC method contracts remain available to code generation from
     * separately compiled repository interfaces without becoming runtime API.
     */
    @Test
    void methodAnnotationsTargetMethodsAndUseClassRetention() {
        for (Class<?> annotationType : List.of(Jdbc.Statement.class,
                                               Jdbc.Execution.class,
                                               Jdbc.GeneratedKeys.class,
                                               Jdbc.RowMapper.class)) {
            Target target = annotationType.getAnnotation(Target.class);
            Retention retention = annotationType.getAnnotation(Retention.class);

            assertThat(Set.of(target.value()), is(Set.of(ElementType.METHOD)));
            assertThat(retention.value(), is(RetentionPolicy.CLASS));
        }
    }

    /**
     * Proves the JDBC client selector is available only to source processing
     * on repository types.
     */
    @Test
    void clientSelectorTargetsRepositoryTypesAndUsesSourceRetention() {
        Target target = Jdbc.Client.class.getAnnotation(Target.class);
        Retention retention = Jdbc.Client.class.getAnnotation(Retention.class);

        assertThat(Set.of(target.value()), is(Set.of(ElementType.TYPE)));
        assertThat(retention.value(), is(RetentionPolicy.SOURCE));
    }

    /**
     * Proves JDBC generation rejects the provider-neutral persistence-unit
     * selector before it can be interpreted as a JDBC client name.
     */
    @Test
    void rejectsDataPersistenceUnitClientSelection() {
        TestCompiler.Result result = compile("InvalidPersistenceUnitRepository.java", """
                package example;

                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;

                @Data.Repository
                @Data.Provider("jdbc")
                @Data.PersistenceUnit("inventory")
                interface InvalidPersistenceUnitRepository {
                    @Jdbc.Statement("select VALUE from TEST_VALUE")
                    String find();
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("JDBC repositories do not use @Data.PersistenceUnit"));
        assertThat(diagnostics, containsString("@Jdbc.Client"));
        assertThat(diagnostics, containsString("data.clients.jdbc"));
    }

    /**
     * Proves a present JDBC client selector must name one required client
     * rather than silently collapsing to the default client.
     */
    @Test
    void rejectsBlankJdbcClientName() {
        TestCompiler.Result result = compile("BlankClientRepository.java", """
                package example;

                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;

                @Data.Repository
                @Data.Provider("jdbc")
                @Jdbc.Client(" ")
                interface BlankClientRepository {
                    @Jdbc.Statement("select VALUE from TEST_VALUE")
                    String find();
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("A JDBC repository @Jdbc.Client value must not be blank."));
    }

    /**
     * Proves that direct and inherited GenericRepository declarations supply
     * concrete entity metadata without requesting derived repository operations.
     */
    @Test
    void acceptsGenericRepositoryParent() {
        TestCompiler.Result result = compile("BookRepositories.java", """
                package example;

                import java.util.List;

                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;

                record Book(long id) {
                }

                interface BookRepository extends Data.GenericRepository<Book, Long> {
                }

                interface GenericBookRepository<E> extends Data.GenericRepository<E, Long> {
                }

                @Data.Repository
                @Data.Provider("jdbc")
                interface Books extends Data.GenericRepository<Book, Long> {
                    @Jdbc.Statement("SELECT ID FROM BOOK")
                    List<Book> all();
                }

                @Data.Repository
                @Data.Provider("jdbc")
                interface IndirectBooks extends BookRepository {
                    @Jdbc.Statement("SELECT ID FROM BOOK")
                    List<Book> all();
                }

                @Data.Repository
                @Data.Provider("jdbc")
                interface GenericIndirectBooks extends GenericBookRepository<Book> {
                    @Jdbc.Statement("SELECT ID FROM BOOK")
                    List<Book> all();
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("example/Books__Jdbc.java")), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("example/IndirectBooks__Jdbc.java")), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("example/GenericIndirectBooks__Jdbc.java")), is(true));
    }

    /**
     * Proves that resolving a generic parent does not allow JDBC repositories
     * to inherit Data repository operations.
     */
    @Test
    void rejectsGenericOperationBearingDataRepositoryParent() {
        TestCompiler.Result result = compile("InvalidGenericCrudRepository.java", """
                package example;

                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;

                record Book(long id) {
                }

                interface BookRepository<E> extends Data.CrudRepository<E, Long> {
                }

                @Data.Repository
                @Data.Provider("jdbc")
                interface InvalidGenericCrudRepository extends BookRepository<Book> {
                    @Jdbc.Statement("SELECT ID FROM BOOK")
                    Book find();
                }
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics,
                   containsString("A JDBC repository may extend Data.GenericRepository, but it must not extend "
                                          + "a Data repository interface that declares operations."));
    }

    /**
     * Proves that JDBC rejects Data repository parents that declare operations before it attempts to generate their
     * inherited entity operations.
     */
    @Test
    void rejectsOperationBearingDataRepositoryParent() {
        for (String repositoryType : List.of("BasicRepository", "CrudRepository", "PageableRepository")) {
            String repositoryName = "Invalid" + repositoryType;
            TestCompiler.Result result = compile(repositoryName + ".java", """
                    package example;

                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;

                    record Book(long id) {
                    }

                    @Data.Repository
                    @Data.Provider("jdbc")
                    interface %s extends Data.%s<Book, Long> {
                        @Jdbc.Statement("SELECT ID FROM BOOK")
                        Book find();
                    }
                    """.formatted(repositoryName, repositoryType));

            String diagnostics = String.join("\n", result.diagnostics());
            assertThat(diagnostics, result.success(), is(false));
            assertThat(diagnostics,
                       containsString("A JDBC repository may extend Data.GenericRepository, but it must not extend "
                                              + "a Data repository interface that declares operations."));
        }
    }

    private static TestCompiler.Result compile(String fileName, String source) {
        return TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class,
                                      Data.class,
                                      JdbcClient.class,
                                      Service.class,
                                      Tx.class,
                                      Generated.class,
                                      TypeName.class,
                                      DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class,
                                      JdbcRepositoryGeneratorProvider.class))
                .addSource(fileName, source)
                .build()
                .compile();
    }
}
