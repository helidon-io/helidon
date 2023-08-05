/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

final class TestConditionallyCloseableConnection {

    private DataSource ds;

    private TestConditionallyCloseableConnection() {
        super();
    }

    @BeforeEach
    final void initializeDataSource() throws SQLException {
        final JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:testConditionallyCloseableConnection");
        ds.setUser("sa");
        ds.setPassword("sa");
        this.ds = ds;
    }

    @AfterEach
    final void closeDataSource() throws SQLException {
        try (final Connection c = this.ds.getConnection();
             final Statement s = c.createStatement();) {
            s.execute("SHUTDOWN");
        }
    }

    @SuppressWarnings("try")
    @Test
    final void testLegacyIsCloseable() throws SQLException {
        try (@SuppressWarnings("deprecation")
             final ConditionallyCloseableConnection c = new ConditionallyCloseableConnection(this.ds.getConnection());
             final Statement s = c.createStatement();
             final ResultSet rs = s.executeQuery("SHOW TABLES")) {
            assertThat(rs.next(), is(false)); // no tables

            // ConditionallyCloseableConnections are closeable by default.
            assertThat(c.isCloseable(), is(true));

            c.setCloseable(false);
            assertThat(c.isCloseable(), is(false));

            // Closing a ConditionallyCloseableConnection when
            // isCloseable() returns false does not actually close the delegate.
            c.close();
            assertThat(c.delegate().isClosed(), is(false));

            // IN BACKWARDS COMPATIBILITY MODE ONLY, it also does not
            // make the ConditionallyCloseableConnection itself look
            // like it is closed.
            assertThat(c.isClosed(), is(false));

            // Make sure we recorded that a close() attempt happened.
            assertThat(c.isClosePending(), is(true));

            // We can reset the closeable status.
            c.setCloseable(true);
            assertThat(c.isCloseable(), is(true));
            assertThat(c.isClosed(), is(false));

            // This also wipes out the fact that a close was
            // previously attempted.
            assertThat(c.isClosePending(), is(false));

            // Closing a ConditionallyCloseableConnection when
            // isCloseable() returns true actually irrevocably closes
            // the connection.
            c.close(); // closes for real
            assertThat(c.isClosed(), is(true));
            assertThat(c.isClosePending(), is(false));

            // Still closed.
            c.setCloseable(false); // won't matter
            assertThat(c.isClosed(), is(true));
            assertThat(c.isClosePending(), is(false));

            // Note that the JDBC specification says that closing a
            // connection will release "this Connection object's
            // database and JDBC resources immediately"
            // (https://docs.oracle.com/en/java/javase/19/docs/api/java.sql/java/sql/Connection.html#close()).
            // However it is unclear whether a Statement constitutes
            // either a "database" or a "JDBC resource" in this
            // context. You would think it would, but H2 does not
            // close open Statements or ResultSets when their creating
            // Connection is closed, and neither does PostgreSQL.
            assertThat(s.isClosed(), is(false));
            assertThat(rs.isClosed(), is(false));
        }
    }

    @SuppressWarnings("try")
    @Test
    final void testIsCloseable() throws SQLException {
        try (final ConditionallyCloseableConnection c = new ConditionallyCloseableConnection(this.ds.getConnection(), true, true);
             final Statement s = c.createStatement();
             final ResultSet rs = s.executeQuery("SHOW TABLES")) {
            assertThat(rs.next(), is(false)); // no tables

            // ConditionallyCloseableConnections are closeable by default.
            assertThat(c.isCloseable(), is(true));

            c.setCloseable(false);
            assertThat(c.isCloseable(), is(false));

            // Closing a ConditionallyCloseableConnection when
            // isCloseable() returns false does not actually close the delegate.
            c.close();
            assertThat(c.delegate().isClosed(), is(false));

            // It does make the ConditionallyCloseableConnection
            // itself look like it is closed, however.
            assertThat(c.isClosed(), is(true));

            // Make sure we recorded that a close() attempt happened.
            assertThat(c.isClosePending(), is(true));

            // We can reset the closeable status.
            c.setCloseable(true);
            assertThat(c.isCloseable(), is(true));
            assertThat(c.isClosed(), is(false));

            // This also wipes out the fact that a close was
            // previously attempted.
            assertThat(c.isClosePending(), is(false));

            // Closing a ConditionallyCloseableConnection when
            // isCloseable() returns true actually irrevocably closes
            // the connection.
            c.close(); // closes for real
            assertThat(c.isClosed(), is(true));
            assertThat(c.isClosePending(), is(false));

            // Still closed.
            c.setCloseable(false); // won't matter
            assertThat(c.isClosed(), is(true));
            assertThat(c.isClosePending(), is(false));

            // Note that the JDBC specification says that closing a
            // connection will release "this Connection object's
            // database and JDBC resources immediately"
            // (https://docs.oracle.com/en/java/javase/19/docs/api/java.sql/java/sql/Connection.html#close()).
            // However it is unclear whether a Statement constitutes
            // either a "database" or a "JDBC resource" in this
            // context. You would think it would, but H2 does not
            // close open Statements or ResultSets when their creating
            // Connection is closed, and neither does PostgreSQL.
            assertThat(s.isClosed(), is(false));
            assertThat(rs.isClosed(), is(false));
        }
    }

}
