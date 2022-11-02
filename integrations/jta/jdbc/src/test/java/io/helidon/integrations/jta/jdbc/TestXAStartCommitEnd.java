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
        // DriverManager.registerDriver(new com.arjuna.ats.jdbc.TransactionalDriver());
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

    @Test
    final void testUnenlisted() throws NotSupportedException, SystemException, SQLException {
        LOGGER.info("Starting testUnenlisted()");
        
        // The default isolation level for H2 connections is
        // TRANSACTION_READ_COMMITTED.  See for yourself:
        // https://github.com/h2database/h2database/blob/version-2.1.212/h2/src/main/org/h2/engine/SessionLocal.java#L223
        try (Connection h2Connection = this.h2ds.getConnection()) {
            assertThat(h2Connection.getTransactionIsolation(), is(TRANSACTION_READ_COMMITTED));
        }

        // We haven't done anything funky with Narayana. If you don't
        // configure it in any special way, then the default isolation
        // level it uses for connections it manages is
        // TRANSACTION_SERIALIZABLE.  See for yourself:
        // https://github.com/jbosstm/narayana/blob/5.12.0.Final/ArjunaJTA/jdbc/classes/com/arjuna/ats/jdbc/common/JDBCEnvironmentBean.java#L36
        JDBCEnvironmentBean jdbcEnvironmentBean = getJDBCEnvironmentBean();
        assertThat(jdbcEnvironmentBean.getIsolationLevel(), is(TRANSACTION_SERIALIZABLE));

        // Create a new Narayana "ConnectionImple", which is a
        // *logical* Connection implementation that knows how to
        // delegate operations to a *physical* Connection returned by
        // an XAConnection implementation.
        //
        // The first parameter, dbName, is seemingly ignored because
        // here we are supplying an XADataSource directly.
        Connection logicalConnection = new ConnectionImple("IGNORED", "sa", "sa", null, new LocalXADataSource(this.h2ds));

        // The isolation level of the Naryana-implemented logical
        // connection is not necessarily the same as the connection it
        // wraps.
        assertThat(logicalConnection.getTransactionIsolation(), is(jdbcEnvironmentBean.getIsolationLevel()));

        this.tm.begin();

        // Hmm; somewhat oddly you can close this connection while a
        // transaction is in process.  I guess it's not enlisted yet,
        // so yeah, this should be legal.
        logicalConnection.close();
        assertThat(logicalConnection.isClosed(), is(true));

        // End the transaction. Always use the TransactionManager to
        // do this, rather than, say, tm.getTransaction().rollback(),
        // because the state machine seems to be odd; see
        // https://groups.google.com/g/narayana-users/c/eYVUmhE9QZg.
        this.tm.rollback();
        assertThat(this.tm.getStatus(), is(STATUS_NO_TRANSACTION));

        LOGGER.info("Ending testUnenlisted()");
    }

    @Test
    final void testEnlistedCloseBeforeCommit()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               NotSupportedException,
               RollbackException,
               SystemException,
               SQLException {
        LOGGER.info("Started testEnlistedCloseBeforeCommit()");
        
        this.tm.begin();

        Connection logicalConnection;
        try (ConnectionImple connectionImple = new ConnectionImple("IGNORED", "sa", "sa", null, new LocalXADataSource(this.h2ds));
             Statement s = connectionImple.createStatement();
             ResultSet rs = s.executeQuery("SHOW TABLES")) {
            logicalConnection = connectionImple;
            JdbcConnection physicalConnection = connectionImple.unwrap(JdbcConnection.class);
            assertThat(physicalConnection, is(not(nullValue())));
            assertThat(rs.next(), is(false));
        }

        // Interesting. close() was called (by the try-resources block
        // above) on a ConnectionImple that was enlisted in a
        // transaction by virtue of the createStatement() call (one of
        // several possible calls that will cause enlistment to
        // happen).
        //
        // Narayana tries to be helpful. It determines that H2 in
        // particular is one of several databases for which a
        // com.arjuna.ats.internal.jdbc.drivers.modifiers.IsSameRMModifier
        // should be installed
        // (https://github.com/jbosstm/narayana/blob/5.12.0.Final/ArjunaJTA/jdbc/classes/com/arjuna/ats/internal/jdbc/drivers/modifiers/list.java#L38-L49).
        // This modifier also happens to return true from its
        // supportsMultipleConnections() method ("Does their [sic]
        // JDBC driver support multiple connections in a single
        // transaction?")
        // (https://github.com/jbosstm/narayana/blob/5.12.0.Final/ArjunaJTA/jdbc/classes/com/arjuna/ats/internal/jdbc/drivers/modifiers/IsSameRMModifier.java#L68-L72).
        //
        // When this return value is true, then when an operation
        // (like createStatement()) is called on the ConnectionImple
        // that requires enlistment in a transaction, a
        // ConnectionSynchronization is registered to run at
        // afterCompletion(int) time that will call closeImpl() on the
        // ConnectionImple when the transaction is over
        // (https://github.com/jbosstm/narayana/blob/5.12.0.Final/ArjunaJTA/jdbc/classes/com/arjuna/ats/internal/jdbc/ConnectionImple.java#L989-L998).
        // 
        // As part of its registration, it bumps the "use count" of
        // the ConnectionImple from 1 to 2
        // (https://github.com/jbosstm/narayana/blob/5.12.0.Final/ArjunaJTA/jdbc/classes/com/arjuna/ats/internal/jdbc/ConnectionSynchronization.java#L51).
        //
        // This all means that at close() time *above*, the use count
        // will be decremented from 2 to 1, not from 1 to 0, when
        // closeImpl() runs (see
        // https://github.com/jbosstm/narayana/blob/5.12.0.Final/ArjunaJTA/jdbc/classes/com/arjuna/ats/internal/jdbc/ConnectionImple.java#L352).
        //
        // THAT means the only place where close() is actually called
        // on the Connection that the ConnectionImple wraps is never
        // hit UNTIL the ConnectionSynchronization runs at
        // afterCompletion() time, whereupon the use count *will* get
        // decremented to 0.  (More specifically, if anything anywhere
        // manages to bump the use count from 1 to 0, actual closing
        // becomes possible.  This is, as you might expect, very
        // fragile.)
        //
        // (Note that if you call connectionImple.close() "by hand" in
        // the try-with-resources block above, the use count will drop
        // from 2 to 1, and then when the try-with-resources block
        // calls close() *again*, the use count will drop from 1 to 0,
        // and the following assertion will not hold.)
        //
        // So for most popular databases, calling close() when the
        // connection is enlisted just delists the connection and does
        // nothing else.
        //
        // THEREFORE, logicalConnection#isClosed() must return false.
        assertThat(logicalConnection.isClosed(), is(false));

        this.tm.commit();

        // NOW our connection should report closure:
        assertThat(logicalConnection.isClosed(), is(true));

        LOGGER.info("Ended testEnlistedCloseBeforeCommit()");
    }

>>>>>>> f83ff2dff (Squashable commit; initial work)
}
