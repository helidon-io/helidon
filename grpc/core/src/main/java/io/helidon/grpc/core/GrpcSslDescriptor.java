/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.nio.file.Paths;

import io.helidon.config.Config;
import io.helidon.config.objectmapping.Value;

/**
 * GrpcSslDescriptor contains details about configuring TLS of a {@link io.grpc.Channel}.
 */
public class GrpcSslDescriptor {
    private final boolean enabled;
    private final boolean jdkSSL;
    private final String tlsCert;
    private final String tlsKey;
    private final String tlsCaCert;

    private GrpcSslDescriptor(boolean enabled, boolean jdkSSL, String tlsCert, String tlsKey, String tlsCaCert) {
        this.enabled = enabled;
        this.jdkSSL = jdkSSL;
        this.tlsCert = tlsCert;
        this.tlsKey = tlsKey;
        this.tlsCaCert = tlsCaCert;
    }

    /**
     * Return a new instance of {@link io.helidon.grpc.core.GrpcSslDescriptor.Builder}.
     * @return a new instance of {@link io.helidon.grpc.core.GrpcSslDescriptor.Builder}
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
     * Create an instance of sslConfig from external configuration source.
     *
     * @param config external config
     * @return an instance of sslconfig
     */
    public static GrpcSslDescriptor create(Config config) {
        return builder(config).build();
    }

    /**
     * Check if SSL is enabled. If this is false, then none of the other configuration values are used.
     * @return true if ssl is enabled; false otherwise
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
    public String tlsCert() {
        return tlsCert;
    }

    /**
     * Get the client private key path. Can be either client or server private key.
     * @return the path to tls private key
     */
    public String tlsKey() {
        return tlsKey;
    }

    /**
     * Get the CA (certificate authority) certificate path.
     * @return the path to CA certificate
     */
    public String tlsCaCert() {
        return tlsCaCert;
    }

    /**
     * Builder to build a new instance of {@link io.helidon.grpc.core.GrpcSslDescriptor}.
     */
    public static class Builder implements io.helidon.common.Builder<GrpcSslDescriptor> {

        private boolean enabled = true;
        private boolean jdkSSL;
        private String tlsCert;
        private String tlsKey;
        private String tlsCaCert;

        private Builder() {

        }

        private Builder(Config config) {
            if (config == null) {
                return;
            }

            Path path = Paths.get(config.get("path").asString().orElse(""));

            String tlsCert = config.get("tlsCert").asString().orElse(null);
            if (tlsCert != null) {
                this.tlsCert = path.resolve(tlsCert).toAbsolutePath().toString();
            }

            String tlsKey = config.get("tlsKey").asString().orElse(null);
            if (tlsKey != null) {
                this.tlsKey = path.resolve(tlsKey).toAbsolutePath().toString();
            }

            String tlsCaCert = config.get("tlsCaCert").asString().orElse(null);
            if (tlsCaCert != null) {
                this.tlsCaCert = path.resolve(tlsCaCert).toAbsolutePath().toString();
            }

            this.jdkSSL = config.get("jdkSSL").asBoolean().orElse(false);
            this.enabled = config.get("enabled").asBoolean().orElse(true);
        }

        /**
         * Enable or disable Ssl. If enabled is false then the rest of the SslDescriptor properties are ignored.
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
        @Value()
        public Builder jdkSSL(boolean jdkSSL) {
            this.jdkSSL = jdkSSL;
            return this;
        }

        /**
         * Set the client tlsCert path. Required only if mutual auth is desired.
         * @param tlsCert the path to client's certificate
         * @return this instance for fluent API
         */
        @Value
        public Builder tlsCert(String tlsCert) {
            this.tlsCert = tlsCert;
            return this;
        }

        /**
         * Set the client private key path. Required only if mutual auth is desired.
         * @param tlsKey the 's TLS private key
         * @return this instance for fluent API
         */
        @Value
        public Builder tlsKey(String tlsKey) {
            this.tlsKey = tlsKey;
            return this;
        }

        /**
         * Set the CA (certificate authority) certificate path.
         * @param caCert the path to CA certificate
         * @return this instance for fluent API
         */
        @Value
        public Builder tlsCaCert(String caCert) {
            this.tlsCaCert = caCert;
            return this;
        }

        /**
         * Create and return a new instance of {@link io.helidon.grpc.core.GrpcSslDescriptor}.
         * @return a new instance of {@link io.helidon.grpc.core.GrpcSslDescriptor}
         */
        public GrpcSslDescriptor build() {
            return new GrpcSslDescriptor(enabled, jdkSSL, tlsCert, tlsKey, tlsCaCert);
        }
    }
}
