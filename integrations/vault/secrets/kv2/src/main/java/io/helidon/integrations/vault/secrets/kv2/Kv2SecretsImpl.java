package io.helidon.integrations.vault.secrets.kv2;

import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.VaultOptionalResponse;

class Kv2SecretsImpl implements Kv2Secrets {
    private final Kv2SecretsRx delegate;

    Kv2SecretsImpl(Kv2SecretsRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public VaultOptionalResponse<GetKv2.Response> get(GetKv2.Request request) {
        return delegate.get(request).await();
    }

    @Override
    public UpdateKv2.Response update(UpdateKv2.Request request) {
        return delegate.update(request).await();
    }

    @Override
    public CreateKv2.Response create(CreateKv2.Request request) {
        return delegate.create(request).await();
    }

    @Override
    public DeleteKv2.Response delete(DeleteKv2.Request request) {
        return delegate.delete(request).await();
    }

    @Override
    public UndeleteKv2.Response undelete(UndeleteKv2.Request request) {
        return delegate.undelete(request).await();
    }

    @Override
    public DestroyKv2.Response destroy(DestroyKv2.Request request) {
        return delegate.destroy(request).await();
    }

    @Override
    public DeleteAllKv2.Response deleteAll(DeleteAllKv2.Request request) {
        return delegate.deleteAll(request).await();
    }

    @Override
    public VaultOptionalResponse<ListSecrets.Response> list(ListSecrets.Request request) {
        return delegate.list(request).await();
    }
}
