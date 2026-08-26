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

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcMapperAccessibilityGenerationTest {

    @Test
    void rejectsMapperOutsideThePublicAnnotationBoundDuringJavaCompilation() {
        TestCompiler.Result result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(Data.class,
                                      JdbcClient.class,
                                      Service.class,
                                      Tx.class,
                                      Generated.class,
                                      TypeName.class,
                                      DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class,
                                      JdbcPersistenceGeneratorProvider.class))
                .addSource("InvalidMapperRepository.java", """
                        package example;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface InvalidMapperRepository {
                            @Jdbc.Statement("select VALUE from TEST_VALUE")
                            @Jdbc.RowMapper(String.class)
                            String find();
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("incompatible types"));
        assertThat(diagnostics, containsString("java.lang.String"));
    }

    @Test
    void leavesInaccessibleMapperDiagnosticsToJavaCompilation() throws IOException {
        TestCompiler.Result result = TestCompiler.builder()
                .currentRelease()
                .printDiagnostics(false)
                .addProcessor(AptProcessor::new)
                .addClasspath(List.of(Data.class,
                                      JdbcClient.class,
                                      Service.class,
                                      Tx.class,
                                      Generated.class,
                                      TypeName.class,
                                      DataGeneratorProvider.class,
                                      RepositoryCodegenProvider.class,
                                      JdbcPersistenceGeneratorProvider.class))
                .addSource("InaccessibleMapperContainer.java", """
                        package example;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        import io.helidon.data.jdbc.JdbcClient;

                        public final class InaccessibleMapperContainer {
                            private static final class Mapper implements JdbcClient.RowMapper<String> {
                                @Override
                                public String map(JdbcClient.Row row) {
                                    return row.required(1, String.class);
                                }
                            }

                            @Data.Repository
                            @Data.Provider("jdbc")
                            public interface Repository {
                                @Jdbc.Statement("select VALUE from TEST_VALUE")
                                @Jdbc.RowMapper(Mapper.class)
                                String find();
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(result.diagnostics(),
                   hasItem(allOf(containsString("InaccessibleMapperContainer.Mapper"),
                                 containsString("has private access"))));
        Path generatedSource = result.sourceOutput()
                .resolve("example/InaccessibleMapperContainer_Repository__Jdbc.java");
        assertThat(Files.exists(generatedSource), is(true));
        assertThat(Files.readString(generatedSource),
                   containsString("InaccessibleMapperContainer.Mapper mapperRowMapper"));
    }
}
