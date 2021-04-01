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

import io.helidon.common.reactive.Single;
import io.helidon.integrations.vault.Engine;
import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.SecretsRx;
import io.helidon.integrations.vault.VaultOptionalResponse;

/**
 * Secrets for KV version 1 secrets engine.
 *
 * @see #ENGINE
 * @see io.helidon.integrations.vault.Vault#secrets(io.helidon.integrations.vault.Engine)
 */
public interface Kv1SecretsRx extends SecretsRx {
    /**
     * KV (Key/Value) secrets engine version 1.
     * <p>
     * Documentation:
     * <a href="https://www.vaultproject.io/docs/secrets/kv/kv-v1">https://www.vaultproject.io/docs/secrets/kv/kv-v1</a>
     */
    Engine<Kv1SecretsRx> ENGINE = Engine.create(Kv1SecretsRx.class, "kv", "kv1", "1");

    /**
     * Get a secret.
     *
     * @param path relative to the mount point, no leading slash
     * @return the secret if exists
     */
    default Single<Optional<Secret>> get(String path) {
        return get(GetKv1.Request.create(path))
                .map(VaultOptionalResponse::entity)
                .map(it -> it.map(Function.identity()));
    }

    Single<VaultOptionalResponse<GetKv1.Response>> get(GetKv1.Request request);

    /**
     * Create a new secret on the defined path.
     *
     * @param path relative to the mount point, no leading slash
     * @param newSecretValues values to use in the new secret
     */
    default Single<CreateKv1.Response> create(String path, Map<String, String> newSecretValues) {
        return create(CreateKv1.Request.builder()
                              .path(path)
                              .secretValues(newSecretValues));
    }

    Single<CreateKv1.Response> create(CreateKv1.Request request);

    /**
     * Update a secret on the defined path. The new values replace existing values.
     *
     * @param path relative to the mount point, no leading slash
     * @param newValues new values of the secret
     */
    default Single<UpdateKv1.Response> update(String path, Map<String, String> newValues) {
        return update(UpdateKv1.Request.builder()
                              .path(path)
                              .secretValues(newValues));
    }

    Single<UpdateKv1.Response> update(UpdateKv1.Request request);

    /**
     * Delete the secret.
     *
     * @param path relative to the mount point, no leading slash
     */
    default Single<DeleteKv1.Response> delete(String path) {
        return delete(DeleteKv1.Request.builder()
                              .path(path));
    }

    Single<DeleteKv1.Response> delete(DeleteKv1.Request request);
}
