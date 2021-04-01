package io.helidon.integrations.vault.secrets.cubbyhole;

import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.VaultOptionalResponse;
import io.helidon.integrations.vault.VaultRestException;

class CubbyholeSecretsImpl implements CubbyholeSecrets {
    private final CubbyholeSecretsRx delegate;

    CubbyholeSecretsImpl(CubbyholeSecretsRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public VaultOptionalResponse<GetCubbyhole.Response> get(GetCubbyhole.Request request) {
        return delegate.get(request).await();
    }

    @Override
    public CreateCubbyhole.Response create(CreateCubbyhole.Request request) throws VaultRestException {
        return delegate.create(request).await();
    }

    @Override
    public UpdateCubbyhole.Response update(UpdateCubbyhole.Request request) {
        return delegate.update(request).await();
    }

    @Override
    public DeleteCubbyhole.Response delete(DeleteCubbyhole.Request request) {
        return delegate.delete(request).await();
    }

    @Override
    public VaultOptionalResponse<ListSecrets.Response> list(ListSecrets.Request request) {
        return delegate.list(request).await();
    }
}
