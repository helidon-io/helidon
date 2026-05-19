/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Base64Value;
import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.configurable.Resource;
import io.helidon.common.crypto.AsymmetricCipher;
import io.helidon.common.crypto.PasswordKeyDerivation;
import io.helidon.common.crypto.SymmetricCipher;
import io.helidon.common.pki.Keys;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;

/**
 * Encryption utilities for secrets protection.
 */
public final class EncryptionUtil {
    private static final System.Logger LOGGER = System.getLogger(EncryptionUtil.class.getName());

    // SecureRandom instances cannot be in memory when building native image
    private static final LazyValue<SecureRandom> SECURE_RANDOM = LazyValue.create(SecureRandom::new);

    private static final int SALT_LENGTH = 16;
    private static final int NONCE_LENGTH = 12; //(Also called IV) Needs to be 12 when using GCM!
    private static final int HASH_ITERATIONS = 10000;
    static final int ENVELOPE_HASH_ITERATIONS = 600000;
    static final int ENVELOPE_MIN_HASH_ITERATIONS = 600000;
    static final int ENVELOPE_MAX_HASH_ITERATIONS = 10000000;
    private static final int ENVELOPE_VERSION = 1;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int ENVELOPE_HEADER_LENGTH = Byte.BYTES + Integer.BYTES;
    private static final int SYMMETRIC_CIPHER_MIN_LENGTH = SALT_LENGTH + Integer.BYTES + NONCE_LENGTH + GCM_TAG_LENGTH;
    private static final int ENVELOPE_MAX_LENGTH = 64 * 1024;
    private static final int KEY_LENGTH = 256;

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
            Base64Value value = Base64Value.createFromEncoded(encryptedBase64);
            return AsymmetricCipher.decrypt(AsymmetricCipher.ALGORITHM_RSA_ECB_OAEP256, null, key, value).toDecodedString();
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
            Base64Value value = Base64Value.create(secret);
            return AsymmetricCipher.encrypt(AsymmetricCipher.ALGORITHM_RSA_ECB_OAEP256, null, key, value).toBase64();
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
        Objects.requireNonNull(secret, "Secret message must be provided to be encrypted");

