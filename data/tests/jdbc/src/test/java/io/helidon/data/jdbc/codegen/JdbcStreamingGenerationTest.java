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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
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

class JdbcStreamingGenerationTest {
    /**
     * Compiles scalar, record, mapper and collision cases through the existing processor.
     */
    @Test
    void compilesStreamingControlsAndExistingBindings() {
        for (String mapping : List.of("", "@Jdbc.RowMapper", "@Jdbc.RowMapper(ValueMapper.class)")) {
            TestCompiler.Result result = compile("StreamingRepository.java", """
                    package example;
                    import java.util.function.Consumer;
                    import java.util.stream.Stream;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                    import io.helidon.data.jdbc.JdbcClient;
                    final class ValueMapper implements JdbcClient.RowMapper<String> {
                        public String map(JdbcClient.Row row) { return row.get(1, String.class); }
                    }
                    @Data.Repository @Data.Provider("jdbc")
                    interface StreamingRepository {
                        @Jdbc.Statement("SELECT :value WHERE :value = :value")
                        %s
                        void find(String value, Consumer<Stream<String>> jdbcClient);
                        @Jdbc.Statement("SELECT ? AS value")
                        void records(Consumer<Stream<Projection>> SQL_RECORDS, String statement);
                    }
                    record Projection(String value) { }
                    """.formatted(mapping));
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        }
    }

