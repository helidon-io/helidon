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
package io.helidon.data.jdbc.tests.support;

import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.tests.application.ContactView;
import io.helidon.data.jdbc.tests.application.TestSql;
import io.helidon.data.jdbc.tests.application.transaction.TransactionSql;
import io.helidon.service.registry.Service;

/**
 * Resets portable application data and inspects committed state outside the adapter under test.
 */
@SuppressWarnings("helidon:api:preview")
@Service.Singleton
public final class DatabaseFixture {
    private final JdbcClient client;

    /**
     * Creates database support for the Default JDBC Client.
     *
     * @param client qualified JDBC client
     */
    @Service.Inject
    DatabaseFixture(@Data.ProviderType("jdbc")
                    @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client) {
        this.client = client;
    }

    /**
     * Restores the two baseline contacts.
     */
    public void reset() {
        client.create(TestSql.RESET).execute();
        client.create(TestSql.RESTORE_WITH_EMAIL)
                .bind(1, 1L)
                .bind(2, "alpha")
                .bind(3, "alpha@example.test")
                .execute();
        client.create(TestSql.RESTORE_WITHOUT_EMAIL)
                .bind(1, 2L)
                .bind(2, "beta")
                .execute();
    }

    /**
     * Reads committed state through a dedicated support operation.
     *
     * @param name contact name
     * @return matching contact
     */
    public Optional<ContactView> committedByName(String name) {
        return client.create(TestSql.FIND_BY_NAME)
                .bind(1, name)
                .map(row -> new ContactView(row.required("ID", Long.class),
                                            row.required("NAME", String.class),
                                            row.optional("EMAIL", String.class)))
                .optional();
    }

    /**
     * Restores one committed baseline row for a transaction-matrix scenario.
     */
    public void resetTransactionMatrix() {
        client.create(TransactionSql.RESET).execute();
        client.create(TransactionSql.RESTORE).execute();
    }

    /**
     * Reads the committed transaction-matrix row count outside the scenario operation.
     *
     * @return committed row count
     */
    public long committedTransactionRowCount() {
        return client.create(TransactionSql.QUERY).map(Long.class).one();
    }

    /**
     * Reads committed transaction-matrix values outside the scenario operation.
     *
     * @return committed values in deterministic order
     */
    public List<String> committedTransactionValues() {
        return client.create(TransactionSql.LIST_VALUES).map(String.class).list();
    }
}
