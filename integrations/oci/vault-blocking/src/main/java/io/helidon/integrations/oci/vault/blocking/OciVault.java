package io.helidon.integrations.oci.vault.blocking;

import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.vault.CreateSecret;
import io.helidon.integrations.oci.vault.Decrypt;
import io.helidon.integrations.oci.vault.DeleteSecret;
import io.helidon.integrations.oci.vault.Encrypt;
import io.helidon.integrations.oci.vault.GetKey;
import io.helidon.integrations.oci.vault.GetSecret;
import io.helidon.integrations.oci.vault.GetSecretBundle;
import io.helidon.integrations.oci.vault.GetVault;
import io.helidon.integrations.oci.vault.Secret;
import io.helidon.integrations.oci.vault.Sign;
import io.helidon.integrations.oci.vault.Verify;

public interface OciVault {
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
