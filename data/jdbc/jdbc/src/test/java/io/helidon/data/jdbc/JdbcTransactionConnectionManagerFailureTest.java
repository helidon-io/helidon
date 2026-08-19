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

import java.sql.Connection;

import javax.sql.DataSource;

import io.helidon.data.DataException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcTransactionConnectionManagerFailureTest {

    @Test
    void sanitizesRuntimeConnectionAcquisitionFailure() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        IllegalStateException driverFailure = driverRuntimeFailure("private transaction URL");
        when(dataSource.getConnection()).thenThrow(driverFailure);
        JdbcTransactionConnectionManager manager = activeManager("acquisition");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> manager.acquire(dataSource));

        assertSanitized(failure, driverFailure, "acquiring a transaction connection");
        assertThrows(DataException.class, () -> manager.acquire(dataSource));
        manager.rollback("acquisition");
        manager.end();
    }

    @Test
    void sanitizesRuntimeConnectionInspectionFailureBeforeInvalidation() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        IllegalStateException driverFailure = driverRuntimeFailure("private transaction connection properties");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenThrow(driverFailure);
        JdbcTransactionConnectionManager manager = activeManager("inspection");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> manager.acquire(dataSource));

        assertSanitized(failure, driverFailure, "inspecting transaction automatic commit mode");
        verify(connection).abort(any());
        verify(connection).close();
        manager.rollback("inspection");
        manager.end();
    }

    @Test
    void sanitizesRuntimeConnectionSetupFailureBeforeInvalidation() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        IllegalStateException driverFailure = driverRuntimeFailure("private transaction setup detail");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(driverFailure).when(connection).setAutoCommit(false);
        JdbcTransactionConnectionManager manager = activeManager("setup");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                     () -> manager.acquire(dataSource));

        assertSanitized(failure, driverFailure, "disabling automatic commit mode");
        verify(connection).abort(any());
        verify(connection).close();
        manager.rollback("setup");
        manager.end();
    }

    private static JdbcTransactionConnectionManager activeManager(String identity) {
        JdbcTransactionConnectionManager manager = new JdbcTransactionConnectionManager();
        manager.start(Jdbc.PROVIDER);
        manager.begin(identity);
        return manager;
    }

    private static IllegalStateException driverRuntimeFailure(String secret) {
        IllegalStateException failure = new IllegalStateException(secret, new RuntimeException("private cause"));
        failure.addSuppressed(new RuntimeException("private suppressed"));
        return failure;
    }

    private static void assertSanitized(IllegalStateException actual,
                                        RuntimeException original,
                                        String operation) {
        assertThat(actual, not(sameInstance(original)));
        assertThat(actual.getMessage(),
                   is("The JDBC provider encountered an exception of type '" + original.getClass().getName()
                              + "' while " + operation + "."));
        assertThat(actual.getMessage(), not(containsString("private")));
        assertThat(actual.getCause(), nullValue());
        assertThat(actual.getSuppressed().length, is(0));
    }
}
