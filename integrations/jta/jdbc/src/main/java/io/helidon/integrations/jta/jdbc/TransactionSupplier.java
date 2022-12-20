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

import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;

/**
 * A supplier of {@link Transaction}s.
 *
 * @see Transaction
 *
 * @see jakarta.transaction.TransactionManager#getTransaction()
 */
@FunctionalInterface
public interface TransactionSupplier {

    /**
     * Returns the current {@link Transaction} representing the transaction context of the calling thread, or {@code
     * null} if there is no such context at invocation time.
     *
     * @return the current {@link Transaction} representing the transaction context of the calling thread, or {@code
     * null} if there is no such context at invocation time
     *
     * @exception SystemException if there was an unexpected error condition
     */
    Transaction getTransaction() throws SystemException;

}
