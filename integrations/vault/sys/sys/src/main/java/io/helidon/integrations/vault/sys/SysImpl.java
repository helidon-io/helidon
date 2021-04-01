package io.helidon.integrations.vault.sys;

class SysImpl implements Sys {
    private final SysRx delegate;

    SysImpl(SysRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public EnableEngine.Response enableEngine(EnableEngine.Request request) {
        return delegate.enableEngine(request).await();
    }

    @Override
    public DisableEngine.Response disableEngine(DisableEngine.Request request) {
        return delegate.disableEngine(request).await();
    }

    @Override
    public EnableAuth.Response enableAuth(EnableAuth.Request request) {
        return delegate.enableAuth(request).await();
    }

    @Override
    public DisableAuth.Response disableAuth(DisableAuth.Request request) {
        return delegate.disableAuth(request).await();
    }

    @Override
    public CreatePolicy.Response createPolicy(CreatePolicy.Request request) {
        return delegate.createPolicy(request).await();
    }

    @Override
    public DeletePolicy.Response deletePolicy(DeletePolicy.Request request) {
        return delegate.deletePolicy(request).await();
    }
}
