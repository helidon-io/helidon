package io.helidon.integrations.oci.vault;

import io.helidon.integrations.common.rest.ApiOptionalResponse;

class OciVaultImpl implements OciVault {
    private final OciVaultRx delegate;

    OciVaultImpl(OciVaultRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public ApiOptionalResponse<Secret> getSecret(GetSecret.Request request) {
        return delegate.getSecret(request).await();
    }

    @Override
    public CreateSecret.Response createSecret(CreateSecret.Request request) {
        return delegate.createSecret(request).await();
    }

    @Override
    public ApiOptionalResponse<GetSecretBundle.Response> getSecretBundle(GetSecretBundle.Request request) {
        return delegate.getSecretBundle(request).await();
    }

    @Override
    public DeleteSecret.Response deleteSecret(DeleteSecret.Request request) {
        return delegate.deleteSecret(request).await();
    }

    @Override
    public Encrypt.Response encrypt(Encrypt.Request request) {
        return delegate.encrypt(request).await();
    }

    @Override
    public Decrypt.Response decrypt(Decrypt.Request request) {
        return delegate.decrypt(request).await();
    }

    @Override
    public Sign.Response sign(Sign.Request request) {
        return delegate.sign(request).await();
    }

    @Override
    public Verify.Response verify(Verify.Request request) {
        return delegate.verify(request).await();
    }

    @Override
    public ApiOptionalResponse<GetKey.Response> getKey(GetKey.Request request) {
        return delegate.getKey(request).await();
    }

    @Override
    public ApiOptionalResponse<GetVault.Response> getVault(GetVault.Request request) {
        return delegate.getVault(request).await();
    }
}
