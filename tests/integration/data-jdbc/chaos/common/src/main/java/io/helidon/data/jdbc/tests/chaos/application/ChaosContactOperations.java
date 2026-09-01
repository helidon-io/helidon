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
package io.helidon.data.jdbc.tests.chaos.application;

/**
 * Application-facing operations exercised by portable JDBC chaos smoke tests.
 */
public interface ChaosContactOperations {
    /**
     * Executes malformed SQL and is expected to fail before returning a value.
     */
    void executeMalformedSql();

    /**
     * Inserts one contact row.
     *
     * @param id contact identifier
     * @param name contact name
     */
    void insertContact(long id, String name);

    /**
     * Reads text as a numeric value and is expected to fail during conversion.
     *
     * @return unreachable scalar value
     */
    long executeConversionFailureQuery();

    /**
     * Inserts one contact row and returns the generated key.
     *
     * @param name contact name
     * @return generated identifier
     */
    long insertGeneratedContact(String name);

    /**
     * Counts committed chaos contacts after a failure.
     *
     * @return committed row count
     */
    long countContacts();

    /**
     * Returns the physical database session identifier used by the current transaction.
     *
     * @return database session identifier
     */
    long currentSessionId();

    /**
     * Updates the gate row used to synchronize a physical connection disruption.
     *
     * @param id gate row identifier
     */
    void updateGate(long id);
}
