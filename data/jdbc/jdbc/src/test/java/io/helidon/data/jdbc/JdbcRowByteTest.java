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
package io.helidon.data.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Optional;

import io.helidon.data.DataException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcRowByteTest {

    /**
     * Verifies that wrapper and primitive byte requests use the portable JDBC
     * numeric getter for both index and label access.
     */
    @Test
    void readsByteThroughThePortableNumericGetter() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getByte(1)).thenReturn((byte) 2, (byte) 3);
        JdbcRow row = newRow(resultSet);

        assertThat(row.get(1, Byte.class), is((byte) 2));
        assertThat(row.optional("byte_value", byte.class), is(Optional.of((byte) 3)));

        verify(resultSet, times(2)).getByte(1);
        verify(resultSet, times(2)).wasNull();
        verify(resultSet, never()).getObject(1, Byte.class);
    }

    /**
     * Verifies that the primitive JDBC null sentinel is interpreted through
     * {@code wasNull} for optional and required byte reads.
     */
    @Test
    void distinguishesSqlNullFromAZeroByteValue() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.wasNull()).thenReturn(false, true, true);
        JdbcRow row = newRow(resultSet);

        assertThat(row.get(1, Byte.class), is((byte) 0));
        assertThat(row.optional(1, Byte.class), is(Optional.empty()));
        assertThrows(DataException.class, () -> row.get(1, Byte.class));

        verify(resultSet, times(3)).getByte(1);
        verify(resultSet, times(3)).wasNull();
        verify(resultSet, never()).getObject(1, Byte.class);
    }

    private static JdbcRow newRow(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("BYTE_VALUE");
        JdbcOperation operation = new JdbcOperation("SELECT BYTE_VALUE",
                                                    new JdbcOperation.Bind[0],
                                                    JdbcPreparationPlan.query());
        return new JdbcRow(resultSet, JdbcColumnLayout.create(metadata, operation), operation);
    }
}
