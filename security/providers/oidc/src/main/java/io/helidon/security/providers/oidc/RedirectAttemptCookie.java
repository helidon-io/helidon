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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.http.HeaderNames;
import io.helidon.http.SetCookie;
import io.helidon.security.providers.oidc.common.OidcConfig;

final class RedirectAttemptCookie {
    private RedirectAttemptCookie() {
    }

    static SetCookie create(OidcConfig oidcConfig, String tenantId, String state, int attempt) {
        String value = String.valueOf(attempt);
        return builder(oidcConfig, name(oidcConfig, tenantId, state), value, true)
                .build();
    }

    static SetCookie remove(OidcConfig oidcConfig, String tenantId, String state) {
        return builder(oidcConfig, name(oidcConfig, tenantId, state), "", false)
                .build();
    }

    static Optional<String> find(OidcConfig oidcConfig,
                                 Map<String, List<String>> headers,
                                 String tenantId,
                                 String state) {
        List<String> cookies = headers.get(HeaderNames.COOKIE_NAME);
        if ((cookies == null) || cookies.isEmpty()) {
            return Optional.empty();
        }

        String valuePrefix = name(oidcConfig, tenantId, state) + "=";
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

    static String name(OidcConfig oidcConfig, String tenantId, String state) {
        return oidcConfig.redirectAttemptParam() + "_" + hash(tenantId + "\n" + counterState(oidcConfig, state));
    }

    static String counterState(OidcConfig oidcConfig, String state) {
        int queryStart = state.indexOf('?');
        if (queryStart < 0) {
            return state;
        }

        int fragmentStart = state.indexOf('#', queryStart);
        String path = state.substring(0, queryStart);
        String query = fragmentStart < 0
                ? state.substring(queryStart + 1)
                : state.substring(queryStart + 1, fragmentStart);
        String fragment = fragmentStart < 0 ? "" : state.substring(fragmentStart);
        List<String> queryParams = new ArrayList<>();
        for (String queryParam : query.split("&")) {
            if (!queryParam.isEmpty() && !counterQueryParam(oidcConfig, queryParam)) {
                queryParams.add(queryParam);
            }
        }
        if (queryParams.isEmpty()) {
            return path + fragment;
        }
        return path + "?" + String.join("&", queryParams) + fragment;
    }

    private static boolean counterQueryParam(OidcConfig oidcConfig, String queryParam) {
        int equals = queryParam.indexOf('=');
        String name = equals < 0 ? queryParam : queryParam.substring(0, equals);
        if (matches(name, oidcConfig.redirectAttemptParam())) {
            return true;
        }
        if (!oidcConfig.useParam()) {
            return false;
        }
        return matches(name, oidcConfig.paramName())
                || matches(name, oidcConfig.idTokenParamName())
                || matches(name, oidcConfig.tenantParamName());
    }

    private static boolean matches(String actualName, String expectedName) {
        return actualName.equals(expectedName)
                || actualName.equals(URLEncoder.encode(expectedName, StandardCharsets.UTF_8));
    }

    private static SetCookie.Builder builder(OidcConfig oidcConfig, String name, String value, boolean create) {
        SetCookie configuredCookie = create
                ? oidcConfig.tokenCookieHandler().createCookie(value).build()
                : oidcConfig.tokenCookieHandler().removeCookie().build();
        SetCookie.Builder builder = SetCookie.builder(name, value);
        configuredCookie.expires().ifPresent(builder::expires);
        configuredCookie.maxAge().ifPresent(builder::maxAge);
        configuredCookie.domain().ifPresent(builder::domain);
        configuredCookie.path().ifPresent(builder::path);
        builder.secure(configuredCookie.secure());
        builder.httpOnly(configuredCookie.httpOnly());
        configuredCookie.sameSite().ifPresent(builder::sameSite);
        return builder;
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(Arrays.copyOf(digest, 12));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest is not available", e);
        }
    }
}
