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
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcBatchGenerationTest {
    @Test
    void rejectsConfiguredBatchWithOrWithoutDefaultSql() {
        for (String reference : List.of("${application.batch}", "${application.batch:insert into T values (?)}")) {
            TestCompiler.Result result = compile("BatchRepository.java", """
                    package example;
                    import java.util.List;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    @Data.Repository @Data.Provider("jdbc")
                    interface BatchRepository {
                        @Jdbc.Statement("%s")
                        @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                        void insert(List<String> values);
                    }
                    """.formatted(reference));
            assertThat(result.success(), is(false));
            assertThat(String.join("\n", result.diagnostics()),
                       containsString("BATCH execution does not support configured statements."));
        }
    }

    @Test
    void compilesAllShapesAndGeneratedNameCollisions() {
        TestCompiler.Result result = compile("BatchRepository.java", """
                package example;
                import java.util.*;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                record Item(Integer statement, String rows, byte[] bindings) { }
                interface Parent<T> {
                    @Jdbc.Statement("insert into T values (?, ?)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    JdbcClient.BatchResult generic(Iterable<T> iterator, int index);
                }
                @Data.Repository @Data.Provider("jdbc")
                interface BatchRepository extends Parent<String> {
                    @Jdbc.Statement("insert into T values (:statement, :rows, :bindings, :statement, :jdbcClient)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    JdbcClient.BatchResult insert(Iterator<Item> element, String jdbcClient);
                    @Jdbc.Statement("insert into T values (?, ?, ?)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    void arrays(int[] iterator, String SQL_ARRAYS, String... rows);
                    @Jdbc.Statement("insert into T values (?)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    void bytes(byte... bindings);
                    @Jdbc.Statement("insert into T values (?, ?)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    void binary(List<String> value, byte[] rows);
                    @Jdbc.Statement("insert into T values (?, ?, ?, ?, ?, ?)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    void sameComponents(List<Item> first, List<Item> second);
                }
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    @Test
    void compilesAliasedBatchDiamondsAndSourceShapes() {
        TestCompiler.Result result = compile("BatchRepository.java", """
                package example;
                import java.util.*;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                interface Parent<T> {
                    @Jdbc.Statement("insert into T values (:value, :tenant, :value)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    JdbcClient.BatchResult insert(@Data.Param("value") Iterable<T> original,
                                                  @Data.Param("tenant") String constant);
                }
                interface Left extends Parent<String> {
                    @Override
                    @Jdbc.Statement("insert into T values (:value, :tenant, :value)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    JdbcClient.BatchResult insert(Iterable<String> jdbcClient, String rows);
                }
                interface Right {
                    @Jdbc.Statement("insert into T values (:value, :tenant, :value)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    JdbcClient.BatchResult insert(@Data.Param("value") Iterable<String> bindings,
                                                  @Data.Param("tenant") String statement);
                }
                @Data.Repository @Data.Provider("jdbc")
                interface BatchRepository extends Left, Right {
                    @Jdbc.Statement("update T set NAME = :names where ID = :ids or ID = :ids")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    void update(@Data.Param("names") String[] rows, @Data.Param("ids") long... bindings);
                }
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    @Test
    void rejectsBatchAliasCollisionsAndPositionalAliases() {
        TestCompiler.Result duplicate = compile("BatchRepository.java", """
                package example;
                import java.util.*;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                @Data.Repository @Data.Provider("jdbc")
                interface BatchRepository {
                    @Jdbc.Statement("insert into T values (:value)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    void insert(@Data.Param("value") Iterable<String> rows, String value);
                }
                """);
        assertThat(duplicate.success(), is(false));
        assertThat(String.join("\n", duplicate.diagnostics()),
                   containsString("The effective repository parameter name 'value' is duplicated."));

        TestCompiler.Result positional = compile("BatchRepository.java", """
                package example;
                import java.util.*;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                @Data.Repository @Data.Provider("jdbc")
                interface BatchRepository {
                    @Jdbc.Statement("insert into T values (?)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    void insert(@Data.Param("value") Iterable<String> rows);
                }
                """);
        assertThat(positional.success(), is(false));
        assertThat(String.join("\n", positional.diagnostics()),
                   containsString("@Data.Param requires named SQL markers and cannot be used with positional SQL."));
    }

    @Test
    void rejectsAliasesOnRecordSourcesBeforeFlattening() {
        for (String sql : List.of("insert into T values (:name)", "insert into T values (?)")) {
            TestCompiler.Result result = compile("BatchRepository.java", """
                    package example;
                    import java.util.*;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    record Item(String name) { }
                    @Data.Repository @Data.Provider("jdbc")
                    interface BatchRepository {
                        @Jdbc.Statement("%s")
                        @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                        void insert(@Data.Param("records") Iterable<Item> rows);
                    }
                    """.formatted(sql));
            assertThat(sql + " " + result.diagnostics(), result.success(), is(false));
            assertThat(String.join("\n", result.diagnostics()),
                       containsString("A batch record source cannot declare @Data.Param. Use its record component names."));
        }
    }

    @Test
    void compilesScalarAliasesBesideRecordComponentsAndBinarySources() {
        TestCompiler.Result result = compile("BatchRepository.java", """
                package example;
                import java.util.*;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                record Item(String name, long id) { }
                @Data.Repository @Data.Provider("jdbc")
                interface BatchRepository {
                    @Jdbc.Statement("insert into T values (:name, :tenant, :id, :tenant)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    void records(@Data.Param("tenant") String jdbcClient, Iterable<Item> rows);
                    @Jdbc.Statement("insert into T values (:bytes, :constant, :bytes)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    void binary(@Data.Param("bytes") Iterable<byte[]> bindings,
                                @Data.Param("constant") byte[] SQL_BINARY);
                    @Jdbc.Statement("insert into T values (:bytes)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    void bytes(@Data.Param("bytes") byte... row);
                }
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    @Test
    void rejectsInvalidBatchAliasesAndUnusedOrAmbiguousInputs() {
        for (String alias : List.of("", " ", ":value", "value ", "two words", "a.b", "1value", "value/*comment*/")) {
            TestCompiler.Result result = compile("BatchRepository.java", """
                    package example;
                    import java.util.*;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    @Data.Repository @Data.Provider("jdbc")
                    interface BatchRepository {
                        @Jdbc.Statement("insert into T values (:value)")
                        @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                        void insert(@Data.Param("%s") Iterator<String> rows);
                    }
                    """.formatted(alias));
            assertThat(alias + " " + result.diagnostics(), result.success(), is(false));
            assertThat(String.join("\n", result.diagnostics()), containsString("The @Data.Param value"));
        }
        for (String declaration : List.of(
                "void insert(@Data.Param(\"other\") Iterable<String> value)",
                "void insert(@Data.Param(\"value\") Iterable<String> rows, @Data.Param(\"unused\") String constant)",
                "void insert(@Data.Param(\"value\") String constant, Iterable<Item> rows)")) {
            TestCompiler.Result result = compile("BatchRepository.java", """
                    package example;
                    import java.util.*;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    record Item(String value) { }
                    @Data.Repository @Data.Provider("jdbc")
                    interface BatchRepository {
                        @Jdbc.Statement("insert into T values (:value)")
                        @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                        %s;
                    }
                    """.formatted(declaration));
            assertThat(declaration + " " + result.diagnostics(), result.success(), is(false));
        }
    }

    @Test
    void requiresExplicitResolutionOfConflictingInheritedBatchAliases() {
        String source = """
                package example;
                import java.util.*;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                interface Left {
                    @Jdbc.Statement("insert into T values (:value)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    void insert(@Data.Param("value") Iterable<String> rows);
                }
                interface Right {
                    @Jdbc.Statement("insert into T values (:value)")
                    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    void insert(@Data.Param("other") Iterable<String> rows);
                }
                @Data.Repository @Data.Provider("jdbc")
                interface BatchRepository extends Left, Right {
                    %s
                }
                """;
        TestCompiler.Result conflict = compile("BatchRepository.java", source.formatted(""));
        assertThat(conflict.success(), is(false));
        assertThat(String.join("\n", conflict.diagnostics()), containsString("Declare an explicit @Data.Param"));
        TestCompiler.Result resolved = compile("BatchRepository.java", source.formatted("""
                @Override
                @Jdbc.Statement("insert into T values (:chosen)")
                @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                void insert(@Data.Param("chosen") Iterable<String> statement);
                """));
        assertThat(String.join("\n", resolved.diagnostics()), resolved.success(), is(true));
    }

    @Test
    void rejectsUnsupportedShapesAndIncompleteCoverage() {
        for (String declaration : List.of("void run(String constant)", "int run(List<String> rows)",
                "void run(List<List<String>> rows)", "void run(String[][] rows)", "void run(List rows)",
                "void run(java.util.stream.Stream<String> rows)", "void run(List<String> rows, int unused)")) {
            TestCompiler.Result result = compile("BatchRepository.java", """
                    package example;
                    import java.util.*;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    @Data.Repository @Data.Provider("jdbc")
                    interface BatchRepository {
                        @Jdbc.Statement("insert into T values (:rows)")
                        @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                        %s;
                    }
                    """.formatted(declaration));
            assertThat(declaration + " " + String.join("\n", result.diagnostics()), result.success(), is(false));
        }
    }

    /**
     * A separately compiled generic parent supplies the batch declaration resolved at the concrete child use site.
     */
    @Test
    void compilesSeparatelyCompiledGenericContract() {
        TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addClasspath(List.of(JdbcClient.class))
                .addSource("Parent.java", """
                        package example;
                        import java.util.*;
                        import io.helidon.data.jdbc.*;
                        public interface Parent<T> {
                            @Jdbc.Statement("insert into T values (?, ?)")
                            @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                            JdbcClient.BatchResult insert(Iterable<T> rows, byte[] constant);
                        }
                        """).build().compile();
        assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
        TestCompiler.Result child = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class, Data.class, JdbcClient.class, Service.class, Tx.class,
                        Generated.class, TypeName.class, DataGeneratorProvider.class, RepositoryCodegenProvider.class,
                        JdbcRepositoryGeneratorProvider.class))
                .addClasspathEntry(parent.classOutput())
                .addSource("Child.java", """
                        package example;
                        import io.helidon.data.Data;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Child extends Parent<String> { }
                        """).build().compile();
        assertThat(String.join("\n", child.diagnostics()), child.success(), is(true));
    }

    @Test
    void inheritsBatchAliasesFromSeparatelyCompiledGenericContract() {
        TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addClasspath(List.of(Data.class, JdbcClient.class))
                .addSource("Parent.java", """
                        package example;
                        import java.util.*;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.*;
                        public interface Parent<T> {
                            @Jdbc.Statement("insert into T values (:value, :tenant, :value)")
                            @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                            JdbcClient.BatchResult insert(@Data.Param("value") Iterable<T> original,
                                                          @Data.Param("tenant") String constant);
                        }
                        """).build().compile();
        assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
        TestCompiler.Result child = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class, Data.class, JdbcClient.class, Service.class, Tx.class,
                        Generated.class, TypeName.class, DataGeneratorProvider.class, RepositoryCodegenProvider.class,
                        JdbcRepositoryGeneratorProvider.class))
                .addClasspathEntry(parent.classOutput())
                .addSource("Child.java", """
                        package example;
                        import java.util.*;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.*;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Child extends Parent<String> {
                            @Override
                            @Jdbc.Statement("insert into T values (:value, :tenant, :value)")
                            @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                            JdbcClient.BatchResult insert(Iterable<String> jdbcClient, String rows);
                        }
                        """).build().compile();
        assertThat(String.join("\n", child.diagnostics()), child.success(), is(true));
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
