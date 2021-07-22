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
