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

package io.helidon.webclient.api;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Api;
import io.helidon.http.Headers;

/**
 * Immutable redirect security state propagated between WebClient protocol implementations.
 */
@Api.Internal
public final class RedirectSecurityState {
    private static final RedirectSecurityState INITIAL =
            new RedirectSecurityState(null, null, false, false, false, true, Set.of());

    private final ClientRequestOrigin lastUriOrigin;
    private final ClientRequestOrigin lastEffectiveOrigin;
    private final boolean redirectPending;
    private final boolean crossedOrigin;
    private final boolean replayingEntity;
    private final boolean automaticCookiesAllowed;
    private final Set<String> suppressedCookieNames;

    private RedirectSecurityState(ClientRequestOrigin lastUriOrigin,
                                  ClientRequestOrigin lastEffectiveOrigin,
                                  boolean redirectPending,
                                  boolean crossedOrigin,
                                  boolean replayingEntity,
                                  boolean automaticCookiesAllowed,
                                  Set<String> suppressedCookieNames) {
        this.lastUriOrigin = lastUriOrigin;
        this.lastEffectiveOrigin = lastEffectiveOrigin;
        this.redirectPending = redirectPending;
        this.crossedOrigin = crossedOrigin;
        this.replayingEntity = replayingEntity;
        this.automaticCookiesAllowed = automaticCookiesAllowed;
        this.suppressedCookieNames = Set.copyOf(suppressedCookieNames);
    }

    /**
     * Initial state for a request that has not followed a redirect.
     *
     * @return initial state
     */
    public static RedirectSecurityState initial() {
        return INITIAL;
    }

    /**
     * Mark the next finalized request as a redirect target.
     * A missing finalized source is treated as an origin crossing.
     *
     * @param replayingEntity whether this redirect replays a non-empty or potentially non-empty entity
     * @return pending redirect state
     */
    public RedirectSecurityState forRedirect(boolean replayingEntity) {
        boolean sourceUnknown = lastUriOrigin == null || lastEffectiveOrigin == null;
        return new RedirectSecurityState(lastUriOrigin,
                                         lastEffectiveOrigin,
                                         true,
                                         crossedOrigin || sourceUnknown,
                                         replayingEntity,
                                         automaticCookiesAllowed,
                                         suppressedCookieNames);
    }

    /**
     * Capture the immutable URI and effective HTTP origins of a finalized request.
     *
     * @param uri final request URI
     * @param headers final request headers
     * @return finalized state
     */
    public RedirectSecurityState finalized(ClientUri uri, Headers headers) {
        ClientRequestOrigin uriOrigin = ClientRequestOrigin.create(uri);
        ClientRequestOrigin effectiveOrigin = ClientRequestOrigin.create(uri, headers);
        boolean crossed = crossedOrigin;
        if (redirectPending && lastUriOrigin != null) {
            crossed |= !lastUriOrigin.equals(uriOrigin) || !lastEffectiveOrigin.equals(effectiveOrigin);
        }
        return new RedirectSecurityState(uriOrigin,
                                         effectiveOrigin,
                                         false,
                                         crossed,
                                         replayingEntity,
                                         automaticCookiesAllowed,
                                         suppressedCookieNames);
    }

    /**
     * Whether finalizing the pending redirect at the provided target would cross an origin boundary.
     *
     * @param uri target request URI
     * @param headers current target headers
     * @return whether the redirect is or would become cross-origin
     */
    public boolean wouldCrossOrigin(ClientUri uri, Headers headers) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        if (crossedOrigin) {
            return true;
        }
        if (!redirectPending || lastUriOrigin == null) {
            return false;
        }
        return !lastUriOrigin.equals(ClientRequestOrigin.create(uri))
                || !lastEffectiveOrigin.equals(ClientRequestOrigin.create(uri, headers));
    }

    /**
     * Whether any followed redirect crossed an origin boundary.
     *
     * @return whether origin crossing is sticky for this chain
     */
    public boolean crossedOrigin() {
        return crossedOrigin;
    }

    /**
     * Whether the current redirect is replaying a non-empty or potentially non-empty entity.
     *
     * @return whether entity replay policy applies
     */
    public boolean replayingEntity() {
        return replayingEntity;
    }

    /**
     * Whether the next finalized request is a redirect target.
     *
     * @return whether a redirect comparison is pending
     */
    public boolean redirectPending() {
        return redirectPending;
    }

    /**
     * Last finalized URI origin.
     *
     * @return URI origin, or empty before the first request is finalized
     */
    public Optional<ClientRequestOrigin> lastUriOrigin() {
        return Optional.ofNullable(lastUriOrigin);
    }

    /**
     * Last finalized effective HTTP origin.
     *
     * @return effective origin, or empty before the first request is finalized
     */
    public Optional<ClientRequestOrigin> lastEffectiveOrigin() {
        return Optional.ofNullable(lastEffectiveOrigin);
    }

    /**
     * Whether automatic CookieStore and configured default cookies may be added to later attempts.
     *
     * @return whether automatic cookies remain enabled for this request chain
     */
    public boolean automaticCookiesAllowed() {
        return automaticCookiesAllowed;
    }

    /**
     * Cookie names explicitly removed or replaced by request customization.
     *
     * @return suppressed automatic cookie names
     */
    public Set<String> suppressedCookieNames() {
        return suppressedCookieNames;
    }

    /**
     * Return a copy with updated automatic-cookie policy.
     *
     * @param automaticCookiesAllowed whether automatic cookies remain enabled
     * @param suppressedCookieNames cookie names that must not be reintroduced automatically
     * @return updated state
     */
    public RedirectSecurityState cookiePolicy(boolean automaticCookiesAllowed, Set<String> suppressedCookieNames) {
        return new RedirectSecurityState(lastUriOrigin,
                                         lastEffectiveOrigin,
                                         redirectPending,
                                         crossedOrigin,
                                         replayingEntity,
                                         automaticCookiesAllowed,
                                         suppressedCookieNames);
    }

    static RedirectSecurityState legacy(ClientUri redirectSourceUri, boolean crossedOrigin) {
        if (redirectSourceUri == null) {
            return crossedOrigin
                    ? new RedirectSecurityState(null, null, false, true, false, true, Set.of())
                    : initial();
        }
        ClientRequestOrigin origin = ClientRequestOrigin.create(redirectSourceUri);
        return new RedirectSecurityState(origin, origin, true, crossedOrigin, false, true, Set.of());
    }
}
