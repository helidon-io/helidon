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

import java.util.Objects;

import javax.net.ssl.X509TrustManager;

/**
 * A {@link javax.net.ssl.TrustManager} that is both a {@link javax.net.ssl.X509TrustManager} as well as a
 * {@link TlsReloadableComponent}.
 */
public abstract class TlsReloadableX509TrustManager implements X509TrustManager, TlsReloadableComponent {

    /**
     * Default constructor.
     */
    protected TlsReloadableX509TrustManager() {
    }

    /**
     * Creates a new reloadable trust manager.
     *
     * @param trustManager the underlying trust manager
     * @return a reloadable trust manager
     */
    public static TlsReloadableX509TrustManager create(X509TrustManager trustManager) {
        return new TlsInternalReloadableX509TrustManager(Objects.requireNonNull(trustManager));
    }

    /**
     * Creates a new reloadable trust manager.
     *
     * @return a reloadable trust manager
     */
    public static TlsReloadableX509TrustManager create() {
        return new TlsInternalReloadableX509TrustManager(null);
    }

    /**
     * Establishes a new trust manager delegate.
     *
     * @param trustManager the new trust manager
     */
    public abstract void trustManager(X509TrustManager trustManager);

}
