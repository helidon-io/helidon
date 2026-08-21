/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.tests.application.transaction;

/**
 * Focused transaction operations shared by imperative and declarative tests.
 */
public interface FocusedTransactionOperations {

    /**
     * Inserts a row in the current transaction, or starts a required transaction when none is active.
     *
     * @param value row value
     * @return update count
     */
    long insertRequired(String value);

    /**
     * Inserts a row in an independent new transaction.
     *
     * @param value row value
     * @return update count
     */
    long insertNew(String value);

    /**
     * Inserts a row outside a suspended caller transaction.
     *
     * @param value row value
     * @return update count
     */
    long insertUnsupported(String value);

    /**
     * Runs an invalid query inside the current transaction, or starts a required transaction when none is active.
     */
    void failRequired();
}
