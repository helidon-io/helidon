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

package io.helidon.integrations.vault.secrets.kv2;

import java.util.Map;
import java.util.Optional;

import io.helidon.integrations.vault.Secrets;
import io.helidon.integrations.vault.VaultOptionalResponse;

/**
 * Secrets for KV version 2 secrets engine blocking implementation.
 * All methods block the current thread. This implementation is not suitable for reactive programming.
 * Use {@link io.helidon.integrations.vault.secrets.kv2.Kv2SecretsRx} in reactive code.
 */
public interface Kv2Secrets extends Secrets {
    /**
     * Create blocking Key/Vault secrets from its reactive counterpart.
     *
     * @param reactiveSecrets reactive secrets
     * @return blocking secrets
     */
    static Kv2Secrets create(Kv2SecretsRx reactiveSecrets) {
        return new Kv2SecretsImpl(reactiveSecrets);
    }

    /**
     * Get the latest version of a secret.
     *
     * @param path relative to the mount point, no leading slash
     * @return the secret
     */
    default Optional<Kv2Secret> get(String path) {
        return get(GetKv2.Request.create(path))
                .entity()
                .map(Kv2Secret.class::cast);
    }

    /**
     * Get a version of a secret.
     *
     * @param path relative to the mount point, no leading slash
     * @param version version to retrieve
     * @return the secret
     */
    default Optional<Kv2Secret> get(String path, int version) {
        return get(GetKv2.Request.builder()
                           .path(path)
                           .version(version))
                .entity()
                .map(Kv2Secret.class::cast);
    }

    /**
     * Get a version of a secret.
     *
     * @param request with secret's path and optional version
     * @return vault response with the secret if found
     */
    VaultOptionalResponse<GetKv2.Response> get(GetKv2.Request request);

    /**
     * Update a secret on the defined path. The new values replace existing values.
     *
     * @param path relative to the mount point, no leading slash
     * @param newValues new values of the secret
     * @return the version created
     */
    default Integer update(String path, Map<String, String> newValues) {
        return update(UpdateKv2.Request.builder()
                              .path(path)
                              .secretValues(newValues))
                .version();
    }

    /**
     * Update a secret on the defined path. The new values replace existing values.
     *
     * @param path relative to the mount point, no leading slash
     * @param newValues new values of the secret
     * @param expectedVersion expected latest version
     * @return the version created
     */
    default Integer update(String path, Map<String, String> newValues, int expectedVersion) {
        return update(UpdateKv2.Request.builder()
                              .path(path)
                              .secretValues(newValues)
                              .expectedVersion(expectedVersion))
                .version();
    }

    /**
     * Update a secret on the defined path. The new values replace existing values.
     *
     * @param request update request with path, new values and expected version
     * @return vault response with the version created
     */
    UpdateKv2.Response update(UpdateKv2.Request request);

    /**
     * Create a new secret on the defined path.
     *
     * @param path relative to the mount point, no leading slash
     * @param newSecretValues values to use in the new secret
     * @return vault response
     */
    default CreateKv2.Response create(String path, Map<String, String> newSecretValues) {
        return create(CreateKv2.Request.builder()
                              .path(path)
                              .secretValues(newSecretValues));
    }

    /**
     * Create a new secret.
     *
     * @param request request with path and values
     * @return create secret response
     */
    CreateKv2.Response create(CreateKv2.Request request);

    /**
     * Delete specific versions of a secret.
     *
     * @param path relative to the mount point, no leading slash
     * @param versions versions to delete
     * @return vault response
     */
    default DeleteKv2.Response delete(String path, int... versions) {
        return delete(DeleteKv2.Request.builder()
                              .path(path)
                              .versions(versions));
    }

    /**
     * Delete a secret version.
     *
     * @param request request with path and version(s)
     * @return delete secret response
     */
    DeleteKv2.Response delete(DeleteKv2.Request request);

    /**
     * Undelete deleted versions of a secret.
     * This method can be called repeatedly and even on non-existent versions without throwing an exception.
     *
     * @param path relative to the mount point, no leading slash
     * @param versions versions to undelete
     * @return vault response
     */
    default UndeleteKv2.Response undelete(String path, int... versions) {
        return undelete(UndeleteKv2.Request.builder()
                                .path(path)
                                .versions(versions));
    }

    /**
     * Undelete a secret version.
     *
     * @param request request with and and version(s)
     * @return undelete secret response
     */
    UndeleteKv2.Response undelete(UndeleteKv2.Request request);

    /**
     * Permanently remove specific versions of a secret.
     * This method can be called repeatedly and even on non-existent versions without throwing an exception.
     *
     * @param path relative to the mount point, no leading slash
     * @param versions versions to destroy
     * @return vault response
     */
    default DestroyKv2.Response destroy(String path, int... versions) {
        return destroy(DestroyKv2.Request.builder()
                               .path(path)
                               .versions(versions));
    }

    /**
     * Destroy a secret version.
     *
     * @param request request with path and version(s)
     * @return destroy secret response
     */
    DestroyKv2.Response destroy(DestroyKv2.Request request);

    /**
     * Delete the secret and all its versions permanently.
     * This method can be called repeatedly and even on non-existent versions without throwing an exception.
     *
     * @param path relative to the mount point, no leading slash
     * @return vault response
     */
    default DeleteAllKv2.Response deleteAll(String path) {
        return deleteAll(DeleteAllKv2.Request.builder()
                                 .path(path));
    }

    /**
     * Delete the secret and all its versions permanently.
     * This method can be called repeatedly and even on non-existent versions without throwing an exception.
     *
     * @param request with relative path to the mount point, no leading slash
     * @return vault response
     */
    DeleteAllKv2.Response deleteAll(DeleteAllKv2.Request request);
}
