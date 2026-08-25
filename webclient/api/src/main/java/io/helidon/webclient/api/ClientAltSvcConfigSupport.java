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

package io.helidon.webclient.api;

import io.helidon.builder.api.Prototype;

final class ClientAltSvcConfigSupport {
    private ClientAltSvcConfigSupport() {
    }

    static final class BuilderDecorator implements Prototype.BuilderDecorator<ClientAltSvcConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(ClientAltSvcConfig.BuilderBase<?, ?> target) {
            if (!target.enabled()) {
                return;
            }

            for (String protocol : target.protocols()) {
                if (protocol.isEmpty()) {
                    throw new IllegalArgumentException("Client Alt-Svc protocol cannot be empty when enabled.");
                }
                for (int index = 0; index < protocol.length(); index++) {
                    if (protocol.charAt(index) > 0xFF) {
                        throw new IllegalArgumentException(
                                "Client Alt-Svc protocol contains a character outside the byte range when enabled.");
                    }
                }
                if (protocol.length() > 0xFF) {
                    throw new IllegalArgumentException(
                            "Client Alt-Svc protocol exceeds the 255-byte ALPN limit when enabled.");
                }
            }
        }
    }
}
