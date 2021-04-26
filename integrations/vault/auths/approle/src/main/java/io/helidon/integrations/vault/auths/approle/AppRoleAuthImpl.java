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
