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

package io.helidon.integrations.vault.secrets.kv1;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.Secrets;
import io.helidon.integrations.vault.VaultOptionalResponse;

/**
 * Secrets for KV version 1 secrets engine.
 * All methods block the current thread. This implementation is not suitable for reactive programming.
 * Use {@link io.helidon.integrations.vault.secrets.kv1.Kv1SecretsRx} in reactive code.
 */
public interface Kv1Secrets extends Secrets {
    /**
     * Get a secret.
     *
     * @param path relative to the mount point, no leading slash
     * @return the secret if exists
     */
    default Optional<Secret> get(String path) {
        return get(GetKv1.Request.create(path))
                .entity()
                .map(Function.identity());
    }

    /**
     * Get a secret.
     *
     * @param request with secret's path
     * @return response with secret if found
     */
    VaultOptionalResponse<GetKv1.Response> get(GetKv1.Request request);

    /**
     * Create a new secret on the defined path.
     *
     * @param path relative to the mount point, no leading slash
     * @param newSecretValues values to use in the new secret
     * @return vault response
     */
    default CreateKv1.Response create(String path, Map<String, String> newSecretValues) {
        return create(CreateKv1.Request.builder()
                              .path(path)
                              .secretValues(newSecretValues));
    }

    /**
     * Create a new secret on the defined path.
     *
     * @param request with path and secret's values
     * @return vault response
     */
    CreateKv1.Response create(CreateKv1.Request request);

    /**
     * Update a secret on the defined path. The new values replace existing values.
     *
     * @param path relative to the mount point, no leading slash
     * @param newValues new values of the secret
     * @return vault response
     */
    default UpdateKv1.Response update(String path, Map<String, String> newValues) {
        return update(UpdateKv1.Request.builder()
                              .path(path)
                              .secretValues(newValues));
    }

    /**
     * Update a secret on the defined path. The new values replace existing values.
     *
     * @param request with secret's path and new values
     * @return vault response
     */
    UpdateKv1.Response update(UpdateKv1.Request request);

    /**
     * Delete the secret.
     *
     * @param path relative to the mount point, no leading slash
     * @return vault response
     */
    default DeleteKv1.Response delete(String path) {
        return delete(DeleteKv1.Request.builder()
                              .path(path));
    }

    /**
     * Delete the secret.
     *
     * @param request request with secret's path
     * @return vault response
     */
    DeleteKv1.Response delete(DeleteKv1.Request request);
}
