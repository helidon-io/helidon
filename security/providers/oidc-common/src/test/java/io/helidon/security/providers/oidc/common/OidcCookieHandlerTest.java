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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import io.helidon.common.Base64Value;
import io.helidon.common.crypto.CryptoException;
import io.helidon.common.crypto.SymmetricCipher;
import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OidcCookieHandlerTest {
    private static final char[] ENCRYPTION_PASSWORD = "test-password".toCharArray();
    private static final byte CURRENT_VERSION = 1;
    private static final byte[] CURRENT_VERSION_HEADER = {CURRENT_VERSION};
    private static final int CURRENT_NUMBER_OF_ITERATIONS = 600_000;
    private static final int LEGACY_NUMBER_OF_ITERATIONS = 10_000;
    private static final String LEGACY_ENCRYPTED_COOKIE =
            "9WmBEiNX4CF9l4lj+1axdgAAAAySayWBmiIG5e2hIYy7ilR2iML6S+qvr2M4U7593tCWI/SjCZsZ2XQ=";

    private static OidcCookieHandler handler;

    @BeforeAll
    static void initClass() {
        handler = OidcCookieHandler.builder()
                .encryptionEnabled(false)
                .cookieName("COOKIE")
                .build();
    }

    @Test
    void testFindCookieMissing() {
        Map<String, List<String>> headers = Map.of();
        Optional<Single<String>> cookie = handler.findCookie(headers);

        assertThat(cookie, is(Optional.empty()));
    }

    @Test
    void testFindCookiePresent() {
        String expectedValue = "cookieValue";
        Map<String, List<String>> headers = Map.of("Accept", List.of("application/json"),
                                                   "Cookie", List.of("COOKIE=" + expectedValue));
        Optional<Single<String>> cookie = handler.findCookie(headers);

        assertThat(cookie, not(Optional.empty()));
        String cookieValue = cookie.get().await();
        assertThat(cookieValue, is(expectedValue));

        headers = Map.of("Accept", List.of("application/json"),
                         "Cookie", List.of("COOKIE=" + expectedValue + ";abc=bbc;uao=aee"));
        cookie = handler.findCookie(headers);

        assertThat(cookie, not(Optional.empty()));
        cookieValue = cookie.get().await();
        assertThat(cookieValue, is(expectedValue));

        headers = Map.of("Accept", List.of("application/json"),
                         "Cookie", List.of("abc=bbc; COOKIE=" + expectedValue + ";uao=aee"));
        cookie = handler.findCookie(headers);

        assertThat(cookie, not(Optional.empty()));
        cookieValue = cookie.get().await();
        assertThat(cookieValue, is(expectedValue));

        headers = Map.of("Accept", List.of("application/json"),
                         "Cookie", List.of("abc=bbc;uao=aee;COOKIE=" + expectedValue));
        cookie = handler.findCookie(headers);

        assertThat(cookie, not(Optional.empty()));
        cookieValue = cookie.get().await();
        assertThat(cookieValue, is(expectedValue));
    }

    @Test
    void testDefaultHandlerRejectsLegacyEncryptedCookie() {
        OidcCookieHandler encryptedHandler = encryptedHandler();

        assertCryptoException(() -> encryptedHandler.findCookie(Map.of("Cookie",
                                                                       List.of("COOKIE="
                                                                                       + LEGACY_ENCRYPTED_COOKIE)))
                .orElseThrow()
                .await());
    }

    @Test
    void testLegacyFallbackReadsLegacyEncryptedCookie() {
        String expectedValue = "cookieValue";
        OidcCookieHandler encryptedHandler = encryptedHandler(false, true);

        Optional<Single<String>> cookie = encryptedHandler.findCookie(Map.of("Cookie",
                                                                             List.of("COOKIE=" + LEGACY_ENCRYPTED_COOKIE)));

        assertThat(cookie.map(Single::await), is(Optional.of(expectedValue)));
    }

    @Test
    void testLegacyEncryptionFallbackReadsCurrentEncryptedCookie() {
        String expectedValue = "cookieValue";
        String currentEncrypted = cookieValue(encryptedHandler()
                                                      .createCookie(expectedValue)
                                                      .await()
                                                      .build()
                                                      .toString());

        assertAll(() -> assertCryptoException(() -> encryptedHandler(true, false)
                .findCookie(Map.of("Cookie", List.of("COOKIE=" + currentEncrypted)))
                .orElseThrow()
                .await()),
                  () -> assertThat(encryptedHandler(true, true)
                                           .findCookie(Map.of("Cookie", List.of("COOKIE=" + currentEncrypted)))
                                           .map(Single::await),
                                   is(Optional.of(expectedValue))));
    }

    @Test
    void testNewEncryptedCookieUsesCurrentDefaults() {
        String expectedValue = "cookieValue";
        OidcCookieHandler encryptedHandler = encryptedHandler();
        String encrypted = cookieValue(encryptedHandler.createCookie(expectedValue).await().build().toString());
        Base64Value encryptedPayload = versionedPayload(encrypted);

        Optional<Single<String>> cookie = encryptedHandler.findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted)));

        assertAll(() -> assertThat(cookie.map(Single::await), is(Optional.of(expectedValue))),
                  () -> assertThat(currentCipher(ENCRYPTION_PASSWORD)
                                           .decrypt(encryptedPayload)
                                           .toDecodedString(),
                                   is(expectedValue)),
                  () -> assertThrows(CryptoException.class,
                                     () -> unversionedCurrentCipher(ENCRYPTION_PASSWORD)
                                             .decrypt(encryptedPayload)),
                  () -> assertThrows(CryptoException.class,
                                     () -> legacyCipher(ENCRYPTION_PASSWORD)
                                             .decrypt(encryptedPayload)));
    }

    @Test
    void testLegacyEncryptedCookieUsesLegacyDefaults() {
        String expectedValue = "cookieValue";
        OidcCookieHandler encryptedHandler = encryptedHandler(true, false);
        String encrypted = cookieValue(encryptedHandler.createCookie(expectedValue).await().build().toString());

        Optional<Single<String>> cookie = encryptedHandler.findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted)));

        assertAll(() -> assertThat(cookie.map(Single::await), is(Optional.of(expectedValue))),
                  () -> assertThat(legacyCipher(ENCRYPTION_PASSWORD)
                                           .decrypt(Base64Value.createFromEncoded(encrypted))
                                           .toDecodedString(),
                                   is(expectedValue)),
                  () -> assertThrows(CryptoException.class,
                                     () -> currentCipher(ENCRYPTION_PASSWORD)
                                             .decrypt(Base64Value.createFromEncoded(encrypted))));
    }

    @Test
    void testEncryptedHandlersCanReadCookiesTheyCreate() {
        assertCookieRoundTrip(encryptedHandler());
        assertCookieRoundTrip(encryptedHandler(false, true));
        assertCookieRoundTrip(encryptedHandler(true, false));
        assertCookieRoundTrip(encryptedHandler(true, true));
    }

    @Test
    void testCurrentEncryptedCookieRejectsTamperedPayload() {
        String encrypted = cookieValue(encryptedHandler().createCookie("cookieValue").await().build().toString());
        byte[] versioned = Base64Value.createFromEncoded(encrypted).toBytes();
        versioned[versioned.length - 1] ^= 1;
        String tampered = Base64Value.create(versioned).toBase64();

        assertCryptoException(() -> encryptedHandler().findCookie(Map.of("Cookie", List.of("COOKIE=" + tampered)))
                .orElseThrow()
                .await());
    }

    @Test
    void testCurrentEncryptedCookieRejectsTamperedVersion() {
        String encrypted = cookieValue(encryptedHandler().createCookie("cookieValue").await().build().toString());
        byte[] versioned = Base64Value.createFromEncoded(encrypted).toBytes();
        versioned[0] = 2;
        String tampered = Base64Value.create(versioned).toBase64();

        assertCryptoException(() -> encryptedHandler(false, true)
                .findCookie(Map.of("Cookie", List.of("COOKIE=" + tampered)))
                .orElseThrow()
                .await());
    }

    private static void assertCookieRoundTrip(OidcCookieHandler encryptedHandler) {
        String expectedValue = "cookieValue";
        String encrypted = cookieValue(encryptedHandler.createCookie(expectedValue).await().build().toString());

        Optional<Single<String>> cookie = encryptedHandler.findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted)));

        assertThat(cookie.map(Single::await), is(Optional.of(expectedValue)));
    }

    private static String cookieValue(String setCookie) {
        int begin = setCookie.indexOf('=') + 1;
        int end = setCookie.indexOf(';');
        return setCookie.substring(begin, end == -1 ? setCookie.length() : end);
    }

    private static void assertCryptoException(Executable executable) {
        CompletionException exception = assertThrows(CompletionException.class, executable);
        assertThat(exception.getCause(), instanceOf(CryptoException.class));
    }

    private static SymmetricCipher currentCipher(char[] password) {
        return SymmetricCipher.builder()
                .password(password)
                .numberOfIterations(CURRENT_NUMBER_OF_ITERATIONS)
                .additionalAuthenticatedData(CURRENT_VERSION_HEADER)
                .build();
    }

    private static SymmetricCipher unversionedCurrentCipher(char[] password) {
        return SymmetricCipher.create(password);
    }

    private static SymmetricCipher legacyCipher(char[] password) {
        return SymmetricCipher.builder()
                .password(password)
                .numberOfIterations(LEGACY_NUMBER_OF_ITERATIONS)
                .build();
    }

    private static OidcCookieHandler encryptedHandler() {
        return encryptedHandler(false, false);
    }

    private static OidcCookieHandler encryptedHandler(boolean legacyCookieEncryption, boolean legacyCookieFallback) {
        return OidcCookieHandler.builder()
                .encryptionEnabled(true)
                .encryptionPassword(ENCRYPTION_PASSWORD)
                .legacyCookieEncryption(legacyCookieEncryption)
                .legacyCookieFallback(legacyCookieFallback)
                .cookieName("COOKIE")
                .build();
    }

    private static Base64Value versionedPayload(String encrypted) {
        byte[] versioned = Base64Value.createFromEncoded(encrypted).toBytes();
        assertThat(versioned.length > 1, is(true));
        assertThat(versioned[0], is(CURRENT_VERSION));
        return Base64Value.create(Arrays.copyOfRange(versioned, 1, versioned.length));
    }
}
