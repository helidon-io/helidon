/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;

import io.helidon.common.LazyValue;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.config.DeprecatedConfig;

/**
 * A class wrapping transport layer security (TLS) configuration for
 * WebServer sockets.
 */
public final class WebServerTls {
    private static final String PROTOCOL = "TLS";
    // secure random cannot be stored in native image, it must
    // be initialized at runtime
    private static final LazyValue<Random> RANDOM = LazyValue.create(SecureRandom::new);

    private final Set<String> enabledTlsProtocols;
    private final Set<String> cipherSuite;
    private final SSLContext sslContext;
    private final boolean enabled;
    private final ClientAuthentication clientAuth;

    private WebServerTls(Builder builder) {
        this.enabledTlsProtocols = Set.copyOf(builder.enabledTlsProtocols);
        this.cipherSuite = builder.cipherSuite;
        this.sslContext = builder.sslContext;
        this.enabled = (null != sslContext);
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

    Collection<String> enabledTlsProtocols() {
        return enabledTlsProtocols;
    }

    SSLContext sslContext() {
        return sslContext;
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
    public static class Builder implements io.helidon.common.Builder<WebServerTls> {
        private final Set<String> enabledTlsProtocols = new HashSet<>();

        private SSLContext sslContext;
        private KeyConfig privateKeyConfig;
        private KeyConfig trustConfig;
        private long sessionCacheSize;
        private long sessionTimeoutSeconds;

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

            if (!enabled) {
                this.sslContext = null;
                // ssl is disabled
                return new WebServerTls(this);
            }

            if (null == sslContext) {
                // no explicit ssl context, build it using private key and trust store
                sslContext = newSSLContext();
            }

            return new WebServerTls(this);
        }

        /**
         * Update this builder from configuration.
         *
         * @param config config on the node of SSL configuration
         * @return this builder
         */
        public Builder config(Config config) {
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
        public Builder clientAuth(ClientAuthentication clientAuth) {
            this.clientAuth = Objects.requireNonNull(clientAuth);
            return this;
        }

        /**
         * Configures a {@link SSLContext} to use with the server socket. If not {@code null} then
         * the server enforces an SSL communication.
         *
         * @param context a SSL context to use
         * @return this builder
         */
        public Builder sslContext(SSLContext context) {
            this.enabled = true;
            this.sslContext = context;
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
        public Builder privateKey(KeyConfig privateKeyConfig) {
            // setting private key, need to reset ssl context
            this.enabled = true;
            this.sslContext = null;
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
        public Builder trust(KeyConfig trustConfig) {
            // setting explicit trust, need to reset ssl context
            this.enabled = true;
            this.sslContext = null;
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
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            this.explicitEnabled = enabled;
            return this;
        }

        private SSLContext newSSLContext() {
            try {
                if (null == privateKeyConfig) {
                    throw new IllegalStateException("Private key must be configured when SSL is enabled.");
                }
                KeyManagerFactory kmf = buildKmf(this.privateKeyConfig);
                TrustManagerFactory tmf = buildTmf(this.trustConfig);

                // Initialize the SSLContext to work with our key managers.
                SSLContext ctx = SSLContext.getInstance(PROTOCOL);
                ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

                SSLSessionContext sessCtx = ctx.getServerSessionContext();
                if (sessionCacheSize > 0) {
                    sessCtx.setSessionCacheSize((int) Math.min(sessionCacheSize, Integer.MAX_VALUE));
                }
                if (this.sessionTimeoutSeconds > 0) {
                    sessCtx.setSessionTimeout((int) Math.min(sessionTimeoutSeconds, Integer.MAX_VALUE));
                }
                return ctx;
            } catch (IOException | GeneralSecurityException e) {
                throw new IllegalStateException("Failed to build server SSL Context!", e);
            }
        }

        private static KeyManagerFactory buildKmf(KeyConfig privateKeyConfig) throws IOException, GeneralSecurityException {
            String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
            if (algorithm == null) {
                algorithm = "SunX509";
            }

            byte[] passwordBytes = new byte[64];
            RANDOM.get().nextBytes(passwordBytes);
            char[] password = Base64.getEncoder().encodeToString(passwordBytes).toCharArray();

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setKeyEntry("key",
                           privateKeyConfig.privateKey().orElseThrow(() -> new RuntimeException("Private key not available")),
                           password,
                           privateKeyConfig.certChain().toArray(new Certificate[0]));

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
            kmf.init(ks, password);

            return kmf;
        }

        private static TrustManagerFactory buildTmf(KeyConfig trustConfig)
                throws IOException, GeneralSecurityException {
            List<X509Certificate> certs;

            if (trustConfig == null) {
                certs = List.of();
            } else {
                certs = trustConfig.certs();
            }

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);

            int i = 1;
            for (X509Certificate cert : certs) {
                ks.setCertificateEntry(String.valueOf(i), cert);
                i++;
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            return tmf;
        }
    }

}
