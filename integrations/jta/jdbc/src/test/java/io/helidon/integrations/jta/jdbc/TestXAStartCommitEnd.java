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

import java.sql.SQLException;
import java.util.logging.Logger;

import javax.transaction.xa.XAResource;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static javax.transaction.xa.XAResource.TMSUCCESS;
import static javax.transaction.xa.XAResource.TMFAIL;
import static jakarta.transaction.Status.STATUS_COMMITTED;
import static jakarta.transaction.Status.STATUS_NO_TRANSACTION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TestXAStartCommitEnd {

    private static final Logger LOGGER = Logger.getLogger(TestXAStartCommitEnd.class.getName());

    private JdbcDataSource h2ds;

    private TransactionManager tm;

    private TestXAStartCommitEnd() throws SQLException {
        super();
    }

    @BeforeEach
    final void initializeH2DataSource() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test");
        ds.setUser("sa");
        ds.setPassword("sa");
        this.h2ds = ds;
    }

    @BeforeEach
    final void initializeTransactionManager() throws SystemException {
        this.tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
        this.tm.setTransactionTimeout(20 * 60); // 20 minutes for debugging
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

    @Test
    final void testEnlistXARTwice()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SystemException {
        LOGGER.info("Starting testEnlistXARTwice()");
        tm.begin();
        Transaction t = tm.getTransaction();
        XAResource xaResource = new NoOpXAResource();
        t.enlistResource(xaResource);

        // This is actually interesting.  Narayana checks to see if an
        // XAResource that is equal (using Object#equals(Object)) to
        // the supplied XAResource exists in its guts.  This is
        // effectively testing to see if the *resource* has been seen
        // before.
        //
        // If one does, then obviously it is not reenlisted and this
        // second enlistment request is effectively ignored.  In our
        // case, this is all true, so this second enlistment is a
        // no-op.
        //
        // If one does not, then TransactionImple#isNewRM(XAResource)
        // is called. It's not a boolean-returning method. It loops
        // over all known XAResources and calls their
        // isSameRM(XAResource) method. This is effectively testing to
        // see if the *system to which the resource is connected* (the
        // resource manager for which the XA resource is an adapter)
        // has been seen before.
        //
        // If it finds one, then the "first registered RM instance
        // [the existing one, not the new one] will be used to drive
        // the transaction completion. We add it [the new one, not the
        // existing one] to the duplicateResource list so we can
        // delist it correctly later though."  This means more or less
        // whatever you do there will only be one XAResource that
        // deals with the prepare/commit/rollback cycle, but there
        // could potentially be several that take part in the
        // start/end cycle.
        //
        // In this second case described above, start would be called
        // this time with TMJOIN. See also:
        // https://github.com/jbosstm/narayana/blob/c5f02d07edb34964b64341974ab689ea44536603/ArjunaJTA/jdbc/classes/com/arjuna/ats/internal/jdbc/drivers/modifiers/ConnectionModifier.java#L82-L89
        t.enlistResource(xaResource);
        tm.commit();
        assertThat(t.getStatus(), is(STATUS_COMMITTED));
        assertThat(tm.getStatus(), is(STATUS_NO_TRANSACTION));
        LOGGER.info("Ending testEnlistXARTwice()");
    }

    @Test
    final void testTransactionCommitInsteadOfTransactionManagerCommitBlocksThings()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SystemException {
        LOGGER.info("Starting testTransactionCommitInsteadOfTransactionManagerCommitBlocksThings()");

        tm.begin();
        Transaction t = tm.getTransaction();
        t.commit();
        assertThat(t.getStatus(), is(STATUS_COMMITTED));
        assertThat(tm.getStatus(), is(STATUS_COMMITTED));
        assertThrows(NotSupportedException.class, tm::begin);
        tm.commit();
        assertThat(t.getStatus(), is(STATUS_COMMITTED));
        assertThat(tm.getStatus(), is(STATUS_NO_TRANSACTION));

        LOGGER.info("Ending testTransactionCommitInsteadOfTransactionManagerCommitBlocksThings()");
    }

    @Test
    final void testBeginCommitEnlistAndDelistWithTMSUCCESS()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SystemException {
        LOGGER.info("Starting testBeginCommitEnlistAndDelistWithTMSUCCESS()");

        XAResource nop = new NoOpXAResource();
        tm.begin();
        Transaction t = tm.getTransaction();

        // https://jakarta.ee/specifications/transactions/2.0/jakarta-transactions-spec-2.0.html#resource-enlistment
        //
        // "The enlistResource request results in the transaction
        // manager informing the resource manager to start associating
        // the transaction with the work performed through the
        // corresponding resource—by invoking the XAResource.start
        // method [on the same thread]."  So this causes
        // XAResource#start to be invoked.
        t.enlistResource(nop);

        // Note that (at least with TMSUCCESS) explicit delisting does
        // NOT prevent the XAResource from being enrolled!
        assertThat(t.delistResource(nop, TMSUCCESS), is(true));
        tm.commit();

        LOGGER.info("Ending testBeginCommitEnlistAndDelistWithTMSUCCESS()");
    }

    @Test
    final void testBeginCommitDelistWithTMSUCCESS()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SystemException {
        LOGGER.info("Starting testBeginCommitDelistWithTMSUCCESS()");

        XAResource nop = new NoOpXAResource();
        tm.begin();
        Transaction t = tm.getTransaction();
        // Try delisting before ever enlisting; should be false
        assertThat(t.delistResource(nop, TMSUCCESS), is(false));
        tm.commit();

        LOGGER.info("Ending testBeginCommitDelistWithTMSUCCESS()");
    }

    @Test
    final void testBeginCommitEnlistAndDelistWithTMFAIL()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SystemException {
        LOGGER.info("Starting testBeginCommitEnlistAndDelistWithTMFAIL()");

        XAResource nop = new NoOpXAResource();
        tm.begin();
        Transaction t = tm.getTransaction();
        t.enlistResource(nop);
        // Note that (at least with TMFAIL) explicit delisting does
        // NOT prevent the XAResource from being enrolled!
        assertThat(t.delistResource(nop, TMFAIL), is(true));
        tm.commit();

        LOGGER.info("Ending testBeginCommitEnlistAndDelistWithTMFAIL()");
    }

    @Test
    final void testBeginCommitWithXAResourceThatEnlistsItselfThenRegistersAsASynchronization()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SystemException {
        LOGGER.info("Starting testBeginCommitWithXAResourceThatEnlistsItselfThenRegistersAsASynchronization()");

        NoOpXAResource noOpXAResource = new NoOpXAResource();
        tm.begin();
        Transaction t = tm.getTransaction();
        t.enlistResource(noOpXAResource);
        t.registerSynchronization(noOpXAResource);

        // There is no need, by spec, to delist a resource explicitly.
        // assertThat(t.delistResource(noOpXAResource, TMSUCCESS), is(true));

        // Don't call Transaction#commit(); that does not perform
        // disassociation.  Only TransactionManager#commit() does
        // that.  It is also obliged by spec to delist resources.
        tm.commit();

        // You can't delist a resource when the transaction is no
        // longer active.
        assertThrows(IllegalStateException.class, () -> t.delistResource(noOpXAResource, TMSUCCESS));

        LOGGER.info("Ending testBeginCommitWithXAResourceThatEnlistsItselfThenRegistersAsASynchronization()");
    }

    @Test
    final void testBeginCommitWithSynchronizationThatEnlistsItselfDuringBeforeCompletion()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SystemException {
        LOGGER.info("Starting testBeginCommitWithSynchronizationThatEnlistsItselfDuringBeforeCompletion()");

        NoOpXAResource noOpXAResource = new NoOpXAResource(this.tm);
        tm.begin();
        Transaction t = tm.getTransaction();
        t.registerSynchronization(noOpXAResource);
        tm.commit();

        LOGGER.info("Ending testBeginCommitWithSynchronizationThatEnlistsItselfDuringBeforeCompletion()");
    }

}
