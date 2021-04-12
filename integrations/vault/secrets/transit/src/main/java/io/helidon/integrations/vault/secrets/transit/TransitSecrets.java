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

package io.helidon.integrations.vault.secrets.transit;

import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.Secrets;
import io.helidon.integrations.vault.VaultOptionalResponse;

/**
 * API operations for Vault's Transit Secrets Engine.
 */
public interface TransitSecrets extends Secrets {
    /**
     * Create blocking transit secrets from its reactive counterpart.
     * This method should not be used when injection is available, as an instance
     * of this class can be injected.
     * This method should never be used in reactive environment, unless running
     * in an executor service (use the {@link io.helidon.integrations.vault.secrets.transit.TransitSecretsRx}
     * operations in reactive environment).
     *
     * @param reactive reactive transit secrets
     * @return blocking transit secrets
     */
    static TransitSecrets create(TransitSecretsRx reactive) {
        return new TransitSecretsImpl(reactive);
    }
    /**
     * List available keys.
     *
     * @param request list request, path is ignored
     * @return list of available keys
     */
    @Override
    VaultOptionalResponse<ListSecrets.Response> list(ListSecrets.Request request);

    /**
     * Creates a new named encryption key of the specified type.
     *
     * @param request create key request
     * @return future with response
     */
    CreateKey.Response createKey(CreateKey.Request request);

    /**
     * Delete a named ecryption key.
     * Deletion is not allowed by default,
     * {@link #updateKeyConfig(io.helidon.integrations.vault.secrets.transit.UpdateKeyConfig.Request)}
     * must be called before deleting.
     *
     * @param request delete key request
     * @return future with response
     */
    DeleteKey.Response deleteKey(DeleteKey.Request request);

    /**
     * Tune configuration of a key.
     *
     * @param request update configuration request
     * @return future with response
     * @see io.helidon.integrations.vault.secrets.transit.UpdateKeyConfig.Request#allowDeletion(boolean)
     */
    UpdateKeyConfig.Response updateKeyConfig(UpdateKeyConfig.Request request);

    /**
     * Encrypts the provided plaintext using the named key. This path supports the create and update policy
     * capabilities as follows: if the user has the create capability for this endpoint in their policies, and the key does not
     * exist, it will be upserted with default values (whether the key requires derivation depends on whether the context
     * parameter is empty or not). If the user only has update capability and the key does not exist, an error will be returned.
     *
     * @param request encrypt request
     * @return future with response
     */
    Encrypt.Response encrypt(Encrypt.Request request);

    /**
     * Encrypts the provided batch of plaintext strings using the named key. This path supports the create and
     * update policy capabilities as follows: if the user has the create capability for this endpoint in their policies, and
     * the key does not exist, it will be upserted with default values (whether the key requires derivation depends on whether
     * the context parameter is empty or not). If the user only has update capability and the key does not exist, an error will
     * be returned.
     *
     * @param request encrypt request
     * @return future with response
     */
    EncryptBatch.Response encrypt(EncryptBatch.Request request);

    /**
     * Decrypts the provided ciphertext using the named key.
     *
     * @param request decrypt request
     * @return future with response
     */
    Decrypt.Response decrypt(Decrypt.Request request);

    /**
     * Decrypts the provided batch of ciphertext strings using the named key.
     *
     * @param request decrypt request
     * @return future with response
     */
    DecryptBatch.Response decrypt(DecryptBatch.Request request);

    /**
     * Hmac of a message.
     * Equivalent of a signature when using symmetric keys.
     *
     * @param request hmac request
     * @return hmac response
     */
    Hmac.Response hmac(Hmac.Request request);

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
}
