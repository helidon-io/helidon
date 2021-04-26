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
