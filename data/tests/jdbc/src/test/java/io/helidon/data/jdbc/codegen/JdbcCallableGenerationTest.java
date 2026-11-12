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
import java.io.Writer;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import io.helidon.builder.api.RuntimeType;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.common.mapper.OptionalValue;
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

class JdbcCallableGenerationTest {
    @Test
    void rejectsMissingExplicitRegistrationType() {
        TestCompiler.Result result = compile("""
                @Jdbc.Statement("{call routine(:value)}")
                @Jdbc.OutParameter("value")
                void execute();
                """);
        assertThat(result.success(), is(false));
        assertThat(String.join("\n", result.diagnostics()), containsString("type"));
    }

    @Test
    void rejectsRepeatedOutputAndInvalidScale() {
        TestCompiler.Result repeated = compile("""
                @Jdbc.Statement("{call routine(:value, :value)}")
                @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
                int execute();
                """);
        assertThat(repeated.success(), is(false));
        assertThat(String.join("\n", repeated.diagnostics()), containsString("exactly once"));
        TestCompiler.Result scale = compile("""
                @Jdbc.Statement("{call routine(:value)}")
                @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER, scale = 2)
                int execute();
                """);
        assertThat(scale.success(), is(false));
        assertThat(String.join("\n", scale.diagnostics()), containsString("scale"));
    }

    @Test
    void rejectsQueryOutputsAndSnapshotCursors() {
        TestCompiler.Result query = compile("""
                @Jdbc.Statement("{call routine(:value)}")
                @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
                @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
                int execute();
                """);
        assertThat(query.success(), is(false));
        TestCompiler.Result cursor = compile("""
                @Jdbc.Statement("{call routine(:value)}")
                @Jdbc.OutParameter(value = "value", type = JDBCType.REF_CURSOR)
                io.helidon.data.jdbc.JdbcClient.OutParameters execute();
                """);
        assertThat(cursor.success(), is(false));
        assertThat(String.join("\n", cursor.diagnostics()), containsString("snapshot"));
    }

    @Test
    void rejectsNestedCursorRowsAndScopedResultsEvenWithAMapper() {
        for (String type : List.of("java.util.List<java.util.List<String>>",
                                   "java.util.Optional<java.util.Optional<String>>",
                                   "io.helidon.data.jdbc.JdbcClient.OutParameters", "java.sql.Blob")) {
            TestCompiler.Result result = compile("""
                    @Jdbc.Statement("{call routine(:rows)}")
                    @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
                    @Jdbc.RowMapper
                    """ + type + " execute();");
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        }
    }

    /**
     * CLASS retained outputs and generic input types survive separate parent compilation.
     */
    @Test
    void generatesFromSeparatelyCompiledParent() throws IOException {
        TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addClasspath(List.of(RuntimeType.class, JdbcClient.class, Data.class))
                .addSource("Parent.java", """
                        package example;
                        import io.helidon.data.jdbc.Jdbc;
                        import io.helidon.data.Data;
                        import java.sql.JDBCType;
                        public interface Parent<T> {
                            @Jdbc.Statement("{? = call routine(?)}")
                            @Jdbc.OutParameter(type = JDBCType.INTEGER)
                            <E extends Exception> T execute(T value) throws E;

                            @Jdbc.Statement("${application.total:{? = call routine(:input)}}")
                            @Jdbc.OutParameter
                            T configured(@Data.Param("input") T value);

                            @Jdbc.Statement("{call routine(:value, :rows)}")
                            @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
                            @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
                            Outputs<T> aggregate();
                        }
                        """)
                .addSource("Outputs.java", """
                        package example;
                        import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
                        import java.sql.JDBCType;
                        import io.helidon.data.jdbc.Jdbc;
                        public record Outputs<T>(int value, List<T> rows) {
                        }
                        """)
                .addSource("IntegerMapper.java", """
                        package example;
                        import io.helidon.data.jdbc.JdbcClient;
                        public final class IntegerMapper implements JdbcClient.RowMapper<Integer> {
                            public Integer map(JdbcClient.Row row) { return row.get(1, Integer.class); }
                        }
                        """).build().compile();
        assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
        TestCompiler.Result child = compiler().addClasspathEntry(parent.classOutput())
                .addSource("Child.java", """
                        package example;
                        import io.helidon.data.Data;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Child extends Parent<Integer> {
                        }
                        """).build().compile();
        assertThat(String.join("\n", child.diagnostics()), child.success(), is(true));
        String generated = Files.readString(child.sourceOutput().resolve("example/Child__Jdbc.java"));
        assertThat(generated, containsString("GeneratedJdbcData.prepareCall("));
        assertThat(generated, containsString("true, true)"));
    }

