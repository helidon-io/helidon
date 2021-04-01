package io.helidon.integrations.vault.auths.approle;

import io.helidon.integrations.vault.VaultOptionalResponse;

class AppRoleAuthImpl implements AppRoleAuth {
    private final AppRoleAuthRx delegate;

    AppRoleAuthImpl(AppRoleAuthRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public CreateAppRole.Response createAppRole(CreateAppRole.Request request) {
        return delegate.createAppRole(request).await();
    }

    @Override
    public DeleteAppRole.Response deleteAppRole(DeleteAppRole.Request request) {
        return delegate.deleteAppRole(request).await();
    }

    @Override
    public VaultOptionalResponse<ReadRoleId.Response> readRoleId(ReadRoleId.Request request) {
        return delegate.readRoleId(request).await();
    }

    @Override
    public GenerateSecretId.Response generateSecretId(GenerateSecretId.Request request) {
        return delegate.generateSecretId(request).await();
    }

    @Override
    public DestroySecretId.Response destroySecretId(DestroySecretId.Request request) {
        return delegate.destroySecretId(request).await();
    }

    @Override
    public Login.Response login(Login.Request request) {
        return delegate.login(request).await();
    }
}
