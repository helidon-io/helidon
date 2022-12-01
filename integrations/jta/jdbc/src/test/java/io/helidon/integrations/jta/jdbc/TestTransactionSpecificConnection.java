/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.jta.jdbc;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.ProxyConnection;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

@Deprecated(forRemoval = true, since = "3.1.1")
final class TestTransactionSpecificConnection {

    private JdbcDataSource h2ds;

    private TransactionManager tm;

    private TestTransactionSpecificConnection() {
        super();
    }

    @BeforeEach
    void initializeH2DataSource() throws SQLException, SystemException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test");
        ds.setUser("sa");
        ds.setPassword("sa");
        this.h2ds = ds;
        this.tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
        this.tm.setTransactionTimeout(20 * 60); // 20 minutes for debugging
    }

    @Test
    void testConnectionPoolSemantics() throws IllegalAccessException, NoSuchFieldException, SQLException {
        Field delegate = ProxyConnection.class.getDeclaredField("delegate");
        assertThat(delegate.trySetAccessible(), is(true));
        HikariConfig hc = new HikariConfig();
        hc.setDataSource(this.h2ds);
        try (HikariDataSource ds = new HikariDataSource(hc)) {
            Connection c1 = ds.getConnection();
            Connection d1 = (Connection) delegate.get(c1);
            c1.close();
            assertThat(c1.isClosed(), is(true));
            assertThat(d1.isClosed(), is(false));

            Connection c2 = ds.getConnection();
            Connection d2 = (Connection) delegate.get(c2);
            c2.close();
            assertThat(c2.isClosed(), is(true));
            assertThat(c1, not(sameInstance(c2)));
            assertThat(d1, sameInstance(d2));
        }
    }

    @Test
    void testCloseableAndClosedBehavior() throws SQLException {
        @SuppressWarnings("removal")
        io.helidon.integrations.jta.jdbc.JtaDataSource.TransactionSpecificConnection c =
            new io.helidon.integrations.jta.jdbc.JtaDataSource.TransactionSpecificConnection(this.h2ds.getConnection());

        assertThat(c.isCloseable(), is(false));
        assertThat(c.isClosed(), is(false));
        assertThat(c.getAutoCommit(), is(false));

        c.close(); // no-op
        assertThat(c.isCloseable(), is(false));
        assertThat(c.isClosed(), is(false));
        assertThat(c.isCloseCalled(), is(true));

        c.setCloseable(true);
        assertThat(c.isCloseable(), is(true));
        assertThat(c.isCloseCalled(), is(true)); // still
        assertThat(c.isClosed(), is(false));

        c.close(); // the real thing
        assertThat(c.isClosed(), is(true));
        assertThat(c.isCloseCalled(), is(true));
        assertThat(c.isCloseable(), is(false));
    }

    @Test
    void testTransactionManagerSemantics()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SQLException,
               SystemException {
        TransactionManager tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
        tm.setTransactionTimeout(20 * 60); // 20 minutes for debugging
        tm.begin();
        Transaction t = tm.getTransaction();

        // The TransactionSpecificConnection class does not interact
        // with the TransactionManager in any way by itself; prove
        // that this is the case.
        @SuppressWarnings("removal")
        io.helidon.integrations.jta.jdbc.JtaDataSource.TransactionSpecificConnection c
            = new io.helidon.integrations.jta.jdbc.JtaDataSource.TransactionSpecificConnection(this.h2ds.getConnection());

        // (The jakarta.transaction.Transaction/TransactionManager
        // state machine is weirder than weird.)
        t.commit(); // unnecessary
        tm.commit();

        // Committing the Transaction results in STATUS_COMMITTED.
        // Makes sense.
        assertThat(t.getStatus(), is(Status.STATUS_COMMITTED));

        // Committing the TransactionManager results in
        // STATUS_NO_TRANSACTION.
        // (https://jakarta.ee/specifications/transactions/2.0/jakarta-transactions-spec-2.0.html#completing-a-transaction).
        // Also makes sense.  What may not immediately make sense is
        // why they're not the same state. See
        // https://groups.google.com/g/narayana-users/c/eYVUmhE9QZg.
        assertThat(tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));

        assertThat(c.isCloseable(), is(false));

        c.close();
        assertThat(c.isClosed(), is(false));

        c.setCloseable(true);
        assertThat(c.isCloseable(), is(true));

        c.close();
        assertThat(c.isClosed(), is(true));
    }

}
