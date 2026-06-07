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

import java.util.List;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.helidon.webserver.http.DirectTransportRequest;

/**
 * Request-time SNI policy support.
 * <p>
 * TLS selection happens before HTTP parsing. This helper is the shared protocol hook that checks the parsed HTTP
 * authority against the SNI context selected for the connection.
 */
@Api.Internal
public final class SniRequestSupport {
    private SniRequestSupport() {
    }

    /**
     * Validate an HTTP authority against the connection SNI context.
     *
     * @param sniContext SNI context
     * @param prologue   HTTP prologue
     * @param headers    request headers
     * @param authority  request authority
     */
    public static void validateAuthority(SniContext sniContext,
                                         HttpPrologue prologue,
                                         Headers headers,
                                         String authority) {
        Objects.requireNonNull(prologue);
        Objects.requireNonNull(headers);
        try {
            validateAuthorityCheck(checkAuthority(sniContext, authority), prologue, headers);
        } catch (IllegalArgumentException e) {
            throw invalidAuthority(prologue, headers, e.getMessage(), e);
        }
    }

    /**
     * Validate an already parsed SNI authority check result.
     *
     * @param check    authority check result
     * @param prologue HTTP prologue
     * @param headers  request headers
     */
    public static void validateAuthorityCheck(SniContext.AuthorityCheck check, HttpPrologue prologue, Headers headers) {
        Objects.requireNonNull(check);
        Objects.requireNonNull(prologue);
        Objects.requireNonNull(headers);
        switch (check) {
        case ALLOWED -> {
        }
        case AUTHORITY_MISMATCH -> throw misdirected(prologue,
                                                     headers,
                                                     "HTTP authority does not match the selected SNI host");
        case FALLBACK_AUTHORITY -> throw misdirected(prologue,
                                                     headers,
                                                     "HTTP authority requires a configured SNI virtual host");
        default -> throw new IllegalStateException("Unknown SNI authority check: " + check);
        }
    }

    /**
     * Check an HTTP authority against the connection SNI context.
     *
     * @param sniContext SNI context
     * @param authority  request authority
     * @return authority check result
     */
    public static SniContext.AuthorityCheck checkAuthority(SniContext sniContext, String authority) {
        Objects.requireNonNull(sniContext);
        Objects.requireNonNull(authority);
        if (authority.isBlank()) {
            throw new IllegalArgumentException("HTTP authority is required by SNI policy");
        }
        try {
            return sniContext.checkAuthority(authority);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid HTTP authority: " + e.getMessage(), e);
        }
    }

    /**
     * Return the single HTTP/1 Host header authority required by SNI policy.
     *
     * @param prologue HTTP prologue
     * @param headers  request headers
     * @return Host header authority
     */
    public static String singleHostAuthority(HttpPrologue prologue, Headers headers) {
        Objects.requireNonNull(prologue);
        Objects.requireNonNull(headers);
        List<String> hostHeaders = headers.all(HeaderNames.HOST, List::of);
        if (hostHeaders.isEmpty()) {
            throw missingAuthority(prologue, headers);
        }
        if (hostHeaders.size() > 1) {
            throw invalidAuthority(prologue,
                                   headers,
                                   "Only a single Host header is allowed by SNI policy",
                                   null);
        }
        return hostHeaders.getFirst();
    }

    /**
     * Create the request exception for a missing authority.
     *
     * @param prologue HTTP prologue
     * @param headers  request headers
     * @return request exception
     */
    public static RequestException missingAuthority(HttpPrologue prologue, Headers headers) {
        Objects.requireNonNull(prologue);
        Objects.requireNonNull(headers);
        return invalidAuthority(prologue, headers, "HTTP authority is required by SNI policy", null);
    }

    private static RequestException invalidAuthority(HttpPrologue prologue,
                                                     Headers headers,
                                                     String message,
                                                     Throwable cause) {
        return RequestException.builder()
                .type(DirectHandler.EventType.BAD_REQUEST)
                .status(Status.BAD_REQUEST_400)
                .request(DirectTransportRequest.create(prologue, headers))
                .setKeepAlive(false)
                .message(message)
                .cause(cause)
                .build();
    }

    private static RequestException misdirected(HttpPrologue prologue, Headers headers, String message) {
        return RequestException.builder()
                .type(DirectHandler.EventType.OTHER)
                .status(Status.MISDIRECTED_REQUEST_421)
                .request(DirectTransportRequest.create(prologue, headers))
                .setKeepAlive(true)
                .message(message)
                .build();
    }
}
