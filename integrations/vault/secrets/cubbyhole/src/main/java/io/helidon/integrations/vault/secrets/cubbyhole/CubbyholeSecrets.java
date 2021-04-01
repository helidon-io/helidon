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
import io.helidon.integrations.vault.VaultRestException;

/**
 * Cubbyhole engine secrets API.
 * Cubbyhole secrets are scoped to the current token and are not visible by other users.
 * This is a blocking API that blocks the current thread for each method. DO NOT USE IN REACTIVE CODE.
 *
 * @see io.helidon.integrations.vault.secrets.cubbyhole.CubbyholeSecretsRx
 */
public interface CubbyholeSecrets extends Secrets {
    static CubbyholeSecrets create(CubbyholeSecretsRx reactiveSecrets) {
        return new CubbyholeSecretsImpl(reactiveSecrets);
    }

    default Optional<Secret> get(String path) {
        return get(GetCubbyhole.Request.builder().path(path))
                .entity()
                .map(Function.identity());
    }

    default CreateCubbyhole.Response create(String path, Map<String, String> values) {
        return create(CreateCubbyhole.Request.builder()
                              .path(path)
                              .secretValues(values));
    }

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
     */
    CreateCubbyhole.Response create(CreateCubbyhole.Request request) throws VaultRestException;

    /**
     * Update a secret on the defined path. The new values replace existing values.
     *
     * @param request update request (same as create request)
     * @throws io.helidon.integrations.vault.VaultRestException in case the secret does not exist or the API call fails
     */
    UpdateCubbyhole.Response update(UpdateCubbyhole.Request request);

    /**
     * Delete the secret.
     *
     * @param request delete request
     */
    DeleteCubbyhole.Response delete(DeleteCubbyhole.Request request);
}
