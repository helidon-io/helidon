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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcOffsetTemporalGenerationTest {

    /**
     * Proves both offset-bearing temporal types fail code generation in every
     * implicit scalar position that would otherwise claim a portable mapping.
     */
    @Test
    void rejectsOffsetTemporalTypesFromImplicitScalarPositions() {
        for (String javaType : List.of("java.time.OffsetTime", "java.time.OffsetDateTime")) {
            assertRejected(javaType,
                           "parameter",
                           "@Jdbc.Statement(\"update TEST_VALUE set VALUE = ?\") void update(" + javaType + " value);",
                           "Declarative SQL does not support the parameter type '" + javaType + "'.");
            assertRejected(javaType,
                           "query-result",
                           "@Jdbc.Statement(\"select VALUE from TEST_VALUE\") " + javaType + " find();",
                           "The JDBC result type '" + javaType
                                   + "' must be scalar, be a record, or declare @Jdbc.RowMapper.");
            assertRejected(javaType,
                           "generated-key",
                           "@Jdbc.Statement(\"insert into TEST_VALUE default values\") "
                                   + "@Jdbc.GeneratedKeys " + javaType + " insert();",
                           "The JDBC result type '" + javaType
                                   + "' must be scalar, be a record, or declare @Jdbc.RowMapper.");
            assertRejected(javaType,
                           "record-component",
                           "record Result(" + javaType + " value) {} "
                                   + "@Jdbc.Statement(\"select VALUE from TEST_VALUE\") Result find();",
                           "Record component 'value' uses the unsupported type '" + javaType + "'.");
        }
    }

    /**
     * Proves an application can still return offset-bearing values when an
     * explicit mapper reconstructs them from a supported representation.
     */
    @Test
    void acceptsOffsetTemporalResultsWithExplicitMappers() {
        TestCompiler.Result result = compile("MappedOffsetRepository.java", """
                package example;

                import java.time.OffsetDateTime;
                import java.time.OffsetTime;

                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;
                import io.helidon.data.jdbc.JdbcClient;

                final class OffsetTimeMapper implements JdbcClient.RowMapper<OffsetTime> {
                    @Override
                    public OffsetTime map(JdbcClient.Row row) {
                        return OffsetTime.parse(row.required(1, String.class));
                    }
                }

                final class OffsetDateTimeMapper implements JdbcClient.RowMapper<OffsetDateTime> {
                    @Override
                    public OffsetDateTime map(JdbcClient.Row row) {
                        return OffsetDateTime.parse(row.required(1, String.class));
                    }
                }

                @Data.Repository
                @Data.Provider("jdbc")
                interface MappedOffsetRepository {
                    @Jdbc.Statement("select OFFSET_TIME_TEXT from TEST_VALUE")
                    @Jdbc.RowMapper(OffsetTimeMapper.class)
                    OffsetTime offsetTime();

                    @Jdbc.Statement("select OFFSET_DATE_TIME_TEXT from TEST_VALUE")
                    @Jdbc.RowMapper(OffsetDateTimeMapper.class)
                    OffsetDateTime offsetDateTime();
                }
                """);

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
    }

    private static void assertRejected(String javaType,
                                       String scenario,
                                       String declaration,
                                       String expectedDiagnostic) {
        String typeName = javaType.substring(javaType.lastIndexOf('.') + 1);
        String repositoryName = "Unsupported" + typeName + scenario.replace("-", "") + "Repository";
        TestCompiler.Result result = compile(repositoryName + ".java", """
                package example;

                import io.helidon.data.Data;
                import io.helidon.data.jdbc.Jdbc;

                @Data.Repository
                @Data.Provider("jdbc")
                interface %s {
                    %s
                }
                """.formatted(repositoryName, declaration));

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(scenario + ": " + diagnostics, result.success(), is(false));
        assertThat(scenario + ": " + diagnostics, diagnostics, containsString(expectedDiagnostic));
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
