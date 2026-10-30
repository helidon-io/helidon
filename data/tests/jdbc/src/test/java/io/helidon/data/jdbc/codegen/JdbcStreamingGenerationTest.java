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
