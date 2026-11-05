/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.Optional;

import io.helidon.common.uri.UriPath;

/**
 * Matches HTTP path against configured path of a route.
 */
public interface PathMatcher {
    /**
     * Match the provided path against the configured path.
     * Must do a full match on the whole path.
     *
     * @param path HTTP path that was requested by user
     * @return match result
     */
    PathMatchers.MatchResult match(UriPath path);

    /**
     * Match the provided path against the configured path as a prefix match.
     * This is used to handle service routing, where service path pattern may match only a subset of segments.
     * The matching MUST always match exact segments.
     *
     * @param uriPath path that was requested by user
     * @return match result
     */
    PathMatchers.PrefixMatchResult prefixMatch(UriPath uriPath);

    /**
     * Returns the matching element for this matcher. This could be a prefix, pattern,
     * etc. depending on the type of matcher.
     *
     * @return optional matching element
     */
    default Optional<String> matchingElement() {
        return Optional.empty();
    }

    /**
     * Returns the configured route template for diagnostic output.
     * The template must be independent of request data and must not contain values extracted from requests.
     * Implementations that cannot provide such a template should return {@link Optional#empty()}.
     * An empty string represents a matcher that contributes no path component, such as a match-any matcher.
     * The default implementation returns {@link Optional#empty()}.
     *
     * @return optional configured route template
     * @since 28.0.0
     */
    default Optional<String> routeTemplate() {
        return Optional.empty();
    }
}
