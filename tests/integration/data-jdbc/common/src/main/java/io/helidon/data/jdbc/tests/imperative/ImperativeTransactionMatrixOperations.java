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

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.tests.application.transaction.TransactionMatrixOperations;
import io.helidon.data.jdbc.tests.application.transaction.TransactionOperation;
import io.helidon.data.jdbc.tests.application.transaction.TransactionPolicy;
import io.helidon.data.jdbc.tests.application.transaction.TransactionSql;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;

/**
 * Executes the transaction matrix with the public imperative JDBC client.
 */
@SuppressWarnings("helidon:api:preview")
@Service.Singleton
public final class ImperativeTransactionMatrixOperations implements TransactionMatrixOperations {
    private static final String GENERATED_KEY_COLUMN_PROPERTY = "helidon.data.jdbc.tests.generated-key-column";

    private final JdbcClient client;

    /**
     * Creates the imperative transaction adapter.
     *
     * @param client qualified JDBC client
     */
    @Service.Inject
    ImperativeTransactionMatrixOperations(@Data.ProviderType("jdbc")
                                          @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client) {
        this.client = client;
    }

    @Override
    public long execute(TransactionPolicy policy, TransactionOperation operation) {
        if (policy == TransactionPolicy.NONE) {
            return execute(operation);
        }
        return Tx.transaction(policy.type(), () -> execute(operation));
    }

    private long execute(TransactionOperation operation) {
        return switch (operation) {
        case QUERY -> client.create(TransactionSql.QUERY).map(Long.class).one();
        case UPDATE -> client.create(TransactionSql.UPDATE).execute();
        case GENERATED_KEY -> generatedKeys(client.create(TransactionSql.GENERATED_KEY))
                .map(row -> row.get(1, Long.class))
                .one();
        };
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
