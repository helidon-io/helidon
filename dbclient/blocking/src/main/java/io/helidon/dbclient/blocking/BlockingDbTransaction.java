/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.dbclient.blocking;

import io.helidon.dbclient.DbTransaction;


/**
 * Database blocking transaction.
 * Holds a single transaction to the database (if supported).
 * The transaction completes once {@link io.helidon.dbclient.blocking.BlockingDbClient#inTransaction(java.util.function.Function)} returns
 * the result provided by the body of the lambda within it.
 */
public interface BlockingDbTransaction extends BlockingDbExecute {
    /**
     * Configure this transaction to (eventually) rollback.
     */
    void rollback();

    /**
     * Create implementation of BlockingDbTransaction.
     *
     * @param dbTransaction
     * @return Blocking DbTransaction
     */
    static BlockingDbTransaction create(DbTransaction dbTransaction){
        return new BlockingDbTransactionImpl(dbTransaction);
    }
}
