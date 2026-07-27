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
import static io.helidon.data.jdbc.codegen.JdbcCodegenTestSupport.occurrences;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcGeneratedKeyGenerationTest {
    @Test
    void generatesArbitraryKeyColumnsAsBuilderCalls() throws IOException {
        TestCompiler.Result result = compiler()
                .addSource("ManyKeyColumnsRepository.java", """
                        package example;

                        import java.util.Optional;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface ManyKeyColumnsRepository {
                            @Jdbc.Statement("insert into MANY_KEYS default values")
                            @Jdbc.GeneratedKeys({
                                    "COLUMN_01", "COLUMN_02", "COLUMN_03", "COLUMN_04",
                                    "COLUMN_05", "COLUMN_06", "COLUMN_07", "COLUMN_08",
                                    "COLUMN_09", "COLUMN_10", "COLUMN_11"
                            })
                            long insert();

                            @Jdbc.Statement("insert into MANY_KEYS default values")
                            @Jdbc.GeneratedKeys
                            long insertDefault();

                            @Jdbc.Statement("insert into MANY_KEYS default values")
                            @Jdbc.GeneratedKeys
                            Optional<Long> insertOptional();
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput()
                                                 .resolve("example/ManyKeyColumnsRepository__Jdbc.java"));
        assertThat(occurrences(source, ".addColumn("), is(11));
        assertThat(source, containsString(".generatedKeys().addColumn(\"COLUMN_01\")"));
        assertThat(source, containsString(".addColumn(\"COLUMN_11\").map("
                                                  + "row -> row.required(1, Long.class)).one();"));
        assertThat(source, containsString(".generatedKeys().map("
                                                  + "row -> row.required(1, Long.class)).one();"));
        assertThat(source, containsString(".generatedKeys().map("
                                                  + "row -> row.optional(1, Long.class))"
                                                  + ".optional().flatMap(value -> value);"));
        assertThat(source, not(containsString("List.of(")));
        assertThat(source, not(containsString("new String[")));
    }
}
