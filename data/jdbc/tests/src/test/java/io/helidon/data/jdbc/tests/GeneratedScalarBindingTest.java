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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneratedScalarBindingTest {

    @Test
    void generatedRepositoryBindsEveryReferenceScalarAndCanonicalTypedNull() {
        JdbcClient client = mock(JdbcClient.class);
        JdbcClient.Statement statement = mock(JdbcClient.Statement.class);
        when(client.create(anyString())).thenReturn(statement);
        when(statement.bind(anyInt(), any())).thenReturn(statement);
        when(statement.bindNull(anyInt(), any())).thenReturn(statement);
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

        for (int index = 0; index < values.length; index++) {
            verify(statement).bind(index + 1, values[index]);
        }
        verify(statement).execute();

        clearInvocations(client, statement);
        invoke(repository, new Object[values.length]);

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
        for (int index = 0; index < nullTypes.length; index++) {
            verify(statement).bindNull(index + 1, nullTypes[index]);
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
}
