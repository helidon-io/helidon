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

package io.helidon.security.providers.config.vault;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletionException;

import io.helidon.common.Base64Value;
import io.helidon.common.crypto.CryptoException;
import io.helidon.common.crypto.SymmetricCipher;
import io.helidon.config.Config;
import io.helidon.security.Security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class ConfigVaultProviderTest {
    private static final byte CURRENT_VERSION = 1;
    private static final byte[] CURRENT_VERSION_HEADER = {CURRENT_VERSION};
    private static final int CURRENT_NUMBER_OF_ITERATIONS = 600_000;
    private static final int LEGACY_NUMBER_OF_ITERATIONS = 10_000;
    private static final String LEGACY_DEFAULT_ENCRYPTED =
            "helidon:2:D+nDAR+d+Xbl0FNzUl9SyAAAAAyuqKfZ+5yITNfNcgKD6MIda1TrNeh0vO/G1YN0ldH4Azez+SIZ";
    private static final String LEGACY_OVERRIDE_ENCRYPTED =
            "helidon:2:rUa70wZipovWwn2gUXpE+QAAAAzd6oQVM8xrgIG7nX3IsTXgepP1MRW39rA/j4L9Us81DYIAMCiV";

    private static Security security;
    private static Security builtSecurity;

    @BeforeAll
    static void initClass() {
        Config config = Config.create();
        ConfigVaultProvider provider = ConfigVaultProvider.builder()
                .config(config.get("security.0.config-vault"))
                .build();

        security = Security.builder()
                .config(config.get("security"))
                .build();

        builtSecurity = Security.builder()
                .addSecret("password", provider, ConfigVaultProvider.SecretConfig.create("configured-password"))
                .addEncryption("config-vault-configured",
                               provider,
                               ConfigVaultProvider.EncryptionConfig.create("configured-password".toCharArray()))
                .build();
    }

    @Test
    void testEncryptionFromConfig() {
        String secretString = "my secret";
        byte[] secret = secretString.getBytes(StandardCharsets.UTF_8);

        String encryptedDefault = security.encrypt("config-vault-default", secret).await();
        String encryptedOverride = security.encrypt("config-vault-override", secret).await();

        assertThat(encryptedOverride, not(encryptedDefault));

        byte[] decrypted = security.decrypt("config-vault-default", encryptedDefault).await();
        assertThat(new String(decrypted), is(secretString));

        decrypted = security.decrypt("config-vault-override", encryptedOverride).await();
        assertThat(new String(decrypted), is(secretString));

        assertCurrentEncryption("very much secret".toCharArray(), encryptedDefault, secretString);
        assertCurrentEncryption("override".toCharArray(), encryptedOverride, secretString);

        // now make sure we used a different password
        assertCryptoException(() -> security.decrypt("config-vault-override", encryptedDefault).await());

        assertCryptoException(() -> security.decrypt("config-vault-default", encryptedOverride).await());
    }

    @Test
    void testLegacyEncryptionFromConfigRejectedByDefault() {
        assertCryptoException(() -> security.decrypt("config-vault-default", LEGACY_DEFAULT_ENCRYPTED).await());
        assertCryptoException(() -> security.decrypt("config-vault-override", LEGACY_OVERRIDE_ENCRYPTED).await());
    }

    @Test
    void testCurrentEncryptionRejectsTamperedVersion() {
        String encrypted = security.encrypt("config-vault-default",
                                            "my secret".getBytes(StandardCharsets.UTF_8))
                .await();
        String tampered = tamperVersion(encrypted);

        assertCryptoException(() -> security.decrypt("config-vault-default", tampered).await());
        assertCryptoException(() -> security.decrypt("config-vault-provider-fallback", tampered).await());
    }

    @Test
    void testCurrentEncryptionRejectsTamperedPayload() {
        String encrypted = security.encrypt("config-vault-default",
                                            "my secret".getBytes(StandardCharsets.UTF_8))
                .await();
        String tampered = tamperPayload(encrypted);

        assertCryptoException(() -> security.decrypt("config-vault-default", tampered).await());
        assertCryptoException(() -> security.decrypt("config-vault-provider-fallback", tampered).await());
    }

    @Test
    void testLegacyFallbackFromConfigCanBeRead() {
        String secretString = "my secret";

        assertDecrypts("config-vault-provider-fallback", LEGACY_DEFAULT_ENCRYPTED, secretString);
        assertDecrypts("config-vault-entry-fallback", LEGACY_DEFAULT_ENCRYPTED, secretString);
        assertDecrypts("config-vault-override-fallback", LEGACY_OVERRIDE_ENCRYPTED, secretString);

        assertCryptoException(() -> security.decrypt("config-vault-provider-fallback", LEGACY_OVERRIDE_ENCRYPTED)
                .await());
        assertCryptoException(() -> security.decrypt("config-vault-override-fallback", LEGACY_DEFAULT_ENCRYPTED)
                .await());
    }

    @Test
    void testLegacyEncryptionFromConfigWritesLegacyCiphertext() {
        String secretString = "my secret";
        byte[] secret = secretString.getBytes(StandardCharsets.UTF_8);

        String encryptedDefault = security.encrypt("config-vault-provider-legacy", secret).await();
        String encryptedOverride = security.encrypt("config-vault-override-legacy", secret).await();

        assertLegacyEncryption("very much secret".toCharArray(), encryptedDefault, secretString);
        assertLegacyEncryption("override".toCharArray(), encryptedOverride, secretString);
    }

    @Test
    void testLegacyEncryptionWithFallbackFromConfig() {
        String secretString = "my secret";
        byte[] secret = secretString.getBytes(StandardCharsets.UTF_8);
        char[] password = "very much secret".toCharArray();
        String currentEncrypted = currentEncrypt(password, secret);

        assertCryptoException(() -> security.decrypt("config-vault-provider-legacy", currentEncrypted).await());
        assertThat(new String(security.decrypt("config-vault-entry-legacy-fallback", currentEncrypted).await(),
                              StandardCharsets.UTF_8),
                   is(secretString));

        String encrypted = security.encrypt("config-vault-entry-legacy-fallback", secret).await();
        assertLegacyEncryption(password, encrypted, secretString);
    }

    @Test
    void testSecretFromConfig() {
        String password = security.secret("password", "default-value").await();

        assertThat(password, is("secret-password"));
    }

    @Test
    void testSecretFromBuilt() {
        String password = builtSecurity.secret("password", "default-value").await();

        assertThat(password, is("configured-password"));
    }

    @Test
    void testProgrammaticEncryptionConfig() {
        String secretString = "my secret";
        byte[] secret = secretString.getBytes(StandardCharsets.UTF_8);
        char[] password = "configured-password".toCharArray();

        String encrypted = builtSecurity.encrypt("config-vault-configured", secret).await();

        assertThat(new String(builtSecurity.decrypt("config-vault-configured", encrypted).await(),
                              StandardCharsets.UTF_8),
                   is(secretString));
        assertCurrentEncryption(password, encrypted, secretString);

        String legacyEncrypted = legacyCipher(password).encryptToString(Base64Value.create(secret));
        assertCryptoException(() -> builtSecurity.decrypt("config-vault-configured", legacyEncrypted).await());
    }

    @Test
    void testProgrammaticLegacyFlags() {
        String secretString = "my secret";
        byte[] secret = secretString.getBytes(StandardCharsets.UTF_8);
        char[] password = "configured-password".toCharArray();
        ConfigVaultProvider provider = ConfigVaultProvider.builder()
                .masterPassword(password)
                .legacyFallback(true)
                .build();
        Security security = Security.builder()
                .addEncryption("provider-fallback", provider, ConfigVaultProvider.EncryptionConfig.create())
                .addEncryption("fallback-disabled",
                               provider,
                               ConfigVaultProvider.EncryptionConfig.create(password, false, false))
                .addEncryption("legacy-write",
                               provider,
                               ConfigVaultProvider.EncryptionConfig.create(password, true, false))
                .build();
        String legacyEncrypted = legacyCipher(password).encryptToString(Base64Value.create(secret));

        assertThat(new String(security.decrypt("provider-fallback", legacyEncrypted).await(), StandardCharsets.UTF_8),
                   is(secretString));
        assertCryptoException(() -> security.decrypt("fallback-disabled", legacyEncrypted).await());

        String encrypted = security.encrypt("legacy-write", secret).await();

        assertLegacyEncryption(password, encrypted, secretString);
    }

    private static void assertDecrypts(String encryptionName, String encrypted, String expectedSecret) {
        byte[] decrypted = security.decrypt(encryptionName, encrypted).await();

        assertThat(new String(decrypted, StandardCharsets.UTF_8), is(expectedSecret));
    }

    private static void assertCryptoException(Executable executable) {
        CompletionException exception = Assertions.assertThrows(CompletionException.class, executable);
        assertThat(exception.getCause(), instanceOf(CryptoException.class));
    }

    private static SymmetricCipher currentCipher(char[] password) {
        return SymmetricCipher.builder()
                .password(password)
                .numberOfIterations(CURRENT_NUMBER_OF_ITERATIONS)
                .additionalAuthenticatedData(CURRENT_VERSION_HEADER)
                .build();
    }

    private static SymmetricCipher legacyCipher(char[] password) {
        return SymmetricCipher.builder()
                .password(password)
                .numberOfIterations(LEGACY_NUMBER_OF_ITERATIONS)
                .build();
    }

    private static void assertLegacyEncryption(char[] password, String encrypted, String expectedSecret) {
        assertThat(legacyCipher(password).decryptFromString(encrypted).toDecodedString(), is(expectedSecret));
        Assertions.assertThrows(CryptoException.class, () -> currentDecrypt(password, encrypted));
    }

    private static void assertCurrentEncryption(char[] password, String encrypted, String expectedSecret) {
        assertThat(currentDecrypt(password, encrypted).toDecodedString(), is(expectedSecret));
        assertCurrentEncryptionCannotBeReadByLegacy(password, encrypted);
    }

    private static void assertCurrentEncryptionCannotBeReadByLegacy(char[] password, String encrypted) {
        Assertions.assertThrows(CryptoException.class, () -> legacyCipher(password).decryptFromString(encrypted));
    }

    private static String currentEncrypt(char[] password, byte[] secret) {
        byte[] encrypted = currentCipher(password).encrypt(Base64Value.create(secret)).toBytes();
        byte[] versioned = new byte[encrypted.length + 1];
        versioned[0] = CURRENT_VERSION;
        System.arraycopy(encrypted, 0, versioned, 1, encrypted.length);
        return Base64Value.create(versioned).toBase64();
    }

    private static String tamperVersion(String encrypted) {
        byte[] versioned = versionedBytes(encrypted);
        versioned[0] = 2;
        return Base64Value.create(versioned).toBase64();
    }

    private static String tamperPayload(String encrypted) {
        byte[] versioned = versionedBytes(encrypted);
        versioned[versioned.length - 1] ^= 1;
        return Base64Value.create(versioned).toBase64();
    }

    private static byte[] versionedBytes(String encrypted) {
        byte[] versioned = Base64Value.createFromEncoded(encrypted).toBytes();
        assertThat(versioned.length > 1, is(true));
        assertThat(versioned[0], is(CURRENT_VERSION));
        return versioned;
    }

    private static Base64Value currentDecrypt(char[] password, String encrypted) {
        byte[] versioned;
        try {
            versioned = Base64Value.createFromEncoded(encrypted).toBytes();
        } catch (RuntimeException e) {
            throw new CryptoException("Config vault encrypted value does not use the current format", e);
        }
        if (versioned.length == 0 || versioned[0] != CURRENT_VERSION) {
            throw new CryptoException("Config vault encrypted value does not use the current format");
        }
        return currentCipher(password).decrypt(Base64Value.create(Arrays.copyOfRange(versioned, 1, versioned.length)));
    }
}
