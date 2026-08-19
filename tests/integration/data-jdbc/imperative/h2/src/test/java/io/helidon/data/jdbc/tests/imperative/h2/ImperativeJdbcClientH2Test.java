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
package io.helidon.data.jdbc.tests.imperative.h2;

import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.DataException;
import io.helidon.data.NoResultException;
import io.helidon.data.NonUniqueResultException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.tests.database.H2Database;
import io.helidon.data.jdbc.tests.support.TestConfigFactory;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("helidon:api:preview")
class ImperativeJdbcClientH2Test {
    @BeforeEach
    void configureH2() {
        TestConfigFactory.config(H2Database.config());
    }

    /**
     * Proves an imperative application resolves a qualified public client and
     * cannot reuse a terminal execution stage.
     */
    @Test
    void resolvesAQualifiedClientAndEnforcesTerminalLifecycle() {
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        try {
            Qualifier provider = Qualifier.builder()
                    .typeName(Data.ProviderType.TYPE)
                    .value("jdbc")
                    .build();
            JdbcClient client = manager.registry()
                    .get(JdbcClient.class,
                         Qualifier.createNamed(Service.Named.DEFAULT_NAME),
                         provider);

            assertThat(openSessions(client), is(1L));
            assertThat(client.create("SELECT NAME FROM CONTACT WHERE ID = ?")
                               .bind(1, 1L)
                               .map(String.class)
                               .one(),
                       is("alpha"));
            assertThat(client.create("SELECT NAME FROM CONTACT WHERE ID = ?")
                               .bind(1, Long.MAX_VALUE)
                               .map(String.class)
                               .optional(),
                       is(Optional.empty()));
            assertThrows(NoResultException.class,
                         () -> client.create("SELECT NAME FROM CONTACT WHERE ID = ?")
                                 .bind(1, Long.MAX_VALUE)
                                 .map(String.class)
                                 .one());
            assertThrows(NonUniqueResultException.class,
                         () -> client.create("SELECT NAME FROM CONTACT")
                                 .map(String.class)
                                 .one());

            JdbcClient.Rows<String> rows = client.create("SELECT NAME FROM CONTACT ORDER BY ID")
                    .map(String.class);
            assertThat(rows.list(), is(List.of("alpha", "beta")));
            assertThrows(IllegalStateException.class, rows::list);
            assertThat(openSessions(client), is(1L));

            assertThrows(DataException.class,
                         () -> client.create("SELECT MISSING_COLUMN FROM CONTACT")
                                 .map(String.class)
                                 .list());
            assertThat(openSessions(client), is(1L));

            IllegalStateException mapperFailure = assertThrows(
                    IllegalStateException.class,
                    () -> client.create("SELECT NAME FROM CONTACT")
                            .map(row -> {
                                row.required(1, String.class);
                                throw new IllegalStateException("deliberate imperative mapper failure");
                            })
                            .list());
            assertThat(mapperFailure.getMessage(), is("deliberate imperative mapper failure"));
            assertThat(openSessions(client), is(1L));

            assertThat(client.create("SELECT NAME FROM CONTACT ORDER BY ID")
                               .map(String.class)
                               .list(),
                       is(List.of("alpha", "beta")));
        } finally {
            manager.shutdown();
        }
    }

    private static long openSessions(JdbcClient client) {
        return client.create("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS")
                .map(Long.class)
                .one();
    }
}
