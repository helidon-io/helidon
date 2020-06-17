/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.encryption;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.OptionalHelper;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;

/**
 * Encryption utilities for secrets protection.
 */
public final class EncryptionUtil {
    private static final Logger LOGGER = Logger.getLogger(EncryptionUtil.class.getName());

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int SALT_LENGTH = 16;
    private static final int NONCE_LENGTH = 12; //(Also called IV) Needs to be 12 when using GCM!
    private static final int SEED_LENGTH = 16;
    private static final int HASH_ITERATIONS = 10000;
    private static final int KEY_LENGTH_LEGACY = 128;
    private static final int KEY_LENGTH = 256;
    private static final int AUTHENTICATION_TAG_LENGTH = 128;

    private EncryptionUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Decrypt using RSA with OAEP.
     * Expects message encrypted with the public key.
     *
     * @param key             private key used to decrypt
     * @param encryptedBase64 base64 encoded encrypted secret
     * @return Secret value
     * @throws ConfigEncryptionException If any problem with decryption occurs
     */
    public static String decryptRsa(PrivateKey key, String encryptedBase64) throws ConfigEncryptionException {
        Objects.requireNonNull(key, "Key must be provided for decryption");
        Objects.requireNonNull(encryptedBase64, "Encrypted bytes must be provided for decryption (base64 encoded)");

        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (ConfigEncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigEncryptionException("Failed to decrypt value using RSA. Returning clear text value as is: "
                                                        + encryptedBase64, e);
        }
    }

    /**
     * Decrypt using RSA (private or public key).
     * Expects message encrypted with the other key.
     *
     * @param key             private or public key to use to decrypt
     * @param encryptedBase64 base64 encoded encrypted secret
     * @return Secret value
     * @throws ConfigEncryptionException If any problem with decryption occurs
     */
    public static String decryptRsaLegacy(Key key, String encryptedBase64) throws ConfigEncryptionException {
        Objects.requireNonNull(key, "Key must be provided for decryption");
        Objects.requireNonNull(encryptedBase64, "Encrypted bytes must be provided for decryption (base64 encoded)");

        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (ConfigEncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigEncryptionException("Failed to decrypt value using RSA. Returning clear text value as is: "
                                                    + encryptedBase64, e);
        }
    }

