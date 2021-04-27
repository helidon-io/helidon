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

package io.helidon.common.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Util class used for encryption key derivation from the password.
 */
public class PasswordKeyDerivation {

    private PasswordKeyDerivation() {
        throw new IllegalStateException("This class cannot be instantiated");
    }

    /**
     * Derive key from the password with the usage of the salt.
     * <p>
     * Uses algorithm PBKDF2WithHmacSHA256 for password derivation by default.
     *
     * @param password   base for key derivation
     * @param salt       salt for key derivation
     * @param keySize    output key size in bits
     * @param iterations number of iterations used in derivation
     * @return derived key from the password
     */
    public static byte[] deriveKey(char[] password, byte[] salt, int iterations, int keySize) {
        return deriveKey("PBKDF2WithHmacSHA256", null, password, salt, iterations, keySize);
    }

    /**
     * Derive key from the password with the usage of the salt and selected algorithm.
     *
     * @param algorithm  algorithm to be used for derivation
     * @param provider   provider of the algorithm
     * @param password   base for key derivation
     * @param salt       salt for key derivation
     * @param keySize    output key size in bits
     * @param iterations number of iterations used in derivation
     * @return derived key from the password
     */
    public static byte[] deriveKey(String algorithm, String provider, char[] password, byte[] salt, int iterations, int keySize) {
        try {
            SecretKeyFactory secretKeyFactory;
            if (provider == null) {
                secretKeyFactory = SecretKeyFactory.getInstance(algorithm);
            } else {
                secretKeyFactory = SecretKeyFactory.getInstance(algorithm, provider);
            }
            KeySpec keySpec = new PBEKeySpec(password, salt, iterations, keySize);
            return secretKeyFactory.generateSecret(keySpec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException e) {
            throw new CryptoException("Failed to derive the key from the password", e);
        }
    }

}
