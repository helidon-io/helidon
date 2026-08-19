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
package io.helidon.data.jdbc.tests.declarative.repository;

import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.transaction.Tx;

/**
 * Exercises transaction annotations copied to generated repository methods.
 */
@Data.Repository
@Data.Provider("jdbc")
public interface TransactionalContactRepository {

    /**
     * Inserts a contact only when a transaction is already active.
     *
     * @param name contact name
     * @return generated identifier
     */
    @Tx.Mandatory
    @Jdbc.Statement("INSERT INTO CONTACT (NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys("id")
    long mandatory(String name);

    /**
     * Inserts a contact in a new transaction.
     *
     * @param name contact name
     * @return generated identifier
     */
    @Tx.New
    @Jdbc.Statement("INSERT INTO CONTACT (NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys("id")
    long inNewTransaction(String name);

    /**
     * Inserts a contact only when no transaction is active.
     *
     * @param name contact name
     * @return generated identifier
     */
    @Tx.Never
    @Jdbc.Statement("INSERT INTO CONTACT (NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys("id")
    long never(String name);

    /**
     * Inserts a contact in the current transaction or a new transaction.
     *
     * @param name contact name
     * @return generated identifier
     */
    @Tx.Required
    @Jdbc.Statement("INSERT INTO CONTACT (NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys("id")
    long required(String name);

    /**
     * Inserts a contact with the current transaction when one is active.
     *
     * @param name contact name
     * @return generated identifier
     */
    @Tx.Supported
    @Jdbc.Statement("INSERT INTO CONTACT (NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys("id")
    long supported(String name);

    /**
     * Inserts a contact outside the current transaction.
     *
     * @param name contact name
     * @return generated identifier
     */
    @Tx.Unsupported
    @Jdbc.Statement("INSERT INTO CONTACT (NAME) VALUES (:name)")
    @Jdbc.GeneratedKeys("id")
    long unsupported(String name);

    /**
     * Returns a contact name when it exists.
     *
     * @param name contact name
     * @return matching name
     */
    @Jdbc.Statement("SELECT NAME FROM CONTACT WHERE NAME = :name")
    Optional<String> findByName(String name);
}
