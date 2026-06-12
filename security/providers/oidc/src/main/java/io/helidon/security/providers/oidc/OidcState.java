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

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.Base64Value;
import io.helidon.common.crypto.SymmetricCipher;
import io.helidon.security.providers.oidc.common.TenantConfig;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;

final class OidcState {
    private static final Logger LOGGER = Logger.getLogger(OidcState.class.getName());
    private static final long STATE_TIMEOUT_SECONDS = 300;
    private static final long QUERY_RESULT_TIMEOUT_SECONDS = 60;
    private static final long CLOCK_SKEW_SECONDS = 60;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String KEY_REDIRECT = "r";
    private static final String KEY_ACCESS_TOKEN = "a";
    private static final String KEY_NONCE = "n";
    private static final String KEY_ISSUED_AT = "i";
    private static final String KEY_EXPIRES = "e";
    private static final String QUERY_RESULT_PREFIX = "h.";
    private static final String QUERY_RESULT_NONCE_COOKIE_SUFFIX = "_QR_NONCE";
    private static final String LOGIN_STATE_NONCE_COOKIE_SUFFIX = "_STATE_NONCE";
    private static final String PATH_PREFIX = "Path=";
    private static final String SAME_SITE_PREFIX = "SameSite=";
    private static final String MAX_AGE_PREFIX = "Max-Age=";
    private static final int MAX_QUERY_RESULT_LENGTH = 16 * 1024;

    private OidcState() {
    }

    static String createLoginState(String redirectUri, TenantConfig tenantConfig) {
        long now = Instant.now().getEpochSecond();
        return createLoginState(redirectUri, tenantConfig, now);
    }

    static String createLoginState(String redirectUri, TenantConfig tenantConfig, long now) {
        return createLoginState(redirectUri, tenantConfig, Optional.empty(), now);
    }

    static String createLoginState(String redirectUri, TenantConfig tenantConfig, String nonce) {
        long now = Instant.now().getEpochSecond();
        return createLoginState(redirectUri, tenantConfig, Optional.of(nonce), now);
    }

    static String createLoginState(String redirectUri, TenantConfig tenantConfig, String nonce, long now) {
        return createLoginState(redirectUri, tenantConfig, Optional.of(nonce), now);
    }

    private static String createLoginState(String redirectUri,
                                           TenantConfig tenantConfig,
                                           Optional<String> nonce,
                                           long now) {
        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add(KEY_REDIRECT, redirectUri)
                .add(KEY_ISSUED_AT, now)
                .add(KEY_EXPIRES, expiresAt(now, STATE_TIMEOUT_SECONDS));
        nonce.ifPresent(it -> builder.add(KEY_NONCE, it));
        return encrypt(builder.build(), tenantConfig);
    }

    static Optional<String> loginRedirect(String encryptedState, TenantConfig tenantConfig) {
        return loginRedirect(encryptedState, tenantConfig, Optional.empty(), false);
    }

    static Optional<String> loginRedirect(String encryptedState,
                                          TenantConfig tenantConfig,
                                          Optional<String> expectedNonce,
                                          boolean requireNonce) {
        return decrypt(encryptedState, tenantConfig)
                .filter(OidcState::notExpired)
                .filter(json -> nonceMatches(json, expectedNonce, requireNonce))
                .flatMap(json -> OidcUtil.localRedirectUri(json.getString(KEY_REDIRECT, null)));
    }

    static String createQueryResult(String accessToken, JsonObject tokenResponse, TenantConfig tenantConfig) {
        long now = Instant.now().getEpochSecond();
        return createQueryResult(accessToken, tokenResponse, tenantConfig, now);
    }

    static String createQueryResult(String accessToken,
                                    JsonObject tokenResponse,
                                    TenantConfig tenantConfig,
                                    String nonce) {
        long now = Instant.now().getEpochSecond();
        return createQueryResult(accessToken, tokenResponse, tenantConfig, Optional.of(nonce), now);
    }

    static String createQueryResult(String accessToken, JsonObject tokenResponse, TenantConfig tenantConfig, long now) {
        return createQueryResult(accessToken, tokenResponse, tenantConfig, Optional.empty(), now);
    }

    private static String createQueryResult(String accessToken,
                                            JsonObject tokenResponse,
                                            TenantConfig tenantConfig,
                                            Optional<String> nonce,
                                            long now) {
        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add(KEY_ACCESS_TOKEN, accessToken)
                .add(KEY_ISSUED_AT, now)
                .add(KEY_EXPIRES, expiresAt(now, queryResultTimeout(tokenResponse)));
        nonce.ifPresent(it -> builder.add(KEY_NONCE, it));
        String cipherText = encrypt(builder.build(), tenantConfig);
        if (cipherText.length() > MAX_QUERY_RESULT_LENGTH) {
            throw new IllegalStateException("OIDC query parameter handoff exceeds maximum encrypted size");
        }
        return QUERY_RESULT_PREFIX + cipherText;
    }

    static Optional<String> queryResultAccessToken(String encryptedResult, TenantConfig tenantConfig) {
        return queryResultAccessToken(encryptedResult, tenantConfig, Optional.empty(), false);
    }

    static Optional<String> queryResultAccessToken(String encryptedResult,
                                                   TenantConfig tenantConfig,
                                                   Optional<String> expectedNonce,
                                                   boolean requireNonce) {
        return queryResultCipherText(encryptedResult)
                .flatMap(it -> decrypt(it, tenantConfig))
                .filter(OidcState::notExpired)
                .filter(json -> queryResultNonceMatches(json, expectedNonce, requireNonce))
                .map(json -> json.getString(KEY_ACCESS_TOKEN, null));
    }

    static boolean isQueryResult(String encryptedResult) {
        return encryptedResult.startsWith(QUERY_RESULT_PREFIX);
    }

