package io.helidon.integrations.vault.secrets.kv1;

import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.VaultOptionalResponse;

class Kv1SecretsImpl implements Kv1Secrets {
    private final Kv1SecretsRx delegate;

    Kv1SecretsImpl(Kv1SecretsRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public VaultOptionalResponse<GetKv1.Response> get(GetKv1.Request request) {
        return delegate.get(request).await();
    }

    @Override
    public CreateKv1.Response create(CreateKv1.Request request) {
        return delegate.create(request).await();
    }

    @Override
    public UpdateKv1.Response update(UpdateKv1.Request request) {
        return delegate.update(request).await();
    }

    @Override
    public DeleteKv1.Response delete(DeleteKv1.Request request) {
        return delegate.delete(request).await();
    }

    @Override
    public VaultOptionalResponse<ListSecrets.Response> list(ListSecrets.Request request) {
        return delegate.list(request).await();
    }
}
