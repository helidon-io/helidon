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

/**
 * Common cryptography implementations.
 * <br>
 * This module is used to create digest (such as hash, hmac or signature) and perform cryptographic operations
 * to encrypt and decrypt data with usage of AES, ChaCha20 or RSA.
 * <br>
 * All of the supported algorithms by default, are available at the corresponding class as a constant.
 * It is not required to use only algorithms available by default. All of the algorithms and their providers are
 * configurable over the builder of each class.
 * <br>
 * Digests:
 * <pre>
 *     <code>HashDigest</code> - class which creates hash digest of the message
 *     <code>HmacDigest</code> - class which creates message authentication code with addition of secret key
 *     <code>Signature</code> - class which creates RSA/EC signature
 * </pre>
 * Encryption/Decryption:
 * <pre>
 *     <code>SymmetricCipher</code> - class which encrypts and decrypts provided message by symmetric cipher (AES, ChaCha20 etc.)
 *     <code>AsymmetricCipher</code> - class which encrypts and decrypts provided message by asymmetric cipher (RSA)
 * </pre>
 */
package io.helidon.common.crypto;