        return encryptAesBytes(masterPassword, secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encrypt using AES with GCM method into a versioned envelope, key is derived from password with random salt.
     *
     * @param masterPassword master password
     * @param secret         secret to encrypt
     * @param iterations     PBKDF2 iteration count
     * @return Encrypted envelope base64 encoded
     * @throws ConfigEncryptionException If any problem with encryption occurs
     */
    public static String encryptAesEnvelope(char[] masterPassword, String secret, int iterations)
            throws ConfigEncryptionException {
        Objects.requireNonNull(secret, "Secret message must be provided to be encrypted");

        return encryptAesEnvelopeBytes(masterPassword, secret.getBytes(StandardCharsets.UTF_8), iterations);
    }

    /**
     * Encrypt using AES with GCM method into a versioned envelope, key is derived from password with random salt.
     *
     * @param masterPassword master password
     * @param secret         secret to encrypt
     * @return Encrypted envelope base64 encoded
     * @throws ConfigEncryptionException If any problem with encryption occurs
     */
    public static String encryptAesEnvelope(char[] masterPassword, String secret) throws ConfigEncryptionException {
        return encryptAesEnvelope(masterPassword, secret, ENVELOPE_HASH_ITERATIONS);
    }

    /**
     * Encrypt using AES with GCM method, key is derived from password with random salt.
     *
     * @param masterPassword master password
     * @param secret         secret to encrypt
     * @return Encrypted value base64 encoded
     * @throws ConfigEncryptionException If any problem with encryption occurs
     * @deprecated this method will be removed once a separate module for encryption is created
     */
    @Deprecated(since = "2.2.0")
    public static String encryptAesBytes(char[] masterPassword, byte[] secret) throws ConfigEncryptionException {
        Objects.requireNonNull(masterPassword, "Password must be provided for encryption");
        Objects.requireNonNull(secret, "Secret message must be provided to be encrypted");

        byte[] salt = SECURE_RANDOM.get().generateSeed(SALT_LENGTH);
        byte[] nonce = SECURE_RANDOM.get().generateSeed(NONCE_LENGTH);
        byte[] key = PasswordKeyDerivation
                .deriveKey("PBKDF2WithHmacSHA256", null, masterPassword, salt, HASH_ITERATIONS, KEY_LENGTH);
        byte[] encrypted = SymmetricCipher.encrypt(SymmetricCipher.ALGORITHM_AES_GCM, key, nonce, Base64Value.create(secret))
                .toBytes();

        // get bytes to base64 (salt + nonce + encrypted message)
        byte[] bytesToEncode = new byte[encrypted.length + salt.length + nonce.length];
        System.arraycopy(salt, 0, bytesToEncode, 0, salt.length);
        System.arraycopy(nonce, 0, bytesToEncode, salt.length, nonce.length);
        System.arraycopy(encrypted, 0, bytesToEncode, nonce.length + salt.length, encrypted.length);

        return Base64.getEncoder().encodeToString(bytesToEncode);
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
        return new String(decryptAesBytes(masterPassword, encryptedBase64), StandardCharsets.UTF_8);
    }

    /**
     * Decrypt using AES from a versioned envelope.
     *
     * @param masterPassword  master password
     * @param encryptedBase64 encrypted envelope, base64 encoded
     * @return Decrypted secret
     * @throws ConfigEncryptionException if something bad happens during decryption (e.g. wrong password)
     */
    public static String decryptAesEnvelope(char[] masterPassword, String encryptedBase64)
            throws ConfigEncryptionException {
        return new String(decryptAesEnvelopeBytes(masterPassword, encryptedBase64), StandardCharsets.UTF_8);
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
     * @deprecated This method will be moved to a new module
     */
    @Deprecated(since = "2.2.0")
    public static byte[] decryptAesBytes(char[] masterPassword, String encryptedBase64) {
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

            byte[] key = PasswordKeyDerivation
                    .deriveKey("PBKDF2WithHmacSHA256", null, masterPassword, salt, HASH_ITERATIONS, KEY_LENGTH);
            Base64Value encryptedValue = Base64Value.create(encryptedBytes);
            return SymmetricCipher.decrypt(SymmetricCipher.ALGORITHM_AES_GCM, key, nonce, encryptedValue).toBytes();
        } catch (Throwable e) {
            throw new ConfigEncryptionException("Failed to decrypt value using AES. Returning clear text value as is: "
                                                        + encryptedBase64, e);
        }
    }

    static void validateEnvelopeIterations(int iterations) {
        if (iterations < ENVELOPE_MIN_HASH_ITERATIONS || iterations > ENVELOPE_MAX_HASH_ITERATIONS) {
            throw new ConfigEncryptionException("PBKDF2 iterations must be between "
                                                        + ENVELOPE_MIN_HASH_ITERATIONS + " and "
                                                        + ENVELOPE_MAX_HASH_ITERATIONS + ", but was " + iterations);
        }
    }

    private static String encryptAesEnvelopeBytes(char[] masterPassword, byte[] secret, int iterations)
            throws ConfigEncryptionException {
        Objects.requireNonNull(masterPassword, "Password must be provided for encryption");
        Objects.requireNonNull(secret, "Secret message must be provided to be encrypted");
        validateEnvelopeIterations(iterations);
        if (secret.length > ENVELOPE_MAX_LENGTH - ENVELOPE_HEADER_LENGTH - SYMMETRIC_CIPHER_MIN_LENGTH) {
            throw new ConfigEncryptionException("Secret value is too large");
        }

        try {
            byte[] header = envelopeHeader(iterations);
            SymmetricCipher cipher = SymmetricCipher.builder()
                    .password(masterPassword)
                    .numberOfIterations(iterations)
                    .keySize(KEY_LENGTH)
                    .additionalAuthenticatedData(header)
                    .build();
            byte[] encrypted = cipher.encrypt(Base64Value.create(secret)).toBytes();
            if (header.length + encrypted.length > ENVELOPE_MAX_LENGTH) {
                throw new ConfigEncryptionException("Secret value is too large");
            }

            BufferData bytesToEncode = BufferData.create(header.length + encrypted.length);
            bytesToEncode.write(header);
            bytesToEncode.write(encrypted);

            return Base64.getEncoder().encodeToString(bytesToEncode.readBytes());
        } catch (ConfigEncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigEncryptionException("Failed to encrypt using AES envelope", e);
        }
    }

    private static byte[] decryptAesEnvelopeBytes(char[] masterPassword, String encryptedBase64) {
        Objects.requireNonNull(masterPassword, "Password must be provided for encryption");
        Objects.requireNonNull(encryptedBase64, "Encrypted bytes must be provided for decryption (base64 encoded)");

        try {
            AesEnvelope envelope = decodeAesEnvelope(encryptedBase64);
            switch (envelope.version()) {
            case ENVELOPE_VERSION:
                return decryptAesEnvelopeV1(masterPassword, envelope);
            default:
                throw new ConfigEncryptionException("Unsupported AES envelope version: " + envelope.version());
            }
        } catch (ConfigEncryptionException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ConfigEncryptionException("Encrypted AES envelope is not valid", e);
        } catch (Exception e) {
            throw new ConfigEncryptionException("Failed to decrypt value using AES envelope", e);
        }
    }

    private static byte[] decryptAesEnvelopeV1(char[] masterPassword, AesEnvelope envelope) {
        validateEnvelopeIterations(envelope.iterations());
        SymmetricCipher cipher = SymmetricCipher.builder()
                .password(masterPassword)
                .numberOfIterations(envelope.iterations())
                .keySize(KEY_LENGTH)
                .additionalAuthenticatedData(envelope.header())
                .build();

        return cipher.decrypt(Base64Value.create(envelope.encrypted())).toBytes();
    }

    static AesEnvelope decodeAesEnvelope(String encryptedBase64) {
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(encryptedBase64);
        } catch (IllegalArgumentException e) {
            throw new ConfigEncryptionException("Encrypted AES envelope is not valid", e);
        }

        if (decodedBytes.length < ENVELOPE_HEADER_LENGTH + SYMMETRIC_CIPHER_MIN_LENGTH
                || decodedBytes.length > ENVELOPE_MAX_LENGTH) {
            throw new ConfigEncryptionException("Encrypted AES envelope is not valid");
        }

        BufferData envelopeData = BufferData.create(decodedBytes);
        byte[] header = new byte[ENVELOPE_HEADER_LENGTH];
        envelopeData.read(header);

        BufferData headerData = BufferData.create(header);
        int version = headerData.read();
        int iterations = headerData.readInt32();

        return new AesEnvelope(version, iterations, header, envelopeData.readBytes());
    }

