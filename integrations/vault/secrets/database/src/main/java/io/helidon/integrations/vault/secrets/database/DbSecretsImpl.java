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
