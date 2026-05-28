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

import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.uri.UriAuthority;

final class SniConfigSupport {
    private SniConfigSupport() {
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<SniConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(SniConfig.BuilderBase<?, ?> target) {
            SniMode mode = target.mode();
            Optional<String> host = target.host();

            if (mode == SniMode.EXPLICIT) {
                if (host.isEmpty() || host.get().isBlank()) {
                    throw new IllegalArgumentException("SNI host must be configured when SNI mode is explicit");
                }
                UriAuthority authority = UriAuthority.create(host.get());
                if (authority.hasPort()) {
                    throw new IllegalArgumentException("SNI host must not include a port");
                }
                target.host(authority.host().value());
                return;
            }

            if (host.isPresent()) {
                throw new IllegalArgumentException("SNI host can only be configured when SNI mode is explicit");
            }
        }
    }
}