    static String queryResultPrefix() {
        return QUERY_RESULT_PREFIX;
    }

    static String queryResultNonceCookieName(String tokenCookieName) {
        return tokenCookieName + QUERY_RESULT_NONCE_COOKIE_SUFFIX;
    }

    static String loginStateNonceCookieName(String tokenCookieName) {
        return tokenCookieName + LOGIN_STATE_NONCE_COOKIE_SUFFIX;
    }

    static String queryResultNonceSetCookie(String tokenCookieName, String nonce, String cookieOptions) {
        return nonceSetCookie(queryResultNonceCookieName(tokenCookieName), nonce, QUERY_RESULT_TIMEOUT_SECONDS, cookieOptions);
    }

    static String loginStateNonceSetCookie(String tokenCookieName,
                                           String nonce,
                                           String redirectUri,
                                           String cookieOptions) {
        return nonceSetCookie(loginStateNonceCookieName(tokenCookieName),
                              nonce,
                              STATE_TIMEOUT_SECONDS,
                              loginStateNonceCookieOptions(redirectUri, cookieOptions));
    }

    static String loginStateNonceRemoveCookie(String tokenCookieName, String redirectUri, String cookieOptions) {
        return loginStateNonceCookieName(tokenCookieName)
                + "=; Expires=Thu, 01 Jan 1970 00:00:00 GMT"
                + loginStateNonceCookieOptions(redirectUri, cookieOptions);
    }

    private static Optional<String> queryResultCipherText(String encryptedResult) {
        if (!isQueryResult(encryptedResult)) {
            return Optional.empty();
        }
        String cipherText = encryptedResult.substring(QUERY_RESULT_PREFIX.length());
        if (cipherText.isEmpty() || cipherText.length() > MAX_QUERY_RESULT_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(cipherText);
    }

    private static boolean queryResultNonceMatches(JsonObject json,
                                                   Optional<String> expectedNonce,
                                                   boolean requireNonce) {
        return nonceMatches(json, expectedNonce, requireNonce);
    }

    private static boolean nonceMatches(JsonObject json, Optional<String> expectedNonce, boolean requireNonce) {
        String nonce = json.getString(KEY_NONCE, null);
        if (nonce == null) {
            return !requireNonce;
        }
        return expectedNonce.filter(nonce::equals).isPresent();
    }

    private static boolean notExpired(JsonObject json) {
        long now = Instant.now().getEpochSecond();
        JsonNumber issuedAt = json.getJsonNumber(KEY_ISSUED_AT);
        JsonNumber expires = json.getJsonNumber(KEY_EXPIRES);
        return issuedAt != null
                && expires != null
                && issuedAt.longValue() <= now + CLOCK_SKEW_SECONDS
                && expires.longValue() >= now;
    }

    private static long expiresAt(long now, long timeoutSeconds) {
        return now + timeoutSeconds;
    }

    private static long queryResultTimeout(JsonObject tokenResponse) {
        JsonNumber expiresIn = tokenResponse.getJsonNumber("expires_in");
        if (expiresIn == null || expiresIn.longValue() <= 0) {
            return QUERY_RESULT_TIMEOUT_SECONDS;
        }
        return Math.min(expiresIn.longValue(), QUERY_RESULT_TIMEOUT_SECONDS);
    }

    private static String nonceSetCookie(String cookieName, String nonce, long maxAgeSeconds, String cookieOptions) {
        return cookieName + "=" + nonce + "; Max-Age=" + maxAgeSeconds + withoutMaxAge(cookieOptions);
    }

    private static String loginStateNonceCookieOptions(String redirectUri, String cookieOptions) {
        return withoutOptions(cookieOptions, MAX_AGE_PREFIX, PATH_PREFIX, SAME_SITE_PREFIX)
                + "; Path=" + loginStateNonceCookiePath(redirectUri)
                + "; SameSite=Lax";
    }

    private static String loginStateNonceCookiePath(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return "/";
        }

        String path = redirectUri;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        int fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }
        if (path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String withoutMaxAge(String cookieOptions) {
        return withoutOptions(cookieOptions, MAX_AGE_PREFIX);
    }

    private static String withoutOptions(String cookieOptions, String... removedPrefixes) {
        if (cookieOptions == null || cookieOptions.isBlank()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (String option : cookieOptions.split(";")) {
            String trimmed = option.trim();
            if (!trimmed.isEmpty() && !hasAnyPrefix(trimmed, removedPrefixes)) {
                result.append("; ").append(trimmed);
            }
        }
        return result.toString();
    }

    private static boolean hasAnyPrefix(String option, String[] prefixes) {
        for (String prefix : prefixes) {
            if (option.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return true;
            }
        }
        return false;
    }

    private static String encrypt(JsonObject json, TenantConfig tenantConfig) {
        SymmetricCipher cipher = SymmetricCipher.create(tenantConfig.clientSecret().toCharArray());
        return ENCODER.encodeToString(cipher.encrypt(Base64Value.create(json.toString().getBytes(StandardCharsets.UTF_8)))
                                              .toBytes());
    }

    private static Optional<JsonObject> decrypt(String encrypted, TenantConfig tenantConfig) {
        try {
            SymmetricCipher cipher = SymmetricCipher.create(tenantConfig.clientSecret().toCharArray());
            String json = new String(cipher.decrypt(Base64Value.create(DECODER.decode(encrypted))).toBytes(),
                                     StandardCharsets.UTF_8);
            try (JsonReader reader = Json.createReader(new StringReader(json))) {
                return Optional.of(reader.readObject());
            }
        } catch (RuntimeException e) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "Failed to decrypt OIDC redirect state", e);
            }
            return Optional.empty();
        }
    }
}
