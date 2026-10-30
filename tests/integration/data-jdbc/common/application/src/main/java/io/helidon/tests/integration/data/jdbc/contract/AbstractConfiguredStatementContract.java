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
package io.helidon.tests.integration.data.jdbc.contract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.tests.integration.data.jdbc.application.ConfiguredOperations;
import io.helidon.tests.integration.data.jdbc.application.ContactView;
import io.helidon.tests.integration.data.jdbc.support.DatabaseFixture;
import io.helidon.tests.integration.data.jdbc.support.TestConfigFactory;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies replacement SQL against the same compiled application on real drivers.
 */
public abstract class AbstractConfiguredStatementContract {

    protected abstract Config databaseConfig();

    protected abstract Class<? extends ConfiguredOperations> operationsType();

    protected boolean nullableInputs() {
        return false;
    }

    @Test
    protected void replacesReordersAndRepeatsInputsWithTheSameCompiledRepository() {
        for (String predicate : List.of("NAME = :name OR EMAIL = :email",
                "EMAIL = :email OR NAME = :name OR EMAIL = :email")) {
            Map<String, String> config = new HashMap<>(databaseConfig().asMap().get());
            config.put("data.clients.jdbc.0.statements.find",
                    "SELECT ID, NAME, EMAIL FROM CONTACT WHERE " + predicate + " ORDER BY ID");
            config.put("data.clients.jdbc.0.statements.rename", "UPDATE CONTACT SET NAME = :name WHERE ID = :id");
            config.put("data.clients.jdbc.0.statements.insert", "INSERT INTO CONTACT (NAME) VALUES (:value)");
            TestConfigFactory.config(Config.just(ConfigSources.create(config)));
            ServiceRegistryManager manager = ServiceRegistryManager.start();
            try {
                DatabaseFixture database = manager.registry().get(DatabaseFixture.class);
                database.reset();
                ConfiguredOperations operations = manager.registry().get(operationsType());
                assertThat(operations.find("beta", "alpha@example.test").size(), is(2));
                JdbcClient configured = JdbcClient.builder()
                        .config(Config.just(ConfigSources.create(config)).get("data.clients.jdbc.0")).build();
                JdbcClient copied = JdbcClient.builder().from(configured.prototype()).build();
                assertThat(copied.createNamed("find").bind("email", "alpha@example.test").bind("name", "beta")
                        .map(Long.class).list(), is(List.of(1L, 2L)));
                if (nullableInputs()) {
                    assertThat(operations.find("beta", null),
                            is(List.of(new ContactView(2L, "beta", Optional.empty()))));
                    assertThat(operations.find(null, null), is(List.of()));
                }
                long key = operations.insert("configured-key").orElseThrow();
                assertThat(database.committedByName("configured-key").orElseThrow().id(), is(key));
                Tx.transaction(Tx.Type.REQUIRED, () -> {
                    assertThat(operations.rename("configured-commit", 1L), is(1L));
                    return null;
                });
                assertThat(database.committedByName("configured-commit").isPresent(), is(true));
                assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
                    operations.rename("configured-rollback", 1L);
                    throw new IllegalStateException("The application rejected this transaction.");
                }));
                assertThat(database.committedByName("configured-rollback"), is(Optional.empty()));
                assertThat(operations.find("configured-commit", "absent").size(), is(1));
            } finally {
                manager.shutdown();
                TestConfigFactory.reset();
            }
        }
    }

    @Test
    protected void validatesReplacementResultChannelsAndColumns() {
        for (String replacement : List.of("SELECT ID, NAME AS OTHER_NAME, EMAIL FROM CONTACT "
                + "WHERE NAME = :name OR EMAIL = :email", "UPDATE CONTACT SET NAME = :name WHERE EMAIL = :email")) {
            Map<String, String> config = new HashMap<>(databaseConfig().asMap().get());
            config.put("data.clients.jdbc.0.statements.find", replacement);
            TestConfigFactory.config(Config.just(ConfigSources.create(config)));
            ServiceRegistryManager manager = ServiceRegistryManager.start();
            try {
                DatabaseFixture database = manager.registry().get(DatabaseFixture.class);
                database.reset();
                ConfiguredOperations operations = manager.registry().get(operationsType());
                assertThrows(DataException.class, () -> operations.find("alpha", "alpha@example.test"));
                assertThat(database.committedByName("beta").isPresent(), is(true));
            } finally {
                manager.shutdown();
                TestConfigFactory.reset();
            }
        }
    }
}