    /**
     * Streaming repositories compile with record elements and explicit mappers generated in the same round.
     */
    @Test
    void compilesGeneratedStreamingElementsAndMappers() {
        TestCompiler.Result result = compiler()
                .addProcessor(new AbstractProcessor() {
                    private boolean generated;

                    @Override
                    public Set<String> getSupportedAnnotationTypes() {
                        return Set.of("*");
                    }

                    @Override
                    public SourceVersion getSupportedSourceVersion() {
                        return SourceVersion.latestSupported();
                    }

                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        if (generated || roundEnv.processingOver()) {
                            return false;
                        }
                        generated = true;
                        Map<String, String> sources = Map.of(
                                "GeneratedRow", """
                                        package example;
                                        public record GeneratedRow<T>(T value) { }
                                        """,
                                "GeneratedMapper", """
                                        package example;
                                        import io.helidon.data.jdbc.JdbcClient;
                                        public final class GeneratedMapper implements JdbcClient.RowMapper<GeneratedRow<String>> {
                                            public GeneratedRow<String> map(JdbcClient.Row row) {
                                                return new GeneratedRow<>(row.get("value", String.class));
                                            }
                                        }
                                        """,
                                "Repository", """
                                        package example;
                                        import java.util.function.Consumer;
                                        import java.util.stream.Stream;
                                        import io.helidon.data.Data;
                                        import io.helidon.data.jdbc.Jdbc;
                                        @Data.Repository @Data.Provider("jdbc")
                                        public interface Repository {
                                            @Jdbc.Statement("SELECT :bound AS value")
                                            void records(@Data.Param("bound") String value,
                                                         Consumer<Stream<GeneratedRow<String>>> action);
                                            @Jdbc.Statement("SELECT :bound AS value")
                                            @Jdbc.RowMapper(GeneratedMapper.class)
                                            void mapped(Consumer<Stream<GeneratedRow<String>>> action,
                                                        @Data.Param("bound") String value);
                                        }
                                        """);
                        for (var source : sources.entrySet()) {
                            try (var writer = processingEnv.getFiler()
                                    .createSourceFile("example." + source.getKey()).openWriter()) {
                                writer.write(source.getValue());
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                        return false;
                    }
                })
                .addSource("Trigger.java", """
                        package example;
                        @Deprecated final class Trigger { }
                        """).build().compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        assertThat(Files.isRegularFile(result.classOutput().resolve("example/Repository__Jdbc.class")), is(true));
        assertThat(Files.isRegularFile(result.classOutput().resolve("example/GeneratedRow.class")), is(true));
        assertThat(Files.isRegularFile(result.classOutput().resolve("example/GeneratedMapper.class")), is(true));
    }

    /**
     * Supported method type parameters and checked exceptions survive direct and inherited streaming declarations.
     */
    @Test
    void preservesStreamingMethodGenericsAndCheckedExceptions() {
        for (boolean inherited : new boolean[] {false, true}) {
            TestCompiler.Result result = compile("Repository.java", """
                    package example;
                    import java.io.IOException;
                    import java.io.UncheckedIOException;
                    import java.util.function.Consumer;
                    import java.util.stream.Stream;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                    interface Parent {
                        @Jdbc.Statement("SELECT :bound")
                        <E extends Exception> void find(@Data.Param("bound") String value,
                                                       Consumer<Stream<String>> action) throws E;
                    }
                    @Data.Repository @Data.Provider("jdbc")
                    public interface Repository %s {
                        %s
                    }
                    final class Caller {
                        static <E extends Exception> void consume(Repository__Jdbc repository,
                                                                 Consumer<Stream<String>> action) throws E {
                            repository.<E>find("value", action);
                        }
                        static void checked(Repository__Jdbc repository, Consumer<Stream<String>> action) {
                            try {
                                repository.<IOException>find("value", action);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    }
                    """.formatted(inherited ? "extends Parent" : "", inherited ? "" : """
                            @Jdbc.Statement("SELECT :bound")
                            <E extends Exception> void find(@Data.Param("bound") String value,
                                                           Consumer<Stream<String>> action) throws E;
                            """));
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        }
    }

    /**
     * Unsupported controls and returned streams fail during generation.
     */
    @Test
    void rejectsUnsupportedStreamingShapes() {
        for (String declaration : List.of(
                "void find(Consumer action)",
                "void find(Consumer<Stream> action)",
                "void find(Consumer<Stream<?>> action)",
                "<T> void find(Consumer<Stream<T>> action)",
                "void find(Consumer<String> action)",
                "void find(Consumer<Stream<String>> first, Consumer<Stream<String>> second)",
                "String find(Consumer<Stream<String>> action)",
                "Stream<String> find()",
                "@Jdbc.Execution(Jdbc.ExecutionType.UPDATE) void find(Consumer<Stream<String>> action)",
                "@Jdbc.GeneratedKeys void find(Consumer<Stream<String>> action)")) {
            TestCompiler.Result result = compile("InvalidRepository.java", """
                    package example;
                    import java.util.function.Consumer;
                    import java.util.stream.Stream;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                    @Data.Repository @Data.Provider("jdbc")
                    interface InvalidRepository {
                        @Jdbc.Statement("SELECT VALUE")
                        %s;
                    }
                    """.formatted(declaration));
            assertThat(declaration + String.join("\n", result.diagnostics()), result.success(), is(false));
        }
    }


    /**
     * Retained JDBC declarations resolve stream elements from a separately compiled generic parent.
     */
    @Test
    void compilesInheritedGenericStreamingControl() {
        TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addClasspath(List.of(RuntimeType.class, JdbcClient.class))
                .addSource("Parent.java", """
                        package example;
                        import java.util.function.Consumer;
                        import java.util.stream.Stream;
                        import io.helidon.data.jdbc.Jdbc;
                        public interface Parent<T> {
                            @Jdbc.Statement("SELECT ?")
                            void values(Consumer<Stream<T>> action, String value);
                        }
                        """).build().compile();
        assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
        TestCompiler.Result child = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(RuntimeType.class, Data.class, JdbcClient.class, Service.class,
                                      Tx.class, Generated.class, TypeName.class, DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class, JdbcRepositoryGeneratorProvider.class))
                .addClasspathEntry(parent.classOutput())
                .addSource("Child.java", """
                        package example;
                        import io.helidon.data.Data;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Child extends Parent<String> { }
                        """).build().compile();
        assertThat(String.join("\n", child.diagnostics()), child.success(), is(true));
    }

    /**
     * Unrelated generic parents can declare the same streaming contract with either callback position.
     */
    @Test
    void compilesStreamingDiamondsWithNamedAndPositionalInputs() {
        for (boolean first : new boolean[] {false, true}) {
            for (String sql : List.of("SELECT ?", "SELECT :value")) {
                String parameters = first ? "Consumer<Stream<T>> action, T value"
                        : "T value, Consumer<Stream<T>> action";
                TestCompiler.Result result = compile("Repository.java", """
                        package example;
                        import java.util.function.Consumer;
                        import java.util.stream.Stream;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        interface Left<T> {
                            @Jdbc.Statement("%s") void find(%s);
                        }
                        interface Right<T> {
                            @Jdbc.Statement("%s") void find(%s);
                        }
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Repository extends Left<String>, Right<String> { }
                        """.formatted(sql, parameters, sql, parameters.replace("action", "rows")));
                assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
            }
        }
    }

