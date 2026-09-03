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
package io.helidon.data.jdbc.tests.imperative;

import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.tests.application.ContactOperations;
import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.application.TestSql;
import io.helidon.service.registry.Service;

/**
 * Uses the public JDBC client from an imperative application service.
 */
@SuppressWarnings("helidon:api:preview")
@Service.Singleton
public final class ImperativeContactOperations implements ContactOperations {
    private static final String GENERATED_KEY_COLUMN_PROPERTY = "helidon.data.jdbc.tests.generated-key-column";

    private final JdbcClient client;

    /**
     * Creates a contact store for the default registry-managed JDBC client.
     *
     * @param client qualified JDBC client
     */
    @Service.Inject
    ImperativeContactOperations(@Data.ProviderType("jdbc")
                                @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client) {
        this.client = client;
    }

    /**
     * Returns all contacts in identifier order.
     *
     * @return contacts
     */
    @Override
    public List<ContactView> findAll() {
        return client.create(TestSql.FIND_ALL)
                .map(row -> new ContactView(row.get("ID", Long.class),
                                            row.get("NAME", String.class),
                                            row.optional("EMAIL", String.class)))
                .list();
    }

    /**
     * Returns exactly one contact with the requested name.
     *
     * @param name contact name
     * @return matching contact
     */
    @Override
    public ContactView oneByName(String name) {
        return client.create(TestSql.FIND_BY_NAME)
                .bind(1, name)
                .map(row -> new ContactView(row.get("ID", Long.class),
                                            row.get("NAME", String.class),
                                            row.optional("EMAIL", String.class)))
                .one();
    }

    /**
     * Returns the contact with the requested name.
     *
     * @param name contact name
     * @return matching contact
     */
    @Override
    public Optional<ContactView> findByName(String name) {
        return client.create(TestSql.FIND_BY_NAME)
                .bind(1, name)
                .map(row -> new ContactView(row.get("ID", Long.class),
                                            row.get("NAME", String.class),
                                            row.optional("EMAIL", String.class)))
                .optional();
    }

    /**
     * Returns contacts selected by an optional email filter.
     *
     * @param email email filter
     * @return matching contacts
     */
    @Override
    public List<ContactView> findByEmail(String email) {
        return client.create(TestSql.FIND_BY_EMAIL)
                .bind(1, email)
                .bind(2, email)
                .map(row -> new ContactView(row.get("ID", Long.class),
                                            row.get("NAME", String.class),
                                            row.optional("EMAIL", String.class)))
                .list();
    }

    /**
     * Returns an email as a required scalar.
     *
     * @param id contact identifier
     * @return email
     */
    @Override
    public String requiredEmail(long id) {
        return client.create(TestSql.FIND_EMAIL_BY_ID)
                .bind(1, id)
                .map(String.class)
                .one();
    }

    /**
     * Returns a nullable email as an optional scalar.
     *
     * @param id contact identifier
     * @return email, or empty for no row or SQL {@code NULL}
     */
    @Override
    public Optional<String> optionalEmail(long id) {
        return client.create(TestSql.FIND_EMAIL_BY_ID)
                .bind(1, id)
                .map(String.class)
                .optional();
    }

    /**
     * Requires exactly one row from an unfiltered query.
     *
     * @return the only contact
     */
    @Override
    public ContactView oneFromAll() {
        return client.create(TestSql.FIND_ONE_FROM_ALL)
                .map(row -> new ContactView(row.get("ID", Long.class),
                                            row.get("NAME", String.class),
                                            row.optional("EMAIL", String.class)))
                .one();
    }

    /**
     * Executes a record projection missing a required label.
     *
     * @param id contact identifier
     * @return mapped contact
     */
    @Override
    public ContactView missingRecordLabel(long id) {
        return client.create(TestSql.FIND_WITH_MISSING_LABEL)
                .bind(1, id)
                .map(row -> new ContactView(row.get("ID", Long.class),
                                            row.get("NAME", String.class),
                                            row.optional("EMAIL", String.class)))
                .one();
    }

    /**
     * Executes deliberately invalid SQL.
     */
    @Override
    public void executeInvalidQuery() {
        client.create(TestSql.INVALID_QUERY)
                .map(Long.class)
                .list();
    }

    /**
     * Inserts one contact and returns its generated identifier.
     *
     * @param name contact name
     * @param email non-null email address
     * @return generated identifier
     */
    @Override
    public long insert(String name, String email) {
        JdbcClient.Statement statement = client.create(TestSql.INSERT)
                .bind(1, name)
                .bind(2, email);
        return generatedKeys(statement)
                .map(row -> row.get(1, Long.class))
                .one();
    }

    /**
     * Attempts to insert an SQL {@code NULL} into the required name column.
     *
     * @param email contact email
     * @return generated identifier
     */
    @Override
    public long insertNullName(String email) {
        JdbcClient.Statement statement = client.create(TestSql.INSERT_NULL_NAME)
                .bind(1, email);
        return generatedKeys(statement)
                .map(row -> row.get(1, Long.class))
                .one();
    }

    /**
     * Inserts one contact with an SQL {@code NULL} email and returns its generated identifier.
     *
     * @param name contact name
     * @return generated identifier
     */
    @Override
    public long insertWithoutEmail(String name) {
        JdbcClient.Statement statement = client.create(TestSql.INSERT_WITHOUT_EMAIL)
                .bind(1, name);
        return generatedKeys(statement)
                .map(row -> row.get(1, Long.class))
                .one();
    }

    /**
     * Inserts one contact using driver-default generated keys.
     *
     * @param name contact name
     * @return generated identifier
     */
    @Override
    public long insertWithDefaultKey(String name) {
        JdbcClient.Statement statement = client.create(TestSql.INSERT_WITHOUT_EMAIL)
                .bind(1, name);
        return generatedKeys(statement)
                .map(row -> row.get(1, Long.class))
                .one();
    }

    /**
     * Updates one contact name.
     *
     * @param id contact identifier
     * @param name new contact name
     * @return update count
     */
    @Override
    public long rename(long id, String name) {
        return client.create(TestSql.RENAME)
                .bind(1, name)
                .bind(2, id)
                .execute();
    }

    /**
     * Renames every contact.
     *
     * @param name replacement name
     * @return update count
     */
    @Override
    public long renameAll(String name) {
        return client.create(TestSql.RENAME_ALL)
                .bind(1, name)
                .execute();
    }

    /**
     * Deletes one contact.
     *
     * @param id contact identifier
     * @return update count
     */
    @Override
    public long delete(long id) {
        return client.create(TestSql.DELETE)
                .bind(1, id)
                .execute();
    }

    private static JdbcClient.GeneratedKeys generatedKeys(JdbcClient.Statement statement) {
        JdbcClient.GeneratedKeys generatedKeys = statement.generatedKeys();
        String column = System.getProperty(GENERATED_KEY_COLUMN_PROPERTY);
        if (column != null) {
            generatedKeys.addColumn(column);
        }
        return generatedKeys;
    }
}
