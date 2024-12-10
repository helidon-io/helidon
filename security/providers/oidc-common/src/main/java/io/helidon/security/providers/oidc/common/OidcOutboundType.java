/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
 * Outbound type of the OIDC provider.
 */
public enum OidcOutboundType {

    /**
     * User access token propagation.
     */
    USER_JWT,

    /**
     * Client credentials are used to get access token.
     * See Client credentials flow:
     * <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.4">RFC 674: Section 4.4 Client Credentials Grant</a>
     */
    CLIENT_CREDENTIALS

}
