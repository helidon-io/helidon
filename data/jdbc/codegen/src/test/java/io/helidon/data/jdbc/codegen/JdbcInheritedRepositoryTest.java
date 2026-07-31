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

import static io.helidon.data.jdbc.codegen.JdbcCodegenTestSupport.assertCompilationFailure;
import static io.helidon.data.jdbc.codegen.JdbcCodegenTestSupport.compiler;
import static io.helidon.data.jdbc.codegen.JdbcCodegenTestSupport.occurrences;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcInheritedRepositoryTest {

    @Test
    void generatesInheritedMethodsWithResolvedGenericTypes() throws IOException {
        TestCompiler.Result result = compiler()
                .addSource("InheritedRepository.java", """
                        package example;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;

                        interface GenericRepository<T, ID> {
                            @Jdbc.Statement("select VALUE from ITEM where ID = :id")
                            T find(ID id);
                        }

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface InheritedRepository extends GenericRepository<String, Long> {
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput().resolve("example/InheritedRepository__Jdbc.java"));
        assertThat(source, containsString("String find(Long id)"));
        assertThat(source, containsString("jdbcStatement.bind(1, id);"));
        assertThat(source, containsString("return jdbcStatement.map(String.class).one();"));
    }

    @Test
    void usesTheClosestMethodDeclaration() throws IOException {
        TestCompiler.Result result = compiler()
                .addSource("OverrideRepository.java", """
                        package example;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;

                        interface ParentRepository {
                            @Jdbc.Statement("select OLD_VALUE from ITEM")
                            String find();
                        }

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface OverrideRepository extends ParentRepository {
                            @Override
                            @Jdbc.Statement("select NEW_VALUE from ITEM")
                            String find();
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput().resolve("example/OverrideRepository__Jdbc.java"));
        assertThat(source, containsString("select NEW_VALUE from ITEM"));
        assertThat(source, not(containsString("select OLD_VALUE from ITEM")));
    }

    @Test
    void reportsInvalidInheritedMethodsAtTheirDeclaration() {
        assertCompilationFailure("InvalidInheritedRepository.java", """
                        package example;

                        import io.helidon.data.Data;

                        interface InvalidParentRepository {
                            String find();
                        }

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface InvalidInheritedRepository extends InvalidParentRepository {
                        }
                        """,
                                 "JDBC repository method requires @Jdbc.Statement");
    }
}
