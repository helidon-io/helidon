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

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import io.helidon.integrations.jdbc.ConditionallyCloseableConnection;
import io.helidon.integrations.jta.jdbc.LocalXAResource.Association;
import io.helidon.integrations.jta.jdbc.LocalXAResource.Association.BranchState;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
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
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.helidon.integrations.jta.jdbc.LocalXAResource.Association.BranchState.ACTIVE;
import static io.helidon.integrations.jta.jdbc.LocalXAResource.Association.BranchState.IDLE;
import static io.helidon.integrations.jta.jdbc.LocalXAResource.ASSOCIATIONS;
import static jakarta.transaction.Status.STATUS_NO_TRANSACTION;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TestJtaConnection {

    private static final Logger LOGGER = Logger.getLogger(TestJtaConnection.class.getName());

    private static final JTAEnvironmentBean jtaEnvironmentBean = new JTAEnvironmentBean();

    private JdbcDataSource h2ds;

    private TransactionManager tm;

    private TransactionSynchronizationRegistry tsr;

    private TestJtaConnection() throws SQLException {
        super();
    }

    @BeforeEach
    final void initializeH2DataSource() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        // Use TRACE_LEVEL_FILE=4 to turn on slf4j logging.
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
        // assertThat(ASSOCIATIONS.size(), is(0));
    }

    @DisplayName("Spike")
    @SuppressWarnings("try")
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

        // For this test, where transaction reaping is set to happen eons in the future, we can make this assertion.
        // For real-world scenarios, the reaper might have ended the transaction immediately on another thread.
        assertThat(t.getStatus(), is(Status.STATUS_ACTIVE));

        try (Connection physicalConnection = h2ds.getConnection();
             JtaConnection logicalConnection = new JtaConnection(tm::getTransaction,
                                                                 tsr,
                                                                 true,
                                                                 null,
                                                                 physicalConnection,
                                                                 null,
                                                                 x -> tsr.putResource("xid", x),
                                                                 false)) {

            // Make sure everything is hooked up properly.
            assertThat(logicalConnection.delegate(), sameInstance(physicalConnection));

            // Trigger an Object method; make sure nothing blows up (in case we're using proxies).
            logicalConnection.toString();

            // Up until this point, the connection should not be enlisted. (delegate() and toString() must not cause
            // enlistment.)
            assertThat(logicalConnection.enlisted(), is(false));

            // (Calling enlisted() itself must not cause enlistment.)
            assertThat(logicalConnection.enlisted(), is(false));

            // Since it's not enlisted, it should be closeable (but it has not yet been closed).
            assertThat(logicalConnection.isCloseable(), is(true));
            assertThat(logicalConnection.isClosed(), is(false));

            // Trigger a harmless Connection-related method; make sure nothing blows up.
            logicalConnection.getHoldability();

            // Almost all Connection methods, including that one, will cause enlistment to happen.
            assertThat(logicalConnection.enlisted(), is(true));

            // Make sure the XAResource recorded the association.
            Xid xid = (Xid) this.tsr.getResource("xid");
            assertThat(ASSOCIATIONS.get(xid).branchState(), is(ACTIVE));

            // That means the Connection is no longer closeable.
            assertThat(logicalConnection.isCloseable(), is(false));

            // Ensure JDBC constructs' backlinks work correctly.
            try (Statement s = logicalConnection.createStatement()) {
                assertThat(s, notNullValue());
                assertThat(s.getConnection(), sameInstance(logicalConnection));
                try (ResultSet rs = s.executeQuery("SHOW TABLES")) {
                    assertThat(rs.getStatement(), sameInstance(s));
                }
            }

            // close() should "close" the logical connection, but not close the physical connection.
            logicalConnection.close();
            assertThat(logicalConnection.isClosed(), is(true));
            assertThat(physicalConnection.isClosed(), is(false));

            // Make sure a close() attempt was recorded.
            assertThat(logicalConnection.isClosePending(), is(true));

            // But "closing" should have disassociated the XAResource.
            assertThat(ASSOCIATIONS.get(xid).branchState(), is(IDLE));

            // What happens when we do re-enlisting behavior? First, we'd better appear closed since we called close()
            // above:
            assertThrows(SQLException.class, () -> logicalConnection.getHoldability());

            // Let's reenable closeability for this test, which is something users won't do:
            logicalConnection.setCloseable(true);
            assertThat(logicalConnection.isCloseable(), is(true));
            assertThat(logicalConnection.isClosePending(), is(false));
            assertThat(logicalConnection.isClosed(), is(false));

            // Assert after all of this we're still IDLE, because we don't remove the XAResource from the
            // TransactionSynchronizationRegistry, so the enlisted() method thinks we're already enlisted, so
            // getHoldability() skips enlistment, so everything stays as it was.
            assertThat(ASSOCIATIONS.get(xid).branchState(), is(IDLE));

            // Commit (we didn't actually do any work) AND DISASSOCIATE the transaction, which can only happen with a
            // call to TransactionManager.commit(), not just Transaction.commit().
            tm.commit();

            // Transaction is over; the connection should be closeable again.
            assertThat(logicalConnection.isCloseable(), is(true));

            // Transaction is over; make sure the XAResource removed the association.
            assertThat(ASSOCIATIONS.size(), is(0));

            // We should be able to actually close it early.  The auto-close should not fail, either.
            logicalConnection.close();
            assertThat(logicalConnection.isClosed(), is(true));
            assertThat(logicalConnection.delegate().isClosed(), is(true));
        }

        LOGGER.info("Ending testSpike()");
    }

    @Test
    final void testTimeout() throws InterruptedException, NotSupportedException, RollbackException, SystemException {
        LOGGER.info("Starting testTimeout()");

        tm.setTransactionTimeout(1); // 1 second; the minimum settable value (0 means "use the default" (!))
        tm.begin();

        // For this test, where transaction reaping is set to happen soon, it still won't happen in under 1000
        // milliseconds, so this assertion is OK unless the testing environment is completely pathological. For
        // real-world scenarios, you shouldn't assume anything about the initial status.
        assertThat(tm.getStatus(), is(Status.STATUS_ACTIVE));

        CountDownLatch latch = new CountDownLatch(1);
        Thread mainThread = Thread.currentThread();

        tsr.registerInterposedSynchronization(new Synchronization() {
                public void beforeCompletion() {

                }
                public void afterCompletion(int status) {
                    try {
                        assertThat(status, is(Status.STATUS_ROLLEDBACK));
                        assertThat(Thread.currentThread(), not(mainThread));
                    } finally {
                        latch.countDown();
                    }
                }
            });

        // Wait for the transaction to roll back on the reaper thread.  The transaction timeout is 1 second (see above);
        // this waits for 2 seconds max.  If this fails with an InterruptedException, which should be impossible, check
        // the logs for Narayana warning that the assertions in the synchronization above failed.
        latch.await(2000L, TimeUnit.MILLISECONDS);

        // In this case, we never issued a rollback ourselves and we never acquired a Transaction.  Here we show that
        // you can get a Transaction that is initially in a rolled back state.
        //
        // Verify there *was* a transaction but now it is rolled back, so is essentially useless other than as a
        // tombstone.
        Transaction t = tm.getTransaction();
        assertThat(t, notNullValue());
        assertThat(t.getStatus(), is(Status.STATUS_ROLLEDBACK));

        // Verify that all the other status accessors return the same thing.
        assertThat(tm.getStatus(), is(Status.STATUS_ROLLEDBACK));
        assertThat(tsr.getTransactionStatus(), is(Status.STATUS_ROLLEDBACK));

        // Verify that indeed you cannot enlist any XAResource in the transaction when it is in the rolled back state.
        assertThrows(IllegalStateException.class, () -> t.enlistResource(new NoOpXAResource()));

        // Verify that even though the current transaction is rolled back you can still roll it back again (no-op) and
        // disassociate it from the current thread.
        tm.rollback();
        assertThat(tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));
        assertThat(tsr.getTransactionStatus(), is(Status.STATUS_NO_TRANSACTION));

        // The Transaction itself remains in status Status.STATUS_ROLLEDBACK. Notably it never enters
        // Status.STATUS_NO_TRANSACTION.
        assertThat(t.getStatus(), is(Status.STATUS_ROLLEDBACK));

        assertThat(ASSOCIATIONS.size(), is(0));

        LOGGER.info("Ending testTimeout()");
    }

    @Test
    final void testBeginSuspendBeginCommitResumeCommit()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               InvalidTransactionException,
               NotSupportedException,
               RollbackException,
               SQLException,
               SystemException {
        LOGGER.info("Starting testBeginSuspendBeginCommitResumeCommit()");

        tm.begin();

        Transaction t = tm.getTransaction();
        assertThat(t.getStatus(), is(Status.STATUS_ACTIVE));

        Connection physicalConnection = h2ds.getConnection();
        JtaConnection logicalConnection =
            new JtaConnection(tm::getTransaction,
                              tsr,
                              true,
                              null,
                              physicalConnection,
                              true);

        assertThat(logicalConnection.enlisted(), is(true));
        assertThat(logicalConnection.delegate(), sameInstance(physicalConnection));

        // Suspend the current transaction.  It will stay in Status.STATUS_ACTIVE state, because suspension has no
        // effect on the actual state of the *Transaction*, only on the state of its association with the current
        // thread.
        Transaction s = tm.suspend();
        assertThat(s, sameInstance(t));
        assertThat(s.getStatus(), is(Status.STATUS_ACTIVE));

        // The TransactionManager will report that there is no transaction.
        assertThat(tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));

        assertThat(logicalConnection.isCloseable(), is(false)); // we're still enlisted in a suspended transaction

        logicalConnection.close(); // doesn't really close, but the caller thinks it did, which is what we want

        // We record that a close was pending.
        assertThat(logicalConnection.isClosePending(), is(true));

        // A pending close looks to the caller like a real one.
        assertThat(logicalConnection.isClosed(), is(true));

        // But it's not.
        assertThat(physicalConnection.isClosed(), is(false));

        tm.begin();
        t = tm.getTransaction();
        assertThat(t, not(s));
        assertThat(t.getStatus(), is(Status.STATUS_ACTIVE));
        assertThat(s.getStatus(), is(Status.STATUS_ACTIVE));
        assertThat(tm.getStatus(), is(Status.STATUS_ACTIVE));

        Connection physicalConnection2 = h2ds.getConnection();
        JtaConnection logicalConnection2 =
            new JtaConnection(tm::getTransaction,
                              tsr,
                              true,
                              null,
                              physicalConnection2,
                              true);

        assertThat(logicalConnection2.enlisted(), is(true));
        assertThat(logicalConnection2.delegate(), sameInstance(physicalConnection2));

        tm.commit();

        assertThat(t.getStatus(), is(Status.STATUS_COMMITTED));
        assertThat(tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));

        assertThat(logicalConnection2.enlisted(), is(false)); // we can call this because we haven't closed yet

        assertThat(logicalConnection2.isCloseable(), is(true));
        logicalConnection2.close();
        assertThat(logicalConnection2.isClosed(), is(true));
        assertThat(physicalConnection2.isClosed(), is(true));

        tm.resume(s);

        t = tm.getTransaction();
        assertThat(t, sameInstance(s));
        assertThat(t.getStatus(), is(Status.STATUS_ACTIVE));

        // The first logical connection still looks closed.
        assertThat(logicalConnection.isClosed(), is(true));

        // But it's not.
        assertThat(logicalConnection.isClosePending(), is(true));
        assertThat(physicalConnection.isClosed(), is(false));

        tm.commit();

        // Now it should be closed for real.
        assertThat(logicalConnection.isClosePending(), is(false));
        assertThat(logicalConnection.isClosed(), is(true));
        assertThat(logicalConnection.delegate().isClosed(), is(true));
        assertThat(physicalConnection.isClosed(), is(true));

        assertThat(logicalConnection2.isClosed(), is(true));
        assertThat(physicalConnection2.isClosed(), is(true));

        LOGGER.info("Ending testBeginSuspendBeginCommitResumeCommit()");
    }

}
