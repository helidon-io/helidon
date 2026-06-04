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

import io.helidon.common.Api;

/**
 * Internal classification of the TLS material selected for a connection from SNI.
 * <p>
 * The match type distinguishes exact and wildcard virtual-host matches from fallback to listener default TLS, so later
 * HTTP protocol handling can expose and enforce the correct request-time policy.
 */
@Api.Internal
public enum SniMatchType {
    /**
     * SNI matched an exact configured virtual host.
     */
    EXACT,
    /**
     * SNI matched a configured wildcard virtual host.
     */
    WILDCARD,
    /**
     * ClientHello had no SNI host and listener default TLS was selected.
     */
    FALLBACK_MISSING,
    /**
     * ClientHello had unmatched SNI and listener default TLS was selected.
     */
    FALLBACK_UNMATCHED
}
