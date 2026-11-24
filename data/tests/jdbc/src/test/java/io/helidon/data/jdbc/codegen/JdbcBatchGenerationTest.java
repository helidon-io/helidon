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

import java.io.File;
import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

class JdbcBatchGenerationTest {
    @Test
    void rejectsUnavailableNamesInLiteralBatchContracts() throws IOException {
        for (String declaration : List.of(
                "JdbcClient.BatchResult insert(Iterable<T> arg1, Iterable<T> arg0);",
                "JdbcClient.BatchResult insert(Iterable<T> arg1, T arg0);",
                "JdbcClient.BatchResult insert(Iterator<T> arg1, T arg0);",
                "JdbcClient.BatchResult insert(T[] arg1, T arg0);",
                "JdbcClient.BatchResult insert(T arg1, T... arg0);",
                "JdbcClient.BatchResult insert(@Data.Param(\"arg0\") Iterable<T> values, T arg1);",
                "JdbcClient.BatchResult insert(Iterable<Item> values, T arg1);")) {
            for (String execution : List.of("", "@Jdbc.Execution(Jdbc.ExecutionType.BATCH) @Jdbc.BatchSize(2)")) {
                TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                        .addClasspath(List.of(Data.class, JdbcClient.class)).addOption("-g:none")
                        .addSource("Parent.java", """
                                package example;
                                import java.util.*;
                                import io.helidon.data.Data;
                                import io.helidon.data.jdbc.*;
                                public interface Parent<T> {
                                    record Item(String arg0) { }
                                    @Jdbc.Statement("insert into T values (:arg0, :arg1)")
                                    %s
                                    %s
                                }
                                """.formatted(execution, declaration)).build().compile();
                assertThat(parent.diagnostics().toString(), parent.success(), is(true));
                assertThat(ClassFile.of().parse(Files.readAllBytes(parent.classOutput().resolve("example/Parent.class")))
                                   .methods().stream().filter(method -> method.methodName().equalsString("insert"))
                                   .findFirst().orElseThrow().findAttribute(Attributes.methodParameters()).isEmpty(), is(true));
                TestCompiler.Result child = TestCompiler.builder().currentRelease().printDiagnostics(false)
                        .addProcessor(AptProcessor::new)
                        .addClasspathEntries(List.of(System.getProperty("java.class.path").split(File.pathSeparator)).stream()
                                .map(Path::of).toList())
                        .addClasspathEntry(parent.classOutput())
                        .addSource("Child.java", """
                                package example;
                                import io.helidon.data.Data;
                                @Data.Repository @Data.Provider("jdbc")
                                public interface Child extends Parent<String> { }
                                """).build().compile();
                assertThat(declaration + " " + execution + " " + child.diagnostics(), child.success(), is(false));
                assertThat(child.diagnostics().toString(),
                           containsString("Named SQL requires a known, unique name for every batch input."));
                assertThat(child.diagnostics().toString(), containsString("@Data.Param"));
                assertThat(child.diagnostics().toString(), containsString("-parameters"));
                assertThat(child.diagnostics().toString(), containsString("positional SQL"));
            }
        }
    }