    /**
     * Generic streaming diamonds retain their inputs and controls across separate parent compilation.
     */
    @Test
    void compilesSeparatelyCompiledStreamingDiamonds() {
        for (boolean first : new boolean[] {false, true}) {
            for (boolean named : new boolean[] {false, true}) {
                String parameters = first ? "Consumer<Stream<T>> action, T value"
                        : "T value, Consumer<Stream<T>> action";
                String sql = named ? "SELECT :value" : "SELECT ?";
                TestCompiler.Builder parents = TestCompiler.builder().currentRelease().printDiagnostics(false)
                        .addClasspath(List.of(RuntimeType.class, JdbcClient.class));
                if (named) {
                    parents.addOption("-parameters");
                }
                for (String name : List.of("Left", "Right")) {
                    parents.addSource(name + ".java", """
                            package example;
                            import java.util.function.Consumer;
                            import java.util.stream.Stream;
                            import io.helidon.data.jdbc.Jdbc;
                            public interface %s<T> {
                                @Jdbc.Statement("%s") void find(%s);
                            }
                            """.formatted(name, sql, parameters));
                }
                TestCompiler.Result parent = parents.build().compile();
                assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
                TestCompiler.Result result = compiler().addClasspathEntry(parent.classOutput())
                        .addSource("Repository.java", """
                                package example;
                                import io.helidon.data.Data;
                                @Data.Repository @Data.Provider("jdbc")
                                public interface Repository extends Left<String>, Right<String> { }
                                """).build().compile();
                assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
            }
        }
    }

    /**
     * Inheritance comparison uses original argument positions and still rejects incompatible named bindings.
     */
    @Test
    void rejectsConflictingStreamingDiamondBindings() {
        TestCompiler.Result result = compile("Repository.java", """
                package example;
                import java.util.function.Consumer;
                import java.util.stream.Stream;
                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                interface Left {
                    @Jdbc.Statement("SELECT :first, :second")
                    void find(String first, Consumer<Stream<String>> action, String second);
                }
                interface Right {
                    @Jdbc.Statement("SELECT :first, :second")
                    void find(String second, Consumer<Stream<String>> rows, String first);
                }
                @Data.Repository @Data.Provider("jdbc")
                public interface Repository extends Left, Right { }
                """);
        assertThat(result.success(), is(false));
        assertThat(String.join("\n", result.diagnostics()),
                   containsString("incompatible named SQL parameter bindings"));
    }

    /**
     * Filtering controls during inheritance does not admit invalid streaming declarations.
     */
    @Test
    void rejectsInvalidInheritedStreamingControls() {
        for (String declaration : List.of("void find(Consumer action)", "void find(Consumer<Stream> action)",
                "void find(Consumer<Stream<?>> action)", "void find(Consumer<String> action)",
                "String find(Consumer<Stream<String>> action)",
                "void find(Consumer<Stream<String>> first, Consumer<Stream<String>> second)")) {
            TestCompiler.Result result = compile("Repository.java", """
                    package example;
                    import java.util.function.Consumer;
                    import java.util.stream.Stream;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                    interface Left { @Jdbc.Statement("SELECT VALUE") %s; }
                    interface Right { @Jdbc.Statement("SELECT VALUE") %s; }
                    @Data.Repository @Data.Provider("jdbc")
                    public interface Repository extends Left, Right { }
                    """.formatted(declaration, declaration));
            assertThat(declaration + String.join("\n", result.diagnostics()), result.success(), is(false));
        }
    }

    /**
     * Aliases survive generic parent compilation without Java parameter names and an override that omits them.
     */
    @Test
    void compilesAliasedStreamingDiamondsWithoutParameterNames() {
        for (boolean override : new boolean[] {false, true}) {
            for (boolean first : new boolean[] {false, true}) {
                TestCompiler.Builder parents = TestCompiler.builder().currentRelease().printDiagnostics(false)
                        .addClasspath(List.of(RuntimeType.class, JdbcClient.class, Data.class));
                for (String name : List.of("Left", "Right")) {
                    String parameter = name.equals("Left") ? "SQL_FIND" : "jdbcClient";
                    String input = "@Data.Param(\"bound\") T " + parameter;
                    String control = "Consumer<Stream<T>> rows";
                    parents.addSource(name + ".java", """
                            package example;
                            import java.util.function.Consumer;
                            import java.util.stream.Stream;
                            import io.helidon.data.Data;
                            import io.helidon.data.jdbc.Jdbc;
                            public interface %s<T> {
                                @Jdbc.Statement("SELECT :bound WHERE :bound = :bound") void find(%s);
                            }
                            """.formatted(name, first ? control + ", " + input : input + ", " + control));
                }
                TestCompiler.Result parent = parents.build().compile();
                assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
                String declaration = override ? """
                        @Override @Jdbc.Statement("SELECT :bound WHERE :bound = :bound")
                        void find(%s);
                        """.formatted(first ? "Consumer<Stream<String>> action, String renamed"
                                : "String renamed, Consumer<Stream<String>> action") : "";
                TestCompiler.Result child = compiler().addClasspathEntry(parent.classOutput())
                        .addSource("Repository.java", """
                                package example;
                                import java.util.function.Consumer;
                                import java.util.stream.Stream;
                                import io.helidon.data.Data;
                                import io.helidon.data.jdbc.Jdbc;
                                @Data.Repository @Data.Provider("jdbc")
                                public interface Repository extends Left<String>, Right<String> { %s }
                                """.formatted(declaration)).build().compile();
                assertThat(String.join("\n", child.diagnostics()), child.success(), is(true));
            }
        }
    }

