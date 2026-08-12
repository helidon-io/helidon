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

package io.helidon.webserver.http;

import io.helidon.builder.api.Prototype;

final class AltSvcConfigSupport {
    private AltSvcConfigSupport() {
    }

    static final class BuilderDecorator implements Prototype.BuilderDecorator<AltSvcConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(AltSvcConfig.BuilderBase<?, ?> target) {
            if (!target.enabled()) {
                return;
            }

            String protocol = target.protocol();
            if (protocol.isEmpty()) {
                throw new IllegalArgumentException("Alt-Svc protocol cannot be empty when enabled.");
            }
            for (int index = 0; index < protocol.length(); index++) {
                if (protocol.charAt(index) > 0xFF) {
                    throw new IllegalArgumentException(
                            "Alt-Svc protocol contains a character outside the byte range when enabled.");
                }
            }
            if (protocol.length() > 0xFF) {
                throw new IllegalArgumentException("Alt-Svc protocol exceeds the 255-byte ALPN limit when enabled.");
            }
            if (target.maxAge().isPresent() && target.maxAge().get().isNegative()) {
                throw new IllegalArgumentException("Alt-Svc maxAge cannot be negative.");
            }
            if (target.port().isPresent() && (target.port().get() < 1 || target.port().get() > 65_535)) {
                throw new IllegalArgumentException("Alt-Svc port must be between 1 and 65535 when enabled.");
            }
        }
    }
}
