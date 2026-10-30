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
package io.helidon.tests.integration.data.jdbc.imperative;

import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Service;
import io.helidon.tests.integration.data.jdbc.application.ConfiguredOperations;
import io.helidon.tests.integration.data.jdbc.application.ContactView;

/**
 * Exercises configured definitions through the public imperative client.
 */
@Service.Singleton
public final class ImperativeConfiguredOperations implements ConfiguredOperations {

    private final JdbcClient client;

    @Service.Inject
    ImperativeConfiguredOperations(@Data.ProviderType("jdbc")
                                   @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client) {
        this.client = client;
    }

    @Override
    public List<ContactView> find(String name, String email) {
        return client.createNamed("find").bind("name", name).bind("email", email)
                .map(row -> new ContactView(row.get("ID", Long.class), row.get("NAME", String.class),
                        row.optional("EMAIL", String.class))).list();
    }

    @Override
    public long rename(String name, long id) {
        return client.createNamed("rename").bind("name", name).bind("id", id).execute();
    }

    @Override
    public Optional<Long> insert(String value) {
        return client.createNamed("insert").bind("value", value).generatedKeys().addColumn("id")
                .map(row -> row.get(1, Long.class)).optional();
    }
}
