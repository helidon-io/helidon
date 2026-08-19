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
import io.helidon.data.jdbc.tests.application.transaction.FocusedTransactionOperations;
import io.helidon.data.jdbc.tests.application.transaction.TransactionSql;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;

/**
 * Exercises focused transaction behavior through the public imperative JDBC client.
 */
@SuppressWarnings("helidon:api:preview")
@Service.Singleton
public final class ImperativeFocusedTransactionOperations implements FocusedTransactionOperations {
    private final JdbcClient client;

    /**
     * Creates the imperative focused transaction adapter.
     *
     * @param client qualified JDBC client
     */
    @Service.Inject
    ImperativeFocusedTransactionOperations(@Data.ProviderType("jdbc")
                                           @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client) {
        this.client = client;
    }

    @Override
    public long insertRequired(String value) {
        return Tx.transaction(Tx.Type.REQUIRED, () -> insert(value));
    }

    @Override
    public long insertNew(String value) {
        return Tx.transaction(Tx.Type.NEW, () -> insert(value));
    }

    @Override
    public long insertUnsupported(String value) {
        return Tx.transaction(Tx.Type.UNSUPPORTED, () -> insert(value));
    }

    @Override
    public void failRequired() {
        Tx.transaction(Tx.Type.REQUIRED, () -> {
            client.create(TransactionSql.INVALID_QUERY).map(String.class).list();
            return null;
        });
    }

    private long insert(String value) {
        return client.create(TransactionSql.INSERT_VALUE)
                .bind(1, value)
                .execute();
    }
}
