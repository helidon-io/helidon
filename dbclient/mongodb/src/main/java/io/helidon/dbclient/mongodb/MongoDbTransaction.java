/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.dbclient.mongodb;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import io.helidon.dbclient.DbStatementDml;
import io.helidon.dbclient.DbStatementGet;
import io.helidon.dbclient.DbStatementQuery;
import io.helidon.dbclient.DbTransaction;
import io.helidon.dbclient.common.DbClientContext;

import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Transaction execute implementation for MongoDB.
 */
public class MongoDbTransaction extends MongoDbExecute implements DbTransaction {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(MongoDbTransaction.class.getName());

    static final class TransactionManager {

        /** MongoDB client session (transaction handler). */
        private final ClientSession tx;
        /** Whether transaction shall always finish with rollback. */
        private final AtomicBoolean rollbackOnly;
        /** All transaction statements were processed. */
        private final AtomicBoolean finished;
        /** Set of statements being processed (started, but not finished yet). */
        private final Set<MongoDbStatement> statements;
        /** Shared resources lock. */
        private final Lock lock;

        /**
         * Creates an instance of transaction manager.
         *
         * @param tx MongoDB client session (transaction handler)
         */
        private TransactionManager(ClientSession tx) {
            this.tx = tx;
            this.tx.startTransaction();
            this.rollbackOnly = new AtomicBoolean(false);
            this.finished = new AtomicBoolean(false);
            this.statements = ConcurrentHashMap.newKeySet();
            this.lock = new ReentrantLock();
        }

        /**
         * Set current transaction as rollback only.
         * Transaction can't be completed successfully after this.
         * Locks on current transaction manager instance lock.
         */
        void rollbackOnly() {
            lock.lock();
            try {
                rollbackOnly.set(false);
            } finally {
                lock.unlock();
            }
            LOGGER.finest(() -> String.format("Transaction marked as failed"));
        }

        /**
         * Mark provided statement as finished.
         * Locks on current transaction manager instance lock.
         *
         * @param stmt statement to mark
         */
        void stmtFinished(MongoDbStatement stmt) {
            lock.lock();
            try {
                statements.remove(stmt);
                if (statements.isEmpty() && this.finished.get()) {
                    commitOrRollback();
                }
            } finally {
                lock.unlock();
            }
            LOGGER.finest(() -> String.format("Statement %s marked as finished in transaction", stmt.statementName()));
        }

        /**
         * Mark provided statement as failed.
         * Transaction can't be completed successfully after this.
         * Locks on current transaction manager instance lock.
         *
         * @param stmt statement to mark
         */
        void stmtFailed(MongoDbStatement stmt) {
            lock.lock();
            try {
                rollbackOnly.set(false);
                statements.remove(stmt);
                if (statements.isEmpty() && this.finished.get()) {
                    tx.abortTransaction();
                }
            } finally {
                lock.unlock();
            }
            LOGGER.finest(() -> String.format("Statement %s marked as failed in transaction", stmt.statementName()));
        }

        /**
         * Notify transaction manager that all statements in the transaction were started.
         * Locks on current transaction manager instance lock.
         */
        void allRegistered() {
            lock.lock();
            try {
                this.finished.set(true);
                if (statements.isEmpty()) {
                    commitOrRollback();
                }
            } finally {
                lock.unlock();
            }
            LOGGER.finest(() -> String.format("All statements are registered in current transaction"));
        }

        /**
         * Complete transaction.
         * Transaction is completed depending on <i>rollback only</i> flag.
         * Must run while holding the {@code lock}!
         */
        private void commitOrRollback() {
            // FIXME: Handle
            if (rollbackOnly.get()) {
                tx.abortTransaction();
            } else {
                tx.commitTransaction();
            }
        }

        /**
         * Get MongoDB client session (transaction handler).
         *
         * @return MongoDB client session
         */
        ClientSession tx() {
            return tx;
        }

        /**
         * Add statement to be monitored by transaction manager.
         * All statements in transaction must be registered using this method.
         *
         * @param stmt statement to add
         */
        void addStatement(MongoDbStatement stmt) {
            statements.add(stmt);
        }

    }

    /** Transaction manager instance.  */
    private final TransactionManager txManager;

    /**
     * Creates an instance of MongoDB transaction handler.
     *
     * @param db MongoDB database
     * @param tx MongoDB client session (transaction handler)
     * @param clientContext client context
     */
    MongoDbTransaction(MongoDatabase db,
                       ClientSession tx,
                       DbClientContext clientContext) {
        super(db, clientContext);
        this.txManager = new TransactionManager(tx);
    }

    @Override
    public DbStatementQuery createNamedQuery(String statementName, String statement) {
        return ((MongoDbStatementQuery) super.createNamedQuery(statementName, statement)).inTransaction(txManager);
    }

    @Override
    public DbStatementGet createNamedGet(String statementName, String statement) {
        return ((MongoDbStatementGet) super.createNamedGet(statementName, statement)).inTransaction(txManager);
    }

    @Override
    public DbStatementDml createNamedDmlStatement(String statementName, String statement) {
        return ((MongoDbStatementDml) super.createNamedDmlStatement(statementName, statement)).inTransaction(txManager);
    }

    @Override
    public DbStatementDml createNamedInsert(String statementName, String statement) {
        return ((MongoDbStatementDml) super.createNamedInsert(statementName, statement)).inTransaction(txManager);
    }

    @Override
    public DbStatementDml createNamedUpdate(String statementName, String statement) {
        return ((MongoDbStatementDml) super.createNamedUpdate(statementName, statement)).inTransaction(txManager);
    }

    @Override
    public DbStatementDml createNamedDelete(String statementName, String statement) {
        return ((MongoDbStatementDml) super.createNamedDelete(statementName, statement)).inTransaction(txManager);
    }

    @Override
    public void rollback() {
        this.txManager.rollbackOnly();
    }

    TransactionManager txManager() {
        return txManager;
    }

}
