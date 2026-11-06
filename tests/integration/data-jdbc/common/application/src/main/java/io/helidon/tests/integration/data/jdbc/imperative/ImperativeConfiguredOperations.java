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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.config.Config;
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

    private final Supplier<JdbcClient.Statement> find;
    private final Supplier<JdbcClient.Statement> rename;
    private final Supplier<JdbcClient.Statement> insert;
    private final Supplier<JdbcClient.Statement> byId;
    private final Supplier<JdbcClient.Statement> all;
    private final Supplier<JdbcClient.Statement> aliases;
    private final Supplier<JdbcClient.Statement> aliasInsert;
    private final Supplier<JdbcClient.Statement> aliasUpdate;

    @Service.Inject
    ImperativeConfiguredOperations(@Data.ProviderType("jdbc")
                                   @Service.Named(Service.Named.DEFAULT_NAME) JdbcClient client,
                                   Config config) {
        this.find = client.prepare(config.get("application.sql.find").asString().get(), List.of("name", "email"));
        this.rename = client.prepare(config.get("application.sql.rename").asString().get(), List.of("name", "id"));
        this.insert = client.prepare(config.get("application.sql.insert").asString().get(), List.of("value"));
        this.byId = client.prepare(config.get("application.sql.by-id").asString().get(), List.of("id"));
        this.all = client.prepare(config.get("reporting.catalog.contacts.all").asString().get());
        this.aliases = client.prepare(config.get("application.aliases.find").asString().get(), List.of("email"));
        this.aliasInsert = client.prepare(config.get("application.aliases.insert").asString().get(), List.of("rows", "email"));
        this.aliasUpdate = client.prepare(config.get("application.aliases.update").asString().get(), List.of("id", "name"));
    }

    @Override
    public List<ContactView> find(String name, String email) {
        return find.get().bind("name", name).bind("email", email)
                .map(row -> new ContactView(row.get("ID", Long.class), row.get("NAME", String.class),
                        row.optional("EMAIL", String.class))).list();
    }

    @Override
    public Optional<ContactView> byId(long id) {
        return byId.get().bind("id", id)
                .map(row -> new ContactView(row.get("ID", Long.class), row.get("NAME", String.class),
                        row.optional("EMAIL", String.class))).optional();
    }

    @Override
    public List<ContactView> all() {
        return all.get().map(row -> new ContactView(row.get("ID", Long.class), row.get("NAME", String.class),
                row.optional("EMAIL", String.class))).list();
    }

    @Override
    public long rename(String name, long id) {
        return rename.get().bind("name", name).bind("id", id).execute();
    }

    @Override
    public Optional<Long> insert(String value) {
        return insert.get().bind("value", value).generatedKeys().addColumn("id")
                .map(row -> row.get(1, Long.class)).optional();
    }

    @Override
    public List<String> configuredAliases(String email) {
        Objects.requireNonNull(email, "The email must not be null.");
        return aliases.get().bind("email", email).map(String.class).list();
    }

    @Override
    public Optional<Long> configuredAliasInsert(String name, String email) {
        Objects.requireNonNull(name, "The contact name must not be null.");
        Objects.requireNonNull(email, "The email must not be null.");
        return aliasInsert.get().bind("rows", name).bind("email", email).generatedKeys().addColumn("id")
                .map(row -> row.get(1, Long.class)).optional();
    }

    @Override
    public int configuredAliasUpdate(long id, String name) {
        Objects.requireNonNull(name, "The contact name must not be null.");
        return Math.toIntExact(aliasUpdate.get().bind("id", id).bind("name", name).execute());
    }
}
