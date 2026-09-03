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
import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.application.SqlInjectionOperations;
import io.helidon.data.jdbc.tests.application.TestSql;
import io.helidon.service.registry.Service;

/**
 * Uses the public JDBC client to exercise SQL-injection safety with bound values.
 */
@SuppressWarnings("helidon:api:preview")
@Service.Singleton
public final class ImperativeSqlInjectionOperations implements SqlInjectionOperations {
    private static final String GENERATED_KEY_COLUMN_PROPERTY = "helidon.data.jdbc.tests.generated-key-column";

    private final JdbcClient client;

    /**
     * Creates the imperative SQL-injection operation adapter.
     *
     * @param client qualified JDBC client
     */
    @Service.Inject
    ImperativeSqlInjectionOperations(@Data.ProviderType("jdbc")
                                     @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client) {
        this.client = client;
    }

    @Override
    public long insertContact(String name, String email) {
        return client.create(TestSql.INSERT)
                .bind(1, name)
                .bind(2, email)
                .generatedKeys()
                .addColumn(System.getProperty(GENERATED_KEY_COLUMN_PROPERTY, "id"))
                .map(row -> row.get(1, Long.class))
                .one();
    }

    @Override
    public Optional<ContactView> findByName(String name) {
        return client.create(TestSql.FIND_BY_NAME)
                .bind(1, name)
                .map(ImperativeSqlInjectionOperations::contact)
                .optional();
    }

    @Override
    public List<ContactView> findAllByName(String name) {
        return client.create(TestSql.FIND_ALL_BY_NAME)
                .bind(1, name)
                .map(ImperativeSqlInjectionOperations::contact)
                .list();
    }

    @Override
    public List<ContactView> findAllByNameOrEmail(String value) {
        return client.create(TestSql.FIND_ALL_BY_NAME_OR_EMAIL)
                .bind(1, value)
                .bind(2, value)
                .map(ImperativeSqlInjectionOperations::contact)
                .list();
    }

    @Override
    public long renameByName(String sourceName, String replacementName) {
        return client.create(TestSql.RENAME_BY_NAME)
                .bind(1, replacementName)
                .bind(2, sourceName)
                .execute();
    }

    @Override
    public long deleteByName(String name) {
        return client.create(TestSql.DELETE_BY_NAME)
                .bind(1, name)
                .execute();
    }

    private static ContactView contact(JdbcClient.Row row) {
        return new ContactView(row.get("ID", Long.class),
                               row.get("NAME", String.class),
                               row.optional("EMAIL", String.class));
    }
}
