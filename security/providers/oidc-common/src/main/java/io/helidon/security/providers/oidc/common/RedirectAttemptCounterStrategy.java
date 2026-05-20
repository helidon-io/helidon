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

package io.helidon.security.providers.oidc.common;

/**
 * Strategy used to count redirects to an identity server.
 */
public enum RedirectAttemptCounterStrategy {
    /**
     * Do not count redirect attempts.
     */
    NONE,

    /**
     * Count redirect attempts using a query parameter on the original URI.
     */
    PARAM,

    /**
     * Count redirect attempts using a small cookie.
     */
    COOKIE
}
