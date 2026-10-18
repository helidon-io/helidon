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

package io.helidon.common.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.Api;
import io.helidon.common.Base64Value;
import io.helidon.common.LazyValue;

/**
 * This class provides encryption and decryption using a selected symmetric cipher.
 * <p>
 * A password-based cipher derives a key using a fresh random salt for each encryption and includes the salt and
 * initialization vector in the encrypted message.
 * <p>
 * Construction validates the configuration for both encryption and decryption using JCA initialization.
 * Password-based construction also performs one key derivation with the configured settings for validation.
 * Message-dependent errors, such as invalid block lengths or failed authentication, are reported during operations.
 * <p>
 * A raw-key cipher, configured using {@link Builder#key(byte[])}, uses the supplied key and initialization vector or
 * algorithm parameters directly. Its output contains only the ciphertext, without a salt or initialization-vector envelope.
 * For example:
 * <pre>{@code
 * SymmetricCipher cipher = SymmetricCipher.builder()
 *         .algorithm(SymmetricCipher.ALGORITHM_AES_GCM)
 *         .key(key)
 *         .iv(iv)
 *         .build();
 * Base64Value encrypted = cipher.encrypt(message);
 * }</pre>
 * A raw-key instance permits only one encryption attempt, including a failed attempt, and can decrypt repeatedly.
 * This limit also applies to algorithms that do not require initialization parameters.
 * For algorithms requiring a fresh initialization vector or nonce for each encryption with the same key, configure
 * a new cipher with a fresh value for each message. Each raw-key instance retains its configured parameters;
 * creating another instance does not make reusing the same key and initialization vector safe.
 */
@Api.Stable
public class SymmetricCipher implements CommonCipher {

    /**
     * AES algorithm with CBC method and PKCS5 padding.
     * <br>
     * It is strongly advised to be used together with HMAC or other authenticated message digest.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_AES_CBC = "AES/CBC/PKCS5Padding";

    /**
     * AES algorithm with CTR method and no padding.
     * <br>
     * It is strongly advised to be used together with HMAC or other authenticated message digest.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_AES_CTR = "AES/CTR/NoPadding";

    /**
     * AES algorithm with GCM method and no padding.
     * <br>
     * Does not need to be used with any authenticated message digest since GCM is making its signature by the design.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_AES_GCM = "AES/GCM/NoPadding";

    /**
     * ChaCha20 encryption algorithm.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_CHA_CHA = "ChaCha20";

    /**
     * ChaCha20 encryption algorithm with Poly1305 authentication code.
     * <br>
     * Value is: {@value}.
     */
    public static final String ALGORITHM_CHA_CHA_POLY1305 = "ChaCha20-Poly1305";

    private static final LazyValue<SecureRandom> SECURE_RANDOM = LazyValue.create(SecureRandom::new);
    private static final Pattern PATTERN_ALGORITHM = Pattern.compile("^(\\S+)/\\S+/\\S+$");
    private static final int SALT_LENGTH = 16;
    private static final int DEFAULT_NUMBER_OF_ITERATIONS = 600_000;

    private final String algorithm;
    private final String provider;
    private final char[] password;
    private final byte[] key;
    private final int keySize;
    private final int numberOfIterations;
    private final byte[] additionalAuthenticatedData;
    private final AlgorithmParameterSpec parameters;
    private final AtomicBoolean rawKeyEncryptionUsed;

    private SymmetricCipher(Builder builder) {
        this.algorithm = builder.algorithm;
        this.provider = builder.provider;
        this.password = builder.password;
        this.key = builder.key == null ? null : builder.key.clone();
        this.keySize = builder.keySize;
        this.numberOfIterations = builder.numberOfIterations;
        this.additionalAuthenticatedData = builder.additionalAuthenticatedData;
        this.rawKeyEncryptionUsed = key == null ? null : new AtomicBoolean();
        try {
            this.parameters = builder.iv == null ? builder.parameters : createAlgorithmParameter(algorithm, builder.iv);
            validateConfiguration();
        } catch (CryptoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CryptoException("Invalid symmetric cipher configuration", e);
        }
    }

    /**
     * Create a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new instance based on the password.
     *
     * Default used algorithm is {@link #ALGORITHM_AES_GCM}.
     *
     * @param password password
     * @return new instance
     */
    public static SymmetricCipher create(char[] password) {
        return new Builder().password(password).build();
    }

