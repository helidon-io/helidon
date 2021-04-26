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
