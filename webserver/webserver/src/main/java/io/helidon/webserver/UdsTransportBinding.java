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
import java.util.Objects;
import java.util.Timer;

final class UdsTransportBinding extends SocketTransportBinding {
    UdsTransportBinding(TransportBindingContext listenerContext,
                        UdsTransportConfig config,
                        Timer idleConnectionTimer) {
        super(listenerContext, TransportBindingTypes.UDS, idleConnectionTimer, socket(config));
    }

    private static UnixDomainSocketAddress socket(UdsTransportConfig config) {
        Objects.requireNonNull(config, "config");
        return config.socket()
                .orElseThrow(() -> new IllegalArgumentException("UDS transport binding requires a configured socket"));
    }
}
