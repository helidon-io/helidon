/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import io.helidon.builder.api.Prototype;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.spi.TlsManagerProvider;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

@Prototype.Blueprint(decorator = TlsConfigDecorator.class)
@Configured
interface TlsConfigBlueprint extends Prototype.Factory<Tls> {
    String DEFAULT_PROTOCOL = "TLS";

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
    @ConfiguredOption
    Optional<PrivateKey> privateKey();

    /**
     * Certificate chain of the private key.
     *
     * @return private key certificate chain, only used when private key is configured
     */
    @Prototype.Singular
    @ConfiguredOption(key = "private-key")
    // same config node as privateKey
    List<X509Certificate> privateKeyCertChain();

    /**
     * List of certificates that form the trust manager.
     *
     * @return certificates to be trusted
     */
    @Prototype.Singular
    @ConfiguredOption
    List<X509Certificate> trust();

    /**
     * List of managers (enforced to be at most 1) to manage the lifecycle of the certificate and {@link Tls} creation.
     *
     * @return managers of the tls
     */
    // must be 0..1 (file an issue to allow this to not be a list)
    @ConfiguredOption(provider = true, providerType = TlsManagerProvider.class, providerDiscoverServices = false)
    List<TlsManager> managers();

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
    @ConfiguredOption
    Optional<String> secureRandomProvider();

    /**
     * Algorithm to use when creating a new secure random.
     *
     * @return algorithm to use, by default uses {@link java.security.SecureRandom} constructor
     */
    @ConfiguredOption
    Optional<String> secureRandomAlgorithm();

    /**
     * Algorithm of the key manager factory used when private key is defined.
     * Defaults to {@link javax.net.ssl.KeyManagerFactory#getDefaultAlgorithm()}.
     *
     * @return algorithm to use
     */
    @ConfiguredOption
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
    @ConfiguredOption
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
    @Prototype.Singular
    List<String> applicationProtocols();

    /**
     * Identification algorithm for SSL endpoints.
     *
     * @return configure endpoint identification algorithm, or set to {@code NONE}
     *         to disable endpoint identification (equivalent to hostname verification).
     *         Defaults to {@value Tls#ENDPOINT_IDENTIFICATION_HTTPS}
     */
    @ConfiguredOption(Tls.ENDPOINT_IDENTIFICATION_HTTPS)
    String endpointIdentificationAlgorithm();

    @ConfiguredOption("true")
    boolean enabled();

    /**
     * Trust any certificate provided by the other side of communication.
     * <p>
     * <b>This is a dangerous setting: </b> if set to {@code true}, any certificate will be accepted, throwing away
     * most of the security advantages of TLS. <b>NEVER</b> do this in production.
     *
     * @return whether to trust all certificates, do not use in production
     */
    @ConfiguredOption("false")
    boolean trustAll();

    /**
     * Configure requirement for mutual TLS.
     *
     * @return what type of mutual TLS to use, defaults to {@link TlsClientAuth#NONE}
     */
    @ConfiguredOption(value = "NONE")
    TlsClientAuth clientAuth();

    /**
     * Configure the protocol used to obtain an instance of {@link javax.net.ssl.SSLContext}.
     *
     * @return protocol to use, defaults to {@value DEFAULT_PROTOCOL}
     */
    @ConfiguredOption(DEFAULT_PROTOCOL)
    String protocol();

    /**
     * Use explicit provider to obtain an instance of {@link javax.net.ssl.SSLContext}.
     *
     * @return provider to use, defaults to none (only {@link #protocol()} is used by default)
     */
    @ConfiguredOption
    Optional<String> provider();

    /**
     * Enabled cipher suites for TLS communication.
     *
     * @return cipher suits to enable, by default (or if list is empty), all available cipher suites
     *         are enabled
     */
    @ConfiguredOption(key = "cipher-suite")
    @Prototype.Singular("enabledCipherSuite")
    List<String> enabledCipherSuites();

    /**
     * Enabled protocols for TLS communication.
     * Example of valid values for {@code TLS} protocol: {@code TLSv1.3}, {@code TLSv1.2}
     *
     * @return protocols to enable, by default (or if list is empty), all available protocols are enabled
     */
    @ConfiguredOption(key = "protocols")
    @Prototype.Singular
    List<String> enabledProtocols();

    /**
     * SSL session cache size.
     *
     * @return session cache size, defaults to 1024
     */
    @ConfiguredOption("1024")
    int sessionCacheSize();

    /**
     * SSL session timeout.
     *
     * @return session timeout, defaults to 30 minutes
     */
    @ConfiguredOption("PT30M")
    Duration sessionTimeout();

    /**
     * Type of the key stores used internally to create a key and trust manager factories.
     *
     * @return keystore type, defaults to {@link java.security.KeyStore#getDefaultType()}
     */
    @ConfiguredOption
    Optional<String> internalKeystoreType();

    /**
     * Provider of the key stores used internally to create a key and trust manager factories.
     *
     * @return keystore provider, if not defined, provider is not specified
     */
    @ConfiguredOption
    Optional<String> internalKeystoreProvider();

    // TODO this should be done in the factory method instead of a builder interceptor maybe?

    /**
     * Information generated by interceptor of this type.
     *
     * @return internal information
     */
    @ConfiguredOption(builderMethod = false, configured = false)
    TlsInternalInfo internalInfo();
}
