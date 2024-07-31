/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.spi.TlsManagerProvider;

@Prototype.Blueprint(decorator = TlsConfigDecorator.class)
@Prototype.Configured
interface TlsConfigBlueprint extends Prototype.Factory<Tls> {
    /**
     * The default protocol is set to {@value}.
     */
    String DEFAULT_PROTOCOL = "TLS";
    /**
     * The default session cache size as defined for unset value in {@link javax.net.ssl.SSLSessionContext#getSessionCacheSize()}.
     */
    int DEFAULT_SESSION_CACHE_SIZE = 20480;
    /**
     * The default session timeout as defined for unset value in {@link javax.net.ssl.SSLSessionContext#getSessionTimeout()}.
     */
    String DEFAULT_SESSION_TIMEOUT = "PT24H";

    @Prototype.FactoryMethod
    static Optional<PrivateKey> createPrivateKey(Keys config) {
        return config.privateKey();
    }

    @Prototype.FactoryMethod
    static List<X509Certificate> createPrivateKeyCertChain(Keys config) {
        return config.certChain();
    }

    @Prototype.FactoryMethod
    static List<X509Certificate> createTrust(Keys config) {
        return config.certs();
    }

    /**
     * Provide a fully configured {@link javax.net.ssl.SSLContext}. If defined, context related configuration
     * is ignored.
     *
     * @return SSL context to use
     */
    Optional<SSLContext> sslContext();

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
     * The Tls manager. If one is not explicitly defined in the config then a default manager will be created.
     *
     * @return the tls manager of the tls instance
     * @see ConfiguredTlsManager
     */
    @Option.Configured
    @Option.Provider(value = TlsManagerProvider.class, discoverServices = false)
    TlsManager manager();

    /**
     * Explicit secure random to use.
     *
     * @return secure random to use
     */
    Optional<SecureRandom> secureRandom();

    /**
     * Configure SSL parameters.
     * This will always have a value, as we compute ssl parameters in a builder interceptor from configured options.
     *
     * @return SSL parameters to use
     */
    Optional<SSLParameters> sslParameters();

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
    Optional<String> trustManagerFactoryProvider();

    /**
     * Configure list of supported application protocols (such as {@code h2}) for application layer protocol negotiation (ALPN).
     *
     * @return application protocols
     */
    @Option.Singular
    List<String> applicationProtocols();

    /**
     * Identification algorithm for SSL endpoints.
     *
     * @return configure endpoint identification algorithm, or set to {@code NONE}
     *         to disable endpoint identification (equivalent to hostname verification).
     *         Defaults to {@value Tls#ENDPOINT_IDENTIFICATION_HTTPS}
     */
    @Option.Configured
    @Option.Default(Tls.ENDPOINT_IDENTIFICATION_HTTPS)
    String endpointIdentificationAlgorithm();

    /**
     * Flag indicating whether Tls is enabled.
     *
     * @return enabled flag
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

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
     * Configure requirement for mutual TLS.
     *
     * @return what type of mutual TLS to use, defaults to {@link TlsClientAuth#NONE}
     */
    @Option.Configured
    @Option.Default("NONE")
    TlsClientAuth clientAuth();

    /**
     * Configure the protocol used to obtain an instance of {@link javax.net.ssl.SSLContext}.
     *
     * @return protocol to use, defaults to {@value DEFAULT_PROTOCOL}
     */
    @Option.Configured
    @Option.Default(DEFAULT_PROTOCOL)
    String protocol();

    /**
     * Use explicit provider to obtain an instance of {@link javax.net.ssl.SSLContext}.
     *
     * @return provider to use, defaults to none (only {@link #protocol()} is used by default)
     */
    @Option.Configured
    Optional<String> provider();

    /**
     * Enabled cipher suites for TLS communication.
     *
     * @return cipher suites to enable, by default (or if list is empty), all available cipher suites
     *         are enabled
     */
    @Option.Configured("cipher-suite")
    @Option.Singular("enabledCipherSuite")
    List<String> enabledCipherSuites();

    /**
     * Enabled protocols for TLS communication.
     * Example of valid values for {@code TLS} protocol: {@code TLSv1.3}, {@code TLSv1.2}
     *
     * @return protocols to enable, by default (or if list is empty), all available protocols are enabled
     */
    @Option.Configured("protocols")
    @Option.Singular
    List<String> enabledProtocols();

    /**
     * SSL session cache size.
     *
     * @return session cache size, defaults to {@value DEFAULT_SESSION_CACHE_SIZE}.
     */
    @Option.Configured
    @Option.DefaultInt(DEFAULT_SESSION_CACHE_SIZE)
    int sessionCacheSize();

    /**
     * SSL session timeout.
     *
     * @return session timeout, defaults to {@value DEFAULT_SESSION_TIMEOUT}.
     */
    @Option.Configured
    @Option.Default(DEFAULT_SESSION_TIMEOUT)
    Duration sessionTimeout();

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
