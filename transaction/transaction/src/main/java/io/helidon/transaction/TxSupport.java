/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.transaction;

import java.util.concurrent.Callable;
//import java.util.function.Function;

/**
 *  Implemented by each transaction handling support.
 */
public interface TxSupport {

    /**
     * Type of the transaction API support.
     *
     * @return the transaction API support
     */
    Type type();

    /**
     * Execute provided task as database transaction.
     * Transaction is handled automatically. Task computes and returns result.
     *
     * @param type transaction type
     * @param task task to run in transaction
     * @param <T>  the result type of the task
     * @return computed task result
     * @throws TxException when result computation failed
     */
    <T> T transaction(Tx.Type type, Callable<T> task);

/* Removed: need to decide whether this is needed at all

    /**
     * Execute provided task as database transaction.
     * Transaction is finished manually. Task computes and returns result.
     *
     * @param type transaction type
     * @param task task to run in transaction
     * @param <T>  the result type of the task
     * @return computed task result
     * /
    <T> T transaction(Tx.Type type, Function<Tx.Transaction, T> task);

    /**
     * Start transaction.
     * Transaction is finished manually.
     *
     * @param type transaction type
     * @return transaction handler
     * /
    Tx.Transaction transaction(Tx.Type type);

*/

    /**
     * Transaction API support.
     */
    enum Type {
        /** Implementation of thi interface supports Jakarta Transaction API. */
        JTA,
        /** Implementation of thi interface supports only resource local transactions. */
        RESOURCE_LOCAL
    }

}
