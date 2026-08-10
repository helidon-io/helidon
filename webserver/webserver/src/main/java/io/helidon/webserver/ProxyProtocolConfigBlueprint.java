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

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.configurable.AllowList;

/**
 * Configuration of PROXY protocol support.
 */
@Prototype.Blueprint
@Prototype.Configured
interface ProxyProtocolConfigBlueprint {
    /**
     * Whether PROXY protocol support is enabled.
     *
     * @return whether PROXY protocol support is enabled, defaults to {@code true}
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Trusted proxy allow list; required when PROXY protocol support is enabled.
     * <p>
     * When PROXY protocol support is enabled, this list must be configured explicitly. Incoming PROXY protocol
     * headers are accepted only from trusted peers.
     *
     * @return trusted proxy allow list
     */
    @Option.Configured("trusted-proxies")
    Optional<AllowList> trustedProxies();
}
