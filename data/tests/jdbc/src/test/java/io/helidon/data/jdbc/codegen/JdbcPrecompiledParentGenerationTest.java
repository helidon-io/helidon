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
                .addClasspath(List.of(RuntimeType.class,
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
                                return new Key(row.required(1, Long.class));
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
        assertThat(source, containsString("new Projection<String>(row.required(\"value\", String.class))"));
    }
}