    private static byte[] envelopeHeader(int iterations) {
        BufferData header = BufferData.create(ENVELOPE_HEADER_LENGTH);
        header.writeInt8(ENVELOPE_VERSION);
        header.writeInt32(iterations);
        return header.readBytes();
    }

    static final class AesEnvelope {
        private final int version;
        private final int iterations;
        private final byte[] header;
        private final byte[] encrypted;

        private AesEnvelope(int version, int iterations, byte[] header, byte[] encrypted) {
            this.version = version;
            this.iterations = iterations;
            this.header = header;
            this.encrypted = encrypted;
        }

        int version() {
            return version;
        }

        int iterations() {
            return iterations;
        }

        byte[] header() {
            return header;
        }

        byte[] encrypted() {
            return encrypted;
        }
    }

    static Optional<char[]> resolveMasterPassword(boolean requireEncryption, Config config) {
        Optional<char[]> result = getEnv(ConfigProperties.MASTER_PASSWORD_ENV_VARIABLE)
                .or(() -> {
                    ConfigValue<String> value = config.get(ConfigProperties.MASTER_PASSWORD_CONFIG_KEY).asString();
                    if (value.isPresent()) {
                        if (requireEncryption) {
                            LOGGER.log(Level.WARNING,
                                       "Master password is configured as clear text in configuration when encryption is "
                                               + "required. "
                                               + "This value will be ignored. System property or environment variable "
                                               + "expected!!!");
                            return Optional.empty();
                        }
                    }
                    return value.asOptional();
                })
                .map(String::toCharArray);

        if (result.isEmpty()) {
            LOGGER.log(Level.DEBUG, "Securing properties using master password is not available, as master password is not "
                    + "configured");
        }

        return result;
    }


    static Optional<PrivateKey> resolvePrivateKey(Config config) {
        // load configuration values
        Keys.Builder builder = Keys.builder();
        builder.config(config);

        builder.pem(pemBuilder -> {
            getEnv(ConfigProperties.PRIVATE_KEY_PEM_PATH_ENV_VARIABLE)
                    .map(Paths::get)
                    .ifPresent(path -> pemBuilder.key(Resource.create(path)));

            getEnv(ConfigProperties.PRIVATE_KEY_PASS_ENV_VARIABLE)
                    .map(String::toCharArray)
                    .ifPresent(pemBuilder::keyPassphrase);
        });

        // override the ones defined in environment variables
        getEnv(ConfigProperties.PRIVATE_KEYSTORE_PATH_ENV_VARIABLE)
                .map(Paths::get)
                .ifPresent(path -> builder.keystore(keystoreBuilder -> {
                    keystoreBuilder.keystore(Resource.create(path));
                    getEnv(ConfigProperties.PRIVATE_KEYSTORE_TYPE_ENV_VARIABLE)
                            .ifPresent(keystoreBuilder::type);

                    getEnv(ConfigProperties.PRIVATE_KEYSTORE_PASS_ENV_VARIABLE)
                            .map(String::toCharArray)
                            .ifPresent(keystoreBuilder::passphrase);

                    getEnv(ConfigProperties.PRIVATE_KEY_PASS_ENV_VARIABLE)
                            .map(String::toCharArray)
                            .ifPresent(keystoreBuilder::keyPassphrase);

                    getEnv(ConfigProperties.PRIVATE_KEY_ALIAS_ENV_VARIABLE)
                            .ifPresent(keystoreBuilder::keyAlias);
                }));

        Optional<PrivateKey> result = builder.build().privateKey();

        if (result.isEmpty()) {
            LOGGER.log(Level.DEBUG, "Securing properties using asymmetric cipher is not available, as private key is not "
                    + "configured");
        }

        return result;
    }

    static Optional<String> getEnv(String envVariable) {
        return Optional.ofNullable(System.getenv(envVariable));
    }

}
