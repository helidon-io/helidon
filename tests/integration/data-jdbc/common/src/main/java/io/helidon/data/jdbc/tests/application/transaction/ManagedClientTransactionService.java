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
package io.helidon.data.jdbc.tests.application.transaction;

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;

/**
 * Exercises transaction annotations with an injected registry managed client.
 */
@Service.Singleton
public class ManagedClientTransactionService {

    private final JdbcClient client;

    /**
     * Creates the service with the Default JDBC Client.
     *
     * @param client injected registry managed client
     */
    @Service.Inject
    ManagedClientTransactionService(@Data.ProviderType("jdbc")
                                    @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client) {
        this.client = client;
    }

    /**
     * Inserts and reads one row in a required transaction.
     *
     * @param id row identifier
     * @return row count visible inside the transaction
     */
    @Tx.Required
    public long insertAndCount(int id) {
        client.create("INSERT INTO TX_BOUNDARY (ID) VALUES (?)")
                .bind(1, id)
                .execute();
        return client.create("SELECT COUNT(*) FROM TX_BOUNDARY")
                .map(Long.class)
                .one();
    }

    /**
     * Inserts one row and then forces the required transaction to roll back.
     *
     * @param id row identifier
     */
    @Tx.Required
    public void insertAndFail(int id) {
        client.create("INSERT INTO TX_BOUNDARY (ID) VALUES (?)")
                .bind(1, id)
                .execute();
        throw new IllegalStateException("The managed transaction test requested rollback.");
    }

    /**
     * Inserts one row in a new transaction.
     *
     * @param id row identifier
     */
    @Tx.New
    public void insertInNewTransaction(int id) {
        client.create("INSERT INTO TX_BOUNDARY (ID) VALUES (?)")
                .bind(1, id)
                .execute();
    }
}
