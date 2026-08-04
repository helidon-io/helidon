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
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcBindingGenerationTest {
    @Test
    void acceptsTheSinglePositionalParameterCase() throws IOException {
        TestCompiler.Result result = compiler()
                .addSource("SingleParameterRepository.java", """
                        package example;
                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        @Data.Repository @Data.Provider("jdbc")
                        interface SingleParameterRepository {
                            @Jdbc.Statement("select NAME from POKEMON where ID = ?")
                            String find(long id);
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput()
                                                 .resolve("example/SingleParameterRepository__Jdbc.java"));
        assertThat(source, containsString("jdbcStatement.bind(1, id);"));
    }

    @Test
    void preservesExplicitNullPredicateSemanticsAndBindsEveryMarker() throws IOException {
        TestCompiler.Result result = compiler()
                .addSource("NullablePredicateRepository.java", """
                        package example;

                        import java.util.List;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;

                        record ContactEmail(long id, java.util.Optional<String> email) {
                        }

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface NullablePredicateRepository {
                            @Jdbc.Statement(\"\"\"
                                    select ID as id, EMAIL as email
                                    from CONTACT
                                    where (:email is null or EMAIL = :email)
                                    \"\"\")
                            List<ContactEmail> optionalFilter(String email);

                            @Jdbc.Statement(\"\"\"
                                    select ID as id, EMAIL as email
                                    from CONTACT
                                    where ((:email is null and EMAIL is null) or EMAIL = :email)
                                    \"\"\")
                            List<ContactEmail> nullSafeEquality(String email);
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput()
                                                 .resolve("example/NullablePredicateRepository__Jdbc.java"));
        assertThat(source, containsString("where (? is null or EMAIL = ?)"));
        assertThat(source, containsString("where ((? is null and EMAIL is null) or EMAIL = ?)"));
        assertThat(occurrences(source, "jdbcStatement.bindNull(1, JDBCType.VARCHAR);"), is(2));
        assertThat(occurrences(source, "jdbcStatement.bindNull(2, JDBCType.VARCHAR);"), is(2));
        assertThat(occurrences(source, "jdbcStatement.bind(1, email);"), is(2));
        assertThat(occurrences(source, "jdbcStatement.bind(2, email);"), is(2));
        assertThat(source, containsString("""
                @Override
                public List<ContactEmail> optionalFilter(String email) {
                    JdbcClient.Statement jdbcStatement = jdbcClient.create(SQL_OPTIONAL_FILTER);
                    if (email == null) {
                        jdbcStatement.bindNull(1, JDBCType.VARCHAR);
                    } else {
                        jdbcStatement.bind(1, email);
                    }
                    if (email == null) {
                        jdbcStatement.bindNull(2, JDBCType.VARCHAR);
                    } else {
                        jdbcStatement.bind(2, email);
                    }
                    return jdbcStatement.map(MAPPER_OPTIONAL_FILTER).list();
                }
                """.indent(4)));
    }

    @Test
    void generatesCanonicalTypedNullBindingsForEverySupportedReferenceScalar() throws IOException {
        TestCompiler.Result result = compiler()
                .addSource("ScalarBindingRepository.java", """
                        package example;

                        import java.math.BigDecimal;
                        import java.time.LocalDate;
                        import java.time.LocalDateTime;
                        import java.time.LocalTime;
                        import java.time.OffsetDateTime;
                        import java.time.OffsetTime;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface ScalarBindingRepository {
                            @Jdbc.Statement(\"\"\"
                                    update TEST_VALUE set VALUE = VALUE
                                    where BOOLEAN_VALUE = :booleanValue
                                      and BYTE_VALUE = :byteValue
                                      and SHORT_VALUE = :shortValue
                                      and INTEGER_VALUE = :integerValue
                                      and LONG_VALUE = :longValue
                                      and FLOAT_VALUE = :floatValue
                                      and DOUBLE_VALUE = :doubleValue
                                      and DECIMAL_VALUE = :decimalValue
                                      and STRING_VALUE = :stringValue
                                      and BINARY_VALUE = :binaryValue
                                      and LOCAL_DATE_VALUE = :localDateValue
                                      and LOCAL_TIME_VALUE = :localTimeValue
                                      and LOCAL_DATE_TIME_VALUE = :localDateTimeValue
                                      and OFFSET_TIME_VALUE = :offsetTimeValue
                                      and OFFSET_DATE_TIME_VALUE = :offsetDateTimeValue
                                      and SQL_DATE_VALUE = :sqlDateValue
                                      and SQL_TIME_VALUE = :sqlTimeValue
                                      and SQL_TIMESTAMP_VALUE = :sqlTimestampValue
                                    \"\"\")
                            void update(Boolean booleanValue,
                                        Byte byteValue,
                                        Short shortValue,
                                        Integer integerValue,
                                        Long longValue,
                                        Float floatValue,
                                        Double doubleValue,
                                        BigDecimal decimalValue,
                                        String stringValue,
                                        byte[] binaryValue,
                                        LocalDate localDateValue,
                                        LocalTime localTimeValue,
                                        LocalDateTime localDateTimeValue,
                                        OffsetTime offsetTimeValue,
                                        OffsetDateTime offsetDateTimeValue,
                                        java.sql.Date sqlDateValue,
                                        java.sql.Time sqlTimeValue,
                                        java.sql.Timestamp sqlTimestampValue);
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput()
                                                 .resolve("example/ScalarBindingRepository__Jdbc.java"));
        assertThat(occurrences(source, "JDBCType.BOOLEAN"), is(1));
        assertThat(occurrences(source, "JDBCType.TINYINT"), is(1));
        assertThat(occurrences(source, "JDBCType.SMALLINT"), is(1));
        assertThat(occurrences(source, "JDBCType.INTEGER"), is(1));
        assertThat(occurrences(source, "JDBCType.BIGINT"), is(1));
        assertThat(occurrences(source, "JDBCType.REAL"), is(1));
        assertThat(occurrences(source, "JDBCType.DOUBLE"), is(1));
        assertThat(occurrences(source, "JDBCType.DECIMAL"), is(1));
        assertThat(occurrences(source, "JDBCType.VARCHAR"), is(1));
        assertThat(occurrences(source, "JDBCType.VARBINARY"), is(1));
        assertThat(occurrences(source, "JDBCType.DATE);"), is(2));
        assertThat(occurrences(source, "JDBCType.TIME);"), is(2));
        assertThat(occurrences(source, "JDBCType.TIMESTAMP);"), is(2));
        assertThat(occurrences(source, "JDBCType.TIME_WITH_TIMEZONE);"), is(1));
        assertThat(occurrences(source, "JDBCType.TIMESTAMP_WITH_TIMEZONE);"), is(1));
    }
}
