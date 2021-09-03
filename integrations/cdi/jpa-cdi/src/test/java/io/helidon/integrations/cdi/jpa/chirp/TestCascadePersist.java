/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.jpa.chirp;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

import javax.annotation.sql.DataSourceDefinition;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
import javax.persistence.SynchronizationType;
import javax.persistence.TransactionRequiredException;
import javax.sql.DataSource;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ApplicationScoped
@DataSourceDefinition(
    name = "chirp",
    className = "org.h2.jdbcx.JdbcDataSource",
    url = "jdbc:h2:mem:TestCascadePersist;INIT=SET TRACE_LEVEL_FILE=4\\;SET DB_CLOSE_DELAY=-1",
    serverName = "",
    properties = {
        "user=sa"
    }
)
class TestCascadePersist {

    private SeContainer cdiContainer;

    @Inject
    private TransactionManager transactionManager;

    @PersistenceContext(
        type = PersistenceContextType.TRANSACTION,
        synchronization = SynchronizationType.SYNCHRONIZED,
        unitName = "chirp"
    )
    private EntityManager em;


    /*
     * Constructors.
     */


    TestCascadePersist() {
        super();
    }


    /*
     * Setup and teardown methods.
     */


    @BeforeEach
    void startCdiContainerAndRunDDL() throws SQLException {
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance()
            .addBeanClasses(this.getClass());
        assertNotNull(initializer);
        this.cdiContainer = initializer.initialize();
        final DataSource ds = this.cdiContainer.select(DataSource.class).get();
        assertNotNull(ds);
        try (final Connection connection = ds.getConnection();
             final Statement statement = connection.createStatement()) {
            assertNotNull(statement);
            statement.executeUpdate("RUNSCRIPT FROM 'classpath:chirp.ddl'");
        }
    }

    @AfterEach
    void shutDownCdiContainer() {
        if (this.cdiContainer != null) {
            this.cdiContainer.close();
        }
    }


    /*
     * Support methods.
     */


    TestCascadePersist self() {
        return this.cdiContainer.select(TestCascadePersist.class).get();
    }

    EntityManager getEntityManager() {
        return this.em;
    }

    TransactionManager getTransactionManager() {
        return this.transactionManager;
    }

    private void onShutdown(@Observes @BeforeDestroyed(ApplicationScoped.class) final Object event,
                            final TransactionManager tm) throws SystemException {
        // If an assertion fails, or some other error happens in the
        // CDI container, there may be a current transaction that has
        // neither been committed nor rolled back.  Because the
        // Narayana transaction engine is fundamentally static, this
        // means that a transaction affiliation with the main thread
        // may "leak" into another JUnit test (since JUnit, by
        // default, forks once, and then runs all tests in the same
        // JVM).  CDI, thankfully, will fire an event for the
        // application context shutting down, even in the case of
        // errors.
        if (tm.getStatus() != Status.STATUS_NO_TRANSACTION) {
            tm.rollback();
        }
    }


    /*
     * Test methods.
     */


