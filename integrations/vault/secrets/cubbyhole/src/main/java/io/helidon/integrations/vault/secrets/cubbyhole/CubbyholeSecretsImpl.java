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
