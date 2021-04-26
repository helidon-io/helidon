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

import io.helidon.integrations.vault.Engine;
import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.Secrets;
import io.helidon.integrations.vault.VaultOptionalResponse;

/**
 * Database secrets engine API.
 * <p>
 * All methods block the current thread. This implementation is not suitable for reactive programming.
 * Use {@link io.helidon.integrations.vault.secrets.database.DbSecretsRx} in reactive code.
 */
public interface DbSecrets extends Secrets {
    /**
     * Database secrets engine.
     * <p>
     * Documentation:
     * <a href="https://www.vaultproject.io/docs/secrets/databases">https://www.vaultproject.io/docs/secrets/databases</a>
     */
    Engine<DbSecretsRx> ENGINE = DbSecretsRx.ENGINE;

    /**
     * Create blocking DB secrets from its reactive counterpart.
     *
     * @param reactive reactive DB secrets
     * @return blocking DB secrets
     */
    static DbSecrets create(DbSecretsRx reactive) {
        return new DbSecretsImpl(reactive);
    }

    /**
     * List database connections.
     *
     * @param request request to list roles, path parameter is ignored
     * @return role names
     */
    @Override
    VaultOptionalResponse<ListSecrets.Response> list(ListSecrets.Request request);

    /**
     * Get credentials from the {@code /creds} endpoint.
     *
     * @param name name of the credentials
     * @return credentials
     */
    default Optional<DbCredentials> get(String name) {
        return get(DbGet.Request.builder()
                           .name(name))
                .entity()
                .map(Function.identity());
    }

    /**
     * Get credentials from the {@code /creds} endpoint.
     *
     * @param request request with at least the name
     * @return get DB response
     */
    VaultOptionalResponse<DbGet.Response> get(DbGet.Request request);

    /**
     * Create or update a role definition.
     *
     * @param request role request
     * @return when the role is created
     */
    DbCreateRole.Response createRole(DbCreateRole.Request request);

    /**
     * Configure a database.
     *
     * @param request configuration options - see specific database types
     * @return when the database is configured
     */
    DbConfigure.Response configure(DbConfigure.Request<?> request);

    /**
     * Delete a database configuration.
     *
     * @param name name of the database configuration
     * @return when the database configuration is deleted
     */
    default DbDelete.Response delete(String name) {
        return delete(DbDelete.Request.builder()
                              .name(name));
    }

    /**
     * Delete a database configuration.
     *
     * @param request delete request with at least name configured
     * @return delete database configuration response
     */
    DbDelete.Response delete(DbDelete.Request request);

    /**
     * Delete a database role.
     *
     * @param name name of the role
     * @return when the role is deleted
     */
    default DbDeleteRole.Response deleteRole(String name) {
        return deleteRole(DbDeleteRole.Request.builder()
                                  .name(name));
    }

    /**
     * Delete a database role.
     *
     * @param request request with at least the role name configured
     * @return delete database role response
     */
    DbDeleteRole.Response deleteRole(DbDeleteRole.Request request);
}
