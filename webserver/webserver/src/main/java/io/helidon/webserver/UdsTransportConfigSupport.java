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

import java.net.UnixDomainSocketAddress;

import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;

final class UdsTransportConfigSupport {
    private UdsTransportConfigSupport() {
    }

    static final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Create a Unix domain socket address from configuration.
         *
         * @param config configuration node
         * @return Unix domain socket address
         */
        @Prototype.ConfigFactoryMethod("socket")
        static UnixDomainSocketAddress createSocket(Config config) {
            return UnixDomainSocketAddress.of(config.asString().get());
        }
    }
}
