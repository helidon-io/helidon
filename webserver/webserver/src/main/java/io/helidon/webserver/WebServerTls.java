/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.config.DeprecatedConfig;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.webserver.spi.TlsManagerProvider;

/**
 * A class wrapping transport layer security (TLS) configuration for
 * WebServer sockets.
 */
public final class WebServerTls {
    /**
     * The default protocol is set to {@value}.
     */
    private static final String PROTOCOL = "TLS";

    /**
     * This constant is a context classifier for the x509 client certificate if it is present. Callers may use this
     * constant to lookup the client certificate associated with the current request context.
     */
    public static final String CLIENT_X509_CERTIFICATE = WebServerTls.class.getName() + ".client-x509-certificate";

    private final TlsManager tlsManager;
    private final Set<String> enabledTlsProtocols;
    private final Set<String> cipherSuite;
    private final SSLContext explicitSslContext;
    private final KeyConfig privateKeyConfig;
    private final KeyConfig trustConfig;
    private final boolean trustAll;
    private final int sessionCacheSize;
    private final int sessionTimeoutSeconds;
    private final boolean enabled;
    private final ClientAuthentication clientAuth;

    private WebServerTls(Builder builder) {
        this.tlsManager = builder.tlsManager;
        this.enabledTlsProtocols = Set.copyOf(builder.enabledTlsProtocols);
        this.cipherSuite = builder.cipherSuite;
        this.explicitSslContext = builder.explicitSslContext;
        this.privateKeyConfig = builder.privateKeyConfig;
        this.trustConfig = builder.trustConfig;
        this.trustAll = builder.trustAll;
        this.sessionCacheSize = (int) builder.sessionCacheSize;
        this.sessionTimeoutSeconds = (int) builder.sessionTimeoutSeconds;
        this.enabled = builder.enabled;
        this.clientAuth = builder.clientAuth;
    }

    /**
     * A fluent API builder for {@link WebServerTls}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create TLS configuration from config.
     *
     * @param config located on the node of the tls configuration (usually this is {@code ssl})
     * @return a new TLS configuration
     */
    public static WebServerTls create(Config config) {
        return builder().config(config).build();
    }

    /**
     * The Tls manager. If one is not explicitly defined in the config then a default manager will be created.
     *
     * @return the tls manager of the tls instance
     * @see ConfiguredTlsManager
     */
    public TlsManager manager() {
        return tlsManager;
    }

    /**
     * Trust any certificate provided by the other side of communication.
     * <p>
     * <b>This is a dangerous setting: </b> if set to {@code true}, any certificate will be accepted, throwing away
     * most of the security advantages of TLS. <b>NEVER</b> do this in production.
     *
     * @return whether to trust all certificates, do not use in production
     */
    public boolean trustAll() {
        return trustAll;
    }

    Collection<String> enabledTlsProtocols() {
        return enabledTlsProtocols;
    }

    Optional<SSLContext> explicitSslContext() {
        return Optional.ofNullable(explicitSslContext);
    }

    SSLContext sslContext() {
        if (explicitSslContext != null) {
            return explicitSslContext;
        }

        return manager().sslContext();
    }

    KeyConfig privateKeyConfig() {
        return privateKeyConfig;
    }

    KeyConfig trustConfig() {
        return trustConfig;
    }

    String protocol() {
        return PROTOCOL;
    }

    int sessionCacheSize() {
        return sessionCacheSize;
    }

    int sessionTimeoutSeconds() {
        return sessionTimeoutSeconds;
    }

    ClientAuthentication clientAuth() {
        return clientAuth;
    }

    Set<String> cipherSuite() {
        return cipherSuite;
    }

    /**
     * Whether this TLS config has security enabled (and the socket is going to be
     * protected by one of the TLS protocols), or no (and the socket is going to be plain).
     *
     * @return {@code true} if this configuration represents a TLS configuration, {@code false} for plain configuration
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Fluent API builder for {@link WebServerTls}.
     */
    @Configured
    public static class Builder implements io.helidon.common.Builder<Builder, WebServerTls> {
        private final Set<String> enabledTlsProtocols = new HashSet<>();

