/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.concurrent.CountDownLatch;

import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.transaction.Status.STATUS_ACTIVE;
import static jakarta.transaction.Status.STATUS_NO_TRANSACTION;
import static jakarta.transaction.Status.STATUS_ROLLEDBACK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TestNarayanaBehavior {

    private static final JTAEnvironmentBean jtaEnvironmentBean = new JTAEnvironmentBean();

    private TransactionManager tm;

    private TransactionSynchronizationRegistry tsr;

    private TestNarayanaBehavior() {
        super();
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
    final void rollback() throws SystemException {
        if (this.tm.getStatus() != STATUS_NO_TRANSACTION) {
            this.tm.rollback();
        }
    }

    @AfterEach
    final void resetTransactionTimeout() throws SystemException {
        this.tm.setTransactionTimeout(0); // set back to the default
    }

    @Test
    final void testCanCallGetResourceInAfterCompletion() throws NotSupportedException, SystemException {
        tm.begin();
        boolean[] afterCompletionCalled = new boolean[] { false };
        final Synchronization s = new Synchronization() {
                @Override
                public final void beforeCompletion() {}
                @Override
                public final void afterCompletion(final int status) {
                    try {
                        // (Kind of interesting you have access to the TSR after the transaction is over.)
                        assertThat(tsr.getResource("MARCO"), is("POLO"));
                    } finally {
                        afterCompletionCalled[0] = true;
                    }
                }
            };
        tsr.registerInterposedSynchronization(s);
        tm.setRollbackOnly();
        // (Kind of interesting you can put resources when the transaction is marked for rollback.)
        tsr.putResource("MARCO", "POLO");
        tm.rollback();
        assertThat(afterCompletionCalled[0], is(true));
        assertThrows(IllegalStateException.class, () -> tsr.getResource("MARCO"));
    }
    
    @Test
    final void testCanCallGetResourceAfterSettingRollbackOnly() throws NotSupportedException, SystemException {
        tm.begin();
        tsr.putResource("MARCO", "POLO");
        tm.setRollbackOnly();
        assertThat(tsr.getResource("MARCO"), is("POLO"));
    }

    @Test
    final void testCanCallPutResourceAfterSettingRollbackOnly() throws NotSupportedException, SystemException {
        tm.begin();
        tm.setRollbackOnly();
        // Interesting. You can put a resource after rollback only, but, for example, you can't enlist a resource. Nor
        // can you register a Synchronization. But you could, laboriously, accomplish both those things using this
        // facility. So I'm not sure why the probibitions that exist are in place.
        tsr.putResource("MARCO", "POLO");
        assertThat(tsr.getResource("MARCO"), is("POLO"));
    }

    @Test
    final void testCannotRegisterInterposedSynchronizationAfterSettingRollbackOnly()
        throws NotSupportedException, SystemException {
        tm.begin();
        final Synchronization s = new Synchronization() {
                @Override
                public final void beforeCompletion() {}
                @Override
                public final void afterCompletion(final int status) {}
            };
        tm.setRollbackOnly();
        // If you set the rollback only status first, as we just did, then you cannot register a synchronization; see
        // below. It is not *immediately* obvious why this is prohibited by the specification.  See
        // https://www.eclipse.org/lists/jta-dev/msg00323.html.
        assertThrows(IllegalStateException.class, () -> this.tsr.registerInterposedSynchronization(s));
    }

    @Test
    final void testCannotRegisterSynchronizationAfterSettingRollbackOnly() throws NotSupportedException, SystemException {
        tm.begin();
        final Transaction t = tm.getTransaction();
        final Synchronization s = new Synchronization() {
                @Override
                public final void beforeCompletion() {}
                @Override
                public final void afterCompletion(final int status) {}
            };
        tm.setRollbackOnly();
        // If you set the rollback only status first, as we just did, then you cannot register a synchronization; see
        // below. It is not *immediately* obvious why this is prohibited by the specification.  See
        // https://www.eclipse.org/lists/jta-dev/msg00323.html.
        assertThrows(RollbackException.class, () -> t.registerSynchronization(s));
    }

    @Test
    final void testCannotEnlistResourceAfterSettingRollbackOnly()
        throws NotSupportedException, SystemException {
        tm.begin();
        tm.setRollbackOnly();
        assertThrows(RollbackException.class, () -> tm.getTransaction().enlistResource(new NoOpXAResource()));
    }

    @Test
    final void testTimeout() throws InterruptedException, NotSupportedException, SystemException {
        tm.setTransactionTimeout(1); // 1 second; the minimum settable value (0 means "use the default" (!))
        tm.begin();

        // For this test, where transaction reaping is set to happen soon, it still won't happen in under 1000
        // milliseconds, so this assertion is OK unless the testing environment is completely pathological. For
        // real-world scenarios, you shouldn't assume anything about the initial status.
        assertThat(tm.getStatus(), is(STATUS_ACTIVE));

        CountDownLatch latch = new CountDownLatch(1);
        Thread mainThread = currentThread();
        boolean[] afterCompletionCalled = new boolean[] { false };

        tsr.registerInterposedSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {
                    // Perhaps surprisingly you can put things in the TSR even while the two-phase commit process is
                    // starting.
                    tsr.putResource("MARCO", "POLO");
                }
                @Override
                public void afterCompletion(int status) {
                    try {
                        // Perhaps surprisingly, you can get things out of the TSR even after the transaction is
                        // disassociated and the two-phase commit process has committed. Note that this means among
                        // other things that the specification's (strange) prohibition on registering synchronizations
                        // when a transaction is in the marked-for-rollback state can be easily bypassed, in which case
                        // I wonder why it exists?
                        assertThat(tsr.getResource("MARCO"), is("POLO"));
                        assertThat(status, is(STATUS_ROLLEDBACK));
                        assertThat(currentThread(), not(mainThread));
                    } finally {
                        afterCompletionCalled[0] = true;
                        latch.countDown();
                    }
                }
            });

        // Wait for the transaction to roll back on the reaper thread, not the main thread. The transaction timeout is 1
        // second (see above); here we wait for 2 seconds max. If this fails with an InterruptedException, which should be
        // impossible, check the logs for Narayana warning that the assertions in the synchronization above failed.
        latch.await(2, SECONDS);

        // Make sure the Synchronization fired.
        assertThat(afterCompletionCalled[0], is(Boolean.TRUE));

        // In this case, we never issued a rollback ourselves and we never acquired a Transaction. Here we show that you
        // can get a Transaction that, perhaps somewhat surprisingly, but correctly, is initially in a rolled back
        // state.
        //
        // Verify there *was* a transaction but now it is rolled back, so is essentially useless other than as a
        // tombstone.
        Transaction t = tm.getTransaction();
        assertThat(t, notNullValue());
        assertThat(t.getStatus(), is(STATUS_ROLLEDBACK));

        // Verify that all the other status accessors return the same thing.
        assertThat(tm.getStatus(), is(STATUS_ROLLEDBACK));
        assertThat(tsr.getTransactionStatus(), is(STATUS_ROLLEDBACK));

        // Verify that indeed you cannot enlist any XAResource in the transaction when it is in the rolled back state.
        assertThrows(IllegalStateException.class, () -> t.enlistResource(new NoOpXAResource()));

        // Verify that even though the current transaction is rolled back you can still roll it back again (no-op) and
        // disassociate it from the current thread.
        tm.rollback();
        assertThat(tm.getStatus(), is(STATUS_NO_TRANSACTION));
        assertThat(tsr.getTransactionStatus(), is(STATUS_NO_TRANSACTION));

        // The Transaction itself remains in status STATUS_ROLLEDBACK. Notably it never enters STATUS_NO_TRANSACTION.
        assertThat(t.getStatus(), is(STATUS_ROLLEDBACK));
    }

}
