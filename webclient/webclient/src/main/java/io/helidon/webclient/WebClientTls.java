/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLContext;

import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;

/**
 * Configuration of TLS requests.
 */
public class WebClientTls {

    private final boolean trustAll;
    private final boolean disableHostnameVerification;
    private final PrivateKey clientPrivateKey;
    private final List<X509Certificate> certificates;
    private final List<X509Certificate> clientCertificateChain;
    private final SSLContext sslContext;

    private WebClientTls(Builder builder) {
        this.trustAll = builder.trustAll;
        this.disableHostnameVerification = builder.disableHostnameVerification;
        this.certificates = builder.certificates;
        this.clientPrivateKey = builder.clientPrivateKey;
        this.clientCertificateChain = builder.clientCertificateChain;
        this.sslContext = builder.sslContext;
    }

    /**
     * Fluent API builder for new instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * If all server certificates should be trusted. Trust store is ignored.
     *
     * Default value is {@code false}.
     *
     * @return trust all certificates
     */
    boolean trustAll() {
        return trustAll;
    }

    /**
     * If server hostname verification should be disabled.
     *
     * Default value is {@code false}.
     *
     * @return disable hostname verification
     */
    boolean disableHostnameVerification() {
        return disableHostnameVerification;
    }

    /**
     * Trusted certificates.
     *
     * @return trusted certificates
     */
    List<X509Certificate> certificates() {
        return certificates;
    }

    /**
     * Client private key for authentication.
     *
     * @return client private key
     */
    Optional<PrivateKey> clientPrivateKey() {
        return Optional.ofNullable(clientPrivateKey);
    }

    /**
     * Client certificate chain.
     *
     * @return client certificate chain
     */
    List<X509Certificate> clientCertificateChain() {
        return clientCertificateChain;
    }

    /**
     * Instance of {@link SSLContext}.
     *
     * @return ssl context
     */
    Optional<SSLContext> sslContext() {
        return Optional.ofNullable(sslContext);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WebClientTls that = (WebClientTls) o;
        return trustAll == that.trustAll &&
                disableHostnameVerification == that.disableHostnameVerification &&
                Objects.equals(clientPrivateKey, that.clientPrivateKey) &&
                Objects.equals(certificates, that.certificates) &&
                Objects.equals(clientCertificateChain, that.clientCertificateChain) &&
                Objects.equals(sslContext, that.sslContext);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(trustAll, disableHostnameVerification, clientPrivateKey, certificates, clientCertificateChain, sslContext);
    }

    /**
     * Fluent API builder for {@link WebClientTls} instance.
     */
    public static final class Builder implements io.helidon.common.Builder<WebClientTls> {

        private boolean trustAll = false;
        private boolean disableHostnameVerification = false;
        private PrivateKey clientPrivateKey;
        private List<X509Certificate> certificates = new ArrayList<>();
        private List<X509Certificate> clientCertificateChain = new ArrayList<>();
        private SSLContext sslContext;

        private Builder() {
        }

        /**
         * Sets if hostname verification should be disabled.
         *
         * @param disableHostnameVerification disabled verification
         * @return updated builder instance
         */
        public Builder disableHostnameVerification(boolean disableHostnameVerification) {
            this.disableHostnameVerification = disableHostnameVerification;
            return this;
        }

        /**
         * Sets if all certificates should be trusted to.
         *
         * @param trustAll trust all certificates
         * @return updated builder instance
         */
        public Builder trustAll(boolean trustAll) {
            this.trustAll = trustAll;
            return this;
        }

        /**
         * Sets new certificate trust store.
         *
         * @param keyStore trust store
         * @return updated builder instance
         */
        public Builder certificateTrustStore(KeyConfig keyStore) {
            Objects.requireNonNull(keyStore);
            certificates = keyStore.certs();
            return this;
        }

        /**
         * Sets new certificate key store.
         *
         * @param keyConfig key store
         * @return updated builder instance
         */
        public Builder clientKeyStore(KeyConfig keyConfig) {
            Objects.requireNonNull(keyConfig);
            keyConfig.privateKey().ifPresent(privateKey -> clientPrivateKey = privateKey);
            clientCertificateChain = keyConfig.certChain();
            return this;
        }

        /**
         * Sets new {@link SSLContext} which will be used as base for {@link io.netty.handler.ssl.SslContext}.
         *
         * @param sslContext ssl context
         * @return updated builder instance
         */
        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * Configure a metric from configuration.
         * The following configuration key are used:
         * <table>
         * <caption>Client Metric configuration options</caption>
         * <tr>
         *     <th>key</th>
         *     <th>default</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>server.disable-hostname-verification</td>
         *     <td>{@code false}</td>
         *     <td>Whether this client should perform hostname verification</td>
         * </tr>
         * <tr>
         *     <td>server.trust-all</td>
         *     <td>{@code false}</td>
         *     <td>Whether this client should trust all certificates</td>
         * </tr>
         * <tr>
         *     <td>server.truststore</td>
         *     <td>{@code no default}</td>
         *     <td>Trust store which contains trusted certificates. If set, replaces those present by default</td>
         * </tr>
         * <tr>
         *     <td>client.keystore</td>
         *     <td>{@code no default}</td>
         *     <td>Client key store name/location</td>
         * </tr>
         * </table>
         *
         * @param config configuration to configure this ssl
         * @return updated builder instance
         */
        public Builder config(Config config) {
            Config serverConfig = config.get("server");
            serverConfig.get("disable-hostname-verification").asBoolean().ifPresent(this::disableHostnameVerification);
            serverConfig.get("trust-all").asBoolean().ifPresent(this::trustAll);
            serverConfig.as(KeyConfig::create).ifPresent(this::certificateTrustStore);

            config.get("client").as(KeyConfig::create).ifPresent(this::clientKeyStore);
            return this;
        }

        @Override
        public WebClientTls build() {
            return new WebClientTls(this);
        }
    }
}
