/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.tls.spi.TlsManagerProvider;

/**
 * TLS configuration, used by web server listeners, web client and other components that need TLS.
 */
@Prototype.Blueprint(decorator = TlsConfigDecorator.class)
@Prototype.Configured
@Prototype.CustomMethods(TlsConfigSupport.CustomMethods.class)
interface TlsConfigBlueprint extends TlsMaterialBlueprint, Prototype.Factory<Tls> {
    /**
     * Provide a fully configured {@link javax.net.ssl.SSLContext}. If defined, context related configuration
     * is ignored, and reload of Tls is not supported, and will throw an exception.
     *
     * @return SSL context to use
     */
    Optional<SSLContext> sslContext();

    /**
     * The Tls manager. If one is not explicitly defined in the config then a default manager will be created.
     * Default is either a configuration based TLS manager, or an explicit manager when {@link #sslContext()} is provided.
     *
     * @return the tls manager of the tls instance
     * @see ConfiguredTlsManager
     */
    @Option.Configured
    @Option.Provider(value = TlsManagerProvider.class, discoverServices = false)
    TlsManager manager();

    /**
     * Configure SSL parameters.
     * This will always have a value, as we compute ssl parameters in a builder interceptor from configured options.
     *
     * @return SSL parameters to use
     */
    Optional<SSLParameters> sslParameters();

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
     * When disabled, all other configuration is ignored.
     *
     * @return enabled flag
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

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
     * @return protocol to use, defaults to {@value TlsConfigSupport.CustomMethods#DEFAULT_PROTOCOL}
     */
    @Option.Configured
    @Option.Default(TlsConfigSupport.CustomMethods.DEFAULT_PROTOCOL)
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
     * @return session cache size, defaults to {@value TlsConfigSupport.CustomMethods#DEFAULT_SESSION_CACHE_SIZE}.
     */
    @Option.Configured
    @Option.DefaultInt(TlsConfigSupport.CustomMethods.DEFAULT_SESSION_CACHE_SIZE)
    int sessionCacheSize();

    /**
     * SSL session timeout.
     *
     * @return session timeout, defaults to {@value TlsConfigSupport.CustomMethods#DEFAULT_SESSION_TIMEOUT}.
     */
    @Option.Configured
    @Option.Default(TlsConfigSupport.CustomMethods.DEFAULT_SESSION_TIMEOUT)
    Duration sessionTimeout();

}
