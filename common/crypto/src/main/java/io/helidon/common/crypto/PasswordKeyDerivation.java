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

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Objects;
import java.util.function.Consumer;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;

/**
 * Password-based key derivation.
 * A configured instance can derive keys for different passwords and salts without retaining those inputs.
 * For example:
 * <pre>{@code
 * PasswordKeyDerivation derivation = PasswordKeyDerivation.builder()
 *         .numberOfIterations(iterations)
 *         .keySize(keySize)
 *         .build();
 * byte[] key = derivation.deriveKey(password, salt);
 * }</pre>
 */
@Api.Stable
public class PasswordKeyDerivation implements RuntimeType.Api<PasswordKeyDerivationConfig> {

    private final PasswordKeyDerivationConfig config;

    private PasswordKeyDerivation(PasswordKeyDerivationConfig config) {
        this.config = config;
        if (config.numberOfIterations() <= 0) {
            throw new CryptoException("Number of iterations must be positive");
        }
        if (config.keySize() <= 0) {
            throw new CryptoException("Key size must be positive");
        }
        try {
            secretKeyFactory(config.algorithm(), config.provider().orElse(null));
        } catch (IllegalArgumentException e) {
            throw new CryptoException("Failed to initialize password key derivation", e);
        }
    }

    /**
     * Create a builder for reusable key derivation settings.
     * The iteration count and key size are required. Algorithm and provider availability are checked when building.
     *
     * @return new builder
     * @since 28.0.0
     */
    public static PasswordKeyDerivationConfig.Builder builder() {
        return PasswordKeyDerivationConfig.builder();
    }

    /**
     * Create a key derivation instance from its settings.
     *
     * @param config key derivation settings
     * @return configured key derivation instance
     * @throws CryptoException if settings are invalid or the algorithm or provider is unavailable
     * @since 28.0.0
     */
    public static PasswordKeyDerivation create(PasswordKeyDerivationConfig config) {
        return new PasswordKeyDerivation(Objects.requireNonNull(config));
    }

    /**
     * Create a key derivation instance by customizing its settings.
     *
     * @param consumer builder customizer
     * @return configured key derivation instance
     * @since 28.0.0
     */
    public static PasswordKeyDerivation create(Consumer<PasswordKeyDerivationConfig.Builder> consumer) {
        Objects.requireNonNull(consumer);
        return builder().update(consumer).build();
    }

    /**
     * Derive key from the password with the usage of the salt.
     * <br>
     * Uses algorithm PBKDF2WithHmacSHA256 for password derivation by default.
     *
     * @param password   base for key derivation
     * @param salt       salt for key derivation
     * @param keySize    output key size in bits
     * @param iterations number of iterations used in derivation
     * @return derived key from the password
     */
    public static byte[] deriveKey(char[] password, byte[] salt, int iterations, int keySize) {
        return deriveKeyInternal("PBKDF2WithHmacSHA256", null, password, salt, iterations, keySize);
    }

    /**
     * Derive a key using the selected algorithm and normal JCA provider selection.
     *
     * @param algorithm algorithm to be used for derivation
     * @param password base for key derivation
     * @param salt salt for key derivation
     * @param iterations number of iterations used in derivation
     * @param keySize output key size in bits
     * @return derived key from the password
     * @throws NullPointerException if algorithm, password or salt is {@code null}
     * @since 28.0.0
     */
    public static byte[] deriveKey(String algorithm, char[] password, byte[] salt, int iterations, int keySize) {
        Objects.requireNonNull(algorithm);
        Objects.requireNonNull(password);
        Objects.requireNonNull(salt);
        return deriveKeyInternal(algorithm, null, password, salt, iterations, keySize);
    }

    /**
     * Derive key from the password with the usage of the salt and selected algorithm.
     *
     * @param algorithm  algorithm to be used for derivation
     * @param provider   provider of the algorithm, or {@code null} for normal JCA provider selection
     * @param password   base for key derivation
     * @param salt       salt for key derivation
     * @param keySize    output key size in bits
     * @param iterations number of iterations used in derivation
     * @return derived key from the password
     * @deprecated use {@link #deriveKey(String, char[], byte[], int, int)} for normal JCA provider selection.
     *             For an explicit provider, use {@link #builder()} with
     *             {@link PasswordKeyDerivationConfig.Builder#provider(String)}, then {@link #deriveKey(char[], byte[])}.
     */
    @Deprecated(since = "28.0.0", forRemoval = true)
    public static byte[] deriveKey(String algorithm, String provider, char[] password, byte[] salt, int iterations, int keySize) {
        return deriveKeyInternal(algorithm, provider, password, salt, iterations, keySize);
    }

    @Override
    public PasswordKeyDerivationConfig prototype() {
        return config;
    }

    /**
     * Derive a key using this instance's settings. A new JCA key factory is used for each invocation.
     * Password and salt are not retained by this instance.
     *
     * @param password base for key derivation
     * @param salt salt for key derivation; use a fresh salt for each independently derived key
     * @return derived key
     * @throws NullPointerException if password or salt is {@code null}
     * @since 28.0.0
     */
    public byte[] deriveKey(char[] password, byte[] salt) {
        Objects.requireNonNull(password);
        Objects.requireNonNull(salt);
        return deriveKeyInternal(config.algorithm(), config.provider().orElse(null), password, salt,
                                 config.numberOfIterations(), config.keySize());
    }

    private static byte[] deriveKeyInternal(String algorithm,
                                          String provider,
                                          char[] password,
                                          byte[] salt,
                                          int iterations,
                                          int keySize) {
        try {
            SecretKeyFactory secretKeyFactory = secretKeyFactory(algorithm, provider);
            KeySpec keySpec = new PBEKeySpec(password, salt, iterations, keySize);
            return secretKeyFactory.generateSecret(keySpec).getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new CryptoException("Failed to derive the key from the password", e);
        }
    }

    private static SecretKeyFactory secretKeyFactory(String algorithm, String provider) {
        try {
            return provider == null ? SecretKeyFactory.getInstance(algorithm) : SecretKeyFactory.getInstance(algorithm, provider);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new CryptoException("Failed to derive the key from the password", e);
        }
    }

}
