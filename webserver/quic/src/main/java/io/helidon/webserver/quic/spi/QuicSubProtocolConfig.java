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

package io.helidon.webserver.quic.spi;

import java.util.Set;

import io.helidon.common.Api;
import io.helidon.webserver.quic.QuicTransportBindingTypes;
import io.helidon.webserver.spi.ProtocolConfig;

/**
 * Listener protocol configuration hosted on top of a QUIC transport binding.
 */
@Api.Internal
public interface QuicSubProtocolConfig extends ProtocolConfig {
    /**
     * Whether this protocol configuration is enabled.
     *
     * @return whether this protocol is enabled
     */
    default boolean enabled() {
        return true;
    }

    @Override
    default Set<String> transportBindingTypes() {
        return enabled() ? Set.of(QuicTransportBindingTypes.QUIC) : Set.of();
    }
}
