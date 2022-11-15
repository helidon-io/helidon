/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.transaction.xa.Xid;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.TransactionReaper;
import com.arjuna.ats.arjuna.coordinator.listener.ReaperMonitor;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import io.helidon.integrations.jdbc.ConditionallyCloseableConnection;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static jakarta.transaction.Status.STATUS_NO_TRANSACTION;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TestJTAConnection {

    private static final Logger LOGGER = Logger.getLogger(TestJTAConnection.class.getName());

    private static final JTAEnvironmentBean jtaEnvironmentBean = new JTAEnvironmentBean();

    private JdbcDataSource h2ds;

    private TransactionManager tm;

    private TransactionSynchronizationRegistry tsr;

    private TestJTAConnection() throws SQLException {
        super();
    }

    @BeforeEach
    final void initializeH2DataSource() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test;INIT=SET TRACE_LEVEL_FILE=4");
        ds.setUser("sa");
        ds.setPassword("sa");
        this.h2ds = ds;
    }

    @BeforeEach
    final void initializeTransactionManager() throws SystemException {
        this.tm = jtaEnvironmentBean.getTransactionManager(); // com.arjuna.ats.jta.TransactionManager.transactionManager();
        this.tm.setTransactionTimeout(20 * 60); // 20 minutes for debugging
    }

    @BeforeEach
    final void initializeTransactionSynchronizationRegistry() throws SystemException {
        this.tsr = jtaEnvironmentBean.getTransactionSynchronizationRegistry();
    }

    @AfterEach
    final void rollback() throws SQLException, SystemException {
        switch (this.tm.getStatus()) {
        case STATUS_NO_TRANSACTION:
            break;
        default:
            this.tm.rollback();
            break;
        }
        this.tm.setTransactionTimeout(0);
    }

    @DisplayName("Spike")
    @Test
    final void testSpike()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SQLException,
               SystemException {
        LOGGER.info("Starting testSpike()");
        tm.begin();

        Transaction t = tm.getTransaction();
        assertThat(t, not(nullValue()));

        // For this test, where transaction reaping is set to happen
        // eons in the future, we can make this assertion.  For
        // real-world scenarios, the reaper might have ended the
        // transaction immediately on another thread.
        assertThat(t.getStatus(), is(Status.STATUS_ACTIVE));
        

        try (Connection physicalConnection = h2ds.getConnection();
             JTAConnection logicalConnection = (JTAConnection) JTAConnection.connection(tm::getTransaction,
                                                                                        tsr,
                                                                                        physicalConnection)) {

            // Trigger an Object method; make sure nothing blows up
            // (in case we're using proxies).
            logicalConnection.toString();

            // Up until this point, the connection should not be enlisted.
            assertThat(logicalConnection.xid(), nullValue());

            // That means it should be closeable.
            assertThat(logicalConnection.isCloseable(), is(true));
            assertThat(logicalConnection.isClosed(), is(false));
            
            // Trigger harmless Connection method; make sure nothing
            // blows up.
            logicalConnection.getHoldability();

            // Almost all Connection methods will cause enlistment to
            // happen.  getHoldability(), just invoked, is one of
            // them.
            Xid xid = logicalConnection.xid();
            assertThat(xid, not(nullValue()));

            // That means the Connection is no longer closeable.
            assertThat(logicalConnection.isCloseable(), is(false));

            // Should be a no-op.
            logicalConnection.close();
            assertThat(logicalConnection.isClosed(), is(false));
            
            // Should get the same Xid back whenever we call xid()
            // once we're enlisted.
            assertThat(logicalConnection.xid(), sameInstance(xid));

            // Ensure JDBC constructs' backlinks work correctly.
            try (Statement s = logicalConnection.createStatement()) {
                assertThat(s, not(nullValue()));
                assertThat(s.getConnection(), sameInstance(logicalConnection));
                try (ResultSet rs = s.executeQuery("SHOW TABLES")) {
                    assertThat(rs.getStatement(), sameInstance(s));
                }
            }

            // Commit AND DISASSOCIATE the transaction, which can only
            // happen with a call to TransactionManager.commit(), not
            // just Transaction.commit().
            tm.commit();

            // Transaction is over; the Xid should be null.
            assertThat(logicalConnection.xid(), nullValue());

            // Transaction is over; the connection should be closeable again.
            assertThat(logicalConnection.isCloseable(), is(true));

            // We should be able to actually close it early.  The
            // auto-close should not fail, either.
            logicalConnection.close();
            assertThat(logicalConnection.isClosed(), is(true));
            assertThat(logicalConnection.delegate().isClosed(), is(true));
        }

        LOGGER.info("Ending testSpike()");
    }

    @Test
    final void testTimeout() throws InterruptedException, NotSupportedException, RollbackException, SystemException {
        LOGGER.info("Starting testTimeout()");

        tm.setTransactionTimeout(1); // 1 second; the minimum
        tm.begin();

        // For this test, where transaction reaping is set to happen
        // soon, it still won't happen in under 1000 milliseconds, so
        // this assertion is OK. For real-world scenarios, you
        // shouldn't assume anything about the initial status.
        assertThat(tm.getStatus(), is(Status.STATUS_ACTIVE));

        CountDownLatch latch = new CountDownLatch(1);
        Thread mainThread = Thread.currentThread();
        
        tsr.registerInterposedSynchronization(new Synchronization() {
                public void beforeCompletion() {
                    
                }
                public void afterCompletion(int status) {
                    assertThat(status, is(Status.STATUS_ROLLEDBACK));
                    assertThat(Thread.currentThread(), not(mainThread));
                    latch.countDown();
                }                    
            });
        
        // Wait for the transaction to roll back on the reaper thread.
        // The transaction timeout is 1 second; this waits for 2
        // seconds.  If this fails with an InterruptedException, which
        // should be impossible, check the logs for Narayana warning
        // that the assertion in the synchronization above failed.
        latch.await(1100L, TimeUnit.MILLISECONDS);

        // In this case, we never issued a rollback ourselves and we
        // never acquired a Transaction.  Here we show that you can
        // get a Transaction that is initially in a rolled back state.
        //
        // Verify there *was* a transaction but now it is rolled
        // back, so is essentially useless other than as a
        // tombstone.
        Transaction t = tm.getTransaction();
        assertThat(t, not(nullValue()));
        assertThat(t.getStatus(), is(Status.STATUS_ROLLEDBACK));

        // Verify that all the other status accessors return the
        // same thing.
        assertThat(tm.getStatus(), is(Status.STATUS_ROLLEDBACK));
        assertThat(tsr.getTransactionStatus(), is(Status.STATUS_ROLLEDBACK));

        // Verify that indeed you cannot enlist any XAResource in
        // the transaction when it is in the rolled back state.
        assertThrows(IllegalStateException.class, () -> t.enlistResource(JTAConnection.XA_RESOURCE));

        // Verify that even though the current transaction is
        // rolled back you can still roll it back and disassociate
        // it from the current thread.
        tm.rollback();
        assertThat(tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));

        // The Transaction remains in status Status.STATUS_ROLLEDBACK.
        assertThat(t.getStatus(), is(Status.STATUS_ROLLEDBACK));
    }

}
