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
package io.helidon.data.jdbc.tests;

import java.sql.JDBCType;
import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Service;

/**
 * Uses the public JDBC client from an imperative application service.
 */
@SuppressWarnings("helidon:api:preview")
@Service.Singleton
final class ImperativeContactStore {

    private final JdbcClient client;

    /**
     * Creates a contact store for the default JDBC persistence unit.
     *
     * @param client qualified JDBC client
     */
    @Service.Inject
    ImperativeContactStore(@Data.ProviderType("jdbc")
                           @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client) {
        this.client = client;
    }

    /**
     * Returns all contacts in identifier order.
     *
     * @return contacts
     */
    List<ContactView> findAll() {
        return client.create("SELECT ID, NAME, EMAIL FROM CONTACT ORDER BY ID")
                .map(row -> new ContactView(row.required("ID", Long.class),
                                            row.required("NAME", String.class),
                                            row.optional("EMAIL", String.class)))
                .list();
    }

    /**
     * Returns the contact with the requested name.
     *
     * @param name contact name
     * @return matching contact
     */
    Optional<ContactView> findByName(String name) {
        return client.create("SELECT ID, NAME, EMAIL FROM CONTACT WHERE NAME = ?")
                .bind(1, name)
                .map(row -> new ContactView(row.required("ID", Long.class),
                                            row.required("NAME", String.class),
                                            row.optional("EMAIL", String.class)))
                .optional();
    }

    /**
     * Inserts one contact and returns its generated identifier.
     *
     * @param name contact name
     * @param email optional email address
     * @return generated identifier
     */
    long insert(String name, String email) {
        JdbcClient.Statement statement = client.create("INSERT INTO CONTACT (NAME, EMAIL) VALUES (?, ?)")
                .bind(1, name);
        if (email == null) {
            statement.bindNull(2, JDBCType.VARCHAR);
        } else {
            statement.bind(2, email);
        }
        return statement.generatedKeys()
                .addColumn("ID")
                .map(row -> row.required(1, Long.class))
                .one();
    }

    /**
     * Updates one contact name.
     *
     * @param id contact identifier
     * @param name new contact name
     * @return update count
     */
    long rename(long id, String name) {
        return client.create("UPDATE CONTACT SET NAME = ? WHERE ID = ?")
                .bind(1, name)
                .bind(2, id)
                .execute();
    }
}
