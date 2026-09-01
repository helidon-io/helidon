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
package io.helidon.data.jdbc.tests.chaos.imperative;

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.tests.chaos.application.ChaosContactOperations;
import io.helidon.data.jdbc.tests.chaos.application.ChaosSql;
import io.helidon.service.registry.Service;

/**
 * Exercises JDBC chaos smoke operations through the public imperative {@link JdbcClient} API.
 */
@SuppressWarnings("helidon:api:preview")
@Service.Singleton
public final class ImperativeChaosContactOperations implements ChaosContactOperations {
    private static final String GENERATED_KEY_COLUMN_PROPERTY = "helidon.data.jdbc.tests.chaos.generated-key-column";

    private final JdbcClient client;

    /**
     * Creates the imperative chaos operation adapter.
     *
     * @param client qualified JDBC client
     */
    @Service.Inject
    ImperativeChaosContactOperations(@Data.ProviderType("jdbc")
                                     @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client) {
        this.client = client;
    }

    @Override
    public void executeMalformedSql() {
        client.create(ChaosSql.MALFORMED_QUERY).map(Long.class).one();
    }

    @Override
    public void insertContact(long id, String name) {
        client.create(ChaosSql.INSERT_CONTACT)
                .bind(1, id)
                .bind(2, name)
                .execute();
    }

    @Override
    public long executeConversionFailureQuery() {
        return client.create(ChaosSql.CONVERSION_FAILURE_QUERY).map(Long.class).one();
    }

    @Override
    public long insertGeneratedContact(String name) {
        JdbcClient.GeneratedKeys generatedKeys = client.create(ChaosSql.INSERT_GENERATED_CONTACT)
                .bind(1, name)
                .generatedKeys();
        String column = System.getProperty(GENERATED_KEY_COLUMN_PROPERTY);
        if (column != null) {
            generatedKeys.addColumn(column);
        }
        return generatedKeys.map(row -> row.required(1, Long.class)).one();
    }

    @Override
    public long countContacts() {
        return client.create(ChaosSql.COUNT_CONTACTS).map(Long.class).one();
    }

    @Override
    public long currentSessionId() {
        return client.create(ChaosSql.CURRENT_SESSION_ID).map(Long.class).one();
    }

    @Override
    public void updateGate(long id) {
        client.create(ChaosSql.UPDATE_GATE).bind(1, id).execute();
    }
}
