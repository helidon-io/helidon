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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.Base64Value;
import io.helidon.common.crypto.CryptoException;
import io.helidon.common.crypto.SymmetricCipher;
import io.helidon.http.SetCookie;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
        Optional<String> cookie = handler.findCookie(headers);

        assertThat(cookie, is(Optional.empty()));
    }

    @Test
    void testFindCookiePresent() {
        String expectedValue = "cookieValue";
        Map<String, List<String>> headers = Map.of("Accept", List.of("application/json"),
                                                   "Cookie", List.of("COOKIE=" + expectedValue));
        Optional<String> cookie = handler.findCookie(headers);

        assertThat(cookie, not(Optional.empty()));
        String cookieValue = cookie.get();
        assertThat(cookieValue, is(expectedValue));

        headers = Map.of("Accept", List.of("application/json"),
                         "Cookie", List.of("COOKIE=" + expectedValue + ";abc=bbc;uao=aee"));
        cookie = handler.findCookie(headers);

        assertThat(cookie, not(Optional.empty()));
        cookieValue = cookie.get();
        assertThat(cookieValue, is(expectedValue));

        headers = Map.of("Accept", List.of("application/json"),
                         "Cookie", List.of("abc=bbc; COOKIE=" + expectedValue + ";uao=aee"));
        cookie = handler.findCookie(headers);

        assertThat(cookie, not(Optional.empty()));
        cookieValue = cookie.get();
        assertThat(cookieValue, is(expectedValue));

        headers = Map.of("Accept", List.of("application/json"),
                         "Cookie", List.of("abc=bbc;uao=aee;COOKIE=" + expectedValue));
        cookie = handler.findCookie(headers);

        assertThat(cookie, not(Optional.empty()));
        cookieValue = cookie.get();
        assertThat(cookieValue, is(expectedValue));
    }

    @Test
    void testUncompressedHandlerPreservesCompressionPrefix() {
        String expectedValue = "~tenant";

        Optional<String> cookie = handler.findCookie(Map.of("Cookie", List.of("COOKIE=" + expectedValue)));

        assertThat(cookie, is(Optional.of(expectedValue)));
    }

    @Test
    void testDefaultHandlerRejectsLegacyEncryptedCookie() {
        OidcCookieHandler encryptedHandler = encryptedHandler();

        assertThrows(CryptoException.class,
                     () -> encryptedHandler.findCookie(Map.of("Cookie", List.of("COOKIE=" + LEGACY_ENCRYPTED_COOKIE))));
    }

    @Test
    void testLegacyFallbackReadsLegacyEncryptedCookie() {
        String expectedValue = "cookieValue";
        OidcCookieHandler encryptedHandler = encryptedHandler(false, true);

        Optional<String> cookie = encryptedHandler.findCookie(Map.of("Cookie", List.of("COOKIE=" + LEGACY_ENCRYPTED_COOKIE)));

        assertThat(cookie, is(Optional.of(expectedValue)));
    }

    @Test
    void testLegacyEncryptionFallbackReadsCurrentEncryptedCookie() {
        String expectedValue = "cookieValue";
        String currentEncrypted = encryptedHandler().createCookie(expectedValue).build().value();

        assertAll(() -> assertThrows(CryptoException.class,
                                     () -> encryptedHandler(true, false)
                                             .findCookie(Map.of("Cookie", List.of("COOKIE=" + currentEncrypted)))),
                  () -> assertThat(encryptedHandler(true, true)
                                           .findCookie(Map.of("Cookie", List.of("COOKIE=" + currentEncrypted))),
                                   is(Optional.of(expectedValue))));
    }

    @Test
    void testNewEncryptedCookieUsesCurrentDefaults() {
        String expectedValue = "cookieValue";
        OidcCookieHandler encryptedHandler = encryptedHandler();
        String encrypted = encryptedHandler.createCookie(expectedValue).build().value();
        Base64Value encryptedPayload = versionedPayload(encrypted);

        Optional<String> cookie = encryptedHandler.findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted)));

        assertAll(() -> assertThat(cookie, is(Optional.of(expectedValue))),
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
        String encrypted = encryptedHandler.createCookie(expectedValue).build().value();

        Optional<String> cookie = encryptedHandler.findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted)));

        assertAll(() -> assertThat(cookie, is(Optional.of(expectedValue))),
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
    void testCompressedEncryptedCookieRoundTripAndSize() {
        String expectedValue =
                "eyJhY2Nlc3NUb2tlbiI6ImV5SnliMnhsY3lJNld5SmhaRzFwYmlJc0luVnpaWElpWFgwPSJ9".repeat(45);
        OidcCookieHandler compressedHandler = compressedHandler();
        SetCookie compressedCookie = compressedHandler.createCookie(expectedValue).build();
        SetCookie uncompressedCookie = encryptedHandler().createCookie(expectedValue).build();
        String compressed = compressedCookie.value();
        String uncompressed = uncompressedCookie.value();
        byte[] compressedPayload = currentCipher(ENCRYPTION_PASSWORD)
                .decrypt(versionedPayload(compressed))
                .toBytes();

        assertAll(() -> assertThat(compressedHandler
                                           .findCookie(Map.of("Cookie", List.of("COOKIE=" + compressed))),
                                   is(Optional.of(expectedValue))),
                  () -> assertThat("Compression must reduce the encrypted cookie size",
                                   compressed.length() < uncompressed.length(),
                                   is(true)),
                  () -> assertThat("Compressed cookie must fit into the browser cookie limit",
                                   compressedCookie.toString().length() < 4096,
                                   is(true)),
                  () -> assertThat("The equivalent uncompressed cookie must demonstrate the size regression",
                                   uncompressedCookie.toString().length() > 4096,
                                   is(true)),
                  () -> assertThat(compressedPayload[0], is((byte) 0)),
                  () -> assertThat(compressedPayload[1], is((byte) 0x1f)),
                  () -> assertThat(compressedPayload[2], is((byte) 0x8b)));
    }

    @Test
    void testCompressedRawEncryptedCookieRoundTripAndSize() {
        String expectedValue = largeIdTokenValue();
        OidcCookieHandler compressedHandler = compressedHandler();
        SetCookie compressedCookie = compressedHandler.createCookie(expectedValue).build();
        SetCookie uncompressedCookie = encryptedHandler().createCookie(expectedValue).build();
        String compressed = compressedCookie.value();
        String uncompressed = uncompressedCookie.value();
        byte[] compressedPayload = currentCipher(ENCRYPTION_PASSWORD)
                .decrypt(versionedPayload(compressed))
                .toBytes();

        assertAll(() -> assertThat(compressedHandler
                                           .findCookie(Map.of("Cookie", List.of("COOKIE=" + compressed))),
                                   is(Optional.of(expectedValue))),
                  () -> assertThat("Compression must reduce the encrypted cookie size",
                                   compressed.length() < uncompressed.length(),
                                   is(true)),
                  () -> assertThat("Compressed cookie must fit into the browser cookie limit",
                                   compressedCookie.toString().length() < 4096,
                                   is(true)),
                  () -> assertThat("The equivalent uncompressed cookie must demonstrate the size regression",
                                   uncompressedCookie.toString().length() > 4096,
                                   is(true)),
                  () -> assertThat(compressedPayload[0], is((byte) 1)),
                  () -> assertThat(compressedPayload[1], is((byte) 0x1f)),
                  () -> assertThat(compressedPayload[2], is((byte) 0x8b)));
    }

    @Test
    void testCompressedUnencryptedCookieRoundTripAndSize() {
        String expectedValue =
                "eyJhY2Nlc3NUb2tlbiI6ImV5SnliMnhsY3lJNld5SmhaRzFwYmlJc0luVnpaWElpWFgwPSJ9".repeat(65);
        OidcCookieHandler compressedHandler = unencryptedHandler(true);
        SetCookie compressedCookie = compressedHandler.createCookie(expectedValue).build();
        SetCookie uncompressedCookie = unencryptedHandler(false).createCookie(expectedValue).build();
        String compressed = compressedCookie.value();

        assertAll(() -> assertThat(compressedHandler
                                           .findCookie(Map.of("Cookie", List.of("COOKIE=" + compressed))),
                                   is(Optional.of(expectedValue))),
                  () -> assertThat("Compressed value must use the cookie-safe format",
                                   compressed.startsWith("~"),
                                   is(true)),
                  () -> assertThat("Compression must reduce the unencrypted cookie size",
                                   compressed.length() < uncompressedCookie.value().length(),
                                   is(true)),
                  () -> assertThat("Compressed cookie must fit into the browser cookie limit",
                                   compressedCookie.toString().length() < 4096,
                                   is(true)),
                  () -> assertThat("The equivalent uncompressed cookie must demonstrate the size regression",
                                   uncompressedCookie.toString().length() > 4096,
                                   is(true)));
    }

    @Test
    void testCompressedHandlerReadsExistingUncompressedCookie() {
        String expectedValue = "existingCookieValue";
        String encrypted = encryptedHandler().createCookie(expectedValue).build().value();

        Optional<String> cookie = compressedHandler()
                .findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted)));

        assertThat(cookie, is(Optional.of(expectedValue)));
    }

    @Test
    void testCompressionDisabledHandlerReadsCompressedCookie() {
        String expectedValue =
                "eyJhY2Nlc3NUb2tlbiI6ImV5SnliMnhsY3lJNld5SmhaRzFwYmlJc0luVnpaWElpWFgwPSJ9".repeat(45);
        String encrypted = compressedHandler().createCookie(expectedValue).build().value();

        Optional<String> cookie = encryptedHandler()
                .findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted)));

        assertThat(cookie, is(Optional.of(expectedValue)));
    }

    @Test
    void testCompressionDisabledHandlerReadsCompressedRawCookie() {
        String expectedValue = largeIdTokenValue();
        String encrypted = compressedHandler().createCookie(expectedValue).build().value();

        Optional<String> cookie = encryptedHandler()
                .findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted)));

        assertThat(cookie, is(Optional.of(expectedValue)));
    }

    @Test
    void testCompressionDisabledUnencryptedHandlerReadsCompressedCookie() {
        String expectedValue = largeIdTokenValue();
        String compressed = unencryptedHandler(true).createCookie(expectedValue).build().value();

        Optional<String> cookie = unencryptedHandler(false)
                .findCookie(Map.of("Cookie", List.of("COOKIE=" + compressed)));

        assertAll(() -> assertThat(compressed.startsWith("~"), is(true)),
                  () -> assertThat(cookie, is(Optional.of(expectedValue))));
    }

    @Test
    void testMaximumSizeCompressedRawCookieRoundTrip() {
        String expectedValue = "a.".repeat(32 * 1024);
        String encrypted = compressedHandler().createCookie(expectedValue).build().value();

        Optional<String> cookie = compressedHandler()
                .findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted)));

        assertThat(cookie, is(Optional.of(expectedValue)));
    }

    @Test
    void testLegacyEncryptionDoesNotWriteCompressedCookie() {
        String expectedValue =
                "eyJhY2Nlc3NUb2tlbiI6ImV5SnliMnhsY3lJNld5SmhaRzFwYmlJc0luVnpaWElpWFgwPSJ9".repeat(45);
        String encrypted = encryptedHandler(true, false, true).createCookie(expectedValue).build().value();
        String decrypted = legacyCipher(ENCRYPTION_PASSWORD)
                .decrypt(Base64Value.createFromEncoded(encrypted))
                .toDecodedString();

        assertThat(decrypted, is(expectedValue));
    }

    @Test
    void testLegacyEncryptionFallbackReadsCurrentCompressedRawCookie() {
        String expectedValue = largeIdTokenValue();
        String currentEncrypted = compressedHandler().createCookie(expectedValue).build().value();

        assertAll(() -> assertThrows(CryptoException.class,
                                     () -> encryptedHandler(true, false)
                                             .findCookie(Map.of("Cookie", List.of("COOKIE=" + currentEncrypted)))),
                  () -> assertThat(encryptedHandler(true, true)
                                           .findCookie(Map.of("Cookie", List.of("COOKIE=" + currentEncrypted))),
                                   is(Optional.of(expectedValue))));
    }

    @Test
    void testCompressionDoesNotExpandShortCookie() {
        String expectedValue = "cookieValue";
        String encrypted = compressedHandler().createCookie(expectedValue).build().value();
        String decrypted = currentCipher(ENCRYPTION_PASSWORD)
                .decrypt(versionedPayload(encrypted))
                .toDecodedString();
        String unencrypted = unencryptedHandler(true).createCookie(expectedValue).build().value();

        assertAll(() -> assertThat(decrypted, is(expectedValue)),
                  () -> assertThat(unencrypted, is(expectedValue)));
    }

    @Test
    void testUnencryptedCompressionDoesNotExpandEncodedCookie() {
        String expectedValue = "a".repeat(25);
        String unencrypted = unencryptedHandler(true).createCookie(expectedValue).build().value();
        String encrypted = compressedHandler().createCookie(expectedValue).build().value();
        byte[] compressedPayload = currentCipher(ENCRYPTION_PASSWORD)
                .decrypt(versionedPayload(encrypted))
                .toBytes();

        assertAll(() -> assertThat(compressedPayload[0], is((byte) 1)),
                  () -> assertThat(unencrypted, is(expectedValue)));
    }

    @Test
    void testMalformedUnencryptedCompressedCookieRejected() {
        assertAll(() -> assertThrows(CryptoException.class,
                                     () -> unencryptedHandler(false)
                                             .findCookie(Map.of("Cookie", List.of("COOKIE=~not-base64")))),
                  () -> assertThrows(CryptoException.class,
                                     () -> unencryptedHandler(false)
                                             .findCookie(Map.of("Cookie", List.of("COOKIE=~Y29va2llVmFsdWU=")))));
    }

    @Test
    void testMalformedCompressedCookieRejected() {
        String compressedBase64 = encryptCurrent(new byte[] {0, 1, 2, 3});
        String compressedRaw = encryptCurrent(new byte[] {1, 1, 2, 3});

        assertAll(() -> assertThrows(CryptoException.class,
                                     () -> compressedHandler()
                                             .findCookie(Map.of("Cookie", List.of("COOKIE=" + compressedBase64)))),
                  () -> assertThrows(CryptoException.class,
                                     () -> compressedHandler()
                                             .findCookie(Map.of("Cookie", List.of("COOKIE=" + compressedRaw)))));
    }

    @Test
    void testOversizedCompressedCookieRejected() throws IOException {
        byte[] oversized = "a".repeat(48 * 1024 + 1).getBytes(StandardCharsets.UTF_8);
        String encrypted = encryptCurrent(compress(oversized));

        assertThrows(CryptoException.class,
                     () -> compressedHandler().findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted))));
    }

    @Test
    void testMaximumSizeCompressedBase64CookieAccepted() throws IOException {
        byte[] maximum = "a".repeat(48 * 1024).getBytes(StandardCharsets.UTF_8);
        String encrypted = encryptCurrent(compress(maximum));
        String expectedValue = Base64.getEncoder().encodeToString(maximum);

        Optional<String> cookie = compressedHandler()
                .findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted)));

        assertThat(cookie, is(Optional.of(expectedValue)));
    }

    @Test
    void testOversizedCompressedRawCookieRejected() throws IOException {
        byte[] oversized = "a".repeat(64 * 1024 + 1).getBytes(StandardCharsets.UTF_8);
        String encrypted = encryptCurrent(compress(oversized, (byte) 1));

        assertThrows(CryptoException.class,
                     () -> compressedHandler().findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted))));
    }

    @Test
    void testCurrentEncryptedCookieRejectsTamperedPayload() {
        String encrypted = encryptedHandler().createCookie("cookieValue").build().value();
        byte[] versioned = Base64Value.createFromEncoded(encrypted).toBytes();
        versioned[versioned.length - 1] ^= 1;
        String tampered = Base64Value.create(versioned).toBase64();

        assertThrows(CryptoException.class,
                     () -> encryptedHandler().findCookie(Map.of("Cookie", List.of("COOKIE=" + tampered))));
    }

    @Test
    void testCurrentEncryptedCookieRejectsTamperedVersion() {
        String encrypted = encryptedHandler().createCookie("cookieValue").build().value();
        byte[] versioned = Base64Value.createFromEncoded(encrypted).toBytes();
        versioned[0] = 2;
        String tampered = Base64Value.create(versioned).toBase64();

        assertThrows(CryptoException.class,
                     () -> encryptedHandler(false, true).findCookie(Map.of("Cookie", List.of("COOKIE=" + tampered))));
    }

    private static void assertCookieRoundTrip(OidcCookieHandler encryptedHandler) {
        String expectedValue = "cookieValue";
        String encrypted = encryptedHandler.createCookie(expectedValue).build().value();

        Optional<String> cookie = encryptedHandler.findCookie(Map.of("Cookie", List.of("COOKIE=" + encrypted)));

        assertThat(cookie, is(Optional.of(expectedValue)));
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
        return encryptedHandler(legacyCookieEncryption, legacyCookieFallback, false);
    }

    private static OidcCookieHandler compressedHandler() {
        return encryptedHandler(false, false, true);
    }

    private static OidcCookieHandler unencryptedHandler(boolean compressionEnabled) {
        return OidcCookieHandler.builder()
                .encryptionEnabled(false)
                .compressionEnabled(compressionEnabled)
                .cookieName("COOKIE")
                .build();
    }

    private static OidcCookieHandler encryptedHandler(boolean legacyCookieEncryption,
                                                      boolean legacyCookieFallback,
                                                      boolean compressionEnabled) {
        return OidcCookieHandler.builder()
                .encryptionEnabled(true)
                .encryptionPassword(ENCRYPTION_PASSWORD)
                .compressionEnabled(compressionEnabled)
                .legacyCookieEncryption(legacyCookieEncryption)
                .legacyCookieFallback(legacyCookieFallback)
                .cookieName("COOKIE")
                .build();
    }

    private static String encryptCurrent(byte[] value) {
        byte[] encrypted = currentCipher(ENCRYPTION_PASSWORD)
                .encrypt(Base64Value.create(value))
                .toBytes();
        byte[] versioned = new byte[encrypted.length + 1];
        versioned[0] = CURRENT_VERSION;
        System.arraycopy(encrypted, 0, versioned, 1, encrypted.length);
        return Base64Value.create(versioned).toBase64();
    }

    private static byte[] compress(byte[] value) throws IOException {
        return compress(value, (byte) 0);
    }

    private static byte[] compress(byte[] value, byte marker) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(marker);
        try (GZIPOutputStream gzip = new GZIPOutputStream(result)) {
            gzip.write(value);
        }
        return result.toByteArray();
    }

    private static String largeIdTokenValue() {
        return "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9."
                + "eyJhdWQiOiJjbGllbnQtaWQiLCJpc3MiOiJodHRwczovL2lkZW50aXR5Lm9yYWNsZS5jb20iLCJzdWIiOiJ1c2VyIn0"
                .repeat(40)
                + "."
                + "c2lnbmF0dXJlLWJ5dGVz".repeat(20);
    }

    private static Base64Value versionedPayload(String encrypted) {
        byte[] versioned = Base64Value.createFromEncoded(encrypted).toBytes();
        assertThat(versioned.length > 1, is(true));
        assertThat(versioned[0], is(CURRENT_VERSION));
        return Base64Value.create(Arrays.copyOfRange(versioned, 1, versioned.length));
    }
}
