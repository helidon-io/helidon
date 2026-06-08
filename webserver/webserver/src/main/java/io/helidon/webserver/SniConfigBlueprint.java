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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Listener-scoped server TLS SNI policy configuration.
 * <p>
 * These options define how listener TLS virtual-host selection handles missing or unmatched ClientHello SNI hosts, and
 * how the HTTP request authority is checked after TLS selects certificate material.
 */
@Prototype.Configured
@Prototype.Blueprint
interface SniConfigBlueprint {
    /**
     * Policy for TLS connections without an SNI host; {@link SniSelectionPolicy#REJECT} requires at least one listener
     * virtual host.
     *
     * @return missing SNI policy
     */
    @Option.Configured
    @Option.Default("FALLBACK")
    SniSelectionPolicy missing();

    /**
     * Policy for TLS connections with an SNI host that does not match any configured virtual host;
     * {@link SniSelectionPolicy#REJECT} requires at least one listener virtual host.
     *
     * @return unmatched SNI policy
     */
    @Option.Configured
    @Option.Default("FALLBACK")
    SniSelectionPolicy unmatched();

    /**
     * Policy for HTTP requests whose authority differs from the client-presented SNI host.
     *
     * @return authority mismatch policy
     */
    @Option.Configured("authority-mismatch")
    @Option.Default("REJECT")
    SniAuthorityPolicy authorityMismatch();

    /**
     * Policy for HTTP requests that use default listener TLS but claim a configured virtual-host authority.
     *
     * @return fallback authority policy
     */
    @Option.Configured("fallback-authority")
    @Option.Default("REJECT")
    SniAuthorityPolicy fallbackAuthority();
}
