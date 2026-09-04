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

package io.helidon.quic;

import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLParameters;

@FunctionalInterface
interface QuicTlsServerSelector {
    Selection select(Optional<String> requestedServerName) throws QuicTransportException;

    record Selection(QuicTlsConfigSnapshot config,
                     SSLParameters sslParameters,
                     QuicTlsServerSessionCache sessionCache) {
        public Selection {
            Objects.requireNonNull(config, "config");
            sslParameters = QuicTlsParameters.copy(Objects.requireNonNull(sslParameters, "sslParameters"));
            Objects.requireNonNull(sessionCache, "sessionCache");
        }

        @Override
        public SSLParameters sslParameters() {
            return QuicTlsParameters.copy(sslParameters);
        }
    }
}
