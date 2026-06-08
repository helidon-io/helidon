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

package io.helidon.common.tls;

import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * TLS material used to set up or reload key and trust manager state.
 */
@Prototype.Blueprint
@Prototype.Configured
@Prototype.CustomMethods(TlsConfigSupport.CustomMethods.class)
interface TlsMaterialBlueprint {

    /**
     * Private key to use. For server side TLS, this is required.
     * For client side TLS, this is optional (used when mutual TLS is enabled).
     *
     * @return private key to use
     */
    @Option.Configured
    Optional<PrivateKey> privateKey();

    /**
     * Certificate chain of the private key.
     *
     * @return private key certificate chain, only used when private key is configured
     */
    @Option.Singular
    @Option.Configured("private-key")
    // same config node as privateKey
    List<X509Certificate> privateKeyCertChain();

    /**
     * List of certificates that form the trust manager.
     *
     * @return certificates to be trusted
     */
    @Option.Singular
    @Option.Configured
    List<X509Certificate> trust();

    /**
     * Explicit secure random to use.
     *
     * @return secure random to use
     */
    Optional<SecureRandom> secureRandom();

    /**
     * Provider to use when creating a new secure random.
     * When defined, {@link #secureRandomAlgorithm()} must be defined as well.
     *
     * @return provider to use, by default no provider is specified
     */
    @Option.Configured
    Optional<String> secureRandomProvider();

    /**
     * Algorithm to use when creating a new secure random.
     *
     * @return algorithm to use, by default uses {@link java.security.SecureRandom} constructor
     */
    @Option.Configured
    Optional<String> secureRandomAlgorithm();

    /**
     * Algorithm of the key manager factory used when private key is defined.
     * Defaults to {@link javax.net.ssl.KeyManagerFactory#getDefaultAlgorithm()}.
     *
     * @return algorithm to use
     */
    @Option.Configured
    Optional<String> keyManagerFactoryAlgorithm();

    /**
     * Key manager factory provider.
     *
     * @return provider to use
     */
    @Option.Configured
    Optional<String> keyManagerFactoryProvider();

    /**
     * Trust manager factory algorithm.
     *
     * @return algorithm to use
     */
    @Option.Configured
    Optional<String> trustManagerFactoryAlgorithm();

    /**
     * Trust manager factory provider to use.
     *
     * @return provider to use
     */
    @Option.Configured
    Optional<String> trustManagerFactoryProvider();

    /**
     * Trust any certificate provided by the other side of communication.
     * <p>
     * <b>This is a dangerous setting: </b> if set to {@code true}, any certificate will be accepted, throwing away
     * most of the security advantages of TLS. <b>NEVER</b> do this in production.
     *
     * @return whether to trust all certificates, do not use in production
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean trustAll();

    /**
     * Type of the key stores used internally to create a key and trust manager factories.
     *
     * @return keystore type, defaults to {@link java.security.KeyStore#getDefaultType()}
     */
    @Option.Configured
    Optional<String> internalKeystoreType();

    /**
     * Provider of the key stores used internally to create a key and trust manager factories.
     *
     * @return keystore provider, if not defined, provider is not specified
     */
    @Option.Configured
    Optional<String> internalKeystoreProvider();

    /**
     * Certificate revocation check configuration.
     *
     * @return certificate revocation configuration
     */
    @Option.Configured
    Optional<RevocationConfig> revocation();
}
