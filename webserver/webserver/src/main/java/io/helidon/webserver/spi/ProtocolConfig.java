/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.Set;

import io.helidon.common.Api;
import io.helidon.config.NamedService;
import io.helidon.webserver.TransportBindingTypes;

/**
 * Protocol configuration abstraction, used to setup a protocol.
 */
public interface ProtocolConfig extends NamedService {
    /**
     * Transport binding provider keys compatible with this protocol.
     * <p>
     * An empty set means this protocol has no transport preference and can use the listener default binding.
     * Otherwise, at least one returned transport binding type must be active on the listener.
     * Built-in keys are available from {@link TransportBindingTypes}.
     *
     * @return compatible transport binding types for this protocol
     */
    @Api.Internal
    default Set<String> transportBindingTypes() {
        return Set.of();
    }
}
