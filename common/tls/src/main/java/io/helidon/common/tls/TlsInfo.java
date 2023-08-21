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

/**
 * Provides additional info about the {@link Tls}.
 */
public interface TlsInfo {

    /**
     * Provides the list of the reloadable tls components.
     *
     * @return the list of the reloadable tls components
     */
    List<TlsReloadableComponent> reloadableComponents();

    /**
     * Provides the original trust manager.
     *
     * @return the original trust manager
     */
    X509TrustManager originalTrustManager();

    /**
     * Provides the original key manager.
     *
     * @return original key manager
     */
    X509KeyManager originalKeyManager();

    /**
     * Returns {@code true} if this info was explicitly configured.
     *
     * @return flag indicating whether this configuration was explicitly configured
     */
    default boolean explicitContext() {
        return true;
    }

}
