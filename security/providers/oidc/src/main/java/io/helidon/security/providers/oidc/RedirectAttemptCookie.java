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

package io.helidon.security.providers.oidc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.http.HeaderNames;
import io.helidon.http.SetCookie;
import io.helidon.security.providers.oidc.common.OidcConfig;

final class RedirectAttemptCookie {
    private RedirectAttemptCookie() {
    }

    static SetCookie create(OidcConfig oidcConfig, int attempt) {
        return builder(oidcConfig, String.valueOf(attempt)).build();
    }

    static SetCookie remove(OidcConfig oidcConfig) {
        return builder(oidcConfig, "")
                .expires(Instant.ofEpochMilli(0))
                .build();
    }

    static Optional<String> find(OidcConfig oidcConfig, Map<String, List<String>> headers) {
        List<String> cookies = headers.get(HeaderNames.COOKIE_NAME);
        if ((cookies == null) || cookies.isEmpty()) {
            return Optional.empty();
        }

        String valuePrefix = oidcConfig.redirectAttemptParam() + "=";
        for (String cookie : cookies) {
            for (String cookieValue : cookie.split(";\\s?")) {
                String trimmed = cookieValue.trim();
                if (trimmed.startsWith(valuePrefix)) {
                    return Optional.of(trimmed.substring(valuePrefix.length()));
                }
            }
        }
        return Optional.empty();
    }

    private static SetCookie.Builder builder(OidcConfig oidcConfig, String value) {
        return SetCookie.builder(oidcConfig.redirectAttemptParam(), value)
                .path("/")
                .httpOnly(true)
                .sameSite(SetCookie.SameSite.LAX);
    }
}