        private TlsManager tlsManager;
        private SSLContext explicitSslContext;
        private KeyConfig privateKeyConfig;
        private KeyConfig trustConfig;
        private long sessionCacheSize;
        private long sessionTimeoutSeconds;
        private boolean trustAll;

        private boolean enabled;
        private Boolean explicitEnabled;
        private ClientAuthentication clientAuth;
        private Set<String> cipherSuite = Set.of();

        private Builder() {
            clientAuth = ClientAuthentication.NONE;
        }

        @Override
        public WebServerTls build() {
            boolean enabled;

            if (null == explicitEnabled) {
                enabled = this.enabled;
            } else {
                enabled = explicitEnabled;
            }

            if (tlsManager == null) {
                tlsManager = new ConfiguredTlsManager();
            }

            if (!enabled) {
                this.explicitSslContext = null;
                // ssl is disabled
                return new WebServerTls(this);
            }

            WebServerTls tls = new WebServerTls(this);
            tlsManager.init(tls);
            return tls;
        }

        /**
         * Update this builder from configuration.
         *
         * @param config config on the node of SSL configuration
         * @return this builder
         */
        public Builder config(Config config) {
            config.get("enabled").asBoolean().ifPresent(this::enabled);

            if (explicitEnabled != null && !explicitEnabled) {
                return this;
            }

            config.get("client-auth").asString().ifPresent(this::clientAuth);
            config.get("private-key")
                    .ifExists(it -> privateKey(KeyConfig.create(it)));

            config.get("trust")
                    .ifExists(it -> trust(KeyConfig.create(it)));

            config.get("protocols").asList(String.class).ifPresent(this::enabledProtocols);
            config.get("session-cache-size").asLong().ifPresent(this::sessionCacheSize);
            config.get("cipher-suite").asList(String.class).ifPresent(this::allowedCipherSuite);
            DeprecatedConfig.get(config, "session-timeout-seconds", "session-timeout")
                    .asLong()
                    .ifPresent(this::sessionTimeoutSeconds);

            config.get("manager")
                    .ifExists(it -> tlsManager(TlsManagerProvider.create(it)));
            config.get("trust-all")
                    .asBoolean()
                    .ifPresent(this::trustAll);

            return this;
        }

        /**
         * The Tls manager. If one is not explicitly defined in the config then a default manager will be created.
         *
         * @param tlsManager the Tls manager
         * @return the tls manager of the tls instance
         * @see ConfiguredTlsManager
         * @see TlsManagerProvider
         */
        @ConfiguredOption(provider = true)
        public Builder tlsManager(TlsManager tlsManager) {
            this.enabled = true;
            this.tlsManager = Objects.requireNonNull(tlsManager);
            return this;
        }

        /**
         * Trust any certificate provided by the other side of communication.
         * <p>
         * <b>This is a dangerous setting: </b> if set to {@code true}, any certificate will be accepted, throwing away
         * most of the security advantages of TLS. <b>NEVER</b> do this in production.
         *
         * @param trustAll flag indicating whether to trust all certificates
         * @return whether to trust all certificates, do not use in production
         */
        @ConfiguredOption("false")
        public Builder trustAll(boolean trustAll) {
            this.trustAll = trustAll;
            return this;
        }

        private void clientAuth(String it) {
            clientAuth(ClientAuthentication.valueOf(it.toUpperCase()));
        }

        /**
         * Configures whether client authentication will be required or not.
         *
         * @param clientAuth client authentication
         * @return this builder
         */
        @ConfiguredOption("none")
        public Builder clientAuth(ClientAuthentication clientAuth) {
            this.clientAuth = Objects.requireNonNull(clientAuth);
            return this;
        }

        /**
         * Explicitly configures a {@link SSLContext} to use with the server socket. If not {@code null} then
         * the server enforces an SSL communication, and will override the usage of any {@link TlsManager}.
         *
         * @param context a SSL context to use
         * @return this builder
         */
        public Builder sslContext(SSLContext context) {
            this.enabled = true;
            this.explicitSslContext = context;
            return this;
        }

        /**
         * Configures the TLS protocols to enable with the server socket.
         * @param protocols protocols to enable, if empty, enables defaults
         *
         * @return this builder
         * @throws java.lang.NullPointerException in case the protocols is null
         */
        public Builder enabledProtocols(String... protocols) {
            return enabledProtocols(Arrays.asList(Objects.requireNonNull(protocols)));
        }

