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