    @Test
    void executesLiteralBatchContractsWithReliableNamesAndPositionalBinaryInputs() throws Exception {
        String namedContracts = """
                @Jdbc.Statement("insert into T values (:arg0, :arg1, :arg0)")
                JdbcClient.BatchResult sources(Iterable<T> arg1, Iterable<T> arg0);
                @Jdbc.Statement("insert into T values (:arg0, :arg1, :arg0)")
                @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                void constant(Iterable<T> arg1, T arg0);
                @Jdbc.Statement("insert into T values (:arg0, :arg1, :arg0)")
                @Jdbc.Execution(Jdbc.ExecutionType.BATCH) @Jdbc.BatchSize(1)
                JdbcClient.BatchResult iterator(Iterator<T> arg1, T arg0);
                @Jdbc.Statement("insert into T values (:arg0, :arg1, :arg0)")
                JdbcClient.BatchResult array(T[] arg1, T arg0);
                @Jdbc.Statement("insert into T values (:arg0, :arg1, :arg0)")
                JdbcClient.BatchResult varargs(T arg1, T... arg0);
                """;
        for (String metadata : List.of("source", "retained", "unavailable")) {
            String contracts = """
                    package example;
                    import java.util.*;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    public interface Parent<T> {
                        record Item(String arg1, String arg0) { }
                        @Jdbc.Statement("insert into T values (:arg0, :arg1, :arg0)")
                        @Jdbc.BatchSize(1)
                        JdbcClient.BatchResult aliases(@Data.Param("arg1") Iterable<T> first,
                                                       @Data.Param("arg0") T second);
                        @Jdbc.Statement("insert into T values (:arg0, :arg1, :arg0)")
                        JdbcClient.BatchResult records(Iterable<Item> rows);
                        @Jdbc.Statement("insert into T values (:arg0, :arg1, :arg0)")
                        JdbcClient.BatchResult aliasedIterator(@Data.Param("arg1") Iterator<T> first,
                                                               @Data.Param("arg0") T second);
                        @Jdbc.Statement("insert into T values (:arg0, :arg1, :arg0)")
                        JdbcClient.BatchResult aliasedArrays(@Data.Param("arg1") T[] first,
                                                             @Data.Param("arg0") T... second);
                        @Jdbc.Statement("insert into T values (?, ?)")
                        JdbcClient.BatchResult positionalScalar(Iterable<T> first, T second);
                        @Jdbc.Statement("insert into T values (?, ?, ?, ?)")
                        JdbcClient.BatchResult positional(Iterable<Item> first, Iterator<Item> second);
                        %s
                    }
                    """.formatted(metadata.equals("unavailable") ? "" : namedContracts);
            TestCompiler.Builder child = TestCompiler.builder().currentRelease().printDiagnostics(false)
                    .addProcessor(AptProcessor::new)
                    .addClasspathEntries(List.of(System.getProperty("java.class.path").split(File.pathSeparator)).stream()
                            .map(Path::of).toList())
                    .addSource("Child.java", """
                            package example;
                            import io.helidon.data.Data;
                            import io.helidon.data.jdbc.Jdbc;
                            import io.helidon.data.jdbc.JdbcClient;
                            @Data.Repository @Data.Provider("jdbc")
                            public interface Child extends Parent<String> {
                                @Override
                                @Jdbc.Statement("insert into T values (:arg0, :arg1, :arg0)")
                                @Jdbc.BatchSize(1)
                                JdbcClient.BatchResult aliases(Iterable<String> jdbcClient, String rows);
                            }
                            """);
            Path parentOutput = null;
            if (metadata.equals("source")) {
                child.addSource("Parent.java", contracts);
            } else {
                TestCompiler.Builder parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                        .addClasspath(List.of(Data.class, JdbcClient.class)).addOption("-g:none")
                        .addSource("Parent.java", contracts);
                if (metadata.equals("retained")) {
                    parent.addOption("-parameters");
                }
                TestCompiler.Result result = parent.build().compile();
                assertThat(result.diagnostics().toString(), result.success(), is(true));
                parentOutput = result.classOutput();
                assertThat(ClassFile.of().parse(Files.readAllBytes(parentOutput.resolve("example/Parent.class")))
                                   .methods().stream().filter(method -> method.methodName().equalsString("aliases"))
                                   .findFirst().orElseThrow().findAttribute(Attributes.methodParameters()).isPresent(),
                           is(metadata.equals("retained")));
                child.addClasspathEntry(parentOutput);
            }
            child.addSource("Runner.java", """
                    package example;
                    import java.sql.Connection;
                    import java.sql.PreparedStatement;
                    import java.util.ArrayList;
                    import java.util.List;
                    import java.util.Map;
                    import java.util.TreeMap;
                    import javax.sql.DataSource;
                    import io.helidon.data.jdbc.JdbcClient;
                    import static org.hamcrest.CoreMatchers.is;
                    import static org.hamcrest.MatcherAssert.assertThat;
                    import static org.mockito.ArgumentMatchers.*;
                    import static org.mockito.Mockito.*;
                    public class Runner {
                        public static void main(String[] args) throws Exception {
                            assertThat(args.length, is(0));
                            DataSource source = mock(DataSource.class);
                            Connection connection = mock(Connection.class);
                            PreparedStatement statement = mock(PreparedStatement.class);
                            when(source.getConnection()).thenReturn(connection);
                            when(connection.getAutoCommit()).thenReturn(true);
                            when(connection.prepareStatement(anyString())).thenReturn(statement);
                            when(statement.executeBatch()).thenReturn(new int[] {1});
                            Map<Integer, String> bindings = new TreeMap<>();
                            List<List<String>> rows = new ArrayList<>();
                            doAnswer(call -> {
                                bindings.put(call.getArgument(0), call.getArgument(1));
                                return null;
                            }).when(statement).setString(anyInt(), anyString());
                            doAnswer(_ -> {
                                rows.add(List.copyOf(bindings.values()));
                                bindings.clear();
                                return null;
                            }).when(statement).addBatch();
                            JdbcClient client = JdbcClient.builder().dataSource(source).build();
                            Child repository = new Child__Jdbc(client);
                            assertThat(repository.aliases(List.of("FIRST"), "SECOND").totalUpdateCount().orElseThrow(), is(1L));
                            assertThat(repository.records(List.of(new Parent.Item("FIRST", "SECOND")))
                                               .totalUpdateCount().orElseThrow(), is(1L));
                            assertThat(repository.aliasedIterator(List.of("FIRST").iterator(), "SECOND")
                                               .totalUpdateCount().orElseThrow(), is(1L));
                            assertThat(repository.aliasedArrays(new String[] {"FIRST"}, "SECOND")
                                               .totalUpdateCount().orElseThrow(), is(1L));
                            assertThat(repository.positionalScalar(List.of("FIRST"), "SECOND")
                                               .totalUpdateCount().orElseThrow(), is(1L));
                            assertThat(repository.positional(List.of(new Parent.Item("FIRST", "SECOND")),
                                    List.of(new Parent.Item("THIRD", "FOURTH")).iterator())
                                               .totalUpdateCount().orElseThrow(), is(1L));
                            List<List<String>> expected = new ArrayList<>(List.of(
                                    List.of("SECOND", "FIRST", "SECOND"), List.of("SECOND", "FIRST", "SECOND"),
                                    List.of("SECOND", "FIRST", "SECOND"), List.of("SECOND", "FIRST", "SECOND"),
                                    List.of("FIRST", "SECOND"),
                                    List.of("FIRST", "SECOND", "THIRD", "FOURTH")));
                            %s
                            assertThat(rows, is(expected));
                            verify(statement, times(expected.size())).executeBatch();
                            verify(statement, times(expected.size())).close();
                            verify(connection, times(expected.size())).close();
                            verify(connection, never()).commit();
                            verify(connection, never()).rollback();
                            verify(connection, never()).setAutoCommit(anyBoolean());
                        }
                    }
                    """.formatted(metadata.equals("unavailable") ? "" : """
                            assertThat(repository.sources(List.of("FIRST"), List.of("SECOND"))
                                               .totalUpdateCount().orElseThrow(), is(1L));
                            repository.constant(List.of("FIRST"), "SECOND");
                            assertThat(repository.iterator(List.of("FIRST").iterator(), "SECOND")
                                               .totalUpdateCount().orElseThrow(), is(1L));
                            assertThat(repository.array(new String[] {"FIRST"}, "SECOND")
                                               .totalUpdateCount().orElseThrow(), is(1L));
                            assertThat(repository.varargs("FIRST", "SECOND").totalUpdateCount().orElseThrow(), is(1L));
                            for (int i = 0; i < 5; i++) {
                                expected.add(List.of("SECOND", "FIRST", "SECOND"));
                            }
                            """));
            TestCompiler.Result result = child.build().compile();
            assertThat(metadata + " " + result.diagnostics(), result.success(), is(true));
            Path output = Files.createTempFile("jdbc-literal-batch-names-", ".log");
            Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path") + File.pathSeparator + result.classOutput()
                            + (parentOutput == null ? "" : File.pathSeparator + parentOutput), "example.Runner")
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            try {
                assertThat("The generated batch runner must finish within its test deadline.",
                           process.waitFor(30, TimeUnit.SECONDS), is(true));
                assertThat(Files.readString(output), process.exitValue(), is(0));
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    assertThat("The generated batch runner must terminate.", process.waitFor(10, TimeUnit.SECONDS), is(true));
                }
                Files.deleteIfExists(output);
            }
        }
    }

    /**
     * An implementation compiled against the earlier indexed interface inherits the new named defaults at runtime.
     */
    @Test
    void linksPreviouslyCompiledBatchBindingsToNamedDefaults() throws Exception {
        TestCompiler.Result legacy = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addSource("JdbcClient.java", """
                        package io.helidon.data.jdbc;
                        import java.sql.JDBCType;
                        public interface JdbcClient {
                            interface BatchBindings {
                                BatchBindings bind(int index, Object value);
                                BatchBindings bindNull(int index, JDBCType type);
                            }
                        }
                        """)
                .addSource("LegacyBindings.java", """
                        package example;
                        import java.sql.JDBCType;
                        import io.helidon.data.jdbc.JdbcClient;
                        public class LegacyBindings implements JdbcClient.BatchBindings {
                            public JdbcClient.BatchBindings bind(int index, Object value) { return this; }
                            public JdbcClient.BatchBindings bindNull(int index, JDBCType type) { return this; }
                        }
                        """).build().compile();
        assertThat(String.join("\n", legacy.diagnostics()), legacy.success(), is(true));
        TestCompiler.Result consumer = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addClasspath(List.of(JdbcClient.class)).addClasspathEntry(legacy.classOutput())
                .addSource("Consumer.java", """
                        package example;
                        import java.sql.JDBCType;
                        import io.helidon.data.jdbc.JdbcClient;
                        public class Consumer {
                            public static void main(String[] args) {
                                if (args.length != 0) {
                                    throw new AssertionError("The linkage test does not accept arguments.");
                                }
                                JdbcClient.BatchBindings bindings = new LegacyBindings();
                                bindings.bind(1, "value").bindNull(2, JDBCType.INTEGER);
                                try {
                                    bindings.bind("value", "value");
                                    throw new AssertionError("The named value default must reject unsupported binding.");
                                } catch (UnsupportedOperationException _) { }
                                try {
                                    bindings.bindNull("value", JDBCType.INTEGER);
                                    throw new AssertionError("The named null default must reject unsupported binding.");
                                } catch (UnsupportedOperationException _) { }
                                try {
                                    bindings.bind("value", null);
                                    throw new AssertionError("The named value default must validate null first.");
                                } catch (NullPointerException _) { }
                                try {
                                    bindings.bindNull("value", null);
                                    throw new AssertionError("The named null default must validate null first.");
                                } catch (NullPointerException _) { }
                            }
                        }
                        """).build().compile();
        assertThat(String.join("\n", consumer.diagnostics()), consumer.success(), is(true));
        Path output = Files.createTempFile("jdbc-batch-linkage-", ".log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path") + File.pathSeparator + consumer.classOutput()
                        + File.pathSeparator + legacy.classOutput(), "example.Consumer")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertThat("The linkage consumer must finish within its test deadline.", process.waitFor(20, TimeUnit.SECONDS), is(true));
            assertThat(Files.readString(output), process.exitValue(), is(0));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertThat("The linkage consumer must terminate.", process.waitFor(10, TimeUnit.SECONDS), is(true));
            }
            Files.deleteIfExists(output);
        }
    }

    @Test
    void infersBatchForExactResultsAcrossSupportedShapes() throws IOException {
        for (String execution : List.of("", "@Jdbc.Execution", "@Jdbc.Execution(Jdbc.ExecutionType.AUTO)",
                                        "@Jdbc.Execution(Jdbc.ExecutionType.BATCH)")) {
            TestCompiler.Result result = compile("BatchRepository.java", """
                    package example;
                    import java.util.*;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    import io.helidon.transaction.Tx;
                    record Item(String name, Long id) { }
                    @Data.Repository @Data.Provider("jdbc") @Jdbc.Client("contacts")
                    interface BatchRepository {
                        @Jdbc.Statement("insert into T values (:value, :constant, :value)")
                        %1$s @Tx.Required
                        JdbcClient.BatchResult scalar(@Data.Param("value") Iterable<String> rows,
                                                      @Data.Param("constant") String statement);
                        @Jdbc.Statement("update T set NAME = :name where ID = :id")
                        %1$s
                        JdbcClient.BatchResult records(Iterator<Item> rows);
                        @Jdbc.Statement("insert into T values (?, ?)")
                        %1$s
                        JdbcClient.BatchResult aligned(String[] rows, long... ids);
                    }
                    """.formatted(execution));
            assertThat(execution + " " + result.diagnostics(), result.success(), is(true));
            String source = Files.readString(result.sourceOutput().resolve("example/BatchRepository__Jdbc.java"));
            assertThat(source, containsString(".executeBatch("));
        }
    }

    @Test
    void rejectsInvalidInferredBatchesWithoutQueryFallback() {
        Map<String, String> declarations = Map.of(
                "JdbcClient.BatchResult run(String rows)", "A BATCH method must declare at least one Iterable, Iterator, array, "
                        + "or varargs input source.",
                "JdbcClient.BatchResult run(Iterable<?> rows)",
                "Batch source elements cannot use wildcards or unresolved type parameters.",
                "JdbcClient.BatchResult run(Iterable<String> rows, int unused)", "unused");
        for (String execution : List.of("", "@Jdbc.Execution(Jdbc.ExecutionType.AUTO)")) {
            for (var declaration : declarations.entrySet()) {
                TestCompiler.Result result = compile("BatchRepository.java", """
                        package example;
                        import java.util.*;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.*;
                        @Data.Repository @Data.Provider("jdbc")
                        interface BatchRepository {
                            @Jdbc.Statement("insert into T values (:rows)")
                            %s
                            %s;
                        }
                        """.formatted(execution, declaration.getKey()));
                assertThat(declaration.getKey() + " " + result.diagnostics(), result.success(), is(false));
                assertThat(String.join("\n", result.diagnostics()), containsString(declaration.getValue()));
            }

        }
    }

    @Test
    void preservesMapperAndGeneratedKeySelectionForBatchResult() throws IOException {
        TestCompiler.Result result = compile("BatchRepository.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                interface Parent<T> {
                    @Jdbc.Statement("select VALUE from T where ID = ?") @Jdbc.RowMapper
                    T inherited(long id);
                }
                @Data.Repository @Data.Provider("jdbc")
                interface BatchRepository extends Parent<JdbcClient.BatchResult> {
                    @Jdbc.Statement("select VALUE from T where ID = ?") @Jdbc.RowMapper
                    JdbcClient.BatchResult query(long id);
                    @Jdbc.Statement("insert into T values (?)") @Jdbc.GeneratedKeys @Jdbc.RowMapper
                    JdbcClient.BatchResult keys(String value);
                    @Jdbc.Statement("select VALUE from T where ID = ?")
                    @Jdbc.Execution(Jdbc.ExecutionType.QUERY) @Jdbc.RowMapper
                    JdbcClient.BatchResult explicit(long id);
                }
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput().resolve("example/BatchRepository__Jdbc.java"));
        assertThat(source.contains(".executeBatch("), is(false));
        assertThat(source, containsString(".generatedKeys()"));
        assertThat(source, containsString(".map("));

        TestCompiler.Result invalid = compile("BatchRepository.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                @Data.Repository @Data.Provider("jdbc")
                interface BatchRepository {
                    @Jdbc.Statement("insert into T values (?)") @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
                    JdbcClient.BatchResult update(String value);
                }
                """);
        assertThat(invalid.success(), is(false));
        assertThat(String.join("\n", invalid.diagnostics()),
                   containsString("UPDATE execution must return void, primitive int, or primitive long."));
        TestCompiler.Result unmappedKeys = compile("BatchRepository.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                @Data.Repository @Data.Provider("jdbc")
                interface BatchRepository {
                    @Jdbc.Statement("insert into T values (?)") @Jdbc.GeneratedKeys
                    JdbcClient.BatchResult keys(String value);
                }
                """);
        assertThat(unmappedKeys.success(), is(false));
        assertThat(String.join("\n", unmappedKeys.diagnostics()), containsString("The JDBC mapped row type"));
    }

    @Test
    void preservesOtherResultAndInputContracts() throws IOException {
        TestCompiler.Result result = compile("BatchRepository.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                record BatchResult(String value) { }
                @Data.Repository @Data.Provider("jdbc")
                interface BatchRepository {
                    @Jdbc.Statement("select VALUE from T")
                    BatchResult query();
                    @Jdbc.Statement("update T set VALUE = ?")
                    void update(String value);
                }
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput().resolve("example/BatchRepository__Jdbc.java"));
        assertThat(source.contains(".executeBatch("), is(false));
        assertThat(source, containsString(".execute()"));
        for (String declaration : List.of(
                "Optional<JdbcClient.BatchResult> run(String rows)",
                "List<JdbcClient.BatchResult> run(String rows)",
                "JdbcClient.BatchResult[] run(String rows)",
                "Derived run(String rows)",
                "<T extends JdbcClient.BatchResult> T run(String rows)",
                "<BatchResult extends JdbcClient.BatchResult> BatchResult run(String rows)",
                "Object run(String rows)",
                "void run(Iterable<String> rows)",
                "String run(String[] rows)")) {
            TestCompiler.Result invalid = compile("BatchRepository.java", """
                    package example;
                    import java.util.*;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    interface Derived extends JdbcClient.BatchResult { }
                    @Data.Repository @Data.Provider("jdbc")
                    interface BatchRepository {
                        @Jdbc.Statement("insert into T values (:rows)")
                        %s;
                    }
                    """.formatted(declaration));
            assertThat(declaration + " " + invalid.diagnostics(), invalid.success(), is(false));
            assertThat(String.join("\n", invalid.diagnostics()).contains("BATCH"), is(false));
        }
    }

    /**
     * Execution comparison uses the concrete child return type even when a library omits Java parameter names.
     */
    @Test
    void normalizesSourceAndBinaryGenericBatchDiamonds() {
        String contracts = """
                package example;
                import java.util.*;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                public class Contracts {
                    public interface Parent<R, T> {
                        @Jdbc.Statement("insert into T values (:value, :constant, :value)")
                        R insert(@Data.Param("value") Iterable<T> rows, @Data.Param("constant") String statement);
                        @Jdbc.Statement("select VALUE from T") @Jdbc.RowMapper
                        R query();
                        @Jdbc.Statement("insert into T values (?)") @Jdbc.GeneratedKeys @Jdbc.RowMapper
                        R key(String value);
                    }
                    public interface Left extends Parent<JdbcClient.BatchResult, String> { }
                    public interface Right {
                        @Jdbc.Statement("insert into T values (:value, :constant, :value)")
                        @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                        JdbcClient.BatchResult insert(@Data.Param("value") Iterable<String> bindings,
                                                      @Data.Param("constant") String jdbcClient);
                    }
                }
                """;
        for (boolean configured : List.of(false, true)) {
            String declarations = configured ? contracts.replace("insert into T values (:value, :constant, :value)",
                    "${app.diamond:insert into T values (:value, :constant, :value)}") : contracts;
            for (boolean binary : List.of(false, true)) {
                TestCompiler.Builder child = TestCompiler.builder().currentRelease().printDiagnostics(false)
                        .addProcessor(AptProcessor::new)
                        .addClasspath(List.of(RuntimeType.class, Config.class, OptionalValue.class, Data.class, JdbcClient.class, Service.class, Tx.class,
                                Generated.class, TypeName.class, DataGeneratorProvider.class, RepositoryCodegenProvider.class,
                                JdbcRepositoryGeneratorProvider.class))
                        .addSource("Child.java", """
                                package example;
                                import io.helidon.data.Data;
                                @Data.Repository @Data.Provider("jdbc")
                                public interface Child extends Contracts.Left, Contracts.Right { }
                                """);
                if (binary) {
                    TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                            .addClasspath(List.of(Data.class, JdbcClient.class))
                            .addSource("Contracts.java", declarations).build().compile();
                    assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
                    child.addClasspathEntry(parent.classOutput());
                } else {
                    child.addSource("Contracts.java", declarations);
                }
                TestCompiler.Result result = child.build().compile();
                assertThat("binary=" + binary + " " + result.diagnostics(), result.success(), is(true));
            }
        }
    }

    @Test
    void retainsConflictsBetweenInferredAndExplicitBatchContracts() {
        for (String right : List.of(
                "@Jdbc.Statement(\"insert into OTHER values (:a, :b)\")",
                "@Jdbc.Statement(\"insert into T values (:a, :b)\") @Tx.New",
                "@Jdbc.Statement(\"insert into T values (:a, :b)\") @Jdbc.RowMapper",
                "@Jdbc.Statement(\"insert into T values (:a, :b)\")")) {
            TestCompiler.Result result = compile("BatchRepository.java", """
                    package example;
                    import java.util.*;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    import io.helidon.transaction.Tx;
                    interface Left {
                        @Jdbc.Statement("insert into T values (:a, :b)")
                        JdbcClient.BatchResult run(Iterable<String> a, Iterable<String> b);
                    }
                    interface Right {
                        %s @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                        JdbcClient.BatchResult run(Iterable<String> b, Iterable<String> a);
                    }
                    @Data.Repository @Data.Provider("jdbc")
                    interface BatchRepository extends Left, Right { }
                    """.formatted(right));
            assertThat(right + " " + result.diagnostics(), result.success(), is(false));
            assertThat(String.join("\n", result.diagnostics()), containsString(right.contains("OTHER")
                    || right.contains("@Tx") || right.contains("@Jdbc.RowMapper")
                    ? "conflicting JDBC or transaction annotations" : "incompatible SQL parameter bindings"));
        }
    }

    @Test
    void compilesConfiguredBatchWithOrWithoutDefaultSql() throws IOException {
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
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
            String source = Files.readString(result.sourceOutput().resolve("example/BatchRepository__Jdbc.java"));
            assertThat(source, containsString(".prepareBatch("));
            assertThat(source, containsString(".executeBatch("));
        }
    }

    @Test
    void compilesConfiguredShapesAliasesAndPositionalDuplicateComponents() throws IOException {
        for (String execution : List.of("", "@Jdbc.Execution", "@Jdbc.Execution(Jdbc.ExecutionType.BATCH)")) {
            TestCompiler.Result result = compile("BatchRepository.java", """
                    package example;
                    import java.util.*;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    record Replacement(String name) { }
                    record Expected(long id, String name) { }
                    record Item(String rows, Long id, byte[] bindings) { }
                    @Data.Repository @Data.Provider("jdbc")
                    interface BatchRepository {
                        @Jdbc.Statement("${application.batch}") %1$s
                        JdbcClient.BatchResult scalar(@Data.Param("value") Iterable<String> jdbcClient,
                                                      @Data.Param("tag") String SQL_SCALAR);
                        @Jdbc.Statement("${application.records}") %1$s
                        JdbcClient.BatchResult records(Iterator<Item> statement);
                        @Jdbc.Statement("${application.arrays}") %1$s
                        JdbcClient.BatchResult arrays(String[] row, long... index);
                        @Jdbc.Statement("${application.binary}") %1$s
                        JdbcClient.BatchResult binary(Iterable<byte[]> rows, byte[] constant);
                        @Jdbc.Statement("${application.bytes}") %1$s
                        JdbcClient.BatchResult bytes(byte... values);
                        @Jdbc.Statement("${application.duplicates:update T set NAME = ? where ID = ? and NAME = ?}") %1$s
                        JdbcClient.BatchResult duplicates(Iterable<Replacement> replacements, Iterator<Expected> expected);
                        @Jdbc.Statement("${application.names}") %1$s
                        JdbcClient.BatchResult names(Iterable<String> arg0);
                    }
                    """.formatted(execution));
            assertThat(result.diagnostics().toString(), result.success(), is(true));
            String source = Files.readString(result.sourceOutput().resolve("example/BatchRepository__Jdbc.java"));
            assertThat(source, containsString(", 3, List.of(), false)"));
            assertThat(source, containsString(".bindNull(\"value\","));
            assertThat(source, containsString(".bind(3,"));
        }
    }

    @Test
    void rejectsConfiguredDefaultsAndDeclarationsWithoutAValidStyle() {
        Map<String, String> cases = Map.ofEntries(
                Map.entry("@Jdbc.Statement(\"${app.sql:insert into T values (?)}\") "
                        + "JdbcClient.BatchResult run(@Data.Param(\"name\") Iterable<String> rows)", "requires named SQL"),
                Map.entry("@Jdbc.Statement(\"${app.sql:update T set NAME = :name where ID = :id and NAME = :name}\") "
                        + "JdbcClient.BatchResult run(Iterable<Replacement> one, Iterable<Expected> two)",
                          "a known, unique name"),
                Map.entry("@Jdbc.Statement(\"${app.sql}\") "
                        + "JdbcClient.BatchResult run(@Data.Param(\"id\") Iterable<Long> ids, Iterable<Expected> rows)",
                          "a known, unique name"),
                Map.entry("@Jdbc.Statement(\"${app.sql:insert into T values (?, ?)}\") "
                        + "JdbcClient.BatchResult run(Iterable<String> rows)", "positional markers"),
                Map.entry("@Jdbc.Statement(\"${app.sql:insert into T values (:missing)}\") "
                        + "JdbcClient.BatchResult run(Iterable<String> rows)", "no matching input"),
                Map.entry("@Jdbc.Statement(\"${app.sql:insert into T values (:rows, ?)}\") "
                        + "JdbcClient.BatchResult run(Iterable<String> rows)", "mix named and positional"),
                Map.entry("@Jdbc.Statement(\"${app.sql}\") "
                        + "JdbcClient.BatchResult run(@Data.Param(\"record\") Iterable<Expected> rows)",
                          "record source cannot declare"),
                Map.entry("@Jdbc.Statement(\"${app.sql}\") "
                        + "JdbcClient.BatchResult run(@Data.Param(\"bad name\") Iterable<String> rows)", "@Data.Param value"));
        for (var declaration : cases.entrySet()) {
            TestCompiler.Result result = compile("BatchRepository.java", """
                    package example;
                    import java.util.*;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    record Replacement(String name) { }
                    record Expected(long id, String name) { }
                    @Data.Repository @Data.Provider("jdbc")
                    interface BatchRepository { %s; }
                    """.formatted(declaration.getKey()));
            assertThat(declaration.getKey() + result.diagnostics(), result.success(), is(false));
            assertThat(result.diagnostics().toString(), containsString(declaration.getValue()));
        }
    }

    /**
     * Binary parents need no Java names for positional slots, while aliases retain a complete named contract.
     */
    @Test
    void resolvesConfiguredGenericContractsFromSourceAndBinaryParents() throws IOException {
        String contracts = """
                package example;
                import java.util.*;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                public interface Parent<T, R> {
                    @Jdbc.Statement("${app.positional:insert into T values (?, ?)}")
                    R positional(Iterable<T> values, T constant);
                    @Jdbc.Statement("${app.named:insert into T values (:value, :tag, :value)}")
                    R named(@Data.Param("value") Iterable<T> original, @Data.Param("tag") T constant);
                    @Jdbc.Statement("${app.arg0}")
                    R spelling(Iterable<T> arg0);
                }
                """;
        for (boolean binary : List.of(false, true)) {
            TestCompiler.Builder child = TestCompiler.builder().currentRelease().printDiagnostics(false)
                    .addProcessor(AptProcessor::new)
                    .addClasspath(List.of(RuntimeType.class, Config.class, OptionalValue.class, Data.class, JdbcClient.class, Service.class, Tx.class,
                            Generated.class, TypeName.class, DataGeneratorProvider.class, RepositoryCodegenProvider.class,
                            JdbcRepositoryGeneratorProvider.class))
                    .addSource("Child.java", """
                            package example;
                            import io.helidon.data.Data;
                            import io.helidon.data.jdbc.JdbcClient;
                            @Data.Repository @Data.Provider("jdbc")
                            interface Child extends Parent<String, JdbcClient.BatchResult> { }
                            """);
            if (binary) {
                TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                        .addClasspath(List.of(Data.class, JdbcClient.class)).addSource("Parent.java", contracts).build().compile();
                assertThat(parent.diagnostics().toString(), parent.success(), is(true));
                child.addClasspathEntry(parent.classOutput());
            } else {
                child.addSource("Parent.java", contracts);
            }
            TestCompiler.Result result = child.build().compile();
            assertThat(result.diagnostics().toString(), result.success(), is(true));
            String source = Files.readString(result.sourceOutput().resolve("example/Child__Jdbc.java"));
            assertThat(source, containsString(binary ? ", 2, List.of(), false)" : "List.of(\"values\", \"constant\")"));
            assertThat(source, containsString("List.of(\"value\", \"tag\"), true)"));
            assertThat(source, containsString(binary ? ", 1, List.of(), false)" : "List.of(\"arg0\"), false)"));
        }
    }

    @Test
    void validatesNamedDefaultsAndAliasesAgainstActualBinaryNameMetadata() {
        for (String scenario : List.of("missing-alias-input", "missing-default-input", "retained-arg0")) {
            String declaration = scenario.equals("missing-alias-input")
                    ? "@Jdbc.Statement(\"${app.sql}\") JdbcClient.BatchResult run(@Data.Param(\"value\") Iterable<T> rows, T constant);"
                    : "@Jdbc.Statement(\"${app.sql:insert into T values (:arg0)}\") JdbcClient.BatchResult run(Iterable<T> arg0);";
            TestCompiler.Builder parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                    .addClasspath(List.of(Data.class, JdbcClient.class))
                    .addSource("Parent.java", """
                            package example;
                            import java.util.*;
                            import io.helidon.data.Data;
                            import io.helidon.data.jdbc.*;
                            public interface Parent<T> { %s }
                            """.formatted(declaration));
            if (scenario.equals("retained-arg0")) {
                parent.addOption("-parameters");
            }
            TestCompiler.Result library = parent.build().compile();
            assertThat(library.diagnostics().toString(), library.success(), is(true));
            TestCompiler.Result child = TestCompiler.builder().currentRelease().printDiagnostics(false)
                    .addProcessor(AptProcessor::new)
                    .addClasspath(List.of(RuntimeType.class, Config.class, OptionalValue.class, Data.class, JdbcClient.class,
                            Service.class, Tx.class, Generated.class, TypeName.class, DataGeneratorProvider.class,
                            RepositoryCodegenProvider.class, JdbcRepositoryGeneratorProvider.class))
                    .addClasspathEntry(library.classOutput()).addSource("Child.java", """
                            package example;
                            import io.helidon.data.Data;
                            @Data.Repository @Data.Provider("jdbc")
                            interface Child extends Parent<String> { }
                            """).build().compile();
            assertThat(scenario + child.diagnostics(), child.success(), is(scenario.equals("retained-arg0")));
            if (!child.success()) {
                assertThat(child.diagnostics().toString(), containsString("a known, unique name"));
            }
        }
    }

    @Test
    void rejectsConflictingConfiguredBatchNamesAcrossInheritedDeclarations() {
        TestCompiler.Result result = compile("BatchRepository.java", """
                package example;
                import java.util.*;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.*;
                interface Left {
                    @Jdbc.Statement("${app.sql}")
                    JdbcClient.BatchResult run(Iterable<String> a, Iterable<String> b);
                }
                interface Right {
                    @Jdbc.Statement("${app.sql}") @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                    JdbcClient.BatchResult run(Iterable<String> b, Iterable<String> a);
                }
                @Data.Repository @Data.Provider("jdbc")
                interface BatchRepository extends Left, Right { }
                """);
        assertThat(result.success(), is(false));
        assertThat(result.diagnostics().toString(), containsString("incompatible parameter names or types"));
    }

    /**
     * An implementation can resolve a competing provider default explicitly under normal Java inheritance rules.
     */
    @Test
    void namedDefaultsAllowExplicitResolutionOfCompetingProviderDefaults() {
        for (boolean override : List.of(false, true)) {
            TestCompiler.Result result = TestCompiler.builder().currentRelease().printDiagnostics(false)
                    .addClasspath(List.of(RuntimeType.class, JdbcClient.class)).addSource("Bindings.java", """
                            package example;
                            import java.sql.JDBCType;
                            import io.helidon.data.jdbc.JdbcClient;
                            interface Names {
                                default JdbcClient.BatchBindings bind(String name, Object value) {
                                    return (JdbcClient.BatchBindings) this;
                                }
                            }
                            class Bindings implements Names, JdbcClient.BatchBindings {
                                public JdbcClient.BatchBindings bind(int index, Object value) { return this; }
                                public JdbcClient.BatchBindings bindNull(int index, JDBCType type) { return this; }
                                %s
                            }
                            """.formatted(override
                                    ? "public JdbcClient.BatchBindings bind(String name, Object value) { return this; }" : ""))
                    .build().compile();
            assertThat(result.diagnostics().toString(), result.success(), is(override));
            if (!override) {
                assertThat(result.diagnostics().toString(), containsString("unrelated defaults"));
            }
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
    void compilesLiteralBatchParametersNamedLikeAdapterTypes() {
        TestCompiler.Result result = compile("BatchRepository.java", batchTypeNameCollisions(false));
        assertThat("Literal batch compilation failed.\n" + String.join("\n", result.diagnostics()),
                   result.success(), is(true));
    }

    @Test
    void compilesConfiguredBatchParametersNamedLikeAdapterTypes() {
        TestCompiler.Result result = compile("BatchRepository.java", batchTypeNameCollisions(true));
        assertThat("Configured batch compilation failed.\n" + String.join("\n", result.diagnostics()),
                   result.success(), is(true));
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
                   containsString("The input name 'value' is used more than once."));

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

    @Test
    void rejectsUnsupportedSourceElementsWithSpecificDiagnostics() {
        Map<String, String> declarations = Map.ofEntries(
                Map.entry("void run(Iterable<?> rows)",
                          "Batch source elements cannot use wildcards or unresolved type parameters."),
                Map.entry("void run(Iterable<? extends String> rows)",
                          "Batch source elements cannot use wildcards or unresolved type parameters."),
                Map.entry("void run(Iterable<? super String> rows)",
                          "Batch source elements cannot use wildcards or unresolved type parameters."),
                Map.entry("<T> void run(Iterable<T> rows)",
                          "Batch source elements cannot use wildcards or unresolved type parameters."),
                Map.entry("void run(Iterable<Optional<String>> rows)",
                          "A batch source element must be a supported scalar or flat record."),
                Map.entry("void run(Iterable<Bean> rows)",
                          "A batch source element must be a supported scalar or flat record."),
                Map.entry("void run(Iterable<Nested> rows)", "Every batch record component must be a supported scalar."),
                Map.entry("void run(Iterable<byte[][]> rows)",
                          "A batch source element that is an array must have type byte[]."),
                Map.entry("void run(byte[][] rows)", "Batch source arrays must have one dimension."),
                Map.entry("void run(char[] rows)", "A batch source element must be a supported scalar or flat record."),
                Map.entry("void run(Iterable rows)", "A batch source must declare one concrete element type."),
                Map.entry("void run(Flow.Publisher<String> rows)",
                          "A batch parameter must be a supported scalar value or an Iterable, Iterator, "
                                  + "or one-dimensional array of supported elements."));
        for (Map.Entry<String, String> declaration : declarations.entrySet()) {
            TestCompiler.Result result = compile("BatchRepository.java", """
                    package example;
                    import java.util.*;
                    import java.util.concurrent.Flow;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    class Bean { }
                    record Nested(Bean value) { }
                    @Data.Repository @Data.Provider("jdbc")
                    interface BatchRepository {
                        @Jdbc.Statement("insert into T values (:rows)")
                        @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                        %s;
                    }
                    """.formatted(declaration.getKey()));
            assertThat(declaration.getKey() + " " + result.diagnostics(), result.success(), is(false));
            assertThat(String.join("\n", result.diagnostics()), containsString(declaration.getValue()));
        }
    }

    @Test
    void rejectsGeneratedKeysAndRowMappingForBatches() {
        for (String annotation : List.of("@Jdbc.GeneratedKeys", "@Jdbc.RowMapper")) {
            TestCompiler.Result result = compile("BatchRepository.java", """
                    package example;
                    import java.util.List;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    @Data.Repository @Data.Provider("jdbc")
                    interface BatchRepository {
                        @Jdbc.Statement("insert into T values (:rows)")
                        @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                        %s
                        void run(List<String> rows);
                    }
                    """.formatted(annotation));
            assertThat(annotation + " " + result.diagnostics(), result.success(), is(false));
            assertThat(String.join("\n", result.diagnostics()),
                       containsString("BATCH execution cannot request generated keys or row mapping."));
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
                .addClasspath(List.of(RuntimeType.class, Config.class, OptionalValue.class, Data.class, JdbcClient.class, Service.class, Tx.class,
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
                .addClasspath(List.of(RuntimeType.class, Config.class, OptionalValue.class, Data.class, JdbcClient.class, Service.class, Tx.class,
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

    @Test
    void compilesSizedLiteralAndConfiguredBatchesAcrossExecutionChoices() throws IOException {
        for (String execution : List.of("", "@Jdbc.Execution(Jdbc.ExecutionType.AUTO)",
                                        "@Jdbc.Execution(Jdbc.ExecutionType.BATCH)")) {
            for (String sql : List.of("insert into T values (:batchSize, :size, :offset, :counts)",
                    "${app.sized:insert into T values (:batchSize, :size, :offset, :counts)}")) {
                TestCompiler.Result result = compile("Sized.java", """
                        package example;
                        import java.util.*;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.*;
                        @Data.Repository @Data.Provider("jdbc")
                        interface Sized {
                            @Jdbc.Statement("%s") @Jdbc.BatchSize(3) %s
                            JdbcClient.BatchResult insert(Iterable<String> batchSize, String size,
                                                          long[] offset, int... counts);
                        }
                        """.formatted(sql, execution));
                assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
                assertThat(Files.readString(result.sourceOutput().resolve("example/Sized__Jdbc.java")),
                           containsString(", 3, ("));
            }
        }
    }

    @Test
    void rejectsSizingOnOtherwiseValidNonBatchAndNonGeneratedMethods() {
        for (String declaration : List.of(
                "@Jdbc.Statement(\"select VALUE from T where ID = ?\") @Jdbc.Execution(Jdbc.ExecutionType.QUERY) String run(long id);",
                "@Jdbc.Statement(\"update T set VALUE = ?\") @Jdbc.Execution(Jdbc.ExecutionType.UPDATE) int run(String value);",
                "@Jdbc.Statement(\"update T set VALUE = ?\") void run(String value);",
                "@Jdbc.Statement(\"select VALUE from T\") String run();",
                "@Jdbc.Statement(\"${app.query:select VALUE from T}\") String run();",
                "@Jdbc.Statement(\"select VALUE from T\") default String run() { return \"value\"; }",
                "@Jdbc.Statement(\"select VALUE from T\") static String run() { return \"value\"; }",
                "default void run() { }", "static void run() { }", "void run();")) {
            TestCompiler.Result result = compile("Sized.java", """
                    package example;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    @Data.Repository @Data.Provider("jdbc")
                    interface Sized {
                        @Jdbc.BatchSize(2) %s
                    }
                    """.formatted(declaration));
            assertThat(declaration + " " + result.diagnostics(), result.success(), is(false));
            assertThat(String.join("\n", result.diagnostics()),
                       containsString("@Jdbc.BatchSize can be used only with BATCH execution."));
        }
    }

    @Test
    void rejectsNonpositiveSizesAndInvalidBatchShapes() {
        for (String declaration : List.of("@Jdbc.BatchSize(1) interface Sized { }",
                "interface Sized { void run(@Jdbc.BatchSize(1) String value); }",
                "interface Sized { @Jdbc.BatchSize void run(); }")) {
            TestCompiler.Result result = compile("Sized.java", "package example; import io.helidon.data.jdbc.Jdbc; " + declaration);
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        }
        for (String sql : List.of("insert into T values (?)", "${app.sized:insert into T values (?)}")) {
            for (int size : new int[] {0, -1, Integer.MIN_VALUE}) {
                TestCompiler.Result result = compile("Sized.java", """
                        package example;
                        import java.util.*;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.*;
                        @Data.Repository @Data.Provider("jdbc")
                        interface Sized {
                            @Jdbc.Statement("%s") @Jdbc.BatchSize(%s)
                            JdbcClient.BatchResult run(Iterable<String> rows);
                        }
                        """.formatted(sql, size));
                assertThat(result.success(), is(false));
                assertThat(String.join("\n", result.diagnostics()), containsString("The batch size must be greater than zero."));
            }
        }
        for (String declaration : List.of("int run(Iterable<String> rows)", "void run(String value)",
                "void run(Iterable<Iterable<String>> rows)", "void run(String[][] rows)")) {
            TestCompiler.Result result = compile("Sized.java", """
                    package example;
                    import java.util.*;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.*;
                    @Data.Repository @Data.Provider("jdbc")
                    interface Sized {
                        @Jdbc.Statement("insert into T values (?)") @Jdbc.BatchSize(2)
                        @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
                        %s;
                    }
                    """.formatted(declaration));
            assertThat(declaration + " " + result.diagnostics(), result.success(), is(false));
        }
    }

    @Test
    void preservesSizedGenericContractsAndRequiresExplicitDiamondResolution() throws IOException {
        for (boolean binary : List.of(false, true)) {
            for (String rightSize : List.of("@Jdbc.BatchSize(3)", "@Jdbc.BatchSize(2)", "")) {
                String contracts = """
                        package example;
                        import java.util.*;
                        import io.helidon.data.jdbc.*;
                        public class Contracts {
                            public interface Left<T> {
                                @Jdbc.Statement("insert into T values (?)") @Jdbc.BatchSize(3)
                                JdbcClient.BatchResult insert(Iterable<T> rows);
                            }
                            public interface Right {
                                @Jdbc.Statement("insert into T values (?)") %s
                                JdbcClient.BatchResult insert(Iterable<String> rows);
                            }
                        }
                        """.formatted(rightSize);
                for (boolean override : List.of(false, true)) {
                    TestCompiler.Builder builder = TestCompiler.builder().currentRelease().printDiagnostics(false)
                            .addProcessor(AptProcessor::new)
                            .addClasspath(List.of(RuntimeType.class, Config.class, OptionalValue.class, Data.class, JdbcClient.class,
                                    Service.class, Tx.class, Generated.class, TypeName.class, DataGeneratorProvider.class,
                                    RepositoryCodegenProvider.class, JdbcRepositoryGeneratorProvider.class));
                    if (binary) {
                        TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                                .addClasspath(List.of(JdbcClient.class)).addSource("Contracts.java", contracts).build().compile();
                        assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
                        builder.addClasspathEntry(parent.classOutput());
                    } else {
                        builder.addSource("Contracts.java", contracts);
                    }
                    TestCompiler.Result child = builder.addSource("Sized.java", """
                            package example;
                            import java.util.*;
                            import io.helidon.data.Data;
                            import io.helidon.data.jdbc.*;
                            @Data.Repository @Data.Provider("jdbc")
                            interface Sized extends Contracts.Left<String>, Contracts.Right {
                                %s
                            }
                            """.formatted(override ? """
                                    @Override @Jdbc.Statement("insert into T values (?)") @Jdbc.BatchSize(1)
                                    JdbcClient.BatchResult insert(Iterable<String> rows);
                                    """ : "")).build().compile();
                    boolean valid = override || rightSize.equals("@Jdbc.BatchSize(3)");
                    assertThat(String.join("\n", child.diagnostics()), child.success(), is(valid));
                    if (valid) {
                        assertThat(Files.readString(child.sourceOutput().resolve("example/Sized__Jdbc.java")),
                                   containsString(", " + (override ? 1 : 3) + ", ("));
                    } else {
                        assertThat(String.join("\n", child.diagnostics()), containsString("conflicting JDBC or transaction annotations"));
                    }
                }
            }
        }
    }

    @Test
    void rejectsInvalidSizesRetainedBySeparatelyCompiledGenericParents() {
        for (int size : new int[] {0, -1}) {
            TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                    .addClasspath(List.of(JdbcClient.class))
                    .addSource("Parent.java", """
                            package example;
                            import io.helidon.data.jdbc.*;
                            public interface Parent<T> {
                                @Jdbc.Statement("insert into T values (?)") @Jdbc.BatchSize(%s)
                                JdbcClient.BatchResult insert(Iterable<T> rows);
                            }
                            """.formatted(size)).build().compile();
            assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
            TestCompiler.Result child = TestCompiler.builder().currentRelease().printDiagnostics(false)
                    .addProcessor(AptProcessor::new)
                    .addClasspath(List.of(RuntimeType.class, Config.class, OptionalValue.class, Data.class, JdbcClient.class,
                            Service.class, Tx.class, Generated.class, TypeName.class, DataGeneratorProvider.class,
                            RepositoryCodegenProvider.class, JdbcRepositoryGeneratorProvider.class))
                    .addClasspathEntry(parent.classOutput()).addSource("Child.java", """
                            package example;
                            import io.helidon.data.Data;
                            @Data.Repository @Data.Provider("jdbc")
                            interface Child extends Parent<String> { }
                            """).build().compile();
            assertThat(child.success(), is(false));
            assertThat(String.join("\n", child.diagnostics()), containsString("The batch size must be greater than zero."));
        }
    }

    @Test
    void linksSizedDefaultToPreviouslyCompiledProvidersAndConsumers() throws Exception {
        TestCompiler.Result legacy = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class, Config.class, OptionalValue.class, Data.class, JdbcClient.class,
                        Service.class, Tx.class, Generated.class, TypeName.class, DataGeneratorProvider.class,
                        RepositoryCodegenProvider.class, JdbcRepositoryGeneratorProvider.class))
                .addSource("JdbcClient.java", """
                        package io.helidon.data.jdbc;
                        import java.sql.JDBCType;
                        public interface JdbcClient {
                            interface BatchResult { }
                            interface BatchBindings {
                                BatchBindings bind(int index, Object value);
                                BatchBindings bindNull(int index, JDBCType type);
                            }
                            interface BatchBinder<T> { void bind(BatchBindings bindings, T value); }
                            interface Statement {
                                <T> BatchResult executeBatch(Iterable<T> items, BatchBinder<? super T> binder);
                            }
                        }
                        """)
                .addSource("LegacyRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.*;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface LegacyRepository {
                            @Jdbc.Statement("insert into T values (?)")
                            JdbcClient.BatchResult insert(Iterable<String> rows);
                        }
                        """)
                .addSource("LegacyStatement.java", """
                        package example;
                        import java.util.List;
                        import io.helidon.data.jdbc.JdbcClient;
                        public class LegacyStatement implements JdbcClient.Statement {
                            public <T> JdbcClient.BatchResult executeBatch(Iterable<T> items,
                                    JdbcClient.BatchBinder<? super T> binder) {
                                throw new IllegalStateException("The legacy terminal was invoked.");
                            }
                            public static void oldConsumer() {
                                JdbcClient.Statement statement = new LegacyStatement();
                                statement.executeBatch(List.of(), (_, _) -> { });
                            }
                        }
                        """).build().compile();
        assertThat(String.join("\n", legacy.diagnostics()), legacy.success(), is(true));
        TestCompiler.Result consumer = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addClasspathEntries(List.of(System.getProperty("java.class.path").split(File.pathSeparator)).stream()
                        .map(Path::of).toList()).addClasspathEntry(legacy.classOutput())
                .addSource("Consumer.java", """
                        package example;
                        import java.util.List;
                        import javax.sql.DataSource;
                        import io.helidon.data.jdbc.JdbcClient;
                        import org.mockito.Mockito;
                        public class Consumer {
                            public static void main(String[] args) {
                                DataSource source = Mockito.mock(DataSource.class);
                                JdbcClient client = JdbcClient.builder().dataSource(source).build();
                                if (new LegacyRepository__Jdbc(client).insert(List.of()).totalUpdateCount().orElseThrow() != 0) {
                                    throw new AssertionError("The older generated consumer must retain empty batch behavior.");
                                }
                                Mockito.verifyZeroInteractions(source);
                                JdbcClient.Statement statement = new LegacyStatement();
                                try {
                                    statement.executeBatch(null, 0, null);
                                    throw new AssertionError("References must be validated first.");
                                } catch (NullPointerException _) { }
                                try {
                                    statement.executeBatch(List.of(), 0, (_, _) -> { });
                                    throw new AssertionError("The size must be validated.");
                                } catch (IllegalArgumentException _) { }
                                for (int size : new int[] {1, Integer.MAX_VALUE}) {
                                    try {
                                        statement.executeBatch(List.of(), size, (_, _) -> { });
                                        throw new AssertionError("The default must reject unsupported sizing.");
                                    } catch (UnsupportedOperationException _) { }
                                }
                                try {
                                    LegacyStatement.oldConsumer();
                                    throw new AssertionError("The old consumer must invoke the legacy terminal.");
                                } catch (IllegalStateException expected) {
                                    if (!"The legacy terminal was invoked.".equals(expected.getMessage())) {
                                        throw expected;
                                    }
                                }
                            }
                        }
                        """).build().compile();
        assertThat(String.join("\n", consumer.diagnostics()), consumer.success(), is(true));
        Path output = Files.createTempFile("jdbc-sized-batch-linkage-", ".log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path") + File.pathSeparator + consumer.classOutput()
                        + File.pathSeparator + legacy.classOutput(), "example.Consumer")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertThat("The linkage consumer must finish within its test deadline.", process.waitFor(20, TimeUnit.SECONDS), is(true));
            assertThat(Files.readString(output), process.exitValue(), is(0));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertThat("The linkage consumer must terminate.", process.waitFor(10, TimeUnit.SECONDS), is(true));
            }
            Files.deleteIfExists(output);
        }
    }

    @Test
    void compilesSizedAndExistingPreparationApisOnTheModulePath() {
        TestCompiler.Result result = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addModulepathEntries(List.of(System.getProperty("java.class.path").split(File.pathSeparator)).stream()
                        .map(Path::of).filter(Files::isRegularFile).toList())
                .addModulepath(JdbcClient.class)
                .addSource("module-info.java", "module example.sized { requires io.helidon.data.jdbc; }")
                .addSource("Sized.java", """
                        package example;
                        import java.util.List;
                        import io.helidon.data.jdbc.*;
                        public interface Sized {
                            @Jdbc.BatchSize(2)
                            JdbcClient.BatchResult declaration(Iterable<String> rows);
                            static void use(JdbcClient client) {
                                client.create("insert into T values (?)").executeBatch(List.of("value"), 2,
                                        (bindings, value) -> bindings.bind(1, value));
                                client.prepare("insert into T values (:value)");
                                client.prepare("insert into T values (?)", List.of("value"));
                                GeneratedJdbcData.prepareBatch(client, "insert into T values (?)", 1,
                                        List.of("value"), false);
                            }
                        }
                        """).build().compile();
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    private static String batchTypeNameCollisions(boolean configured) {
        String single = "INSERT INTO T VALUES (?)";
        String pair = "INSERT INTO T VALUES (?, ?)";
        String record = "INSERT INTO T VALUES (:JDBCType)";
        String aliases = "INSERT INTO T VALUES (:payload, :tenant, :suffix)";
        if (configured) {
            single = "${app.single:" + single + "}";
            pair = "${app.pair:" + pair + "}";
            record = "${app.record:" + record + "}";
            aliases = "${app.aliases:" + aliases + "}";
        }
        return """
                package example;
                import java.util.Iterator;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                import io.helidon.data.jdbc.JdbcClient;
                record Payload(String JDBCType) { }
                interface Parent<T> {
                    @Jdbc.Statement("%4$s")
                    JdbcClient.BatchResult inherited(@Data.Param("payload") Iterable<T> Objects,
                                                      @Data.Param("tenant") T data__Objects,
                                                      @Data.Param("suffix") T data__data__Objects);
                }
                @Data.Repository @Data.Provider("jdbc")
                interface BatchRepository extends Parent<String> {
                    @Jdbc.Statement("%1$s")
                    JdbcClient.BatchResult iterable(Iterable<String> Objects);
                    @Jdbc.Statement("%1$s")
                    JdbcClient.BatchResult iterator(Iterator<String> JDBCType);
                    @Jdbc.Statement("%1$s")
                    @Jdbc.BatchSize(2)
                    JdbcClient.BatchResult array(String[] IntStream);
                    @Jdbc.Statement("%1$s")
                    JdbcClient.BatchResult varargs(String... IntStream);
                    @Jdbc.Statement("%1$s")
                    JdbcClient.BatchResult bridge(Iterable<String> GeneratedJdbcData);
                    @Jdbc.Statement("%2$s")
                    JdbcClient.BatchResult constant(String[] values, String Objects);
                    @Jdbc.Statement("%3$s")
                    JdbcClient.BatchResult records(Iterable<Payload> Objects);
                }
                """.formatted(single, pair, record, aliases);
    }

    private static TestCompiler.Result compile(String fileName, String source) {
        return TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class,
                                      Config.class,
                                      OptionalValue.class,
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
