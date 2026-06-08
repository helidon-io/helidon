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

import io.helidon.common.Api;

/**
 * Listener SNI decision attached to a connection after TLS virtual-host selection.
 * <p>
 * Protocol handlers use this context to expose selected host information and to validate the parsed HTTP authority
 * against the TLS SNI selection policy.
 */
@Api.Internal
public interface SniContext {
    /**
     * Normalized SNI host presented by the client.
     *
     * @return presented SNI host, when present
     */
    Optional<String> presentedHost();

    /**
     * Configured virtual-host pattern that matched the presented SNI host.
     *
     * @return matched configured host, when a virtual host matched
     */
    Optional<String> matchedHost();

    /**
     * SNI match type.
     *
     * @return match type
     */
    SniMatchType matchType();

    /**
     * Check whether an HTTP authority is allowed by the listener SNI policy.
     *
     * @param authority HTTP authority
     * @return authority check result
     */
    AuthorityCheck checkAuthority(String authority);

    /**
     * Authority check result.
     */
    enum AuthorityCheck {
        /**
         * Authority is allowed.
         */
        ALLOWED,
        /**
         * Authority differs from the matched SNI virtual-host host.
         */
        AUTHORITY_MISMATCH,
        /**
         * Fallback TLS was used but authority matches a configured virtual host.
         */
        FALLBACK_AUTHORITY
    }
}
