package io.helidon.integrations.vault.auths.k8s;

class K8sAuthImpl implements K8sAuth {
    private final K8sAuthRx delegate;

    K8sAuthImpl(K8sAuthRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public CreateRole.Response createRole(CreateRole.Request request) {
        return delegate.createRole(request).await();
    }

    @Override
    public DeleteRole.Response deleteRole(DeleteRole.Request request) {
        return delegate.deleteRole(request).await();
    }

    @Override
    public Login.Response login(Login.Request request) {
        return delegate.login(request).await();
    }

    @Override
    public ConfigureK8s.Response configure(ConfigureK8s.Request request) {
        return delegate.configure(request).await();
    }
}
