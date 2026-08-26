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
package io.helidon.data.jdbc.tests;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.JDBCType;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;

import io.helidon.data.jdbc.JdbcClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneratedScalarBindingTest {

    /**
     * Proves codegen shares the nullable decision across every supported
     * reference scalar while retaining one invocation per physical marker.
     *
     * @throws Exception when the compiler output cannot be inspected
     */
    @Test
    void generatedRepositorySharesNullableBindHelperAcrossPhysicalMarkers() throws Exception {
        String source = generatedSource(ScalarBindingRepository.class);

        assertThat(source.split("bindParameter\\(jdbcStatement,", -1).length - 1, is(18));
        assertThat(source, containsString("void bindParameter("));
        assertThat(source, containsString("if (value == null) {"));
        assertThat(source, containsString("JdbcClient.bindNull(statement, index, nullType);"));
        assertThat(source, containsString("statement.bind(index, value);"));
        assertThat(source, containsString("JdbcClient.createGenerated(jdbcClient, SQL_BIND_ALL, 18);"));
        assertThat(source, not(containsString("if (booleanValue == null) {")));

        String[] parameterNames = {
                "booleanValue",
                "byteValue",
                "shortValue",
                "integerValue",
                "longValue",
                "floatValue",
                "doubleValue",
                "decimalValue",
                "stringValue",
                "bytesValue",
                "localDateValue",
                "localTimeValue",
                "localDateTimeValue",
                "offsetTimeValue",
                "offsetDateTimeValue",
                "dateValue",
                "timeValue",
                "timestampValue"
        };
        JDBCType[] nullTypes = {
                JDBCType.BOOLEAN,
                JDBCType.TINYINT,
                JDBCType.SMALLINT,
                JDBCType.INTEGER,
                JDBCType.BIGINT,
                JDBCType.REAL,
                JDBCType.DOUBLE,
                JDBCType.DECIMAL,
                JDBCType.VARCHAR,
                JDBCType.VARBINARY,
                JDBCType.DATE,
                JDBCType.TIME,
                JDBCType.TIMESTAMP,
                JDBCType.TIME_WITH_TIMEZONE,
                JDBCType.TIMESTAMP_WITH_TIMEZONE,
                JDBCType.DATE,
                JDBCType.TIME,
                JDBCType.TIMESTAMP
        };
        for (int index = 0; index < parameterNames.length; index++) {
            assertThat(source,
                       containsString("bindParameter(jdbcStatement, " + (index + 1) + ", "
                                              + parameterNames[index] + ", JDBCType."
                                              + nullTypes[index] + ");"));
        }
    }

    /**
     * Proves a repository with one nullable physical bind keeps its branch
     * inline instead of adding a one-use private helper.
     *
     * @throws Exception when the compiler output cannot be inspected
     */
    @Test
    void generatedRepositoryKeepsOneNullableBindInline() throws Exception {
        String source = generatedSource(OverflowRepository.class);

        assertThat(source, containsString("if (name == null) {"));
        assertThat(source, containsString("JdbcClient.bindNull(jdbcStatement, 1, JDBCType.VARCHAR);"));
        assertThat(source, not(containsString("void bindParameter(")));
    }

    /**
     * Proves a repository contract using the preferred helper name receives a
     * compilable generated implementation with a collision-free helper name,
     * and that a repeated parameter is bound at every physical marker.
     *
     * @throws Exception when the compiler output cannot be inspected
     */
    @Test
    void generatedRepositoryAvoidsNullableBindHelperNameCollision() throws Exception {
        String source = generatedSource(NullableBindingCollisionRepository.class);

        assertThat(source.split("bindParameter2\\(jdbcStatement,", -1).length - 1, is(3));
        assertThat(source, containsString("void bindParameter2("));
        assertThat(source, containsString("bindParameter2(jdbcStatement, 1, name, JDBCType.VARCHAR);"));
        assertThat(source, containsString("bindParameter2(jdbcStatement, 2, label, JDBCType.VARCHAR);"));
        assertThat(source, containsString("bindParameter2(jdbcStatement, 3, name, JDBCType.VARCHAR);"));
    }

    /**
     * Proves generated non-null calls use the supported statement contract
     * when an alternate client receives the generated repository.
     */
    @Test
    void generatedRepositoryBindsEveryReferenceScalarThroughTheSupportedContract() {
        JdbcClient client = mock(JdbcClient.class);
        JdbcClient.Statement statement = mock(JdbcClient.Statement.class);
        when(client.create(anyString())).thenReturn(statement);
        when(statement.bind(anyInt(), any())).thenReturn(statement);
        ScalarBindingRepository repository = new ScalarBindingRepository__Jdbc(client);

        Object[] values = {
                Boolean.TRUE,
                (byte) 2,
                (short) 3,
                4,
                5L,
                6.5F,
                7.5D,
                new BigDecimal("8.50"),
                "value",
                new byte[] {9, 10},
                LocalDate.of(2026, 7, 27),
                LocalTime.of(10, 11, 12),
                LocalDateTime.of(2026, 7, 27, 10, 11, 12),
                OffsetTime.parse("10:11:12+05:30"),
                OffsetDateTime.parse("2026-07-27T10:11:12+05:30"),
                java.sql.Date.valueOf("2026-07-27"),
                Time.valueOf("10:11:12"),
                Timestamp.valueOf("2026-07-27 10:11:12")
        };
        invoke(repository, values);

        verify(client).create(anyString());
        for (int index = 0; index < values.length; index++) {
            verify(statement).bind(index + 1, values[index]);
        }
        verify(statement).execute();
    }

    private static void invoke(ScalarBindingRepository repository, Object[] values) {
        repository.bindAll((Boolean) values[0],
                           (Byte) values[1],
                           (Short) values[2],
                           (Integer) values[3],
                           (Long) values[4],
                           (Float) values[5],
                           (Double) values[6],
                           (BigDecimal) values[7],
                           (String) values[8],
                           (byte[]) values[9],
                           (LocalDate) values[10],
                           (LocalTime) values[11],
                           (LocalDateTime) values[12],
                           (OffsetTime) values[13],
                           (OffsetDateTime) values[14],
                           (java.sql.Date) values[15],
                           (Time) values[16],
                           (Timestamp) values[17]);
    }

    private static String generatedSource(Class<?> repositoryType) throws Exception {
        Path testClasses = Path.of(GeneratedScalarBindingTest.class.getProtectionDomain()
                                           .getCodeSource()
                                           .getLocation()
                                           .toURI());
        Path generatedSource = testClasses.getParent()
                .resolve("generated-sources/annotations/")
                .resolve(repositoryType.getName().replace('.', '/') + "__Jdbc.java");
        return Files.readString(generatedSource);
    }
}
