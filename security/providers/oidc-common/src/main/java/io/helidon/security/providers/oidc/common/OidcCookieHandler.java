/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.SetCookie;
import io.helidon.common.reactive.Single;

/**
 * Handler of cookies used in OIDC.
 */
public class OidcCookieHandler {
    private static final Logger LOGGER = Logger.getLogger(OidcCookieHandler.class.getName());

    private final String createCookieOptions;
    private final List<Consumer<SetCookie.Builder>> removeCookieUpdaters = new LinkedList<>();
    private final List<Consumer<SetCookie.Builder>> createCookieUpdaters = new LinkedList<>();
    private final String cookieName;
    private final String valuePrefix;
    private final Function<String, Single<String>> encryptFunction;
    private final Function<String, Single<String>> decryptFunction;

    private OidcCookieHandler(Builder builder) {
        this.cookieName = builder.cookieName;
        this.valuePrefix = cookieName + "=";

        // need to copy the values here, so we do not use future values of the builder
        String path = builder.path;
        boolean httpOnly = builder.httpOnly;
        SetCookie.SameSite sameSite = builder.sameSite;
        String domain = builder.domain;
        boolean secure = builder.secure;
        Long maxAge = builder.maxAge;

        removeCookieUpdaters.add(it -> it.path(path));
        if (httpOnly) {
            removeCookieUpdaters.add(it -> it.httpOnly(true));
        }
        if (sameSite != null) {
            removeCookieUpdaters.add(it -> it.sameSite(sameSite));
        }
        if (domain != null) {
            removeCookieUpdaters.add(it -> it.domain(domain));
        }
        if (secure) {
            removeCookieUpdaters.add(it -> it.secure(true));
        }
        // now we can share the updaters, from this point the two lists diverge
        createCookieUpdaters.addAll(removeCookieUpdaters);

        if (maxAge != null) {
            createCookieUpdaters.add(it -> it.maxAge(Duration.ofSeconds(maxAge)));
        }
        // set expires to 0 - this removes the cookie from browsers
        removeCookieUpdaters.add(it -> it.expires(Instant.ofEpochMilli(0)));

        String value = createCookieDirectValue("value").build().toString();
        int index = value.indexOf(';');
        if (index < 0) {
            this.createCookieOptions = "";
        } else {
            this.createCookieOptions = value.substring(index);
        }

        if (builder.encryptionEnabled) {
            var cookieEncryption = OidcEncryption.create("Cookie(" + cookieName + ")",
                                                         builder.encryptionName,
                                                         builder.encryptionPassword);
            this.encryptFunction = it -> cookieEncryption.encrypt(it.getBytes(StandardCharsets.UTF_8));
            this.decryptFunction = it -> cookieEncryption.decrypt(it).map(String::new);
        } else {
            this.encryptFunction = Single::just;
            this.decryptFunction = Single::just;
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(() -> "OIDC Create cookie example: " + value);
            LOGGER.finest(() -> "OIDC Remove cookie example: " + removeCookie().build());
        }
    }

    static Builder builder() {
        return new Builder();
    }

    /**
     * {@link io.helidon.common.http.SetCookie} builder to set a new cookie,
     * returns a future, as the value may need to be encrypted using a remote service.
     *
     * @param value value of the cookie
     * @return a new builder to configure set cookie configured from OIDC Config
     */
    public Single<SetCookie.Builder> createCookie(String value) {
        return encryptFunction.apply(value)
                .map(this::createCookieDirectValue);
    }

    /**
     * Cookie name.
     *
     * @return name of the cookie to use
     */
    public String cookieName() {
        return cookieName;
    }

    /**
     * {@link io.helidon.common.http.SetCookie} builder to remove an existing cookie (such as during logout).
     *
     * @return a new builder to configure set cookie configured from OIDC Config with expiration set to epoch begin and
     *  empty value
     */
    public SetCookie.Builder removeCookie() {
        SetCookie.Builder builder = SetCookie.builder(cookieName, "");
        removeCookieUpdaters.forEach(it -> it.accept(builder));
        return builder;
    }

    /**
     * Locate cookie in a map of headers and return its value.
     * If the cookie is encrypted, decrypts the cookie value.
     *
     * @param headers headers to process
     * @return cookie value, or empty if the cookie could not be found
     */
    public Optional<Single<String>> findCookie(Map<String, List<String>> headers) {
        Objects.requireNonNull(headers);

        List<String> cookies = headers.get("Cookie");
        if ((cookies == null) || cookies.isEmpty()) {
            return Optional.empty();
        }

        for (String cookie : cookies) {
            //a=b; c=d; e=f
            String[] cookieValues = cookie.split(";\\s?");
            for (String cookieValue : cookieValues) {
                String trimmed = cookieValue.trim();
                if (trimmed.startsWith(valuePrefix)) {
                    return Optional.of(decrypt(trimmed.substring(valuePrefix.length())));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Decrypt a cipher text into clear text (if encryption is enabled).
     *
     * @param cipherText cipher text to decrypt
     * @return secret
     */
    public Single<String> decrypt(String cipherText) {
        return decryptFunction.apply(cipherText);
    }

    String createCookieOptions() {
        return createCookieOptions;
    }

    String cookieValuePrefix() {
        return valuePrefix;
    }

    private SetCookie.Builder createCookieDirectValue(String value) {
        SetCookie.Builder builder = SetCookie.builder(cookieName, value);
        createCookieUpdaters.forEach(it -> it.accept(builder));
        return builder;
    }

    static class Builder implements io.helidon.common.Builder<OidcCookieHandler> {
        static final String DEFAULT_PATH = "/";
        static final boolean DEFAULT_HTTP_ONLY = true;
        static final boolean DEFAULT_SECURE = false;
        static final SetCookie.SameSite DEFAULT_SAME_SITE = SetCookie.SameSite.LAX;

        private String path = DEFAULT_PATH;
        private boolean httpOnly = DEFAULT_HTTP_ONLY;
        private SetCookie.SameSite sameSite = DEFAULT_SAME_SITE;
        private String domain;
        private boolean secure = DEFAULT_SECURE;
        private Long maxAge;
        private String cookieName;
        private String encryptionName;
        private char[] encryptionPassword;
        private boolean encryptionEnabled;

        private Builder() {
        }

        @Override
        public OidcCookieHandler build() {
            return new OidcCookieHandler(this);
        }

        Builder path(String cookiePath) {
            this.path = cookiePath;
            return this;
        }

        Builder httpOnly(boolean cookieHttpOnly) {
            this.httpOnly = cookieHttpOnly;
            return this;
        }

        Builder sameSite(SetCookie.SameSite cookieSameSite) {
            this.sameSite = cookieSameSite;
            return this;
        }

        Builder domain(String cookieDomain) {
            this.domain = cookieDomain;
            return this;
        }

        Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        Builder maxAge(Long maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        Builder cookieName(String cookieName) {
            this.cookieName = cookieName;
            return this;
        }

        public Builder encryptionName(String encryptionName) {
            this.encryptionName = encryptionName;
            return this;
        }

        public Builder encryptionPassword(char[] encryptionPassword) {
            this.encryptionPassword = encryptionPassword;
            return this;
        }

        public Builder encryptionEnabled(Boolean encryptionEnabled) {
            this.encryptionEnabled = encryptionEnabled;
            return this;
        }
    }
}
