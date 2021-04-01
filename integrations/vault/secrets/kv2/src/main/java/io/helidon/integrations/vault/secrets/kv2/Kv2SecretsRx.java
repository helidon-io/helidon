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
import java.util.function.Function;

import io.helidon.common.reactive.Single;
import io.helidon.integrations.vault.Engine;
import io.helidon.integrations.vault.SecretsRx;
import io.helidon.integrations.vault.VaultOptionalResponse;

/**
 * Reactive API for secrets for KV version 2 secrets engine.
 *
 * @see Kv2SecretsRx#ENGINE
 * @see io.helidon.integrations.vault.Vault#secrets(io.helidon.integrations.vault.Engine)
 */
public interface Kv2SecretsRx extends SecretsRx {
    /**
     * KV (Key/Value) secrets engine version 2.
     * <p>
     * Documentation:
     * <a href="https://www.vaultproject.io/docs/secrets/kv/kv-v2>https://www.vaultproject.io/docs/secrets/kv/kv-v2</a>
     */
    Engine<Kv2SecretsRx> ENGINE = Engine.create(Kv2SecretsRx.class, "kv", "secret", "2");

    /**
     * Get the latest version of a secret.
     *
     * @param path relative to the mount point, no leading slash
     * @return the secret
     */
    default Single<Optional<Kv2Secret>> get(String path) {
        return get(GetKv2.Request.create(path))
                .map(VaultOptionalResponse::entity)
                // GetKv2.Response implements Kv2Secret, to satisfy generic signature, we must do this
                .map(it -> it.map(Function.identity()));
    }

    /**
     * Get a version of a secret.
     *
     * @param path relative to the mount point, no leading slash
     * @param version version to retrieve
     * @return the secret
     */
    default Single<Optional<Kv2Secret>> get(String path, int version) {
        return get(GetKv2.Request.builder()
                           .path(path)
                           .version(version))
                .map(VaultOptionalResponse::entity)
                .map(it -> it.map(Function.identity()));
    }

    Single<VaultOptionalResponse<GetKv2.Response>> get(GetKv2.Request request);

    /**
     * Update a secret on the defined path. The new values replace existing values.
     *
     * @param path relative to the mount point, no leading slash
     * @param newValues new values of the secret
     * @return the version created
     */
    default Single<Integer> update(String path, Map<String, String> newValues) {
        return update(UpdateKv2.Request.builder()
                              .path(path)
                              .secretValues(newValues))
                .map(UpdateKv2.Response::version);
    }

    /**
     * Update a secret on the defined path. The new values replace existing values.
     *
     * @param path relative to the mount point, no leading slash
     * @param newValues new values of the secret
     * @param expectedVersion expected latest version
     * @return the version created
     */
    default Single<Integer> update(String path, Map<String, String> newValues, int expectedVersion) {
        return update(UpdateKv2.Request.builder()
                              .path(path)
                              .secretValues(newValues)
                              .expectedVersion(expectedVersion))
                .map(UpdateKv2.Response::version);
    }

    Single<UpdateKv2.Response> update(UpdateKv2.Request request);

    /**
     * Create a new secret on the defined path.
     *
     * @param path relative to the mount point, no leading slash
     * @param newSecretValues values to use in the new secret
     */
    default Single<CreateKv2.Response> create(String path, Map<String, String> newSecretValues) {
        return create(CreateKv2.Request.builder()
                              .path(path)
                              .secretValues(newSecretValues));
    }

    Single<CreateKv2.Response> create(CreateKv2.Request request);

    /**
     * Delete specific versions of a secret.
     *
     * @param path relative to the mount point, no leading slash
     * @param versions versions to delete
     * @return
     */
    default Single<DeleteKv2.Response> delete(String path, int... versions) {
        return delete(DeleteKv2.Request.builder()
                              .path(path)
                              .versions(versions));
    }

    Single<DeleteKv2.Response> delete(DeleteKv2.Request request);

    /**
     * Undelete deleted versions of a secret.
     * This method can be called repeatedly and even on non-existent versions without throwing an exception.
     *
     * @param path relative to the mount point, no leading slash
     * @param versions versions to undelete
     */
    default Single<UndeleteKv2.Response> undelete(String path, int... versions) {
        return undelete(UndeleteKv2.Request.builder()
                                .path(path)
                                .versions(versions));
    }

    Single<UndeleteKv2.Response> undelete(UndeleteKv2.Request request);

    /**
     * Permanently remove specific versions of a secret.
     * This method can be called repeatedly and even on non-existent versions without throwing an exception.
     *
     * @param path relative to the mount point, no leading slash
     * @param versions versions to destroy
     */
    default Single<DestroyKv2.Response> destroy(String path, int... versions) {
        return destroy(DestroyKv2.Request.builder()
                               .path(path)
                               .versions(versions));
    }

    Single<DestroyKv2.Response> destroy(DestroyKv2.Request request);

    /**
     * Delete the secret and all its versions permanently.
     * This method can be called repeatedly and even on non-existent versions without throwing an exception.
     *
     * @param path relative to the mount point, no leading slash
     */
    default Single<DeleteAllKv2.Response> deleteAll(String path) {
        return deleteAll(DeleteAllKv2.Request.builder()
                                 .path(path));
    }

    Single<DeleteAllKv2.Response> deleteAll(DeleteAllKv2.Request request);
}
