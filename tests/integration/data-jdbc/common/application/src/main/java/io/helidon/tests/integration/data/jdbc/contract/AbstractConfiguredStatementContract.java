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
import io.helidon.data.Data;
import io.helidon.data.DataException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
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

    /**
     * Invalid YAML definitions fail on an already resolved client without execution.
     */
    @Test
    protected void rejectsMixedAndMismatchedInputsFromApplicationYaml() {
        Config invalid = Config.just(ConfigSources.classpath("configured-statements/invalid/application.yaml"));
        for (String scenario : List.of("mixed", "missing", "additional", "few", "many")) {
            String sql = invalid.get(scenario + ".find").asString().get();
            Map<String, String> values = new HashMap<>(configuration("configured-statements/application.yaml").asMap().get());
            values.put("application.sql.find", sql);
            TestConfigFactory.config(Config.just(ConfigSources.create(values)));
            try {
                ServiceRegistryManager manager = ServiceRegistryManager.start();
                try {
                    JdbcClient client = manager.registry().get(JdbcClient.class,
                            Qualifier.createNamed(Service.Named.DEFAULT_NAME),
                            Qualifier.builder().typeName(Data.ProviderType.TYPE).value("jdbc").build());
                    assertThrows(IllegalArgumentException.class, () -> client.prepare(sql, List.of("name", "email")));
                    assertThrows(IllegalArgumentException.class,
                            () -> manager.registry().get(operationsType()));
                } finally {
                    manager.shutdown();
                }
            } finally {
                TestConfigFactory.reset();
            }
        }
    }

    @Test
    protected void loadsApplicationYamlStatementsThroughTheApplicationRegistry() {
        TestConfigFactory.config(configuration("configured-statements/application.yaml"));
        try {
            ServiceRegistryManager manager = ServiceRegistryManager.start();
            try {
                DatabaseFixture database = manager.registry().get(DatabaseFixture.class);
                database.reset();
                ConfiguredOperations operations = manager.registry().get(operationsType());
                assertThat(operations.find("beta", "alpha@example.test"), is(List.of(
                        new ContactView(1L, "alpha", Optional.of("alpha@example.test")),
                        new ContactView(2L, "beta", Optional.empty()))));
                if (nullableInputs()) {
                    assertThat(operations.find("beta", null),
                            is(List.of(new ContactView(2L, "beta", Optional.empty()))));
                    assertThat(operations.find(null, null), is(List.of()));
                }
                long key = operations.insert("yaml-key").orElseThrow();
                assertThat(database.committedByName("yaml-key").orElseThrow().id(), is(key));
                Tx.transaction(Tx.Type.REQUIRED, () -> {
                    assertThat(operations.rename("yaml-commit", 1L), is(1L));
                    return null;
                });
                assertThat(database.committedByName("yaml-commit").isPresent(), is(true));
                assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
                    operations.rename("yaml-rollback", 1L);
                    throw new IllegalStateException("The application rejected the YAML transaction.");
                }));
                assertThat(database.committedByName("yaml-rollback"), is(Optional.empty()));
                assertThat(operations.find("yaml-commit", "absent").size(), is(1));
            } finally {
                manager.shutdown();
            }
        } finally {
            TestConfigFactory.reset();
        }
    }

    @Test
    protected void replacesReordersAndRepeatsInputsWithTheSameCompiledRepository() {
        for (String variant : List.of("", "reordered/", "positional/")) {
            Config config = configuration("configured-statements/" + variant + "application.yaml");
            TestConfigFactory.config(config);
            ServiceRegistryManager manager = ServiceRegistryManager.start();
            try {
                DatabaseFixture database = manager.registry().get(DatabaseFixture.class);
                database.reset();
                ConfiguredOperations operations = manager.registry().get(operationsType());
                assertThat(operations.find("beta", "alpha@example.test").size(), is(2));
                JdbcClient client = manager.registry().get(JdbcClient.class,
                        Qualifier.createNamed(Service.Named.DEFAULT_NAME),
                        Qualifier.builder().typeName(Data.ProviderType.TYPE).value("jdbc").build());
                assertThat(operations.byId(1).orElseThrow().name(), is("alpha"));
                assertThat(operations.all().size(), is(2));
                String sql = config.get("application.sql.find").asString().get();
                assertThat(client.prepare(sql, List.of("name", "email")).get()
                        .bind("email", "alpha@example.test").bind("name", "beta")
                        .map(Long.class).list(), is(List.of(1L, 2L)));
                if (variant.equals("positional/")) {
                    assertThat(client.create(sql).bind(1, "beta").bind(2, "alpha@example.test")
                            .map(Long.class).list(), is(List.of(1L, 2L)));
                }
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
        Config invalid = Config.just(ConfigSources.classpath("configured-statements/invalid/application.yaml"));
        for (String scenario : List.of("columns", "channel")) {
            Map<String, String> config = new HashMap<>(configuration("configured-statements/application.yaml").asMap().get());
            config.put("application.sql.find", invalid.get(scenario + ".find").asString().get());
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
    /**
     * A standalone prepared definition commits independently of an enclosing managed transaction.
     */
    @Test
    protected void keepsStandaloneOwnershipInsideAManagedTransaction() {
        Config config = configuration("configured-statements/application.yaml");
        TestConfigFactory.config(config);
        try {
            ServiceRegistryManager manager = ServiceRegistryManager.start();
            try {
                DatabaseFixture database = manager.registry().get(DatabaseFixture.class);
                database.reset();
                ConfiguredOperations operations = manager.registry().get(operationsType());
                JdbcClient standalone = JdbcClient.builder().config(config.get("data.clients.jdbc.0")).build();
                var rename = standalone.prepare(config.get("application.sql.rename").asString().get(), List.of("name", "id"));
                assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
                    // Different rows let the independent connection complete without waiting for the managed write.
                    operations.rename("managed-pending", 1L);
                    assertThat(rename.get().bind("name", "standalone-committed").bind("id", 2L).execute(), is(1L));
                    throw new IllegalStateException("The managed transaction must roll back.");
                }));
                assertThat(database.committedByName("alpha").isPresent(), is(true));
                assertThat(database.committedByName("managed-pending"), is(Optional.empty()));
                assertThat(database.committedByName("standalone-committed").isPresent(), is(true));
            } finally {
                manager.shutdown();
            }
        } finally {
            TestConfigFactory.reset();
        }
    }

    /**
     * Retained generic aliases use replacement YAML while unrelated positional SQL remains valid.
     */
    @Test
    protected void executesConfiguredAliasesAgainstReplacementYaml() {
        for (String variant : List.of("", "reordered/", "positional/")) {
            TestConfigFactory.config(configuration("configured-statements/" + variant + "application.yaml"));
            try {
                ServiceRegistryManager manager = ServiceRegistryManager.start();
                try {
                    DatabaseFixture database = manager.registry().get(DatabaseFixture.class);
                    database.reset();
                    ConfiguredOperations operations = manager.registry().get(operationsType());
                    assertThat(operations.configuredAliases("alpha@example.test"), is(List.of("alpha")));
                    if (nullableInputs()) {
                        assertThat(operations.configuredAliases(null), is(List.of("beta")));
                    }
                    long key = operations.configuredAliasInsert("configured-alias-key",
                            nullableInputs() ? null : "alias@example.test").orElseThrow();
                    assertThat(database.committedByName("configured-alias-key").orElseThrow().id(), is(key));
                    Tx.transaction(Tx.Type.REQUIRED, () -> {
                        assertThat(operations.configuredAliasUpdate(key, "alias-commit"), is(1));
                        return null;
                    });
                    assertThat(database.committedByName("alias-commit").isPresent(), is(true));
                    assertThrows(TxException.class, () -> Tx.transaction(Tx.Type.REQUIRED, () -> {
                        operations.configuredAliasUpdate(key, "alias-rollback");
                        throw new IllegalStateException("The configured alias transaction must roll back.");
                    }));
                    assertThat(database.committedByName("alias-rollback"), is(Optional.empty()));
                    if (nullableInputs()) {
                        assertThrows(DataException.class, () -> operations.configuredAliasInsert(null, null));
                    }
                    assertThat(operations.configuredAliases("alpha@example.test"), is(List.of("alpha")));
                    assertThat(database.committedByName("alias-commit").isPresent(), is(true));
                } finally {
                    manager.shutdown();
                }
            } finally {
                TestConfigFactory.reset();
            }
        }
    }

    /**
     * Aliased repositories reject positional deployment SQL while imperative preparation retains it.
     */
    @Test
    protected void validatesConfiguredAliasStylesAndEffectiveInputSetsAtActivation() {
        Config invalid = Config.just(ConfigSources.classpath("configured-statements/invalid/application.yaml"));
        for (String scenario : List.of("alias-positional", "alias-missing", "alias-additional", "alias-mixed")) {
            Map<String, String> values = new HashMap<>(configuration("configured-statements/application.yaml").asMap().get());
            values.put("application.aliases.find", invalid.get(scenario + ".find").asString().get());
            TestConfigFactory.config(Config.just(ConfigSources.create(values)));
            try {
                ServiceRegistryManager manager = ServiceRegistryManager.start();
                try {
                    if (scenario.equals("alias-positional") && !nullableInputs()) {
                        manager.registry().get(DatabaseFixture.class).reset();
                        ConfiguredOperations operations = manager.registry().get(operationsType());
                        assertThat(operations.configuredAliases("alpha@example.test"), is(List.of("alpha")));
                    } else {
                        assertThrows(IllegalArgumentException.class, () -> manager.registry().get(operationsType()));
                    }
                } finally {
                    manager.shutdown();
                }
            } finally {
                TestConfigFactory.reset();
            }
        }
    }

    private Config configuration(String resource) {
        Map<String, String> values = new HashMap<>(databaseConfig().asMap().get());
        values.putAll(Config.just(ConfigSources.classpath(resource)).asMap().get());
        return Config.just(ConfigSources.create(values));
    }

}
