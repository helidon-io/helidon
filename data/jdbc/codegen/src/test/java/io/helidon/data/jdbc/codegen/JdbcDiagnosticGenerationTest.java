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

import java.nio.file.Files;

import io.helidon.codegen.testing.TestCompiler;

import org.junit.jupiter.api.Test;

import static io.helidon.data.jdbc.codegen.JdbcCodegenTestSupport.assertCompilationFailure;
import static io.helidon.data.jdbc.codegen.JdbcCodegenTestSupport.compiler;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcDiagnosticGenerationTest {
    @Test
    void requiresExplicitJdbcProviderSelection() {
        TestCompiler.Result result = compiler()
                .addSource("UnqualifiedRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository
                        interface UnqualifiedRepository {
                            @Jdbc.Statement("select NAME from POKEMON")
                            String find();
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("example/UnqualifiedRepository__Jdbc.java")), is(false));
    }

    @Test
    void rejectsAmbiguousOrUnsupportedMethodContracts() {
        assertCompilationFailure("BlankStatementRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface BlankStatementRepository {
                            @Jdbc.Statement("  ")
                            String find();
                        }
                        """,
                                 "@Jdbc.Statement SQL must not be blank");
        assertCompilationFailure("AmbiguousRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface AmbiguousRepository {
                            @Jdbc.Statement("select count(*) from POKEMON")
                            long count();
                        }
                        """,
                                 "Cannot infer JDBC execution from primitive long return type");
        assertCompilationFailure("PositionalMismatchRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface PositionalMismatchRepository {
                            @Jdbc.Statement("select NAME from POKEMON where ID = ? and STATE = ?")
                            String find(long id);
                        }
                        """,
                                 "Positional SQL marker count 2 does not match repository parameter count 1");
        assertCompilationFailure("MixedRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface MixedRepository {
                            @Jdbc.Statement("select NAME from POKEMON where ID = :id and STATE = ?")
                            String find(long id, String state);
                        }
                        """,
                                 "cannot mix named and positional markers");
        assertCompilationFailure("UnsupportedParameterRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface UnsupportedParameterRepository {
                            @Jdbc.Statement("select NAME from POKEMON where VALUE = :value")
                            String find(Object value);
                        }
                        """,
                                 "Unsupported declarative SQL parameter type: java.lang.Object");
        assertCompilationFailure("UnsupportedResultRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        final class Pokemon { }
                        @Data.Repository @Data.Provider("jdbc")
                        interface UnsupportedResultRepository {
                            @Jdbc.Statement("select ID from POKEMON")
                            Pokemon find();
                        }
                        """,
                                 "Non-scalar JDBC result must be a record or declare @Jdbc.RowMapper");
        assertCompilationFailure("MismatchedMapperRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        import io.helidon.data.jdbc.JdbcClient;
                        final class LongMapper implements JdbcClient.RowMapper<Long> {
                            public Long map(JdbcClient.Row row) {
                                return row.required(1, Long.class);
                            }
                        }
                        @Data.Repository @Data.Provider("jdbc")
                        interface MismatchedMapperRepository {
                            @Jdbc.Statement("select NAME from POKEMON")
                            @Jdbc.RowMapper(LongMapper.class)
                            String find();
                        }
                        """,
                                 "Mapper must implement JdbcClient.RowMapper<java.lang.String>");
        assertCompilationFailure("UpdateMapperRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface UpdateMapperRepository {
                            @Jdbc.Statement("delete from POKEMON")
                            @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
                            @Jdbc.RowMapper
                            long delete();
                        }
                        """,
                                 "@Jdbc.RowMapper is not valid on an update-count method");
        assertCompilationFailure("PrimitiveMapperRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface PrimitiveMapperRepository {
                            @Jdbc.Statement("select ID from POKEMON")
                            @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
                            @Jdbc.RowMapper
                            long find();
                        }
                        """,
                                 "@Jdbc.RowMapper does not support primitive result type long");
        assertCompilationFailure("PrimitiveExplicitMapperRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        import io.helidon.data.jdbc.JdbcClient;
                        final class PrimitiveLongMapper implements JdbcClient.RowMapper<Long> {
                            public Long map(JdbcClient.Row row) {
                                return row.required(1, Long.class);
                            }
                        }
                        @Data.Repository @Data.Provider("jdbc")
                        interface PrimitiveExplicitMapperRepository {
                            @Jdbc.Statement("select ID from POKEMON")
                            @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
                            @Jdbc.RowMapper(PrimitiveLongMapper.class)
                            long find();
                        }
                        """,
                                 "@Jdbc.RowMapper does not support primitive result type long");
        assertCompilationFailure("QueryVoidRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface QueryVoidRepository {
                            @Jdbc.Statement("select ID from POKEMON")
                            @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
                            void find();
                        }
                        """,
                                 "QUERY and generated-key methods require a materialized result");
        assertCompilationFailure("UpdateResultRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface UpdateResultRepository {
                            @Jdbc.Statement("update POKEMON set NAME = 'renamed'")
                            @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
                            String rename();
                        }
                        """,
                                 "UPDATE execution must return void, primitive int, or primitive long");
        assertCompilationFailure("QueryGeneratedKeysRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface QueryGeneratedKeysRepository {
                            @Jdbc.Statement("select ID from POKEMON")
                            @Jdbc.Execution(Jdbc.ExecutionType.QUERY)
                            @Jdbc.GeneratedKeys
                            Long find();
                        }
                        """,
                                 "@Jdbc.GeneratedKeys requires UPDATE execution");
        assertCompilationFailure("InvalidGeneratedColumnsRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface InvalidGeneratedColumnsRepository {
                            @Jdbc.Statement("insert into POKEMON default values")
                            @Jdbc.GeneratedKeys({"ID", "id"})
                            Long duplicate();
                        }
                        """,
                                 "Duplicate @Jdbc.GeneratedKeys column name: id");
        assertCompilationFailure("BlankGeneratedColumnRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface BlankGeneratedColumnRepository {
                            @Jdbc.Statement("insert into POKEMON default values")
                            @Jdbc.GeneratedKeys(" ")
                            Long blank();
                        }
                        """,
                                 "@Jdbc.GeneratedKeys column names must not be blank");
    }
}
