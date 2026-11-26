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
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcConnectionInvalidatorTest {

    /**
     * Proves ordinary invalidation retains an application primary while both
     * cleanup attempts contribute sanitized diagnostics in encounter order.
     */
    @Test
    void ordinaryInvalidationPreservesItsPrimaryAndSanitizesCleanupFailures() throws Exception {
        Connection connection = mock(Connection.class);
        IllegalStateException primary = new IllegalStateException("application failure");
        OutOfMemoryError abortFailure = new OutOfMemoryError("abort failed");
        IllegalStateException closeFailure = new IllegalStateException("private close detail");
        doThrow(abortFailure).when(connection).abort(any());
        doThrow(closeFailure).when(connection).close();

        Throwable reportedFailure = JdbcConnectionInvalidator.invalidate(connection, primary);

        assertThat(reportedFailure, sameInstance(primary));
        assertThat(primary.getSuppressed().length, is(2));
        assertSanitized(primary.getSuppressed()[0], abortFailure, "aborting a connection");
        assertSanitized(primary.getSuppressed()[1], closeFailure, "closing an invalidated connection");
        InOrder cleanup = inOrder(connection);
        cleanup.verify(connection).abort(any());
        cleanup.verify(connection).close();
    }

    /**
     * Proves infrastructure invalidation promotes a fatal abort failure over
     * a sanitized JDBC primary and still attempts the final close.
     */
    @Test
    void infrastructureInvalidationPromotesAFatalAbortAndContinuesClosing() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException primary = new SQLException("private connection detail", "08006", 91);
        OutOfMemoryError abortFailure = new OutOfMemoryError("abort failed");
        IllegalStateException closeFailure = new IllegalStateException("private close detail");
        doThrow(abortFailure).when(connection).abort(any());
        doThrow(closeFailure).when(connection).close();

        Throwable reportedFailure = JdbcConnectionInvalidator.invalidateInfrastructure(connection, primary);

        assertThat(reportedFailure, sameInstance(abortFailure));
        assertThat(abortFailure.getSuppressed().length, is(2));
        Throwable earlierFailure = abortFailure.getSuppressed()[0];
        assertThat(earlierFailure, not(sameInstance(primary)));
        assertThat(earlierFailure.getMessage(), is("The JDBC driver reported a failure."));
        assertThat(((SQLException) earlierFailure).getSQLState(), is("08006"));
        assertThat(((SQLException) earlierFailure).getErrorCode(), is(91));
        assertSanitized(abortFailure.getSuppressed()[1], closeFailure, "closing an invalidated connection");
        InOrder cleanup = inOrder(connection);
        cleanup.verify(connection).abort(any());
        cleanup.verify(connection).close();
    }

    /**
     * Proves the first fatal infrastructure failure remains primary while a
     * later fatal abort and recoverable close failure are retained.
     */
    @Test
    void infrastructureInvalidationRetainsTheFirstFatalError() throws Exception {
        Connection connection = mock(Connection.class);
        OutOfMemoryError primary = new OutOfMemoryError("first fatal failure");
        AssertionError abortFailure = new AssertionError("second fatal failure");
        IllegalStateException closeFailure = new IllegalStateException("private close detail");
        doThrow(abortFailure).when(connection).abort(any());
        doThrow(closeFailure).when(connection).close();

        Throwable reportedFailure = JdbcConnectionInvalidator.invalidateInfrastructure(connection, primary);

        assertThat(reportedFailure, sameInstance(primary));
        assertThat(primary.getSuppressed().length, is(2));
        assertThat(primary.getSuppressed()[0], sameInstance(abortFailure));
        assertSanitized(primary.getSuppressed()[1], closeFailure, "closing an invalidated connection");
    }

    /**
     * Proves a driver rethrowing the primary instance cannot trigger Java
     * self-suppression or prevent the remaining close attempt.
     */
    @Test
    void infrastructureInvalidationRejectsSelfSuppressionWithoutSkippingClose() throws Exception {
        Connection connection = mock(Connection.class);
        OutOfMemoryError sharedFailure = new OutOfMemoryError("shared fatal failure");
        doThrow(sharedFailure).when(connection).abort(any());

        Throwable reportedFailure = JdbcConnectionInvalidator.invalidateInfrastructure(connection, sharedFailure);

        assertThat(reportedFailure, sameInstance(sharedFailure));
        assertThat(sharedFailure.getSuppressed().length, is(0));
        verify(connection).close();
    }

    private static void assertSanitized(Throwable actual, Throwable original, String operation) {
        assertThat(actual, notNullValue());
        assertThat(actual, not(sameInstance(original)));
        assertThat(actual.getMessage(), containsString(operation));
        assertThat(actual.getMessage(), not(containsString("private")));
    }
}
