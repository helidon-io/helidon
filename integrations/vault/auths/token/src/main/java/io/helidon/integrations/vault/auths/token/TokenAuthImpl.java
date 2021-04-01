package io.helidon.integrations.vault.auths.token;

class TokenAuthImpl implements TokenAuth {
    private final TokenAuthRx delegate;

    TokenAuthImpl(TokenAuthRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public CreateToken.Response createToken(CreateToken.Request request) {
        return delegate.createToken(request).await();
    }

    @Override
    public RenewToken.Response renew(RenewToken.Request request) {
        return delegate.renew(request).await();
    }

    @Override
    public RevokeToken.Response revoke(RevokeToken.Request request) {
        return delegate.revoke(request).await();
    }

    @Override
    public CreateTokenRole.Response createTokenRole(CreateTokenRole.Request request) {
        return delegate.createTokenRole(request).await();
    }

    @Override
    public DeleteTokenRole.Response deleteTokenRole(DeleteTokenRole.Request request) {
        return delegate.deleteTokenRole(request).await();
    }

    @Override
    public RevokeAndOrphanToken.Response revokeAndOrphan(RevokeAndOrphanToken.Request request) {
        return delegate.revokeAndOrphan(request).await();
    }
}