        /**
         * Configures the TLS protocols to enable with the server socket.
         *
         * @param protocols protocols to enable, if empty enables
         *  the default protocols
         * @return this builder
         * @throws java.lang.NullPointerException in case the protocols is null
         */
        public Builder enabledProtocols(Collection<String> protocols) {
            Objects.requireNonNull(protocols);

            this.enabledTlsProtocols.clear();
            this.enabledTlsProtocols.addAll(protocols);
            return this;
        }

        /**
         * Configure private key to use for SSL context.
         *
         * @param privateKeyConfig the required private key configuration parameter
         * @return this builder
         */
        @ConfiguredOption(required = true)
        public Builder privateKey(KeyConfig privateKeyConfig) {
            // setting private key, need to reset ssl context
            this.enabled = true;
            this.explicitSslContext = null;
            this.privateKeyConfig = Objects.requireNonNull(privateKeyConfig);
            return this;
        }

        /**
         * Configure private key to use for SSL context.
         *
         * @param privateKeyConfigBuilder the required private key configuration parameter
         * @return this builder
         */
        public Builder privateKey(Supplier<KeyConfig> privateKeyConfigBuilder) {
            return privateKey(privateKeyConfigBuilder.get());
        }

        /**
         * Set the trust key configuration to be used to validate certificates.
         *
         * @param trustConfig the trust configuration
         * @return this builder
         */
        @ConfiguredOption
        public Builder trust(KeyConfig trustConfig) {
            // setting explicit trust, need to reset ssl context
            this.enabled = true;
            this.explicitSslContext = null;
            this.trustConfig = Objects.requireNonNull(trustConfig);
            return this;
        }

        /**
         * Set the trust key configuration to be used to validate certificates.
         *
         * @param trustConfigBuilder the trust configuration builder
         * @return this builder
         */
        public Builder trust(Supplier<KeyConfig> trustConfigBuilder) {
            return trust(trustConfigBuilder.get());
        }

        /**
         * Set the size of the cache used for storing SSL session objects. {@code 0} to use the
         * default value.
         *
         * @param sessionCacheSize the session cache size
         * @return this builder
         */
        @ConfiguredOption
        public Builder sessionCacheSize(long sessionCacheSize) {
            this.sessionCacheSize = sessionCacheSize;
            return this;
        }

        /**
         * Set the timeout for the cached SSL session objects, in seconds. {@code 0} to use the
         * default value.
         *
         * @param sessionTimeout the session timeout
         * @return this builder
         */
        @ConfiguredOption
        public Builder sessionTimeoutSeconds(long sessionTimeout) {
            this.sessionTimeoutSeconds = sessionTimeout;
            return this;
        }

        /**
         * Set the timeout for the cached SSL session objects. {@code 0} to use the
         * default value.
         *
         * @param timeout the session timeout amount
         * @param unit the session timeout time unit
         * @return this builder
         */
        public Builder sessionTimeout(long timeout, TimeUnit unit) {
            this.sessionTimeoutSeconds = unit.toSeconds(timeout);
            return this;
        }

        /**
         * Set allowed cipher suite. If an empty collection is set, an exception is thrown since
         * it is required to support at least some ciphers.
         *
         * @param cipherSuite allowed cipher suite
         * @return an updated builder
         */
        @ConfiguredOption(key = "cipher-suite")
        public Builder allowedCipherSuite(List<String> cipherSuite) {
            Objects.requireNonNull(cipherSuite);
            if (cipherSuite.isEmpty()) {
                throw new IllegalStateException("Allowed cipher suite has to have at least one cipher specified");
            }
            this.cipherSuite = Set.copyOf(cipherSuite);
            return this;
        }

        /**
         * Whether the TLS config should be enabled or not.
         *
         * @param enabled configure to {@code false} to disable SSL context (and SSL support on the server)
         * @return this builder
         */
        @ConfiguredOption(description = "Can be used to disable TLS even if keys are configured.", value = "true")
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            this.explicitEnabled = enabled;
            return this;
        }
    }

}
