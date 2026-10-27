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

package io.helidon.common.crypto;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import io.helidon.common.Errors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordKeyDerivationTest {
    private static final String DEFAULT_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String MISSING_PROVIDER = "HelidonMissingDerivationTestProvider";
    private static final int ITERATIONS = 100;
    private static final int KEY_SIZE = 256;

    @ParameterizedTest
    @CsvSource({
            "1, 0c60c80f961f0e71f3a9b524af6012062fe037a6",
            "2, ea6c014dc72d6f8ccd1ed92ace1d41f0d8de8957",
            "4096, 4b007901b765489abead49d926f721d065a429c1"
    })
    @SuppressWarnings("removal")
    void testRfc6070KnownAnswers(int iterations, String expectedHex) {
        // PBKDF2-HMAC-SHA1 vectors from https://www.rfc-editor.org/rfc/rfc6070#section-2.
        String algorithm = "PBKDF2WithHmacSHA1";
        byte[] expected = HexFormat.of().parseHex(expectedHex);
        PasswordKeyDerivation derivation = PasswordKeyDerivation.builder()
                .algorithm(algorithm).numberOfIterations(iterations).keySize(160).build();

        assertThat(derivation.deriveKey(password(), salt()), is(expected));
        assertThat(PasswordKeyDerivation.deriveKey(algorithm, password(), salt(), iterations, 160), is(expected));
        assertThat(PasswordKeyDerivation.deriveKey(algorithm, null, password(), salt(), iterations, 160), is(expected));
    }

    @Test
    @SuppressWarnings("removal")
    void testDefaultAndLegacyParity() throws Exception {
        byte[] expected = jcaDeriveKey(DEFAULT_ALGORITHM, password(), salt());
        PasswordKeyDerivationConfig.Builder builder = builder();

        assertThat(builder.algorithm(), is(DEFAULT_ALGORITHM));
        assertThat(builder.provider(), is(Optional.empty()));
        assertThat(builder.build().deriveKey(password(), salt()), is(expected));
        assertThat(PasswordKeyDerivation.deriveKey(password(), salt(), ITERATIONS, KEY_SIZE), is(expected));
        assertThat(PasswordKeyDerivation.deriveKey(DEFAULT_ALGORITHM, password(), salt(), ITERATIONS, KEY_SIZE),
                   is(expected));
        assertThat(PasswordKeyDerivation.deriveKey(DEFAULT_ALGORITHM, null, password(), salt(), ITERATIONS, KEY_SIZE),
                   is(expected));
    }

    @Test
    void testAlternativeAlgorithm() throws Exception {
        String algorithm = "PBKDF2WithHmacSHA512";
        byte[] expected = jcaDeriveKey(algorithm, password(), salt());
        PasswordKeyDerivation derivation = builder().algorithm(algorithm).build();

        assertThat(derivation.deriveKey(password(), salt()), is(expected));
        assertThat(PasswordKeyDerivation.deriveKey(algorithm, password(), salt(), ITERATIONS, KEY_SIZE), is(expected));
    }

    @Test
    @SuppressWarnings("removal")
    void testExplicitProviderAndClear() throws Exception {
        String provider = SecretKeyFactory.getInstance(DEFAULT_ALGORITHM).getProvider().getName();
        PasswordKeyDerivationConfig.Builder builder = builder().provider(provider);
        PasswordKeyDerivation explicit = builder.build();
        byte[] expected = PasswordKeyDerivation.deriveKey(DEFAULT_ALGORITHM, provider,
                                                         password(), salt(), ITERATIONS, KEY_SIZE);

        assertThat(builder.provider(), is(Optional.of(provider)));
        assertThat(explicit.deriveKey(password(), salt()), is(expected));
        builder.provider(MISSING_PROVIDER);
        assertThrows(CryptoException.class, builder::build);
        assertThrows(CryptoException.class,
                     () -> PasswordKeyDerivation.deriveKey(DEFAULT_ALGORITHM, MISSING_PROVIDER,
                                                           password(), salt(), ITERATIONS, KEY_SIZE));
        builder.clearProvider();
        assertThat(builder.provider(), is(Optional.empty()));
        assertThat(builder.build().deriveKey(password(), salt()), is(expected));
        assertThat(explicit.deriveKey(password(), salt()), is(expected));
    }

    @Test
    void testAlgorithmSetterRetainsProvider() {
        PasswordKeyDerivationConfig.Builder builder = builder().provider(MISSING_PROVIDER)
                .algorithm("PBKDF2WithHmacSHA512");

        assertThat(builder.provider(), is(Optional.of(MISSING_PROVIDER)));
        assertThrows(CryptoException.class, builder::build);
        builder.clearProvider().build();
    }

    @Test
    void testNullSettersPreserveConfiguration() {
        PasswordKeyDerivationConfig.Builder builder = builder().provider(MISSING_PROVIDER);

        assertThrows(NullPointerException.class, () -> builder.algorithm(null));
        assertThrows(NullPointerException.class, () -> builder.provider((String) null));
        assertThat(builder.algorithm(), is(DEFAULT_ALGORITHM));
        assertThat(builder.provider(), is(Optional.of(MISSING_PROVIDER)));
        assertThrows(CryptoException.class, builder::build);
        builder.clearProvider().build();
    }

    @Test
    void testRequiredSettings() {
        assertThrows(Errors.ErrorMessagesException.class, () -> PasswordKeyDerivation.builder().build());
        var missingKeySize = assertThrows(Errors.ErrorMessagesException.class,
                                         () -> PasswordKeyDerivation.builder().numberOfIterations(ITERATIONS).build());
        assertThat(missingKeySize.getMessage(), containsString("keySize"));
        var missingIterations = assertThrows(Errors.ErrorMessagesException.class,
                                            () -> PasswordKeyDerivation.builder().keySize(KEY_SIZE).build());
        assertThat(missingIterations.getMessage(), containsString("numberOfIterations"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void testNonPositiveSettingsFailDuringBuild(int value) {
        assertThrows(CryptoException.class, () -> builder().numberOfIterations(value).build());
        assertThrows(CryptoException.class, () -> builder().keySize(value).build());
    }

    @Test
    void testUnavailableAlgorithmFailsDuringBuild() {
        assertThrows(CryptoException.class, () -> builder().algorithm("HelidonMissingDerivationAlgorithm").build());
    }

    @Test
    void testNewApisRejectNullInputs() {
        PasswordKeyDerivation derivation = builder().build();

        assertThrows(NullPointerException.class, () -> derivation.deriveKey(null, salt()));
        assertThrows(NullPointerException.class, () -> derivation.deriveKey(password(), null));
        assertThrows(NullPointerException.class,
                     () -> PasswordKeyDerivation.deriveKey(null, password(), salt(), ITERATIONS, KEY_SIZE));
        assertThrows(NullPointerException.class,
                     () -> PasswordKeyDerivation.deriveKey(DEFAULT_ALGORITHM, null, salt(), ITERATIONS, KEY_SIZE));
        assertThrows(NullPointerException.class,
                     () -> PasswordKeyDerivation.deriveKey(DEFAULT_ALGORITHM, password(), null, ITERATIONS, KEY_SIZE));
        assertThrows(IllegalArgumentException.class, () -> derivation.deriveKey(password(), new byte[0]));
    }

    @Test
    @SuppressWarnings("removal")
    void testLegacyNullPasswordRemainsEquivalentToEmptyPassword() {
        byte[] expected = PasswordKeyDerivation.deriveKey(new char[0], salt(), ITERATIONS, KEY_SIZE);

        assertThat(PasswordKeyDerivation.deriveKey(null, salt(), ITERATIONS, KEY_SIZE), is(expected));
        assertThat(PasswordKeyDerivation.deriveKey(DEFAULT_ALGORITHM, null, null, salt(), ITERATIONS, KEY_SIZE),
                   is(expected));
    }

    @Test
    void testInstanceUsesEachCallsPasswordAndSalt() throws Exception {
        PasswordKeyDerivation derivation = builder().build();
        char[] password = password();
        byte[] salt = salt();
        byte[] original = derivation.deriveKey(password, salt);
        assertThat(password, is(password()));
        assertThat(salt, is(salt()));

        password[0] = 'P';
        salt[0] = 'S';
        byte[] changed = derivation.deriveKey(password, salt);
        assertThat(changed, is(jcaDeriveKey(DEFAULT_ALGORITHM, password, salt)));
        assertThat(changed, is(not(original)));
        assertThat(derivation.deriveKey(password(), salt()), is(original));
        changed[0] ^= 1;
        assertThat(derivation.deriveKey(password, salt), is(jcaDeriveKey(DEFAULT_ALGORITHM, password, salt)));
    }

    private static PasswordKeyDerivationConfig.Builder builder() {
        return PasswordKeyDerivation.builder().numberOfIterations(ITERATIONS).keySize(KEY_SIZE);
    }

    private static byte[] jcaDeriveKey(String algorithm, char[] password, byte[] salt) throws Exception {
        PBEKeySpec specification = new PBEKeySpec(password, salt, ITERATIONS, KEY_SIZE);
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(specification).getEncoded();
        } finally {
            specification.clearPassword();
        }
    }

    private static char[] password() {
        return "password".toCharArray();
    }

    private static byte[] salt() {
        return "salt".getBytes(StandardCharsets.US_ASCII);
    }
}
