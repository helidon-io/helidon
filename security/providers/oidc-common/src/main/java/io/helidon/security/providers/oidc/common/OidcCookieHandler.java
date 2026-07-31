/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.crypto.CryptoException;
import io.helidon.http.SetCookie;

/**
 * Handler of cookies used in OIDC.
 */
public class OidcCookieHandler {
    private static final System.Logger LOGGER = System.getLogger(OidcCookieHandler.class.getName());
    private static final byte COMPRESSED_BASE64_VALUE = 0;
    private static final byte COMPRESSED_RAW_VALUE = 1;
    private static final int COMPRESSION_BUFFER_SIZE = 1024;
    private static final int MAX_COOKIE_VALUE_SIZE = 64 * 1024;
    private static final int MAX_DECOMPRESSED_BASE64_VALUE_SIZE = MAX_COOKIE_VALUE_SIZE / 4 * 3;

    private final String createCookieOptions;
    private final List<Consumer<SetCookie.Builder>> removeCookieUpdaters = new LinkedList<>();
    private final List<Consumer<SetCookie.Builder>> createCookieUpdaters = new LinkedList<>();
    private final String cookieName;
    private final String valuePrefix;
    private final Function<String, String> encryptFunction;
    private final Function<String, String> decryptFunction;

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
                                                         builder.encryptionPassword,
                                                         builder.legacyCookieEncryption,
                                                         builder.legacyCookieFallback);
            // Older instances can decrypt legacy password-encrypted cookies, but cannot decompress their payload.
            // Named encryption ignores the legacy flag; rolling upgrades using it must control compression explicitly.
            boolean legacyPasswordEncryption = builder.legacyCookieEncryption && builder.encryptionName == null;
            boolean compressionEnabled = builder.compressionEnabled && !legacyPasswordEncryption;
            this.encryptFunction = it -> {
                byte[] valueBytes = it.getBytes(StandardCharsets.UTF_8);
                return cookieEncryption.encrypt(compressionEnabled ? compress(valueBytes) : valueBytes);
            };
            // Marker-aware decompression also accepts uncompressed cookies created by older instances.
            this.decryptFunction = it -> new String(decompress(cookieEncryption.decrypt(it)),
                                                    StandardCharsets.UTF_8);
        } else {
            this.encryptFunction = Function.identity();
            this.decryptFunction = Function.identity();
        }

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, () -> "OIDC Create cookie example: " + value);
            LOGGER.log(Level.TRACE, () -> "OIDC Remove cookie example: " + removeCookie().build());
        }
    }

    static Builder builder() {
        return new Builder();
    }

    /**
     * {@link io.helidon.http.SetCookie} builder to set a new cookie,
     * returns a future, as the value may need to be encrypted using a remote service.
     *
     * @param value value of the cookie
     * @return a new builder to configure set cookie configured from OIDC Config
     */
    public SetCookie.Builder createCookie(String value) {
        return createCookieDirectValue(encryptFunction.apply(value));
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
     * {@link io.helidon.http.SetCookie} builder to remove an existing cookie (such as during logout).
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
    public Optional<String> findCookie(Map<String, List<String>> headers) {
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
    public String decrypt(String cipherText) {
        return decryptFunction.apply(cipherText);
    }

    String createCookieOptions() {
        return createCookieOptions;
    }

    String cookieValuePrefix() {
        return valuePrefix;
    }

    private static byte[] compress(byte[] value) {
        if (value.length <= 1 || value.length > MAX_COOKIE_VALUE_SIZE) {
            return value;
        }

        byte compressionMarker = COMPRESSED_RAW_VALUE;
        byte[] uncompressed = value;
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            // Access-token cookies contain Base64-encoded JSON. Decoding canonical Base64 before compression removes
            // its roughly one-third expansion, which can be necessary to fit the encrypted value within browser cookie
            // limits and leaves fewer bytes for GZIP and encryption to process. The exact decode/encode round trip
            // ensures that the marker can be used to restore the original representation byte for byte.
            if (Arrays.equals(value, Base64.getEncoder().encode(decoded))) {
                compressionMarker = COMPRESSED_BASE64_VALUE;
                uncompressed = decoded;
            }
        } catch (IllegalArgumentException e) {
            // Compress the raw value.
        }

        ByteArrayOutputStream result = new ByteArrayOutputStream(value.length);
        // Compressed values use [format marker][GZIP stream]. The marker is a control byte that cannot begin the
        // textual token and URL values handled here. GZIPOutputStream writes the fixed RFC 1952 magic bytes
        // 0x1f, 0x8b immediately after it.
        result.write(compressionMarker);
        try (GZIPOutputStream gzip = new BestCompressionGzipOutputStream(result)) {
            gzip.write(uncompressed);
        } catch (IOException e) {
            throw new CryptoException("OIDC cookie compression failed", e);
        }
        byte[] compressed = result.toByteArray();
        return compressed.length < value.length ? compressed : value;
    }

    private static byte[] decompress(byte[] value) {
        if (value.length == 0) {
            return value;
        }

        boolean base64Value;
        int maxDecompressedValueSize;
        // The leading marker identifies both compression and the original representation. GZIPInputStream below
        // validates the following 0x1f, 0x8b magic bytes; no marker means this is an uncompressed value.
        if (value[0] == COMPRESSED_BASE64_VALUE) {
            base64Value = true;
            maxDecompressedValueSize = MAX_DECOMPRESSED_BASE64_VALUE_SIZE;
        } else if (value[0] == COMPRESSED_RAW_VALUE) {
            base64Value = false;
            maxDecompressedValueSize = MAX_COOKIE_VALUE_SIZE;
        } else {
            return value;
        }
        if (value.length > MAX_COOKIE_VALUE_SIZE) {
            throw new CryptoException("OIDC compressed cookie exceeds the maximum supported size");
        }

        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(value, 1, value.length - 1),
                                                        COMPRESSION_BUFFER_SIZE)) {
            byte[] decompressed = gzip.readNBytes(maxDecompressedValueSize + 1);
            if (decompressed.length > maxDecompressedValueSize) {
                throw new CryptoException("OIDC decompressed cookie exceeds the maximum supported size");
            }
            return base64Value ? Base64.getEncoder().encode(decompressed) : decompressed;
        } catch (IOException e) {
            throw new CryptoException("OIDC cookie decompression failed", e);
        }
    }

    private static final class BestCompressionGzipOutputStream extends GZIPOutputStream {
        private BestCompressionGzipOutputStream(OutputStream out) throws IOException {
            super(out, COMPRESSION_BUFFER_SIZE);
            def.setLevel(Deflater.BEST_COMPRESSION);
        }
    }

    private SetCookie.Builder createCookieDirectValue(String value) {
        SetCookie.Builder builder = SetCookie.builder(cookieName, value);
        createCookieUpdaters.forEach(it -> it.accept(builder));
        return builder;
    }

    static class Builder implements io.helidon.common.Builder<Builder, OidcCookieHandler> {
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
        private boolean compressionEnabled;
        private boolean legacyCookieEncryption;
        private boolean legacyCookieFallback;

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

        Builder compressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        Builder legacyCookieEncryption(boolean legacyCookieEncryption) {
            this.legacyCookieEncryption = legacyCookieEncryption;
            return this;
        }

        Builder legacyCookieFallback(boolean legacyCookieFallback) {
            this.legacyCookieFallback = legacyCookieFallback;
            return this;
        }
    }
}
