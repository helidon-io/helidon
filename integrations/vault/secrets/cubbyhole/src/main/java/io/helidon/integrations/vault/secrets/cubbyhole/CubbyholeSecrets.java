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

import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.Secrets;
import io.helidon.integrations.vault.VaultOptionalResponse;

/**
 * Cubbyhole engine secrets API.
 * Cubbyhole secrets are scoped to the current token and are not visible by other users.
 * This is a blocking API that blocks the current thread for each method. DO NOT USE IN REACTIVE CODE.
 *
 * @see io.helidon.integrations.vault.secrets.cubbyhole.CubbyholeSecretsRx
 */
public interface CubbyholeSecrets extends Secrets {
    /**
     * Create a new instance of blocking API for Cubbyhole secrets from
     * its reactive counterpart.
     * In an environment supporting injection, an instance can be injected and
     * this method should never be called.
     *
     * @param reactiveSecrets reactive Cubbyhole secrets
     * @return blocking Cubbyhole secrets
     */
    static CubbyholeSecrets create(CubbyholeSecretsRx reactiveSecrets) {
        return new CubbyholeSecretsImpl(reactiveSecrets);
    }

    /**
     * Get a Cubbyhole secret.
     *
     * @param path secret's path
     * @return secret if found
     */
    default Optional<Secret> get(String path) {
        return get(GetCubbyhole.Request.builder().path(path))
                .entity()
                .map(Function.identity());
    }

    /**
     * Create a Cubbyhole secret.
     *
     * @param path secret's path
     * @param values value of the new secret
     * @return vault response
     */
    default CreateCubbyhole.Response create(String path, Map<String, String> values) {
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
    default DeleteCubbyhole.Response delete(String path) {
        return delete(DeleteCubbyhole.Request.builder()
                              .path(path));
    }

    /**
     * Get a secret.
     *
     * @param request get cubbyhole request
     * @return the secret if exists
     */
    VaultOptionalResponse<GetCubbyhole.Response> get(GetCubbyhole.Request request);

    /**
     * Create a new secret on the defined path.
     *
     * @param request create cubbyhole request
     * @return vault response
     */
    CreateCubbyhole.Response create(CreateCubbyhole.Request request);

    /**
     * Update a secret on the defined path. The new values replace existing values.
     *
     * @param request update request (same as create request)
     * @return vault response
     */
    UpdateCubbyhole.Response update(UpdateCubbyhole.Request request);

    /**
     * Delete the secret.
     *
     * @param request delete request
     * @return vault response
     */
    DeleteCubbyhole.Response delete(DeleteCubbyhole.Request request);
}
