/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
