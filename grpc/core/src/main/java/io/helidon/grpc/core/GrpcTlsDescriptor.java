/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.grpc.core;

import io.helidon.common.configurable.Resource;
import io.helidon.config.Config;
import io.helidon.config.objectmapping.Value;

/**
 * GrpcTlsDescriptor contains details about configuring TLS of a {@link io.grpc.Channel}.
 */
public class GrpcTlsDescriptor {
    private final boolean enabled;
    private final boolean jdkSSL;
    private final Resource tlsCert;
    private final Resource tlsKey;
    private final Resource tlsCaCert;

    private GrpcTlsDescriptor(boolean enabled, boolean jdkSSL, Resource tlsCert, Resource tlsKey, Resource tlsCaCert) {
        this.enabled = enabled;
        this.jdkSSL = jdkSSL;
        this.tlsCert = tlsCert;
        this.tlsKey = tlsKey;
        this.tlsCaCert = tlsCaCert;
    }

    /**
     * Return a new instance of {@link GrpcTlsDescriptor.Builder}.
     * @return a new instance of {@link GrpcTlsDescriptor.Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return an instance of builder based on the specified external config.
     *
     * @param config external config
     * @return an instance of builder
     */
    public static Builder builder(Config config) {
        return new Builder(config);
    }

    /**
     * Create an instance of a TLS configuration from external configuration source.
     *
     * @param config external config
     * @return an instance of a TLS configuration
     */
    public static GrpcTlsDescriptor create(Config config) {
        return builder(config).build();
    }

    /**
     * Check if TLS is enabled. If this is false, then none of the other configuration values are used.
     * @return true if TLS is enabled; false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if JDK SSL has be used. Only used for TLS enabled server channels.A Ignored by client channel.
     * @return true if JDK ssl has to be used; false otherwise
     */
    public boolean isJdkSSL() {
        return jdkSSL;
    }

    /**
     * Get the tlsCert path. Can be either client or server cert.
     * @return the path to tls certificate
     */
    public Resource tlsCert() {
        return tlsCert;
    }

    /**
     * Get the client private key path. Can be either client or server private key.
     * @return the path to tls private key
     */
    public Resource tlsKey() {
        return tlsKey;
    }

    /**
     * Get the CA (certificate authority) certificate path.
     * @return the path to CA certificate
     */
    public Resource tlsCaCert() {
        return tlsCaCert;
    }

    /**
     * Builder to build a new instance of {@link GrpcTlsDescriptor}.
     */
    public static class Builder implements io.helidon.common.Builder<GrpcTlsDescriptor> {

        private boolean enabled = true;
        private boolean jdkSSL;
        private Resource tlsCert;
        private Resource tlsKey;
        private Resource tlsCaCert;

        private Builder() {

        }

        private Builder(Config config) {
            if (config == null) {
                return;
            }

            // backward compatible
            Resource.create(config, "tls-cert").ifPresent(this::tlsCert);
            config.get("tls-cert.resource").as(Resource::create).ifPresent(this::tlsCert);

            // backward compatible
            Resource.create(config, "tls-key").ifPresent(this::tlsKey);
            config.get("tls-key.resource").as(Resource::create).ifPresent(this::tlsKey);

            // backward compatible
            Resource.create(config, "tls-ca-cert").ifPresent(this::tlsCaCert);
            config.get("tls-ca-cert.resource").as(Resource::create).ifPresent(this::tlsCaCert);

            this.jdkSSL = config.get("jdk-ssl").asBoolean().orElse(false);
            this.enabled = config.get("enabled").asBoolean().orElse(true);
        }

        /**
         * Enable or disable TLS. If enabled is false then the rest of the TLS configuration properties are ignored.
         * @param enabled true to enable, false otherwise
         * @return this instance for fluent API
         */
        @Value(withDefault = "true")
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the type of SSL implementation to be used.
         * @param jdkSSL true to use JDK based SSL, false otherwise
         * @return this instance for fluent API
         */
        @Value(key = "jdk-ssl")
        public Builder jdkSSL(boolean jdkSSL) {
            this.jdkSSL = jdkSSL;
            return this;
        }

        /**
         * Set the client tlsCert path. Required only if mutual auth is desired.
         * @param tlsCert the path to client's certificate
         * @return this instance for fluent API
         */
        @Value(key = "tls-cert")
        public Builder tlsCert(Resource tlsCert) {
            this.tlsCert = tlsCert;
            return this;
        }

        /**
         * Set the client private key path. Required only if mutual auth is desired.
         * @param tlsKey the 's TLS private key
         * @return this instance for fluent API
         */
        @Value(key = "tls-key")
        public Builder tlsKey(Resource tlsKey) {
            this.tlsKey = tlsKey;
            return this;
        }

        /**
         * Set the CA (certificate authority) certificate path.
         * @param caCert the path to CA certificate
         * @return this instance for fluent API
         */
        @Value(key = "tls-ca-cert")
        public Builder tlsCaCert(Resource caCert) {
            this.tlsCaCert = caCert;
            return this;
        }

        /**
         * Create and return a new instance of {@link GrpcTlsDescriptor}.
         * @return a new instance of {@link GrpcTlsDescriptor}
         */
        public GrpcTlsDescriptor build() {
            return new GrpcTlsDescriptor(enabled, jdkSSL, tlsCert, tlsKey, tlsCaCert);
        }
    }
}
