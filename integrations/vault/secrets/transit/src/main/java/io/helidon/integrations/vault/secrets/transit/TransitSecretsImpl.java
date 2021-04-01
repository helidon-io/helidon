package io.helidon.integrations.vault.secrets.transit;

import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.VaultOptionalResponse;

class TransitSecretsImpl implements TransitSecrets {
    private final TransitSecretsRx delegate;

    TransitSecretsImpl(TransitSecretsRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public VaultOptionalResponse<ListSecrets.Response> list(ListSecrets.Request request) {
        return delegate.list(request).await();
    }

    @Override
    public CreateKey.Response createKey(CreateKey.Request request) {
        return delegate.createKey(request).await();
    }

    @Override
    public DeleteKey.Response deleteKey(DeleteKey.Request request) {
        return delegate.deleteKey(request).await();
    }

    @Override
    public UpdateKeyConfig.Response updateKeyConfig(UpdateKeyConfig.Request request) {
        return delegate.updateKeyConfig(request).await();
    }

    @Override
    public Encrypt.Response encrypt(Encrypt.Request request) {
        return delegate.encrypt(request).await();
    }

    @Override
    public EncryptBatch.Response encrypt(EncryptBatch.Request request) {
        return delegate.encrypt(request).await();
    }

    @Override
    public Decrypt.Response decrypt(Decrypt.Request request) {
        return delegate.decrypt(request).await();
    }

    @Override
    public DecryptBatch.Response decrypt(DecryptBatch.Request request) {
        return delegate.decrypt(request).await();
    }

    @Override
    public Hmac.Response hmac(Hmac.Request request) {
        return delegate.hmac(request).await();
    }

    @Override
    public Sign.Response sign(Sign.Request request) {
        return delegate.sign(request).await();
    }

    @Override
    public Verify.Response verify(Verify.Request request) {
        return delegate.verify(request).await();
    }
}
