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
import java.nio.file.Path;
import java.util.List;

import io.helidon.builder.api.RuntimeType;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.data.Data;
import io.helidon.data.codegen.DataGeneratorProvider;
import io.helidon.data.codegen.common.RepositoryCodegenProvider;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcPrecompiledParentGenerationTest {

    /**
     * Proves JDBC generation retains the complete contracts of inherited
     * abstract methods declared by a separately compiled parent interface and
     * resolves a generic record result using the child's concrete type argument.
     *
     * @throws IOException when generated source cannot be inspected
     */
    @Test
    void generatesInheritedMethodsFromPrecompiledParent() throws IOException {
        TestCompiler.Result parent = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(List.of(Config.class, OptionalValue.class, RuntimeType.class,
                                      JdbcClient.class))
                .addSource("PrecompiledRepositoryContract.java", """
                        package example;

                        import io.helidon.data.jdbc.Jdbc;

                        public interface PrecompiledRepositoryContract<T> {
                            @Jdbc.Statement("SELECT COUNT(*) FROM TEST_VALUE")
                            @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
                            int count();

                            @Jdbc.Statement("INSERT INTO TEST_VALUE (VALUE) VALUES (?)")
                            @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
                            @Jdbc.GeneratedKeys("ID")
                            @Jdbc.RowMapper(KeyMapper.class)
                            Key insert(String value);

                            @Jdbc.Statement("SELECT VALUE FROM TEST_VALUE")
                            Projection<T> projection();
                        }
                        """)
                .addSource("Key.java", """
                        package example;

                        public record Key(long id) {
                        }
                        """)
                .addSource("Projection.java", """
                        package example;

                        public record Projection<T>(T value) {
                        }
                        """)
                .addSource("KeyMapper.java", """
                        package example;

                        import io.helidon.data.jdbc.JdbcClient;

                        public final class KeyMapper implements JdbcClient.RowMapper<Key> {
                            @Override
                            public Key map(JdbcClient.Row row) {
                                return new Key(row.get(1, Long.class));
                            }
                        }
                        """)
                .build()
                .compile();

        String parentDiagnostics = String.join("\n", parent.diagnostics());
        assertThat(parentDiagnostics, parent.success(), is(true));

        TestCompiler.Result child = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(Config.class, OptionalValue.class, RuntimeType.class,
                                      Data.class,
                                      JdbcClient.class,
                                      Service.class,
                                      Tx.class,
                                      Generated.class,
                                      TypeName.class,
                                      DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class,
                                      JdbcRepositoryGeneratorProvider.class))
                .addClasspathEntry(parent.classOutput())
                .addSource("PrecompiledParentRepository.java", """
                        package example;

                        import io.helidon.data.Data;

                        @Data.Repository
                        @Data.Provider("jdbc")
                        public interface PrecompiledParentRepository extends PrecompiledRepositoryContract<String> {
                        }
                        """)
                .build()
                .compile();

        String childDiagnostics = String.join("\n", child.diagnostics());
        assertThat(childDiagnostics, child.success(), is(true));

        Path generatedSource = child.sourceOutput().resolve("example/PrecompiledParentRepository__Jdbc.java");
        Path generatedClass = child.classOutput().resolve("example/PrecompiledParentRepository__Jdbc.class");
        assertThat(Files.exists(generatedSource), is(true));
        assertThat(Files.exists(generatedClass), is(true));
        String source = Files.readString(generatedSource);
        assertThat(source, containsString("KeyMapper keyMapper"));
        assertThat(source, containsString(".map(int.class).one()"));
        assertThat(source, containsString(".generatedKeys().addColumn(\"ID\").map(keyMapper).one()"));
        assertThat(source, containsString("JdbcClient.RowMapper<Projection<String>>"));
        assertThat(source, containsString("new Projection<String>(row.get(\"value\", String.class))"));
    }

    /**
     * Proves JDBC generation resolves the entity type supplied by a repository
     * that specializes a separately compiled generic parent interface.
     */
    @Test
    void resolvesEntityTypeFromPrecompiledGenericParent() {
        TestCompiler.Result parent = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addClasspath(List.of(Data.class))
                .addSource("PrecompiledGenericRepository.java", """
                        package example;

                        import io.helidon.data.Data;

                        public interface PrecompiledGenericRepository<E>
                                extends Data.GenericRepository<E, Long> {
                        }
                        """)
                .build()
                .compile();

        String parentDiagnostics = String.join("\n", parent.diagnostics());
        assertThat(parentDiagnostics, parent.success(), is(true));

        TestCompiler.Result child = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(Config.class, OptionalValue.class, RuntimeType.class,
                                      Data.class,
                                      JdbcClient.class,
                                      Service.class,
                                      Tx.class,
                                      Generated.class,
                                      TypeName.class,
                                      DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class,
                                      JdbcRepositoryGeneratorProvider.class))
                .addClasspathEntry(parent.classOutput())
                .addSource("Books.java", """
                        package example;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;

                        record Book(long id, String name) {
                        }

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface Books extends PrecompiledGenericRepository<Book> {
                            @Jdbc.Statement("SELECT NAME FROM BOOK")
                            String name();
                        }
                        """)
                .build()
                .compile();

        String childDiagnostics = String.join("\n", child.diagnostics());
        assertThat(childDiagnostics, child.success(), is(true));
        assertThat(Files.exists(child.sourceOutput().resolve("example/Books__Jdbc.java")), is(true));
    }

    @Test
    void resolvesConfiguredContractsFromSeparatelyCompiledParents() {
        TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addOption("-parameters")
                .addClasspath(List.of(Config.class, OptionalValue.class, RuntimeType.class, JdbcClient.class))
                .addSource("Parent.java", """
                        package example;
                        import io.helidon.data.jdbc.Jdbc;
                        public interface Parent<T> {
                            @Jdbc.Statement("${application.sql.count}") @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
                            int count();
                            @Jdbc.Statement("${application.sql.projection}")
                            T projection(String name);
                        }
                        """).build().compile();
        assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
        TestCompiler.Result child = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(Config.class, OptionalValue.class, RuntimeType.class, Data.class, JdbcClient.class, Service.class, Tx.class,
                        Generated.class, TypeName.class, DataGeneratorProvider.class, RepositoryCodegenProvider.class,
                        JdbcRepositoryGeneratorProvider.class))
                .addClasspathEntry(parent.classOutput())
                .addSource("Child.java", """
                        package example;
                        import io.helidon.data.Data;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Child extends Parent<String> {
                        }
                        """).build().compile();
        assertThat(String.join("\n", child.diagnostics()), child.success(), is(true));
    }

    @Test
    void rejectsConflictingInheritedLogicalInputNames() {
        TestCompiler.Result result = compile("Child.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                interface Left {
                    @Jdbc.Statement("${application.sql.find}") String find(String left);
                }
                interface Right {
                    @Jdbc.Statement("${application.sql.find}") String find(String right);
                }
                @Data.Repository @Data.Provider("jdbc")
                interface Child extends Left, Right {
                }
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        assertThat(String.join("\n", result.diagnostics()), containsString("incompatible parameter names"));
    }

    /**
     * A compiler placeholder cannot establish the deployment marker contract.
     */
    @Test
    void rejectsConfiguredInputsFromParentsWithoutParameterNames() {
        TestCompiler.Result result = compileInherited("""
                public interface Parent<T> {
                    @Jdbc.Statement("${application.sql.projection}")
                    T projection(String name);
                }
                """, false, "extends Parent<String> {}");
        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("parameter 1"));
        assertThat(diagnostics, containsString("projection"));
        assertThat(diagnostics, containsString("-parameters"));
        assertThat(diagnostics, containsString("redeclare"));
    }

    /**
     * Erased declaration descriptors distinguish generic and overloaded compiled methods.
     */
    @Test
    void resolvesRetainedNamesAcrossGenericAndOverloadedDeclarations() {
        TestCompiler.Result result = compileInherited("""
                public interface Parent<T> {
                    @Jdbc.Statement("${application.sql.generic}")
                    T find(T name);
                    @Jdbc.Statement("${application.sql.binary}")
                    String find(byte[] bytes);
                    @Jdbc.Statement("${application.sql.primitive}")
                    String find(boolean enabled, byte tiny, short small, int count, long id, float ratio, double value);
                    @Jdbc.Statement("${application.sql.rename}")
                    void rename(String name);
                    @Jdbc.Statement("${application.sql.count}") @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
                    int count(long id);
                }
                """, true, "extends Parent<String> {}");
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    /**
     * The spelling arg0 is valid when it comes from source or retained metadata.
     */
    @Test
    void acceptsDeclaredArg0NamesInSourceAndCompiledParents() {
        TestCompiler.Result inherited = compileInherited("""
                public interface Parent<T> {
                    @Jdbc.Statement("${application.sql.find}")
                    String find(String arg0);
                }
                """, true, "extends Parent<String> {}");
        assertThat(String.join("\n", inherited.diagnostics()), inherited.success(), is(true));
        TestCompiler.Result source = compile("SourceNames.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                @Data.Repository @Data.Provider("jdbc")
                interface SourceNames {
                    @Jdbc.Statement("${application.sql.find}")
                    String find(String arg0);
                }
                """);
        assertThat(String.join("\n", source.diagnostics()), source.success(), is(true));
    }

    /**
     * A child can restore the declared binding name without rebuilding its parent.
     */
    @Test
    void acceptsSourceRedeclarationWhenParentNamesAreUnavailable() {
        TestCompiler.Result result = compileInherited("""
                public interface Parent<T> {
                    @Jdbc.Statement("${application.sql.find}")
                    T find(String name);
                }
                """, false, """
                extends Parent<String> {
                    @Override @Jdbc.Statement("${application.sql.find}")
                    String find(String name);
                }
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    /**
     * Parameterless configured selection and positional literals do not require names.
     */
    @Test
    void acceptsParameterlessConfiguredAndPositionalLiteralParentsWithoutNames() {
        TestCompiler.Result result = compileInherited("""
                public interface Parent<T> {
                    @Jdbc.Statement("${application.sql.count}")
                    String count();
                    @Jdbc.Statement("SELECT ?")
                    T find(String name);
                }
                """, false, "extends Parent<String> {}");
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    /**
     * Nested declaring interfaces retain names in their own class files.
     */
    @Test
    void resolvesRetainedNamesFromNestedDeclaringInterfaces() {
        TestCompiler.Result result = compileInherited("""
                public interface Parent<T> {
                    interface Nested {
                        @Jdbc.Statement("${application.sql.find}")
                        String find(String name);
                    }
                }
                """, true, "extends Parent.Nested {}");
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    /**
     * Retained aliases supply configured input identities without Java parameter metadata.
     */
    @Test
    void acceptsConfiguredAliasesFromCompiledGenericParentsWithoutNames() throws IOException {
        TestCompiler.Result result = compileInherited("""
                public interface Parent<T> {
                    @Jdbc.Statement("${application.sql.find}")
                    T find(@Data.Param("lookup") T original);
                    @Jdbc.Statement("${application.sql.rename}")
                    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
                    int rename(@Data.Param("id") long original, @Data.Param("replacement") T text);
                }
                """, false, """
                extends Parent<String> {
                    @Override @Jdbc.Statement("${application.sql.find}")
                    String find(String jdbcClient);
                }
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput().resolve("example/Child__Jdbc.java"));
        assertThat(source, containsString("List.of(\"lookup\")"));
        assertThat(source, containsString("GeneratedJdbcData.prepareNamed("));
        assertThat(source, containsString("\"lookup\", JDBCType.VARCHAR"));
        assertThat(Files.exists(result.classOutput().resolve("example/Child__Jdbc.class")), is(true));
    }

    /**
     * A partial alias does not supply the identity of an unrelated unaliased parameter.
     */
    @Test
    void rejectsUnavailableUnaliasedInputsBesideConfiguredAliases() {
        TestCompiler.Result result = compileInherited("""
                public interface Parent<T> {
                    @Jdbc.Statement("${application.sql.find}")
                    T find(@Data.Param("lookup") T original, String missing);
                }
                """, false, "extends Parent<String> {}");
        assertThat(result.success(), is(false));
        assertThat(String.join("\n", result.diagnostics()), containsString("parameter 2"));
        assertThat(String.join("\n", result.diagnostics()), containsString("Declare @Data.Param"));
    }

    private static TestCompiler.Result compileInherited(String parentBody, boolean preserveNames, String childDeclaration) {
        TestCompiler.Builder parentCompiler = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addClasspath(List.of(Config.class, OptionalValue.class, RuntimeType.class, Data.class, JdbcClient.class))
                .addSource("Parent.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        %s
                        """.formatted(parentBody));
        if (preserveNames) {
            parentCompiler.addOption("-parameters");
        }
        TestCompiler.Result parent = parentCompiler.build().compile();
        assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
        return TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(Config.class, OptionalValue.class, RuntimeType.class, Data.class, JdbcClient.class, Service.class, Tx.class,
                        Generated.class, TypeName.class, DataGeneratorProvider.class, RepositoryCodegenProvider.class,
                        JdbcRepositoryGeneratorProvider.class))
                .addClasspathEntry(parent.classOutput())
                .addSource("Child.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Child %s
                        """.formatted(childDeclaration))
                .build().compile();
    }

    private static TestCompiler.Result compile(String fileName, String source) {
        return TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(Config.class, OptionalValue.class, RuntimeType.class,
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