    /**
     * A callback alias is rejected even when inherited or omitted by a child override.
     */
    @Test
    void rejectsAliasesOnStreamingControls() {
        for (String inheritance : List.of("", "extends Parent", "extends Left, Right")) {
            TestCompiler.Result result = compile("Repository.java", """
                    package example;
                    import java.util.function.Consumer;
                    import java.util.stream.Stream;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                    interface Parent {
                        @Jdbc.Statement("SELECT :bound")
                        void find(@Data.Param("bound") String value, @Data.Param("callback") Consumer<Stream<String>> action);
                    }
                    interface Left extends Parent { }
                    interface Right extends Parent { }
                    @Data.Repository @Data.Provider("jdbc")
                    public interface Repository %s {
                        @Jdbc.Statement("SELECT :bound")
                        void find(@Data.Param("bound") String value, %s Consumer<Stream<String>> action);
                    }
                    """.formatted(inheritance, inheritance.isEmpty() ? "@Data.Param(\"callback\")" : ""));
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
            assertThat(String.join("\n", result.diagnostics()), containsString("@Data.Param"));
            assertThat(String.join("\n", result.diagnostics()), containsString("streaming control"));
        }
    }

    /**
     * A callback alias retained in a compiled parent remains invalid after an unannotated override.
     */
    @Test
    void rejectsCompiledAliasesOnStreamingControls() {
        TestCompiler.Result parent = TestCompiler.builder().currentRelease().printDiagnostics(false)
                .addClasspath(List.of(RuntimeType.class, JdbcClient.class, Data.class))
                .addSource("Parent.java", """
                        package example;
                        import java.util.function.Consumer;
                        import java.util.stream.Stream;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        public interface Parent<T> {
                            @Jdbc.Statement("SELECT :bound")
                            void find(@Data.Param("bound") T value,
                                      @Data.Param("callback") Consumer<Stream<T>> action);
                        }
                        """).build().compile();
        assertThat(String.join("\n", parent.diagnostics()), parent.success(), is(true));
        TestCompiler.Result child = compiler().addClasspathEntry(parent.classOutput())
                .addSource("Repository.java", """
                        package example;
                        import java.util.function.Consumer;
                        import java.util.stream.Stream;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        public interface Repository extends Parent<String> {
                            @Override @Jdbc.Statement("SELECT :bound")
                            void find(String renamedValue, Consumer<Stream<String>> renamed);
                        }
                        """).build().compile();
        assertThat(String.join("\n", child.diagnostics()), child.success(), is(false));
        assertThat(String.join("\n", child.diagnostics()), containsString("@Data.Param"));
        assertThat(String.join("\n", child.diagnostics()), containsString("streaming control"));
    }

    /**
     * Aliased streaming inputs retain ordinary positional and duplicate binding restrictions.
     */
    @Test
    void rejectsInvalidAliasedStreamingInputs() {
        for (String declaration : List.of(
                "@Jdbc.Statement(\"SELECT ?\") void find(@Data.Param(\"bound\") String value, Consumer<Stream<String>> action)",
                "@Jdbc.Statement(\"SELECT :bound\") void find(@Data.Param(\"bound\") String first, "
                        + "Consumer<Stream<String>> action, @Data.Param(\"bound\") String second)")) {
            TestCompiler.Result result = compile("Repository.java", """
                    package example;
                    import java.util.function.Consumer;
                    import java.util.stream.Stream;
                    import io.helidon.data.Data;
                    import io.helidon.data.jdbc.Jdbc;
                    @Data.Repository @Data.Provider("jdbc")
                    public interface Repository { %s; }
                    """.formatted(declaration));
            assertThat(String.join("\n", result.diagnostics()), result.success(), is(false));
        }
    }

    private static TestCompiler.Result compile(String fileName, String source) {
        return compiler().addSource(fileName, source).build().compile();
    }

    private static TestCompiler.Builder compiler() {
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
                                      JdbcRepositoryGeneratorProvider.class));
    }
}
