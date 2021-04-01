/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault.secrets.database;

import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.reactive.Single;
import io.helidon.integrations.vault.Engine;
import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.SecretsRx;
import io.helidon.integrations.vault.VaultOptionalResponse;

/**
 * Database secrets engine API.
 * <p>
 * Documentation:
 * <a href="https://www.vaultproject.io/docs/secrets/databases">https://www.vaultproject.io/docs/secrets/databases</a>
 */
public interface DbSecretsRx extends SecretsRx {
    /**
     * Database secrets engine.
     * <p>
     * Documentation:
     * <a href="https://www.vaultproject.io/docs/secrets/databases">https://www.vaultproject.io/docs/secrets/databases</a>
     */
    Engine<DbSecretsRx> ENGINE = Engine.create(DbSecretsRx.class, "database", "database");

    /**
     * List database connections.
     *
     * @param request request to list roles, path parameter is ignored
     * @return role names
     */
    @Override
    Single<VaultOptionalResponse<ListSecrets.Response>> list(ListSecrets.Request request);

    /**
     * Get credentials from the {@code /creds} endpoint.
     *
     * @param name name of the credentials
     * @return credentials
     */
    default Single<Optional<DbCredentials>> get(String name) {
        return get(DbGet.Request.builder()
                           .name(name))
                .map(it -> it.entity().map(Function.identity()));
    }

    Single<VaultOptionalResponse<DbGet.Response>> get(DbGet.Request request);

    /**
     * Create or update a role definition.
     *
     * @param request role request
     * @return when the role is created
     */
    Single<DbCreateRole.Response> createRole(DbCreateRole.Request request);

    /**
     * Configure a database.
     *
     * @param dbRequest configuration options - see specific database types
     * @return when the database is configured
     */
    Single<DbConfigure.Response> configure(DbConfigure.Request<?> dbRequest);

    /**
     * Delete a database configuration.
     *
     * @param name name of the database configuration
     * @return when the database configuration is deleted
     */
    default Single<DbDelete.Response> delete(String name) {
        return delete(DbDelete.Request.builder()
                              .name(name));
    }

    Single<DbDelete.Response> delete(DbDelete.Request request);

    /**
     * Delete a database role.
     *
     * @param name name of the role
     * @return when the role is deleted
     */
    default Single<DbDeleteRole.Response> deleteRole(String name) {
        return deleteRole(DbDeleteRole.Request.builder()
                                  .name(name));
    }

    Single<DbDeleteRole.Response> deleteRole(DbDeleteRole.Request request);
}
