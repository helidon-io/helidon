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
