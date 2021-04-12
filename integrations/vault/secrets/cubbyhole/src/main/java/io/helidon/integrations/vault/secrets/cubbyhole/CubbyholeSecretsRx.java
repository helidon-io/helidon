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

package io.helidon.integrations.vault.secrets.cubbyhole;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.reactive.Single;
import io.helidon.integrations.vault.Engine;
import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.SecretsRx;
import io.helidon.integrations.vault.VaultOptionalResponse;

/**
 * Cubbyhole engine secrets API.
 * Cubbyhole secrets are scoped to the current token and are not visible by other users.
 *
 * @see io.helidon.integrations.vault.Vault#secrets(io.helidon.integrations.vault.Engine)
 */
public interface CubbyholeSecretsRx extends SecretsRx {
    /**
     * Cubbyhole (token scoped) secrets engine.
     * <p>
     * Documentation:
     * <a href="https://www.vaultproject.io/docs/secrets/cubbyhole">https://www.vaultproject.io/docs/secrets/cubbyhole</a>
     */
    Engine<CubbyholeSecretsRx> ENGINE = Engine.create(CubbyholeSecretsRx.class, "cubbyhole", "cubbyhole");

    /**
     * Get a Cubbyhole secret.
     *
     * @param path secret's path
     * @return secret if found
     */
    default Single<Optional<Secret>> get(String path) {
        return get(GetCubbyhole.Request.builder().path(path))
                .map(it -> it.entity().map(Function.identity()));
    }

    /**
     * Create a Cubbyhole secret.
     *
     * @param path secret's path
     * @param values value of the new secret
     * @return vault response
     */
    default Single<CreateCubbyhole.Response> create(String path, Map<String, String> values) {
        return create(CreateCubbyhole.Request.builder()
                              .path(path)
                              .secretValues(values));
    }

    /**
     * Delete a Cubbyhole secret.
     *
     * @param path secret's path
     * @return vault response
     */
    default Single<DeleteCubbyhole.Response> delete(String path) {
        return delete(DeleteCubbyhole.Request.builder()
                              .path(path));
    }

    /**
     * Get a secret.
     *
     * @param request get cubbyhole request
     * @return the secret if exists
     */
    Single<VaultOptionalResponse<GetCubbyhole.Response>> get(GetCubbyhole.Request request);

    /**
     * Create a new secret on the defined path.
     *
     * @param request create cubbyhole request
     * @return vault response
     */
    Single<CreateCubbyhole.Response> create(CreateCubbyhole.Request request);

    /**
     * Update a secret on the defined path. The new values replace existing values.
     *
     * @param request update request (same as create request)
     * @return vault response
     */
    Single<UpdateCubbyhole.Response> update(UpdateCubbyhole.Request request);

    /**
     * Delete the secret.
     *
     * @param request delete request
     * @return vault response
     */
    Single<DeleteCubbyhole.Response> delete(DeleteCubbyhole.Request request);
}