    /**
     * Equivalent repeated declarations retain their meaning across annotation containers, defaults, and hierarchy depths.
     */
    @Test
    void normalizesEquivalentOutputsFromUnrelatedParents() {
        TestCompiler.Result result = compiler().addSource("Child.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                import io.helidon.data.jdbc.JdbcClient;
                import java.sql.JDBCType;
                interface Root {
                    @Jdbc.Statement("{call routine(:value, :other)}")
                    @Jdbc.OutParameterDeclarations({@Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER),
                                                    @Jdbc.OutParameter(value = "other", type = JDBCType.VARCHAR)})
                    JdbcClient.OutParameters execute();
                }
                interface Middle extends Root { }
                interface Other {
                    @Jdbc.Statement("{call routine(:value, :other)}")
                    @Jdbc.OutParameter(value = "other", type = JDBCType.VARCHAR, scale = -1, typeName = "")
                    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
                    JdbcClient.OutParameters execute();
                }
                @Data.Repository @Data.Provider("jdbc")
                public interface Child extends Middle, Other { }
                """).build().compile();
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    /**
     * Result records and mapper services emitted together by another processor remain available to JDBC generation.
     */
    @Test
    void compilesResultAndMapperGeneratedInTheSameRound() {
        TestCompiler.Result result = compiler().processors(List.of(new GeneratedTypesProcessor(), new AptProcessor()))
                .addSource("Trigger.java", "package example; public class Trigger { }").build().compile();
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        assertThat(Files.exists(result.classOutput().resolve("example/GeneratedRepository__Jdbc.class")), is(true));
    }

    /**
     * Component mapper names remain distinct from ordinary repository mapper names in either declaration order.
     */
    @Test
    void reservesComponentMapperNamesAcrossRepositoryMethods() {
        String aggregate = """
                @Jdbc.Statement("{call routine(:rows, :count)}")
                @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
                @Jdbc.OutParameter(value = "count", type = JDBCType.INTEGER)
                Outputs aggregate();
                """;
        String query = """
                @Jdbc.Statement("SELECT value FROM TEST")
                Other aggregate_OUTPUT_0();
                """;
        String records = """
                record Outputs(List<Projection> rows, int count) { }
                record Projection(int value) { }
                record Other(String value) { }
                """;
        for (String methods : List.of(aggregate + query, query + aggregate)) {
            TestCompiler.Result result = compile(methods + records);
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        }
    }

    /**
     * Parameter renaming reserves both component mapper names and mapper names reused from ordinary queries.
     */
    @Test
    void reservesFinalComponentMapperNamesAgainstInputParameters() {
        for (String mapper : List.of("MAPPER_AGGREGATE_OUTPUT_0", "MAPPER_PROJECTION")) {
            String query = mapper.equals("MAPPER_PROJECTION")
                    ? "@Jdbc.Statement(\"SELECT value FROM TEST\") Projection projection();\n" : "";
            TestCompiler.Result result = compile(query + """
                    @Jdbc.Statement("{call routine(:%s, :rows, :count)}")
                    @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
                    @Jdbc.OutParameter(value = "count", type = JDBCType.INTEGER)
                    Outputs aggregate(String %s);
                    record Outputs(List<Projection> rows, int count) { }
                    record Projection(int value) { }
                    """.formatted(mapper, mapper));
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        }
    }

    /**
     * Callable callbacks and materialized cursors preserve their result shapes and control binding.
     */
    @Test
    void compilesCallableCallbacksAndMaterializedCursorsTogether() {
        TestCompiler.Result result = compile("""
                @Jdbc.Statement("{? = call routine(:value)}")
                @Jdbc.OutParameter(type = JDBCType.REF_CURSOR)
                void cursor(Integer value, Consumer<JdbcClient.CallResult> SQL_CURSOR);
                @Jdbc.Statement("{call routine(:value)}")
                @Jdbc.Execution(Jdbc.ExecutionType.CALL)
                void direct(Integer value, Consumer<JdbcClient.CallResult> result);
                @Jdbc.Statement("{? = call routine(:value)}")
                @Jdbc.OutParameter(type = JDBCType.REF_CURSOR)
                List<String> materialized(Integer value);
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    /**
     * Callable declarations retain their scoped result control and cannot return a live stream.
     */
    @Test
    void rejectsQueryStreamControlsAndLiveStreamReturnsForCalls() {
        for (String declaration : List.of(
                "void execute(Consumer<Stream<String>> action)",
                "Stream<String> execute()")) {
            TestCompiler.Result result = compile("""
                    @Jdbc.Statement("{? = call routine()}")
                    @Jdbc.OutParameter(type = JDBCType.REF_CURSOR)
                    """ + declaration + ";");
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        }
    }

    /**
     * Ordinary queries reject JDBC streaming controls while callable callbacks remain available.
     */
    @Test
    void rejectsStreamingControlsForQueries() {
        TestCompiler.Result result = compile("""
                @Jdbc.Statement("SELECT :value")
                void query(String value, Consumer<Stream<String>> action);
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
    }

    @Test
    void bindsEffectiveCallableInputsAndInout() {
        TestCompiler.Result result = compile("""
                @Jdbc.Statement("{call routine(:id, :id, :counter)}")
                @Jdbc.OutParameter("counter")
                int execute(@Data.Param("id") Integer renamed, @Data.Param("counter") int seed);
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    @Test
    void rejectsInvalidCallableAliasesAndCallbackAliases() {
        for (String declaration : List.of(
                """
                @Jdbc.Statement("{call routine(:value)}") @Jdbc.Execution(Jdbc.ExecutionType.CALL)
                void execute(@Data.Param("different") Integer value);
                """,
                """
                @Jdbc.Statement("{call routine(?)}") @Jdbc.Execution(Jdbc.ExecutionType.CALL)
                void execute(@Data.Param("value") Integer value);
                """,
                """
                @Jdbc.Statement("{call routine(:value)}") @Jdbc.Execution(Jdbc.ExecutionType.CALL)
                void execute(@Data.Param("value") Integer renamed, Integer value);
                """,
                """
                @Jdbc.Statement("{call routine()}") @Jdbc.Execution(Jdbc.ExecutionType.CALL)
                void execute(@Data.Param("action") Consumer<JdbcClient.CallResult> action);
                """,
                """
                @Jdbc.Statement("{call routine(:value)}") @Jdbc.OutParameter("value")
                int execute(@Data.Param(":value") int seed);
                """)) {
            TestCompiler.Result result = compile(declaration);
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        }
    }

    @Test
    void compilesNamedConfiguredCallsWithInferredOutputs() {
        for (String sql : List.of("${application.call}",
                                  "${application.call:{call routine(:value, :input, :rows)}}",
                                  "{call routine(:value, :input, :rows)}")) {
            TestCompiler.Result result = compile("""
                    @Jdbc.Statement("%s")
                    @Jdbc.OutParameter("rows") @Jdbc.OutParameter("value")
                    Outputs execute(Integer input, Integer value);
                    record Outputs(List<Integer> rows, int value) { }
                    """.formatted(sql));
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        }
    }

    @Test
    void compilesConfiguredReturnAndCallbackForms() {
        TestCompiler.Result result = compile("""
                @Jdbc.Statement("${application.command:{call routine()}}")
                @Jdbc.Execution(Jdbc.ExecutionType.CALL)
                void command();

                @Jdbc.Statement("${application.total:{? = call routine(:id)}}")
                @Jdbc.OutParameter
                Integer total(@Data.Param("id") Integer renamed);

                @Jdbc.Statement("${application.mixed:{call routine(:output, :input)}}")
                @Jdbc.OutParameter(value = "output", type = JDBCType.INTEGER)
                void mixed(Integer input, Consumer<JdbcClient.CallResult> action);

                @Jdbc.Statement("${application.cursor:{? = call routine(?)}}")
                @Jdbc.OutParameter
                List<Projection> cursor(int id);
                record Projection(int value) { }
                """);
        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    @Test
    void rejectsPositionalOutputsAndIncompatibleConfiguredDefaults() {
        for (String sql : List.of("{call routine(?, ?)}", "${sql:{call routine(?, ?)}}")) {
            TestCompiler.Result result = compile("""
                    @Jdbc.Statement("%s")
                    @Jdbc.OutParameter("output") int execute(int input);
                    """.formatted(sql));
            assertThat(result.diagnostics().toString(), result.success(), is(false));
            assertThat(result.diagnostics().toString(), containsString("Use named SQL markers"));
        }
        for (String declaration : List.of(
                """
                @Jdbc.Statement("{call routine(?)}")
                @Jdbc.OutParameter("output") int execute();
                """,
                """
                @Jdbc.Statement("${sql:{? = call routine(:input)}}")
                @Jdbc.OutParameter("output") int execute(int input);
                """,
                """
                @Jdbc.Statement("{? = call routine(:input, ?)}")
                @Jdbc.OutParameter int execute(int input, int other);
                """,
                """
                @Jdbc.Statement("{call routine(:input, ?)}")
                @Jdbc.Execution(Jdbc.ExecutionType.CALL) void execute(int input, int other);
                """,
                """
                @Jdbc.Statement("{? = call routine(?)}")
                @Jdbc.Execution(Jdbc.ExecutionType.CALL) int execute(int input);
                """,
                """
                @Jdbc.Statement("${sql:{? = call routine(?)}}")
                @Jdbc.Execution(Jdbc.ExecutionType.CALL) int execute(int input);
                """)) {
            TestCompiler.Result result = compile(declaration);
            assertThat(declaration + "\n" + result.diagnostics(), result.success(), is(false));
        }
    }

    @Test
    void rejectsAdditionalOutputsOnLeadingReturns() {
        for (String sql : List.of("{? = call routine(:value)}", "${sql}", "${sql:{? = call routine(?)}}")) {
            for (String shape : List.of("void execute(Consumer<JdbcClient.CallResult> action)",
                                        "JdbcClient.OutParameters execute()")) {
                TestCompiler.Result result = compile("""
                        @Jdbc.Statement("%s")
                        @Jdbc.OutParameterDeclarations({
                            @Jdbc.OutParameter(type = JDBCType.INTEGER),
                            @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER)
                        })
                        %s;
                        """.formatted(sql, shape));
                assertThat(result.diagnostics().toString(), result.success(), is(false));
                assertThat(result.diagnostics().toString(), containsString("only output declaration"));
            }
        }
    }

    @Test
    void compilesPositionalInputsAndSpecialLeadingReturns() {
        TestCompiler.Result result = compile("""
                @Jdbc.Statement("{call routine(?, ?)}")
                @Jdbc.Execution(Jdbc.ExecutionType.CALL)
                void command(Integer first, String second, Consumer<JdbcClient.CallResult> action);
                @Jdbc.Statement("${sql:{call routine(?, ?)}}")
                @Jdbc.Execution(Jdbc.ExecutionType.CALL)
                void configured(Integer first, String second);
                @Jdbc.Statement("{?=call routine(?)}")
                @Jdbc.OutParameter Integer positional(Integer input);
                @Jdbc.Statement("{ ? = CALL routine(:input)}")
                @Jdbc.OutParameter Integer named(Integer input);
                @Jdbc.Statement("{? = call routine()}")
                @Jdbc.OutParameter Integer noArguments();
                """);
        assertThat(result.diagnostics().toString(), result.success(), is(true));
    }

    @Test
    void compilesBothEscapeFormsAcrossLiteralAndConfiguredDeclarations() {
        for (String source : List.of("literal", "default", "configured")) {
            String direct = "{call function(?)}";
            String leading = "{? = call procedure(?)}";
            if (source.equals("default")) {
                direct = "${application.direct:" + direct + "}";
                leading = "${application.leading:" + leading + "}";
            } else if (source.equals("configured")) {
                direct = "${application.direct}";
                leading = "${application.leading}";
            }
            TestCompiler.Result result = compile("""
                    @Jdbc.Statement("%s")
                    @Jdbc.Execution(Jdbc.ExecutionType.CALL)
                    int direct(int input);
                    @Jdbc.Statement("%s")
                    @Jdbc.OutParameter
                    Integer leading(int input);
                    @Jdbc.Statement("%s")
                    @Jdbc.OutParameter(type = JDBCType.INTEGER)
                    void discard(int input);
                    """.formatted(direct, leading, leading));
            assertThat(result.diagnostics().toString(), result.success(), is(true));
        }
    }

    @Test
    void normalizesInferredAndExplicitInheritedCallableOutputs() {
        for (boolean configured : List.of(false, true)) {
            String sql = configured ? "${application.total:{? = call routine(?)}}" : "{? = call routine(?)}";
            TestCompiler.Result result = compiler().addSource("Repository.java", """
                    package example;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                import java.sql.JDBCType;
                    interface First {
                        @Jdbc.Statement("%s") @Jdbc.OutParameter Integer total(int input);
                    }
                    interface Second {
                        @Jdbc.Statement("%s")
                        @Jdbc.OutParameter(type = JDBCType.INTEGER) Integer total(int input);
                    }
                    @Data.Repository @Data.Provider("jdbc")
                    public interface Repository extends First, Second { }
                    """.formatted(sql, sql)).build().compile();
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        }
    }

    @Test
    void retainsAliasesAndCallableOutputsFromBinaryParents() {
        for (boolean configured : List.of(false, true)) {
            String sql = configured ? "${application.value:{call routine(:input, :value)}}" : "{call routine(:input, :value)}";
            TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                    .addClasspath(List.of(RuntimeType.class, Data.class, JdbcClient.class))
                    .addSource("Parent.java", """
                            package example;
                            import io.helidon.data.Data;
                            import io.helidon.data.jdbc.Jdbc;
                            public interface Parent<T> {
                                @Jdbc.Statement("%s")
                                @Jdbc.OutParameter("value")
                                T execute(@Data.Param("input") T renamed, @Data.Param("value") T seed);
                            }
                            """.formatted(sql)).build().compile();
            assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
            TestCompiler.Result child = compiler().addClasspathEntry(parent.classOutput()).addSource("Repository.java", """
                    package example;
                    import io.helidon.data.Data;
                    @Data.Repository @Data.Provider("jdbc")
                    public interface Repository extends Parent<Integer> { }
                    """).build().compile();
            assertThat(String.join("\n", child.diagnostics()), child.success(), is(true));
        }
    }

    @Test
    void rejectsConflictingInheritedCallableOutputTypes() {
        TestCompiler.Result result = compiler().addSource("Repository.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                import java.sql.JDBCType;
                interface First {
                    @Jdbc.Statement("${application.value}")
                    @Jdbc.OutParameter(value = "value", type = JDBCType.INTEGER) int execute(int input);
                }
                interface Second {
                    @Jdbc.Statement("${application.value}")
                    @Jdbc.OutParameter(value = "value", type = JDBCType.BIGINT) int execute(int input);
                }
                @Data.Repository @Data.Provider("jdbc")
                public interface Repository extends First, Second { }
                """).build().compile();
        assertThat(result.diagnostics().toString(), result.success(), is(false));
        assertThat(result.diagnostics().toString(), containsString("conflicting"));
    }

    @Test
    void preservesInheritedDefaultBodiesWithOutputMetadata() {
        TestCompiler.Result result = compiler().addSource("Repository.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                interface Root {
                    @Jdbc.OutParameter default int value() { return 42; }
                }
                interface Parent extends Root {
                    @Override @Jdbc.OutParameter default int value() { return 43; }
                }
                @Data.Repository @Data.Provider("jdbc")
                public interface Repository extends Parent { }
                """).build().compile();
        assertThat(result.diagnostics().toString(), result.success(), is(true));
    }

    @Test
    void preservesJavaNameDifferencesForInheritedLiteralPositionalCalls() {
        TestCompiler.Result result = compiler().addSource("Repository.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                interface First {
                    @Jdbc.Statement("{? = call routine(?)}") @Jdbc.OutParameter int value(int input);
                }
                interface Second {
                    @Jdbc.Statement("{? = call routine(?)}") @Jdbc.OutParameter int value(int renamed);
                }
                @Data.Repository @Data.Provider("jdbc")
                public interface Repository extends First, Second { }
                """).build().compile();
        assertThat(result.diagnostics().toString(), result.success(), is(true));
    }

    @Test
    void rejectsInheritedLiteralCallsWithDifferentInputPositions() {
        TestCompiler.Result result = compiler().addSource("Repository.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                interface First {
                    @Jdbc.Statement("{call routine(:first, :second, :value)}")
                    @Jdbc.OutParameter("value") int value(int first, int second);
                }
                interface Second {
                    @Jdbc.Statement("{call routine(:first, :second, :value)}")
                    @Jdbc.OutParameter("value") int value(int second, int first);
                }
                @Data.Repository @Data.Provider("jdbc")
                public interface Repository extends First, Second { }
                """).build().compile();
        assertThat(result.diagnostics().toString(), result.success(), is(false));
        assertThat(result.diagnostics().toString(), containsString("incompatible named SQL"));
    }

    private static TestCompiler.Result compile(String declarations) {
        return compiler().addSource("Repository.java", """
                package example;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                import java.sql.JDBCType;
                import java.util.List;
                import java.util.function.Consumer;
                import java.util.stream.Stream;
                import io.helidon.data.jdbc.JdbcClient;
                @Data.Repository @Data.Provider("jdbc")
                public interface Repository {
                """ + declarations + "\n}").build().compile();
    }

    private static TestCompiler.Builder compiler() {
        return TestCompiler.builder().currentRelease().printDiagnostics(false).addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class, Data.class, JdbcClient.class, Service.class, Tx.class,
                                      Config.class, OptionalValue.class,
                                      Generated.class, TypeName.class, DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class, JdbcRepositoryGeneratorProvider.class));
    }
    private static final class GeneratedTypesProcessor extends AbstractProcessor {
        private boolean written;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.RELEASE_27;
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (written || round.processingOver()) {
                return false;
            }
            written = true;
            write("example.GeneratedRow", """
                    package example;
                    public record GeneratedRow(int value) {
                    }
                    """);
            write("example.GeneratedMapper", """
                    package example;
                    import io.helidon.data.jdbc.JdbcClient;
                    import io.helidon.service.registry.Service;
                    @Service.Singleton
                    public class GeneratedMapper implements JdbcClient.RowMapper<GeneratedRow> {
                        public GeneratedRow map(JdbcClient.Row row) { return new GeneratedRow(row.get(1, Integer.class)); }
                    }
                    """);
            write("example.GeneratedRepository", """
                    package example;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                    import java.sql.JDBCType;
                    import java.util.List;
                    @Data.Repository @Data.Provider("jdbc")
                    public interface GeneratedRepository {
                        @Jdbc.Statement("{call routine(:rows)}")
                        @Jdbc.OutParameter(value = "rows", type = JDBCType.REF_CURSOR)
                        @Jdbc.RowMapper(GeneratedMapper.class)
                        List<GeneratedRow> execute();
                        @Jdbc.Statement("{call routine(:rows)}")
                        @Jdbc.OutParameter("rows")
                        @Jdbc.RowMapper(GeneratedMapper.class)
                        List<GeneratedRow> inferred();
                    }
                    """);
            return false;
        }

        private void write(String name, String source) {
            try (Writer writer = processingEnv.getFiler().createSourceFile(name).openWriter()) {
                writer.write(source);
            } catch (IOException failure) {
                throw new AssertionError(failure);
            }
        }
    }

}
