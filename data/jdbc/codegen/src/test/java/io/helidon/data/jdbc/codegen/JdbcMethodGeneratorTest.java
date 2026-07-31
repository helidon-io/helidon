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

import io.helidon.codegen.testing.TestCompiler;

import org.junit.jupiter.api.Test;

import static io.helidon.data.jdbc.codegen.JdbcCodegenTestSupport.compiler;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcMethodGeneratorTest {

    @Test
    void generatesOnlyScopedDeclarativeOperations() throws IOException {
        TestCompiler.Result result = compiler()
                .addSource("PokemonRepository.java", """
                        package example;

                        import java.util.List;
                        import java.util.Optional;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        import io.helidon.data.jdbc.JdbcClient;
                        import io.helidon.service.registry.Service;

                        record Pokemon(long id, String name) {
                        }

                        @Service.Singleton
                        final class PokemonMapper implements JdbcClient.RowMapper<Pokemon> {
                            @Override
                            public Pokemon map(JdbcClient.Row row) {
                                return new Pokemon(row.required("ID", Long.class),
                                                   row.required("NAME", String.class));
                            }
                        }

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface PokemonRepository {
                            @Jdbc.Statement("select ID, NAME from POKEMON "
                                    + "where STATE = :state and OWNER = :owner or PREVIOUS_STATE = :state")
                            List<Pokemon> find(String state, Long owner);

                            @Jdbc.Statement("select NAME from POKEMON where ID = ?")
                            Optional<String> findName(long id);

                            @Jdbc.Statement("select ID, NAME from POKEMON where ID = :id")
                            @Jdbc.RowMapper(PokemonMapper.class)
                            Pokemon mapped(long id);

                            @Jdbc.Statement("delete from POKEMON")
                            @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
                            void clear();

                            @Jdbc.Statement("update POKEMON set NAME = :name where ID = :id")
                            @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
                            int rename(String name, long id);

                            @Jdbc.Statement("delete from POKEMON where ID = ?")
                            @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
                            long delete(long id);

                            @Jdbc.Statement("insert into POKEMON(NAME) values (:name)")
                            @Jdbc.GeneratedKeys("ID")
                            long insert(String name);
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput().resolve("example/PokemonRepository__Jdbc.java"));
        assertThat(source, containsString("@SuppressWarnings"));
        assertThat(source, containsString("\"helidon:api:preview\""));
        assertThat(source, not(containsString("\"helidon:api:internal\"")));
        assertThat(source, containsString("where STATE = ? and OWNER = ? or PREVIOUS_STATE = ?"));
        assertThat(source, containsString("jdbcStatement.bind(1, state);"));
        assertThat(source, containsString("jdbcStatement.bindNull(2, JDBCType.BIGINT);"));
        assertThat(source, containsString("jdbcStatement.bind(3, state);"));
        assertThat(source, containsString("return jdbcStatement.map(MAPPER_FIND).list();"));
        assertThat(source, containsString("return jdbcStatement.map(String.class).optional();"));
        assertThat(source, containsString("PokemonMapper pokemonMapper"));
        assertThat(source, containsString("return jdbcStatement.map(pokemonMapper).one();"));
        assertThat(source, not(containsString("new PokemonMapper()")));
        assertThat(source, containsString("jdbcStatement.execute();"));
        assertThat(source, containsString("return Math.toIntExact(jdbcStatement.execute());"));
        assertThat(source, containsString("return jdbcStatement.execute();"));
        assertThat(source,
                   containsString("generatedKeys().addColumn(\"ID\").map("
                                          + "row -> row.required(1, Long.class)).one()"));
    }

}
