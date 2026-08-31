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
package io.helidon.data.jdbc.tests.chaos.support;

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.tests.chaos.application.ChaosSql;
import io.helidon.service.registry.Service;

/**
 * Resets chaos application state and reads committed state outside the adapter under test.
 */
@SuppressWarnings("helidon:api:preview")
@Service.Singleton
public final class ChaosDatabaseFixture {
    private final JdbcClient client;

    /**
     * Creates the chaos database fixture for the default registry-managed JDBC client.
     *
     * @param client qualified JDBC client
     */
    @Service.Inject
    ChaosDatabaseFixture(@Data.ProviderType("jdbc")
                         @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client) {
        this.client = client;
    }

    /**
     * Restores deterministic baseline rows before each chaos smoke scenario.
     */
    public void resetContacts() {
        client.create(ChaosSql.RESET_GENERATED).execute();
        client.create(ChaosSql.RESET_CONTACTS).execute();
        client.create(ChaosSql.RESTORE_CONTACT)
                .bind(1, 1L)
                .bind(2, "alpha")
                .execute();
        client.create(ChaosSql.RESTORE_CONTACT)
                .bind(1, 2L)
                .bind(2, "beta")
                .execute();
    }

    /**
     * Counts committed rows through fixture-owned JDBC work outside the adapter under test.
     *
     * @return committed contact count
     */
    public long committedContactCount() {
        return client.create(ChaosSql.COUNT_CONTACTS).map(Long.class).one();
    }

    /**
     * Counts committed rows by contact name outside the adapter under test.
     *
     * @param name contact name
     * @return committed contact count
     */
    public long committedContactCountByName(String name) {
        return client.create(ChaosSql.COUNT_CONTACTS_BY_NAME)
                .bind(1, name)
                .map(Long.class)
                .one();
    }

    /**
     * Counts committed generated-key rows by contact name outside the adapter under test.
     *
     * @param name generated-row name
     * @return committed generated-row count
     */
    public long committedGeneratedCountByName(String name) {
        return client.create(ChaosSql.COUNT_GENERATED_BY_NAME)
                .bind(1, name)
                .map(Long.class)
                .one();
    }
}
