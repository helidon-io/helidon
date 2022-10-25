/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceContextType;
import jakarta.persistence.SynchronizationType;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@ApplicationScoped
@DataSourceDefinition(
    name = "chirp",
    className = "org.h2.jdbcx.JdbcDataSource",
    url = "jdbc:h2:mem:TestCascadePersist;MODE=LEGACY;INIT=SET TRACE_LEVEL_FILE=4\\;SET DB_CLOSE_DELAY=-1",
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
        assertThat(initializer, notNullValue());
        this.cdiContainer = initializer.initialize();
        final DataSource ds = this.cdiContainer.select(DataSource.class).get();
        assertThat(ds, notNullValue());
        try (final Connection connection = ds.getConnection();
             final Statement statement = connection.createStatement()) {
            assertThat(statement, notNullValue());
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
        assertThat(self, notNullValue());

        // Get the EntityManager that is synchronized with and scoped
        // to a JTA transaction.
        final EntityManager em = self.getEntityManager();
        assertThat(em, notNullValue());
        assertThat(em.isOpen(), is(true));

        // We haven't started any kind of transaction yet and we
        // aren't testing anything using
        // the @jakarta.transaction.Transactional annotation so there is
        // no transaction in effect so the EntityManager cannot be
        // joined to one.
        assertThat(em.isJoinedToTransaction(), is(false));

        // Get the TransactionManager that normally is behind the
        // scenes and use it to start a Transaction.  This simulates
        // entering a method annotated
        // with @Transactional(TxType.REQUIRES_NEW) or similar.
        final TransactionManager tm = self.getTransactionManager();
        assertThat(tm, notNullValue());
        tm.begin();
        assertThat(tm.getStatus(), is(Status.STATUS_ACTIVE));

        // Now magically our EntityManager should be joined to it.
        assertThat(em.isJoinedToTransaction(), is(true));

        // Create an author but don't persist him explicitly.
        Author author = new Author("Abraham Lincoln");

        // No trip to the database has happened yet, so the author's
        // identifier isn't set yet.
        assertThat(author.getId(), nullValue());

        // Set up a blog for that Author.
        Microblog blog = new Microblog(author, "Gettysburg Address Draft 1");

        // Persist the blog.  The Author should be persisted too.
        em.persist(blog);
        assertThat(em.contains(blog), is(true));
        assertThat(em.contains(author), is(true));

        // Commit the transaction.  Because we're relying on the
        // default flush mode, this will cause a flush to the
        // database, which, in turn, will result in identifier
        // generation.
        tm.commit();
        assertThat(tm.getStatus(), is(Status.STATUS_NO_TRANSACTION));
        assertThat(author.getId(), is(1));
        assertThat(blog.getId(), is(1));

        // We're no longer in a transaction.
        assertThat(em.isJoinedToTransaction(), is(false));

        // The persistence context should be cleared.
        assertThat(em.contains(blog), is(false));
        assertThat(em.contains(author), is(false));

        // Let's check the database directly.
        final DataSource ds = this.cdiContainer.select(DataSource.class).get();
        assertThat(ds, notNullValue());
        try (final Connection connection = ds.getConnection();
             final Statement statement = connection.createStatement()) {
            assertThat(statement, notNullValue());
            ResultSet rs = statement.executeQuery("SELECT COUNT(1) FROM MICROBLOG");
            assertThat(rs, notNullValue());
            try {
                assertThat(rs.next(), is(true));
                assertThat(rs.getInt(1), is(1));
            } finally {
                rs.close();
            }
            rs = statement.executeQuery("SELECT COUNT(1) FROM AUTHOR");
            assertThat(rs, notNullValue());
            try {
                assertThat(rs.next(), is(true));
                assertThat(rs.getInt(1), is(1));
            } finally {
                rs.close();
            }
        }

        // Start a new transaction.
        tm.begin();

        assertThat(em.contains(blog), is(false));
        final Microblog newBlog = em.find(Microblog.class, Integer.valueOf(1));
        assertThat(newBlog, notNullValue());
        assertThat(em.contains(newBlog), is(true));

        assertThat(newBlog.getId(), is(blog.getId()));
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
        assertThat(chirp1.getMicroblog(), sameInstance(blog));
        blog.addChirp(chirp2);
        assertThat(chirp2.getMicroblog(), sameInstance(blog));
        blog.addChirp(chirp3);
        assertThat(chirp3.getMicroblog(), sameInstance(blog));

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
            assertThat(statement, notNullValue());
            ResultSet rs = statement.executeQuery("SELECT COUNT(1) FROM CHIRP");
            assertThat(rs, notNullValue());
            try {
                assertThat(rs.next(), is(true));
                assertThat(rs.getInt(1), is(0));
            } finally {
                rs.close();
            }
        }

    }

}
