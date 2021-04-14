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

package io.helidon.integrations.oci.vault;

import io.helidon.integrations.common.rest.ApiOptionalResponse;

public interface OciVault {
    static OciVault create(OciVaultRx reactive) {
        return new OciVaultImpl(reactive);
    }

    /**
     * Gets information about the specified secret.
     *
     * @param request get secret request
     * @return future with secret response or exception
     */
    ApiOptionalResponse<Secret> getSecret(GetSecret.Request request);

    /**
     * Create a new secret.
     *
     * @param request create secret request
     * @return future with create secret response or exception
     */
    CreateSecret.Response createSecret(CreateSecret.Request request);

    /**
     * Gets information about the specified secret.
     *
     * @param request get secret bundle request
     * @return future with response or error
     */
    ApiOptionalResponse<GetSecretBundle.Response> getSecretBundle(GetSecretBundle.Request request);

    /**
     * Schedules a secret deletion.
     *
     * @param request delete secret request
     * @return future with response or error
     */
    DeleteSecret.Response deleteSecret(DeleteSecret.Request request);

    /**
     * Encrypt data.
     *
     * @param request encryption request
     * @return future with encrypted data
     */
    Encrypt.Response encrypt(Encrypt.Request request);

    /**
     * Decrypt data.
     *
     * @param request decryption request
     * @return future with decrypted data
     */
    Decrypt.Response decrypt(Decrypt.Request request);

    /**
     * Sign a message.
     *
     * @param request signature request
     * @return signature response
     */
    Sign.Response sign(Sign.Request request);

    /**
     * Verify a message signature.
     *
     * @param request verification request
     * @return verification response
     */
    Verify.Response verify(Verify.Request request);

    /**
     * Get key metadata.
     *
     * @param request get key request
     * @return get key response
     */
    ApiOptionalResponse<GetKey.Response> getKey(GetKey.Request request);

    /**
     * Get Vault metadata.
     *
     * @param request get vault request
     * @return get vault response
     */
    ApiOptionalResponse<GetVault.Response> getVault(GetVault.Request request);
}