    /**
     * Encrypt secret using RSA with OAEP.
     *
     * @param key     public key used to encrypt
     * @param secret  secret to encrypt
     * @return base64 encoded encrypted bytes
     * @throws ConfigEncryptionException If any problem with encryption occurs
     */
    public static String encryptRsa(PublicKey key, String secret) throws ConfigEncryptionException {
        Objects.requireNonNull(key, "Key must be provided for encryption");
        Objects.requireNonNull(secret, "Secret message must be provided to be encrypted");
        if (secret.getBytes(StandardCharsets.UTF_8).length > 190) {
            throw new ConfigEncryptionException("Secret value is too large. Maximum of 190 bytes is allowed.");
        }

        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new ConfigEncryptionException("Failed to encrypt using RSA key", e);
        }
    }

    /**
     * Encrypt using AES with GCM method, key is derived from password with random salt.
     *
     * @param masterPassword master password
     * @param secret         secret to encrypt
     * @return Encrypted value base64 encoded
     * @throws ConfigEncryptionException If any problem with encryption occurs
     */
    public static String encryptAes(char[] masterPassword, String secret) throws ConfigEncryptionException {
        Objects.requireNonNull(masterPassword, "Password must be provided for encryption");
        Objects.requireNonNull(secret, "Secret message must be provided to be encrypted");

        byte[] salt = SECURE_RANDOM.generateSeed(SALT_LENGTH);
        byte[] nonce = SECURE_RANDOM.generateSeed(NONCE_LENGTH);
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        Cipher cipher = cipher(masterPassword, salt, nonce, Cipher.ENCRYPT_MODE);
        // encrypt
        byte[] encryptedMessageBytes;
        try {
            encryptedMessageBytes = cipher.doFinal(secretBytes);
        } catch (Exception e) {
            throw new ConfigEncryptionException("Failed to encrypt", e);
        }

        // get bytes to base64 (salt + nonce + encrypted message)
        byte[] bytesToEncode = new byte[encryptedMessageBytes.length + salt.length + nonce.length];
        System.arraycopy(salt, 0, bytesToEncode, 0, salt.length);
        System.arraycopy(nonce, 0, bytesToEncode, salt.length, nonce.length);
        System.arraycopy(encryptedMessageBytes, 0, bytesToEncode, nonce.length + salt.length, encryptedMessageBytes.length);

        return Base64.getEncoder().encodeToString(bytesToEncode);
    }

    private static Cipher cipherLegacy(char[] masterPassword, byte[] salt, int cipherMode) throws ConfigEncryptionException {
        try {
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec keySpec = new PBEKeySpec(masterPassword, salt, HASH_ITERATIONS, KEY_LENGTH_LEGACY);
            SecretKeySpec spec = new SecretKeySpec(secretKeyFactory.generateSecret(keySpec).getEncoded(), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(cipherMode, spec, new IvParameterSpec(salt));

            return cipher;
        } catch (Exception e) {
            throw new ConfigEncryptionException("Failed to prepare a cipher instance", e);
        }
    }

    private static Cipher cipher(char[] masterPassword, byte[] salt, byte[] nonce, int cipherMode)
            throws ConfigEncryptionException {
        try {
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec keySpec = new PBEKeySpec(masterPassword, salt, HASH_ITERATIONS, KEY_LENGTH);
            SecretKeySpec spec = new SecretKeySpec(secretKeyFactory.generateSecret(keySpec).getEncoded(), "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(cipherMode, spec, new GCMParameterSpec(AUTHENTICATION_TAG_LENGTH, nonce));

            return cipher;
        } catch (Exception e) {
            throw new ConfigEncryptionException("Failed to prepare a cipher instance", e);
        }
    }

    /**
     * Decrypt using legacy AES.
     * Will only decrypt messages encrypted with previously used AES method.
     *
     * @param masterPassword  master password
     * @param encryptedBase64 encrypted secret, base64 encoded
     * @return Decrypted secret
     */
    public static String decryptAesLegacy(char[] masterPassword, String encryptedBase64) {
        Objects.requireNonNull(masterPassword, "Password must be provided for encryption");
        Objects.requireNonNull(encryptedBase64, "Encrypted bytes must be provided for decryption (base64 encoded)");

        try {
            // decode base64
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedBase64);

            // extract salt and encrypted bytes
            byte[] salt = new byte[SALT_LENGTH];
            byte[] encryptedBytes = new byte[decodedBytes.length - SALT_LENGTH];

            System.arraycopy(decodedBytes, 0, salt, 0, SALT_LENGTH);
            System.arraycopy(decodedBytes, SALT_LENGTH, encryptedBytes, 0, encryptedBytes.length);

            // get cipher
            Cipher cipher = cipherLegacy(masterPassword, salt, Cipher.DECRYPT_MODE);

            // bytes with seed
            byte[] decryptedBytes;
            decryptedBytes = cipher.doFinal(encryptedBytes);
            byte[] originalBytes = new byte[decryptedBytes.length - SEED_LENGTH];
            System.arraycopy(decryptedBytes, SEED_LENGTH, originalBytes, 0, originalBytes.length);

            return new String(originalBytes, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            throw new ConfigEncryptionException("Failed to decrypt value using AES. Returning clear text value as is: "
                                                        + encryptedBase64, e);
        }
    }

    /**
     * Decrypt using AES.
     * Will only decrypt messages encrypted with {@link #encryptAes(char[], String)} as the algorithm used is quite custom
     * (number of bytes of seed, of salt and approach).
     *
     * @param masterPassword  master password
     * @param encryptedBase64 encrypted secret, base64 encoded
     * @return Decrypted secret
     * @throws ConfigEncryptionException if something bad happens during decryption (e.g. wrong password)
     */
    public static String decryptAes(char[] masterPassword, String encryptedBase64) throws ConfigEncryptionException {
        Objects.requireNonNull(masterPassword, "Password must be provided for encryption");
        Objects.requireNonNull(encryptedBase64, "Encrypted bytes must be provided for decryption (base64 encoded)");

        try {
            // decode base64
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedBase64);

            // extract salt and encrypted bytes
            byte[] salt = new byte[SALT_LENGTH];
            byte[] nonce = new byte[NONCE_LENGTH];
            byte[] encryptedBytes = new byte[decodedBytes.length - SALT_LENGTH - NONCE_LENGTH];

            System.arraycopy(decodedBytes, 0, salt, 0, SALT_LENGTH);
            System.arraycopy(decodedBytes, SALT_LENGTH, nonce, 0, NONCE_LENGTH);
            System.arraycopy(decodedBytes, SALT_LENGTH + NONCE_LENGTH, encryptedBytes, 0, encryptedBytes.length);

            // get cipher
            Cipher cipher = cipher(masterPassword, salt, nonce, Cipher.DECRYPT_MODE);

            // bytes with seed
            byte[] decryptedBytes;
            decryptedBytes = cipher.doFinal(encryptedBytes);
            byte[] originalBytes = new byte[decryptedBytes.length];
            System.arraycopy(decryptedBytes, 0, originalBytes, 0, originalBytes.length);

            return new String(originalBytes, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            throw new ConfigEncryptionException("Failed to decrypt value using AES. Returning clear text value as is: "
                                                    + encryptedBase64, e);
        }
    }

    static Optional<char[]> resolveMasterPassword(boolean requireEncryption, Config config) {
        Optional<char[]> result = OptionalHelper.from(getEnv(ConfigProperties.MASTER_PASSWORD_ENV_VARIABLE))
                .or(() -> {
                    ConfigValue<String> value = config.get(ConfigProperties.MASTER_PASSWORD_CONFIG_KEY).asString();
                    if (value.isPresent()) {
                        if (requireEncryption) {
                            LOGGER.warning(
                                    "Master password is configured as clear text in configuration when encryption is required. "
                                            + "This value will be ignored. System property or environment variable expected!!!");
                            return Optional.empty();
                        }
                    }
                    return value.asOptional();
                })
                .asOptional()
                .map(String::toCharArray);

        if (!result.isPresent()) {
            LOGGER.fine("Securing properties using master password is not available, as master password is not configured");
        }

        return result;
    }

    static Optional<PrivateKey> resolvePrivateKey(Config config) {
        // load configuration values
        KeyConfig.PemBuilder pemBuilder = KeyConfig.pemBuilder().config(config);
        KeyConfig.KeystoreBuilder keystoreBuilder = KeyConfig.keystoreBuilder().config(config);

        getEnv(ConfigProperties.PRIVATE_KEY_PEM_PATH_ENV_VARIABLE)
                .map(Paths::get)
                .ifPresent(path -> pemBuilder.key(Resource.create(path)));

        getEnv(ConfigProperties.PRIVATE_KEY_PASS_ENV_VARIABLE)
                .map(String::toCharArray)
                .ifPresent(pemBuilder::keyPassphrase);

        // override the ones defined in environment variables
        getEnv(ConfigProperties.PRIVATE_KEYSTORE_PATH_ENV_VARIABLE)
                .map(Paths::get)
                .ifPresent(path -> keystoreBuilder.keystore(Resource.create(path)));

        getEnv(ConfigProperties.PRIVATE_KEYSTORE_TYPE_ENV_VARIABLE)
                .ifPresent(keystoreBuilder::keystoreType);

        getEnv(ConfigProperties.PRIVATE_KEYSTORE_PASS_ENV_VARIABLE)
                .map(String::toCharArray)
                .ifPresent(keystoreBuilder::keystorePassphrase);

        getEnv(ConfigProperties.PRIVATE_KEY_PASS_ENV_VARIABLE)
                .map(String::toCharArray)
                .ifPresent(keystoreBuilder::keyPassphrase);

        getEnv(ConfigProperties.PRIVATE_KEY_ALIAS_ENV_VARIABLE)
                .ifPresent(keystoreBuilder::keyAlias);

        Optional<PrivateKey> result = KeyConfig.fullBuilder()
                .updateWith(pemBuilder)
                .updateWith(keystoreBuilder)
                .build()
                .privateKey();

        if (!result.isPresent()) {
            LOGGER.fine("Securing properties using asymmetric cipher is not available, as private key is not configured");
        }

        return result;
    }

    static Optional<String> getEnv(String envVariable) {
        return Optional.ofNullable(System.getenv(envVariable));
    }
}
