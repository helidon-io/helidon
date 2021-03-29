package io.helidon.integrations.oci.vault.blocking;

import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.connect.OciRestApi;
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

class BlockingOciVaultImpl implements OciVault {
    private final io.helidon.integrations.oci.vault.OciVault reactiveVault;

    private BlockingOciVaultImpl(io.helidon.integrations.oci.vault.OciVault reactiveVault) {
        this.reactiveVault = reactiveVault;
    }

    static OciVault create(OciRestApi restApi, Config ociConfig) {
        io.helidon.integrations.oci.vault.OciVault reactiveVault = io.helidon.integrations.oci.vault.OciVault.builder()
                .restApi(restApi)
                .config(ociConfig)
                .build();

        return new BlockingOciVaultImpl(reactiveVault);
    }

    @Override
    public ApiOptionalResponse<Secret> getSecret(GetSecret.Request request) {
        return reactiveVault.getSecret(request).await();
    }

    @Override
    public CreateSecret.Response createSecret(CreateSecret.Request request) {
        return reactiveVault.createSecret(request).await();
    }

    @Override
    public ApiOptionalResponse<GetSecretBundle.Response> getSecretBundle(GetSecretBundle.Request request) {
        return reactiveVault.getSecretBundle(request).await();
    }

    @Override
    public DeleteSecret.Response deleteSecret(DeleteSecret.Request request) {
        return reactiveVault.deleteSecret(request).await();
    }

    @Override
    public Encrypt.Response encrypt(Encrypt.Request request) {
        return reactiveVault.encrypt(request).await();
    }

    @Override
    public Decrypt.Response decrypt(Decrypt.Request request) {
        return reactiveVault.decrypt(request).await();
    }

    @Override
    public Sign.Response sign(Sign.Request request) {
        return reactiveVault.sign(request).await();
    }

    @Override
    public Verify.Response verify(Verify.Request request) {
        return reactiveVault.verify(request).await();
    }

    @Override
    public ApiOptionalResponse<GetKey.Response> getKey(GetKey.Request request) {
        return reactiveVault.getKey(request).await();
    }

    @Override
    public ApiOptionalResponse<GetVault.Response> getVault(GetVault.Request request) {
        return reactiveVault.getVault(request).await();
    }
}
