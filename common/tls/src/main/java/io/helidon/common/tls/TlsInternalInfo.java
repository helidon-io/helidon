/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.common.tls;

import java.util.List;

import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

class TlsInternalInfo implements TlsInfo {
    private final boolean explicitContext;
    private final List<TlsReloadableComponent> reloadableComponents;
    private final X509TrustManager originalTrustManager;
    private final X509KeyManager originalKeyManager;

    TlsInternalInfo(boolean explicitContext,
                    List<TlsReloadableComponent> reloadableComponents,
                    X509TrustManager originalTrustManager,
                    X509KeyManager originalKeyManager) {
        this.explicitContext = explicitContext;
        this.reloadableComponents = reloadableComponents;
        this.originalTrustManager = originalTrustManager;
        this.originalKeyManager = originalKeyManager;
    }

    @Override
    public List<TlsReloadableComponent> reloadableComponents() {
        return reloadableComponents;
    }

    @Override
    public X509TrustManager originalTrustManager() {
        return originalTrustManager;
    }

    @Override
    public X509KeyManager originalKeyManager() {
        return originalKeyManager;
    }

    @Override
    public boolean explicitContext() {
        return explicitContext;
    }

}
