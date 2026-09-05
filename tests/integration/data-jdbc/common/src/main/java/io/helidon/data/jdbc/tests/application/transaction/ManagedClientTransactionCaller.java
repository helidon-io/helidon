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
 * Starts a required transaction around a call that starts a new transaction.
 */
@Service.Singleton
public class ManagedClientTransactionCaller {

    private final JdbcClient client;
    private final ManagedClientTransactionService transactionService;

    /**
     * Creates the outer transaction service.
     *
     * @param client injected registry managed client
     * @param transactionService service that starts a new transaction
     */
    @Service.Inject
    ManagedClientTransactionCaller(@Data.ProviderType("jdbc")
                                   @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client,
                                   ManagedClientTransactionService transactionService) {
        this.client = client;
        this.transactionService = transactionService;
    }

    /**
     * Inserts in the required transaction, invokes a new transaction, and
     * then forces the required transaction to roll back.
     *
     * @param requiredId identifier inserted in the required transaction
     * @param newId identifier inserted in the new transaction
     */
    @Tx.Required
    public void insertWithNewAndFail(int requiredId, int newId) {
        client.create("INSERT INTO TX_BOUNDARY (ID) VALUES (?)")
                .bind(1, requiredId)
                .execute();
        transactionService.insertInNewTransaction(newId);
        throw new IllegalStateException("The outer transaction test requested rollback.");
    }
}
