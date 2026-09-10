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
 * SQL-injection safety operations implemented by both JDBC programming styles.
 */
public interface SqlInjectionOperations {
    /**
     * Inserts one contact.
     *
     * @param name contact name
     * @param email contact email
     * @return generated identifier
     */
    long insertContact(String name, String email);

    /**
     * Finds one contact by exact name.
     *
     * @param name contact name
     * @return matching contact
     */
    Optional<ContactView> findByName(String name);

    /**
     * Finds contacts by exact name in identifier order.
     *
     * @param name contact name
     * @return matching contacts
     */
    List<ContactView> findAllByName(String name);

    /**
     * Finds contacts by an exact name or email value.
     *
     * @param value contact name or email value
     * @return matching contacts
     */
    List<ContactView> findAllByNameOrEmail(String value);

    /**
     * Renames contacts matching an exact source name.
     *
     * @param sourceName source name
     * @param replacementName replacement name
     * @return update count
     */
    long renameByName(String sourceName, String replacementName);

    /**
     * Deletes contacts matching an exact name.
     *
     * @param name contact name
     * @return delete count
     */
    long deleteByName(String name);
}
