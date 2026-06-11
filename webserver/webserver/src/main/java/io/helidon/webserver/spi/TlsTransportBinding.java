/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.spi;

import java.util.Objects;

import io.helidon.common.tls.TlsMaterial;

/**
 * Optional transport binding capability for bindings that apply listener TLS state.
 */
public interface TlsTransportBinding extends TransportBinding {
    /**
     * Whether this binding is secured with listener TLS.
     *
     * @return whether listener TLS is enabled
     */
    boolean hasTls();

    /**
     * Reload listener default TLS material for this binding.
     *
     * @param material new TLS material
     */
    void reloadTls(TlsMaterial material);

    /**
     * Reload TLS material for a listener virtual host.
     *
     * @param material new TLS material
     * @param configuredHost configured virtual host
     */
    default void reloadVirtualHostTls(TlsMaterial material, String configuredHost) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(configuredHost, "configuredHost");
        throw new UnsupportedOperationException("Listener virtual hosts are not supported");
    }

    /**
     * Whether this binding supports listener virtual hosts.
     *
     * @return whether listener virtual hosts are supported
     */
    default boolean supportsListenerVirtualHosts() {
        return false;
    }
}