    @Test
    void testCascadePersist()
        throws HeuristicMixedException,
               HeuristicRollbackException,
               InterruptedException,
               NotSupportedException,
               RollbackException,
               SQLException,
               SystemException
    {

        // Get a CDI contextual reference to this test instance.  It
        // is important to use "self" in this test instead of "this".
        final TestCascadePersist self = self();
        assertNotNull(self);

        // Get the EntityManager that is synchronized with and scoped
        // to a JTA transaction.
        final EntityManager em = self.getEntityManager();
        assertNotNull(em);
        assertTrue(em.isOpen());

        // We haven't started any kind of transaction yet and we
        // aren't testing anything using
        // the @javax.transaction.Transactional annotation so there is
        // no transaction in effect so the EntityManager cannot be
        // joined to one.
        assertFalse(em.isJoinedToTransaction());

        // Get the TransactionManager that normally is behind the
        // scenes and use it to start a Transaction.  This simulates
        // entering a method annotated
        // with @Transactional(TxType.REQUIRES_NEW) or similar.
        final TransactionManager tm = self.getTransactionManager();
        assertNotNull(tm);
        tm.begin();
        assertEquals(Status.STATUS_ACTIVE, tm.getStatus());

        // Now magically our EntityManager should be joined to it.
        assertTrue(em.isJoinedToTransaction());

        // Create an author but don't persist him explicitly.
        Author author = new Author("Abraham Lincoln");

        // No trip to the database has happened yet, so the author's
        // identifier isn't set yet.
        assertNull(author.getId());

        // Set up a blog for that Author.
        Microblog blog = new Microblog(author, "Gettysburg Address Draft 1");

        // Persist the blog.  The Author should be persisted too.
        em.persist(blog);
        assertTrue(em.contains(blog));
        assertTrue(em.contains(author));

        // Commit the transaction.  Because we're relying on the
        // default flush mode, this will cause a flush to the
        // database, which, in turn, will result in identifier
        // generation.
        tm.commit();
        assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
        assertEquals(Integer.valueOf(1), author.getId());
        assertEquals(Integer.valueOf(1), blog.getId());

        // We're no longer in a transaction.
        assertFalse(em.isJoinedToTransaction());

        // The persistence context should be cleared.
        assertFalse(em.contains(blog));
        assertFalse(em.contains(author));

        // Let's check the database directly.
        final DataSource ds = this.cdiContainer.select(DataSource.class).get();
        assertNotNull(ds);
        try (final Connection connection = ds.getConnection();
             final Statement statement = connection.createStatement()) {
            assertNotNull(statement);
            ResultSet rs = statement.executeQuery("SELECT COUNT(1) FROM MICROBLOG");
            assertNotNull(rs);
            try {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            } finally {
                rs.close();
            }
            rs = statement.executeQuery("SELECT COUNT(1) FROM AUTHOR");
            assertNotNull(rs);
            try {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            } finally {
                rs.close();
            }
        }

        // Start a new transaction.
        tm.begin();

        assertFalse(em.contains(blog));
        final Microblog newBlog = em.find(Microblog.class, Integer.valueOf(1));
        assertNotNull(newBlog);
        assertTrue(em.contains(newBlog));

        assertEquals(blog.getId(), newBlog.getId());
        blog = newBlog;

        // Now let's have our author write some stuff.
        final Chirp chirp1 = new Chirp(blog, "Four score and seven years ago");
        final Chirp chirp2 = new Chirp(blog, "our fathers brought forth on this continent,");
        final Chirp chirp3 = new Chirp(blog, "a new nation, conceived in Liberty, "
                                       + "and dedicated to the proposition that all men are created "
                                       + "equal. Now we are engaged in a great civil war, testing "
                                       + "whether that nation, or any nation so conceived and so "
                                       + "dedicated, can long endure.");
        blog.addChirp(chirp1);
        assertSame(blog, chirp1.getMicroblog());
        blog.addChirp(chirp2);
        assertSame(blog, chirp2.getMicroblog());
        blog.addChirp(chirp3);
        assertSame(blog, chirp3.getMicroblog());

        // Commit the transaction.  The changes should be propagated.
        // However, this will fail, because the third chirp above is
        // (deliberately) too long.  The transaction should roll back.
        try {
            tm.commit();
            fail("Commit was able to happen");
        } catch (final RollbackException expected) {

        }

        // Now the question is: were any chirps written?  They
        // should not have been written (i.e. the rollback should have
        // functioned properly.  Let's make sure.
        try (final Connection connection = ds.getConnection();
             final Statement statement = connection.createStatement()) {
            assertNotNull(statement);
            ResultSet rs = statement.executeQuery("SELECT COUNT(1) FROM CHIRP");
            assertNotNull(rs);
            try {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            } finally {
                rs.close();
            }
        }

    }

}
