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

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import io.helidon.data.DataException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcColumnLayoutTest {
    /**
     * Proves indexed mapping does not eagerly request label metadata.
     */
    @Test
    void doesNotReadLabelMetadataUntilLabelLookup() throws Exception {
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(3);

        JdbcColumnLayout layout = JdbcColumnLayout.create(metadata, operation());

        assertThat(layout.columnCount(), is(3));
        verify(metadata, never()).getColumnLabel(anyInt());
        verify(metadata, never()).getColumnName(anyInt());
    }

    /**
     * Proves a blank label falls back to the physical column name.
     */
    @Test
    void usesPhysicalColumnNameWhenLabelIsBlank() throws Exception {
        ResultSetMetaData metadata = metadata(" ", "PHYSICAL_NAME");
        JdbcColumnLayout layout = JdbcColumnLayout.create(metadata, operation());

        assertThat(layout.index("physical_name"), is(1));
        assertThat(layout.index("PHYSICAL_NAME"), is(1));
        verify(metadata).getColumnLabel(1);
        verify(metadata).getColumnName(1);
    }

    /**
     * Proves blank label and column-name metadata is rejected consistently.
     */
    @Test
    void rejectsBlankLabelAndColumnNameDeterministically() throws Exception {
        assertUnusableMetadata(" ", "\t");
    }

    /**
     * Proves null label and column-name metadata is rejected consistently.
     */
    @Test
    void rejectsNullLabelAndColumnNameDeterministically() throws Exception {
        assertUnusableMetadata(null, null);
    }

    /**
     * Proves lazy metadata-access failures cross the sanitized provider
     * exception boundary.
     */
    @Test
    void translatesFailureFromLazyMetadataAccess() throws Exception {
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenThrow(new SQLException("label failure"));
        JdbcColumnLayout layout = JdbcColumnLayout.create(metadata, operation());

        DataException failure = assertThrows(DataException.class, () -> layout.index("value"));

        assertThat(failure.getCause().getMessage(), is("The JDBC driver reported a failure."));
    }

    /**
     * Creates one-column metadata with a configured label and physical name.
     *
     * @param label column label
     * @param name physical column name
     * @return metadata mock
     * @throws SQLException when Mockito cannot configure the JDBC methods
     */
    private static ResultSetMetaData metadata(String label, String name) throws SQLException {
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn(label);
        when(metadata.getColumnName(1)).thenReturn(name);
        return metadata;
    }

    /**
     * Verifies deterministic rejection of unusable label metadata.
     *
     * @param label column label
     * @param name physical column name
     * @throws SQLException when Mockito cannot configure the JDBC methods
     */
    private static void assertUnusableMetadata(String label, String name) throws SQLException {
        JdbcColumnLayout layout = JdbcColumnLayout.create(metadata(label, name), operation());

        DataException failure = assertThrows(DataException.class, () -> layout.index("value"));

        assertThat(failure.getMessage(),
                   is("Result column 1 has neither a usable label nor a column name."));
    }

    /**
     * Creates operation metadata for direct layout tests.
     *
     * @return query operation
     */
    private static JdbcOperation operation() {
        return new JdbcOperation("SELECT value", new JdbcOperation.Bind[0], JdbcPreparationPlan.query());
    }

}
