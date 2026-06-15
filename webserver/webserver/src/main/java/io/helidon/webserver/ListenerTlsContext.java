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

package io.helidon.webserver;

import java.util.Objects;

import io.helidon.common.tls.Tls;

/**
 * Listener TLS state shared with transport bindings.
 * <p>
 * Transport bindings that return {@link io.helidon.webserver.spi.TransportBinding.Security#TLS} must use this context for
 * TLS handshakes so listener default TLS, virtual-host TLS selection, and virtual-host TLS reloads remain consistent
 * across all transport implementations.
 */
public interface ListenerTlsContext {
    /**
     * Default listener TLS configuration.
     *
     * @return listener TLS configuration
     */
    Tls tls();

    /**
     * Whether listener virtual-host TLS selection is configured.
     *
     * @return whether virtual hosts are configured
     */
    boolean virtualHostsEnabled();

    /**
     * Validate the listener virtual-host TLS configuration.
     */
    void validateVirtualHosts();

    /**
     * Select TLS for a client-presented SNI host.
     *
     * @param presentedHost normalized SNI host presented by the client
     * @return selected TLS and SNI context
     * @throws RejectedSniException if the listener SNI policy rejects the host
     */
    Selection select(String presentedHost);

    /**
     * Select TLS for a TLS ClientHello without SNI.
     *
     * @return selected TLS and SNI context
     * @throws RejectedSniException if the listener SNI policy rejects missing SNI
     */
    Selection selectWithoutSni();

    /**
     * Selected listener TLS and SNI context.
     */
    final class Selection {
        private final Tls tls;
        private final SniContext sniContext;

        private Selection(Tls tls, SniContext sniContext) {
            this.tls = Objects.requireNonNull(tls, "tls");
            this.sniContext = Objects.requireNonNull(sniContext, "sniContext");
        }

        /**
         * Create a new selection.
         *
         * @param tls selected TLS
         * @param sniContext selected SNI context
         * @return new selection
         */
        public static Selection create(Tls tls, SniContext sniContext) {
            return new Selection(tls, sniContext);
        }

        /**
         * Selected TLS.
         *
         * @return selected TLS
         */
        public Tls tls() {
            return tls;
        }

        /**
         * Selected SNI context.
         *
         * @return selected SNI context
         */
        public SniContext sniContext() {
            return sniContext;
        }
    }

    /**
     * Rejected SNI selection.
     */
    final class RejectedSniException extends IllegalArgumentException {
        /**
         * Whether to send a TLS {@code unrecognized_name} alert.
         */
        private final boolean sendUnrecognizedNameAlert;

        /**
         * Create a new rejected SNI exception.
         *
         * @param message exception message
         * @param sendUnrecognizedNameAlert whether to send a TLS {@code unrecognized_name} alert
         */
        public RejectedSniException(String message, boolean sendUnrecognizedNameAlert) {
            super(message);
            this.sendUnrecognizedNameAlert = sendUnrecognizedNameAlert;
        }

        /**
         * Whether the transport binding should send a TLS {@code unrecognized_name} alert.
         *
         * @return whether to send an alert
         */
        public boolean sendUnrecognizedNameAlert() {
            return sendUnrecognizedNameAlert;
        }
    }
}
