package io.helidon.integrations.vault.secrets.database;

import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.VaultOptionalResponse;

class DbSecretsImpl implements DbSecrets {
    private final DbSecretsRx delegate;

    DbSecretsImpl(DbSecretsRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public VaultOptionalResponse<ListSecrets.Response> list(ListSecrets.Request request) {
        return delegate.list(request).await();
    }

    @Override
    public VaultOptionalResponse<DbGet.Response> get(DbGet.Request request) {
        return delegate.get(request).await();
    }

    @Override
    public DbCreateRole.Response createRole(DbCreateRole.Request request) {
        return delegate.createRole(request).await();
    }

    @Override
    public DbConfigure.Response configure(DbConfigure.Request<?> request) {
        return delegate.configure(request).await();
    }

    @Override
    public DbDelete.Response delete(DbDelete.Request request) {
        return delegate.delete(request).await();
    }

    @Override
    public DbDeleteRole.Response deleteRole(DbDeleteRole.Request request) {
        return delegate.deleteRole(request).await();
    }
}
