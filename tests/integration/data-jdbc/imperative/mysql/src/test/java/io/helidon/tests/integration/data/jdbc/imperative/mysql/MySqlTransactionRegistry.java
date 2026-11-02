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
package io.helidon.tests.integration.data.jdbc.imperative.mysql;

import javax.sql.DataSource;

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.JdbcClientConfig;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.tests.integration.data.jdbc.support.TestDataSourceFactory;
import io.helidon.transaction.spi.TxLifeCycle;
import io.helidon.transaction.spi.TxSupport;

/**
 * Owns public service-registry wiring for one local JDBC transaction scenario.
 */
final class MySqlTransactionRegistry implements AutoCloseable {
    private final ServiceRegistryManager manager;
    private final JdbcClient client;
    private final TxSupport support;

    MySqlTransactionRegistry(DataSource dataSource, TxLifeCycle... observers) {
        TestDataSourceFactory.dataSource("transaction-source", dataSource);
        MySqlTransactionObservers.observers(observers);
        ServiceRegistryManager created = null;
        try {
            created = ServiceRegistryManager.create();
            GlobalServiceRegistry.registry(created.registry());
            Services.set(JdbcClientConfig.class,
                         JdbcClient.builder().dataSourceName("transaction-source").buildPrototype());
            client = created.registry().get(JdbcClient.class,
                                            Qualifier.createNamed(Service.Named.DEFAULT_NAME),
                                            Qualifier.builder().typeName(Data.ProviderType.TYPE).value("jdbc").build());
            support = created.registry().get(TxSupport.class);
        } catch (RuntimeException | Error failure) {
            try {
                if (created != null) {
                    created.shutdown();
                }
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            } finally {
                TestDataSourceFactory.reset();
                MySqlTransactionObservers.observers();
            }
            throw failure;
        }
        manager = created;
    }

    JdbcClient client() {
        return client;
    }

    TxSupport support() {
        return support;
    }

    @Override
    public void close() {
        try {
            manager.shutdown();
        } finally {
            TestDataSourceFactory.reset();
            MySqlTransactionObservers.observers();
        }
    }
}
