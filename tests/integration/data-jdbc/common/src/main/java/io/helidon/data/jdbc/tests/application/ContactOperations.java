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
package io.helidon.data.jdbc.tests.application;

import java.util.List;
import java.util.Optional;

/**
 * Application operations implemented by both JDBC programming styles.
 */
public interface ContactOperations {

    /**
     * Returns all contacts in identifier order.
     *
     * @return contacts
     */
    List<ContactView> findAll();

    /**
     * Returns exactly one contact by name.
     *
     * @param name contact name
     * @return matching contact
     */
    ContactView oneByName(String name);

    /**
     * Finds one contact by name.
     *
     * @param name contact name
     * @return matching contact
     */
    Optional<ContactView> findByName(String name);

    /**
     * Returns contacts selected by an optional email filter.
     *
     * @param email email filter
     * @return matching contacts
     */
    List<ContactView> findByEmail(String email);

    /**
     * Returns an email as a required scalar.
     *
     * @param id contact identifier
     * @return email
     */
    String requiredEmail(long id);

    /**
     * Returns a nullable email as an optional scalar.
     *
     * @param id contact identifier
     * @return email, or empty for SQL {@code NULL}
     */
    Optional<String> optionalEmail(long id);

    /**
     * Requires exactly one result from an unfiltered query.
     *
     * @return the only contact
     */
    ContactView oneFromAll();

    /**
     * Executes a record projection missing a required label.
     *
     * @param id contact identifier
     * @return mapped contact
     */
    ContactView missingRecordLabel(long id);

    /**
     * Executes deliberately invalid SQL.
     */
    void executeInvalidQuery();

    /**
     * Inserts a contact with an email address.
     *
     * @param name contact name
     * @param email contact email
     * @return generated identifier
     */
    long insert(String name, String email);

    /**
     * Attempts to insert an SQL {@code NULL} into the required name column.
     *
     * @param email contact email
     * @return generated identifier
     */
    long insertNullName(String email);

    /**
     * Inserts a contact with an SQL {@code NULL} email.
     *
     * @param name contact name
     * @return generated identifier
     */
    long insertWithoutEmail(String name);

    /**
     * Inserts a contact and requests driver-default generated keys.
     *
     * @param name contact name
     * @return generated identifier
     */
    long insertWithDefaultKey(String name);

    /**
     * Renames one contact.
     *
     * @param id contact identifier
     * @param name replacement name
     * @return update count
     */
    long rename(long id, String name);

    /**
     * Renames every contact.
     *
     * @param name replacement name
     * @return update count
     */
    long renameAll(String name);

    /**
     * Deletes one contact.
     *
     * @param id contact identifier
     * @return update count
     */
    long delete(long id);
}