    /**
     * Encrypt the message with the usage of provided parameters.
     *
     * @param algorithm algorithm to be used
     * @param key       key to encrypt message
     * @param iv        initialization vector
     * @param plain     message
     * @return encrypted message
     */
    public static Base64Value encrypt(String algorithm, byte[] key, byte[] iv, Base64Value plain) {
        return encryptWithIv(algorithm, null, key, iv, plain);
    }

    /**
     * Encrypt the message with the usage of provided parameters.
     *
     * @param algorithm algorithm to be used
     * @param provider  provider of the algorithm, or {@code null} for normal JCA provider selection
     * @param key       key to encrypt message
     * @param iv        initialization vector
     * @param plain     message
     * @return encrypted message
     * @deprecated use {@link #builder()} with {@link Builder#key(byte[])} and {@link Builder#iv(byte[])}, then
     *             {@link #encrypt(Base64Value)}. Set {@link Builder#provider(String)} only for an explicit provider.
     */
    @Deprecated(since = "28.0.0", forRemoval = true)
    public static Base64Value encrypt(String algorithm, String provider, byte[] key, byte[] iv, Base64Value plain) {
        return encryptWithIv(algorithm, provider, key, iv, plain);
    }

    /**
     * Encrypt the message with the usage of provided parameters.
     *
     * @param algorithm algorithm to be used
     * @param provider  provider of the algorithm, or {@code null} for normal JCA provider selection
     * @param key       key to encrypt message
     * @param params    cipher parameter object
     * @param plain     message
     * @return encrypted message
     * @deprecated use {@link #builder()} with {@link Builder#key(byte[])} and
     *             {@link Builder#parameters(AlgorithmParameterSpec)}, then {@link #encrypt(Base64Value)}.
     *             Set {@link Builder#provider(String)} only for an explicit provider.
     */
    @Deprecated(since = "28.0.0", forRemoval = true)
    public static Base64Value encrypt(String algorithm,
                                      String provider,
                                      byte[] key,
                                      AlgorithmParameterSpec params,
                                      Base64Value plain) {
        Objects.requireNonNull(algorithm, "Algorithm cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(params, "Algorithm parameters cannot be null");
        return encryptMessage(algorithm, provider, key, params, null, plain);
    }

    /**
     * Decrypt the message with the usage of provided parameters.
     *
     * @param algorithm algorithm to be used
     * @param key       key to decrypt message
     * @param iv        encrypted message initialization vector
     * @param encrypted encrypted message
     * @return decrypted message
     */
    public static Base64Value decrypt(String algorithm, byte[] key, byte[] iv, Base64Value encrypted) {
        return decryptWithIv(algorithm, null, key, iv, encrypted);
    }

    /**
     * Decrypt the message with the usage of provided parameters.
     *
     * @param algorithm algorithm to be used
     * @param provider  algorithm provider, or {@code null} for normal JCA provider selection
     * @param key       key to decrypt message
     * @param iv        encrypted message initialization vector
     * @param encrypted encrypted message
     * @return decrypted message
     * @deprecated use {@link #builder()} with {@link Builder#key(byte[])} and {@link Builder#iv(byte[])}, then
     *             {@link #decrypt(Base64Value)}. Set {@link Builder#provider(String)} only for an explicit provider.
     */
    @Deprecated(since = "28.0.0", forRemoval = true)
    public static Base64Value decrypt(String algorithm, String provider, byte[] key, byte[] iv, Base64Value encrypted) {
        return decryptWithIv(algorithm, provider, key, iv, encrypted);
    }

    /**
     * Decrypt the message with the usage of provided parameters.
     *
     * @param algorithm algorithm to be used
     * @param provider  algorithm provider, or {@code null} for normal JCA provider selection
     * @param key       key to decrypt message
     * @param params    cipher parameter object, or {@code null} if no parameters are required
     * @param encrypted encrypted message
     * @return decrypted message
     * @deprecated use {@link #builder()} with {@link Builder#key(byte[])} and
     *             {@link Builder#parameters(AlgorithmParameterSpec)}, then {@link #decrypt(Base64Value)}.
     *             Omit parameters when none are required, and set {@link Builder#provider(String)} only for an explicit provider.
     */
    @Deprecated(since = "28.0.0", forRemoval = true)
    public static Base64Value decrypt(String algorithm,
                                      String provider,
                                      byte[] key,
                                      AlgorithmParameterSpec params,
                                      Base64Value encrypted) {
        return decryptMessage(algorithm, provider, key, params, null, encrypted);
    }

    /**
     * Encrypt a message. Password-based instances generate fresh per-message key material and can be reused.
     * A raw-key instance permits only one encryption attempt, even if that attempt fails.
     *
     * @param message plain content
     * @return encrypted content
     * @throws NullPointerException if message is {@code null}
     * @throws CryptoException if a raw-key instance has already attempted encryption, or encryption fails
     */
    @Override
    public Base64Value encrypt(Base64Value message) {
        Objects.requireNonNull(message, "Plain content cannot be null");
        if (key != null) {
            if (!rawKeyEncryptionUsed.compareAndSet(false, true)) {
                throw new CryptoException("Raw-key cipher can only be used for one encryption attempt");
            }
            return encryptMessage(algorithm, provider, key, parameters, additionalAuthenticatedData, message);
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            byte[] salt = new byte[SALT_LENGTH];
            SECURE_RANDOM.get().nextBytes(salt);
            byte[] key = PasswordKeyDerivation.deriveKey(password, salt, numberOfIterations, keySize);
            Cipher cipher = cipher(algorithm, provider, key, null, Cipher.ENCRYPT_MODE);
            if (additionalAuthenticatedData != null) {
                cipher.updateAAD(additionalAuthenticatedData);
            }
            byte[] iv = cipher.getIV();
            outputStream.writeBytes(salt);
            dataOutputStream.writeInt(iv.length);
            outputStream.writeBytes(iv);
            outputStream.writeBytes(cipher.doFinal(message.toBytes()));
            return Base64Value.create(outputStream.toByteArray());
        } catch (IOException | IllegalBlockSizeException | BadPaddingException e) {
            throw new CryptoException("An error occurred while message encryption", e);
        }
    }

    @Override
    public Base64Value decrypt(Base64Value encrypted) {
        Objects.requireNonNull(encrypted, "Encrypted content cannot be null");
        if (key != null) {
            return decryptMessage(algorithm, provider, key, parameters, additionalAuthenticatedData, encrypted);
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(encrypted.toBytes());
                DataInputStream dataInputStream = new DataInputStream(inputStream)) {
            byte[] salt = inputStream.readNBytes(SALT_LENGTH);
            int ivSize = dataInputStream.readInt();
            byte[] iv = inputStream.readNBytes(ivSize);
            byte[] key = PasswordKeyDerivation.deriveKey(password, salt, numberOfIterations, keySize);
            Cipher cipher = cipher(algorithm, provider, key, createAlgorithmParameter(algorithm, iv), Cipher.DECRYPT_MODE);
            if (additionalAuthenticatedData != null) {
                cipher.updateAAD(additionalAuthenticatedData);
            }
            return Base64Value.create(cipher.doFinal(inputStream.readAllBytes()));
        } catch (EOFException e) {
            throw new CryptoException("Encrypted value is not valid", e);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new CryptoException("Failed to decrypt the message", e);
        } catch (IOException e) {
            throw new CryptoException("An error occurred while message decryption", e);
        }
    }

    private static Base64Value encryptWithIv(String algorithm, String provider, byte[] key, byte[] iv, Base64Value plain) {
        Objects.requireNonNull(algorithm, "Algorithm cannot be null");
        Objects.requireNonNull(iv, "Initialization vector cannot be null");
        return encryptMessage(algorithm, provider, key, createAlgorithmParameter(algorithm, iv), null, plain);
    }

    private static Base64Value decryptWithIv(String algorithm, String provider, byte[] key, byte[] iv, Base64Value encrypted) {
        Objects.requireNonNull(algorithm, "Algorithm cannot be null");
        Objects.requireNonNull(iv, "Initialization vector cannot be null");
        return decryptMessage(algorithm, provider, key, createAlgorithmParameter(algorithm, iv), null, encrypted);
    }

    private static Base64Value encryptMessage(String algorithm,
                                            String provider,
                                            byte[] key,
                                            AlgorithmParameterSpec params,
                                            byte[] additionalAuthenticatedData,
                                            Base64Value plain) {
        Objects.requireNonNull(algorithm, "Algorithm cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(plain, "Plain content cannot be null");
        Cipher cipher = cipher(algorithm, provider, key, params, Cipher.ENCRYPT_MODE);
        try {
            if (additionalAuthenticatedData != null) {
                cipher.updateAAD(additionalAuthenticatedData);
            }
            return Base64Value.create(cipher.doFinal(plain.toBytes()));
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new CryptoException("Failed to encrypt the message", e);
        }
    }

    private static Base64Value decryptMessage(String algorithm,
                                            String provider,
                                            byte[] key,
                                            AlgorithmParameterSpec params,
                                            byte[] additionalAuthenticatedData,
                                            Base64Value encrypted) {
        Objects.requireNonNull(algorithm, "Algorithm cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(encrypted, "Encrypted content cannot be null");
        Cipher cipher = cipher(algorithm, provider, key, params, Cipher.DECRYPT_MODE);
        try {
            if (additionalAuthenticatedData != null) {
                cipher.updateAAD(additionalAuthenticatedData);
            }
            return Base64Value.create(cipher.doFinal(encrypted.toBytes()));
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new CryptoException("Failed to decrypt the message", e);
        }
    }

    private static Cipher cipher(String algorithm,
                                 String provider,
                                 byte[] key,
                                 AlgorithmParameterSpec parameterSpec,
                                 int cipherMode) {
        try {
            Matcher matcher = PATTERN_ALGORITHM.matcher(algorithm);
            String keySpecAlg = matcher.matches() ? matcher.group(1) : algorithm;
            SecretKeySpec spec = new SecretKeySpec(key, keySpecAlg);
            Cipher cipher;
            if (provider == null) {
                cipher = Cipher.getInstance(algorithm);
            } else {
                cipher = Cipher.getInstance(algorithm, provider);
            }
            if (parameterSpec == null) {
                cipher.init(cipherMode, spec);
            } else {
                cipher.init(cipherMode, spec, parameterSpec);
            }
            return cipher;
        } catch (Exception e) {
            throw new CryptoException("Failed to prepare a cipher instance", e);
        }
    }

    private static AlgorithmParameterSpec createAlgorithmParameter(String algorithm, byte[] iv) {
        switch (algorithm) {
        case ALGORITHM_AES_GCM:
            return new GCMParameterSpec(128, iv);
        case ALGORITHM_CHA_CHA:
            return new ChaCha20ParameterSpec(iv, 1);
        default:
            return new IvParameterSpec(iv);
        }
    }

    private void validateConfiguration() {
        byte[] validationKey = key == null
                ? PasswordKeyDerivation.deriveKey(password, new byte[SALT_LENGTH], numberOfIterations, keySize)
                : key;
        try {
            Cipher encrypt;
            Cipher decrypt;
            if (key == null) {
                encrypt = cipher(algorithm, provider, validationKey, null, Cipher.ENCRYPT_MODE);
                byte[] iv = encrypt.getIV();
                if (iv == null) {
                    throw new CryptoException("Password-based encryption requires an algorithm with an initialization vector");
                }
                decrypt = cipher(algorithm, provider, validationKey, createAlgorithmParameter(algorithm, iv),
                                 Cipher.DECRYPT_MODE);
            } else {
                // Check decryption first: encryption may silently generate missing parameters.
                decrypt = cipher(algorithm, provider, validationKey, parameters, Cipher.DECRYPT_MODE);
                encrypt = cipher(algorithm, provider, validationKey, parameters, Cipher.ENCRYPT_MODE);
                if (parameters == null && (encrypt.getIV() != null || encrypt.getParameters() != null)) {
                    throw new CryptoException("Raw-key encryption requires explicit initialization parameters"
                                                      + " for this algorithm");
                }
            }
            if (additionalAuthenticatedData != null) {
                encrypt.updateAAD(additionalAuthenticatedData);
                decrypt.updateAAD(additionalAuthenticatedData);
            }
        } finally {
            if (key == null) {
                Arrays.fill(validationKey, (byte) 0);
            }
        }
    }

    /**
     * Builder of the {@link SymmetricCipher}.
     * Optional values can be removed using the corresponding {@code clear} methods; setters do not accept {@code null}.
     * Configure either a password or a raw key. An explicit initialization vector or algorithm parameters can only
     * be used with a raw key, and cannot both be configured.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, SymmetricCipher> {

        private String algorithm = ALGORITHM_AES_GCM;
        private String provider;
        private int numberOfIterations = DEFAULT_NUMBER_OF_ITERATIONS;
        private int keySize = 256;
        private char[] password;
        private byte[] key;
        private byte[] iv;
        private AlgorithmParameterSpec parameters;
        private byte[] additionalAuthenticatedData;

        private Builder() {
        }

        /**
         * Set algorithm which should be used.
         * <br>
         * Default value is {@link #ALGORITHM_AES_GCM}.
         *
         * @param algorithm algorithm to be used
         * @return updated builder instance
         * @throws NullPointerException if algorithm is {@code null}
         */
        public Builder algorithm(String algorithm) {
            Objects.requireNonNull(algorithm);
            this.algorithm = algorithm;
            return this;
        }

        /**
         * Clear the explicit provider selection and use normal JCA provider selection.
         *
         * @return updated builder instance
         * @since 28.0.0
         */
        public Builder clearProvider() {
            this.provider = null;
            return this;
        }

        /**
         * Set provider of the algorithm.
         * <p>
         * To use normal JCA provider selection, call {@link #clearProvider()} instead of passing {@code null}.
         *
         * @param provider provider to be used
         * @return updated builder instance
         * @throws NullPointerException if provider is {@code null}
         */
        public Builder provider(String provider) {
            Objects.requireNonNull(provider);
            this.provider = provider;
            return this;
        }

        /**
         * Clear the password so a raw key can be configured instead.
         *
         * @return updated builder instance
         * @since 28.0.0
         */
        public Builder clearPassword() {
            this.password = null;
            return this;
        }

        /**
         * Set password upon which the cryptography key will be generated.
         * <p>
         * To remove the password, call {@link #clearPassword()} instead of passing {@code null}.
         * A password cannot be combined with a raw key or explicit initialization parameters.
         *
         * @param password base password
         * @return updated builder instance
         * @throws NullPointerException if password is {@code null}
         */
        public Builder password(char[] password) {
            Objects.requireNonNull(password);
            this.password = password.clone();
            return this;
        }

        /**
         * Clear the raw key so a password can be configured instead.
         *
         * @return updated builder instance
         * @since 28.0.0
         */
        public Builder clearKey() {
            this.key = null;
            return this;
        }

        /**
         * Set the raw key used directly for encryption and decryption, without key derivation or a salt/IV envelope.
         * A raw key cannot be combined with a password.
         * The built instance permits only one encryption attempt and can decrypt repeatedly.
         * <p>
         * To remove the key, call {@link #clearKey()} instead of passing {@code null}.
         *
         * @param key raw key, copied by this builder
         * @return updated builder instance
         * @throws NullPointerException if key is {@code null}
         * @since 28.0.0
         */
        public Builder key(byte[] key) {
            Objects.requireNonNull(key);
            this.key = key.clone();
            return this;
        }

        /**
         * Clear the explicit initialization vector.
         *
         * @return updated builder instance
         * @since 28.0.0
         */
        public Builder clearIv() {
            this.iv = null;
            return this;
        }

        /**
         * Set the initialization vector for raw-key operations.
         * Uses a 128-bit tag for AES/GCM and a counter of 1 for ChaCha20, matching the static IV-based shortcuts.
         * Cannot be combined with {@link #parameters(AlgorithmParameterSpec)}.
         * <p>
         * Use a fresh initialization vector or nonce for each encryption with the same key when required by the algorithm.
         * To remove the initialization vector, call {@link #clearIv()} instead of passing {@code null}.
         *
         * @param iv initialization vector, copied by this builder
         * @return updated builder instance
         * @throws NullPointerException if initialization vector is {@code null}
         * @since 28.0.0
         */
        public Builder iv(byte[] iv) {
            Objects.requireNonNull(iv);
            this.iv = iv.clone();
            return this;
        }

        /**
         * Clear the explicit algorithm parameters.
         *
         * @return updated builder instance
         * @since 28.0.0
         */
        public Builder clearParameters() {
            this.parameters = null;
            return this;
        }

        /**
         * Set explicit algorithm parameters for raw-key operations, without converting them from an initialization vector.
         * Cannot be combined with {@link #iv(byte[])}.
         * The parameter object is retained; custom mutable implementations must not be modified after configuration.
         * <p>
         * To remove the parameters, call {@link #clearParameters()} instead of passing {@code null}.
         * Raw-key operations can omit both parameters and IV when the algorithm needs neither for encryption or decryption.
         *
         * @param parameters algorithm parameters
         * @return updated builder instance
         * @throws NullPointerException if parameters are {@code null}
         * @since 28.0.0
         */
        public Builder parameters(AlgorithmParameterSpec parameters) {
            Objects.requireNonNull(parameters);
            this.parameters = parameters;
            return this;
        }

        /**
         * Set size of the key (in bits) which should be generated.
         *
         * Default value is 256 bit.
         * Used only for password-based key derivation.
         *
         * @param keySize size of the key
         * @return updated builder instance
         */
        public Builder keySize(int keySize) {
            this.keySize = keySize;
            return this;
        }

        /**
         * Number of iterations which will be used for key derivation from the password.
         *
         * Default value is 600000.
         * Used only for password-based key derivation.
         *
         * @param numberOfIterations number of iterations
         * @return updated builder instance
         */
        public Builder numberOfIterations(int numberOfIterations) {
            this.numberOfIterations = numberOfIterations;
            return this;
        }

        /**
         * Clear the additional authenticated data.
         *
         * @return updated builder instance
         * @since 28.0.0
         */
        public Builder clearAdditionalAuthenticatedData() {
            this.additionalAuthenticatedData = null;
            return this;
        }

        /**
         * Set additional authenticated data for authenticated encryption algorithms.
         * <p>
         * The configured data is authenticated, but not encrypted. The same value must be configured for decryption.
         * This is supported by authenticated encryption algorithms, such as {@link #ALGORITHM_AES_GCM}.
         * <p>
         * To remove the additional authenticated data, call {@link #clearAdditionalAuthenticatedData()}
         * instead of passing {@code null}.
         *
         * @param additionalAuthenticatedData additional authenticated data
         * @return updated builder instance
         * @throws NullPointerException if additional authenticated data is {@code null}
         */
        public Builder additionalAuthenticatedData(byte[] additionalAuthenticatedData) {
            Objects.requireNonNull(additionalAuthenticatedData);
            this.additionalAuthenticatedData = additionalAuthenticatedData.clone();
            return this;
        }

        /**
         * Algorithm used for encryption and decryption.
         *
         * @return algorithm name
         * @since 28.0.0
         */
        public String algorithm() {
            return algorithm;
        }

        /**
         * Explicit provider selection, if configured.
         *
         * @return provider name, or empty for normal JCA provider selection
         * @since 28.0.0
         */
        public Optional<String> provider() {
            return Optional.ofNullable(provider);
        }

        /**
         * Password used for key derivation, if configured.
         *
         * @return a copy of the password, or empty if not configured
         * @since 28.0.0
         */
        public Optional<char[]> password() {
            return Optional.ofNullable(password).map(value -> value.clone());
        }

        /**
         * Raw key used for encryption and decryption, if configured.
         *
         * @return a copy of the raw key, or empty if not configured
         * @since 28.0.0
         */
        public Optional<byte[]> key() {
            return Optional.ofNullable(key).map(value -> value.clone());
        }

        /**
         * Explicit initialization vector for raw-key operations, if configured.
         *
         * @return a copy of the initialization vector, or empty if not configured
         * @since 28.0.0
         */
        public Optional<byte[]> iv() {
            return Optional.ofNullable(iv).map(value -> value.clone());
        }

        /**
         * Explicit algorithm parameters for raw-key operations, if configured.
         *
         * @return algorithm parameters
         * @since 28.0.0
         */
        public Optional<AlgorithmParameterSpec> parameters() {
            return Optional.ofNullable(parameters);
        }

        /**
         * Size of the derived key in bits.
         *
         * @return key size
         * @since 28.0.0
         */
        public int keySize() {
            return keySize;
        }

        /**
         * Number of iterations used for key derivation.
         *
         * @return number of iterations
         * @since 28.0.0
         */
        public int numberOfIterations() {
            return numberOfIterations;
        }

        /**
         * Additional authenticated data, if configured.
         *
         * @return a copy of the additional authenticated data, or empty if not configured
         * @since 28.0.0
         */
        public Optional<byte[]> additionalAuthenticatedData() {
            return Optional.ofNullable(additionalAuthenticatedData).map(value -> value.clone());
        }

        /**
         * Build a cipher after validating its configuration for encryption and decryption.
         * Password-based validation performs one key derivation with the configured iteration count and key size.
         *
         * @return configured cipher
         * @throws CryptoException if the configuration cannot support both operations
         */
        @Override
        public SymmetricCipher build() {
            if (password == null && key == null) {
                throw new CryptoException("Password or key has to be specified.");
            }
            if (password != null && key != null) {
                throw new CryptoException("Password and key cannot both be specified.");
            }
            if (iv != null && parameters != null) {
                throw new CryptoException("Initialization vector and algorithm parameters cannot both be specified.");
            }
            if (password != null && (iv != null || parameters != null)) {
                throw new CryptoException("Explicit initialization parameters require a raw key.");
            }
            return new SymmetricCipher(this);
        }
    }

}
