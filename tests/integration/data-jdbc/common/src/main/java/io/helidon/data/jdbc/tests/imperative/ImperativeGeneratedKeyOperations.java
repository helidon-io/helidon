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
import java.util.Locale;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.tests.application.ContactLabel;
import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.application.GeneratedKeyOperations;
import io.helidon.data.jdbc.tests.application.TestSql;
import io.helidon.service.registry.Service;

/**
 * Uses the public JDBC client to exercise generated-key mapping variants.
 */
@SuppressWarnings("helidon:api:preview")
@Service.Singleton
public final class ImperativeGeneratedKeyOperations implements GeneratedKeyOperations {
    private static final String GENERATED_KEY_COLUMN_PROPERTY = "helidon.data.jdbc.tests.generated-key-column";

    private final JdbcClient client;

    /**
     * Creates the imperative generated-key operation adapter.
     *
     * @param client qualified JDBC client
     */
    @Service.Inject
    ImperativeGeneratedKeyOperations(@Data.ProviderType("jdbc")
                                     @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client) {
        this.client = client;
    }

    @Override
    public long insertScalar(String name) {
        return generatedKeys(statement(name), keyColumn())
                .map(row -> row.get(1, Long.class))
                .one();
    }

    @Override
    public Optional<Long> insertOptionalScalar(String name) {
        return generatedKeys(statement(name), keyColumn())
                .map(row -> row.get(1, Long.class))
                .optional();
    }

    @Override
    public List<Long> insertScalarList(String name) {
        return generatedKeys(statement(name), keyColumn())
                .map(row -> row.get(1, Long.class))
                .list();
    }

    @Override
    public ContactView insertRecord(String name, String email) {
        JdbcClient.Statement statement = client.create(TestSql.INSERT)
                .bind(1, name)
                .bind(2, email);
        return generatedKeys(statement, keyColumns("id", "name", "email"))
                .map(row -> new ContactView(row.get("ID", Long.class),
                                            row.get("NAME", String.class),
                                            row.optional("EMAIL", String.class)))
                .one();
    }

    @Override
    public ContactLabel insertMapped(String name) {
        return generatedKeys(statement(name), keyColumns("id", "name"))
                .map(row -> new ContactLabel(row.get("ID", Long.class),
                                             "preferred:" + row.get("NAME", String.class)))
                .one();
    }

    @Override
    public long insertWithInvalidGeneratedKeyColumn(String name) {
        return generatedKeys(statement(name), "MISSING_KEY")
                .map(row -> row.get(1, Long.class))
                .one();
    }

    private JdbcClient.Statement statement(String name) {
        return client.create(TestSql.INSERT_WITHOUT_EMAIL)
                .bind(1, name);
    }

    private static String keyColumn() {
        return System.getProperty(GENERATED_KEY_COLUMN_PROPERTY, "id");
    }

    private static String[] keyColumns(String... defaultColumns) {
        if (System.getProperty(GENERATED_KEY_COLUMN_PROPERTY) == null) {
            return defaultColumns;
        }
        String[] result = new String[defaultColumns.length];
        for (int index = 0; index < defaultColumns.length; index++) {
            result[index] = defaultColumns[index].toUpperCase(Locale.ROOT);
        }
        return result;
    }

    private static JdbcClient.GeneratedKeys generatedKeys(JdbcClient.Statement statement, String... columns) {
        JdbcClient.GeneratedKeys generatedKeys = statement.generatedKeys();
        for (String column : columns) {
            generatedKeys.addColumn(column);
        }
        return generatedKeys;
    }
}
