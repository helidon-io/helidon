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

package io.helidon.grpc.server;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.helidon.config.Config;

/**
 * SSL configuration details.
 */
public class SslConfiguration {
    private boolean jdkSSL;
    private String tlsCert;
    private String tlsKey;
    private String tlsCaCert;

    /**
     * Create a new instance.
     *
     * @param jdkSSL    flag specifying whether to use JDK SSL implementation
     * @param tlsCert   the TLS certificate file
     * @param tlsKey    the TLS key file
     * @param tlsCaCert the TLS CA file
     */
    private SslConfiguration(boolean jdkSSL, String tlsCert, String tlsKey, String tlsCaCert) {
        this.jdkSSL = jdkSSL;
        this.tlsCert = tlsCert;
        this.tlsKey = tlsKey;
        this.tlsCaCert = tlsCaCert;
    }

    /**
     * Return true if JDK SSL implementation should be used.
     *
     * @return {@code true} if JDK SSL implementation should be used;
     *         {@code false} otherwise
     */
    public boolean isJdkSSL() {
        return jdkSSL;
    }

    /**
     * Return TLS certs file.
     *
     * @return the TLS certs file
     */
    public String getTLSCerts() {
        return tlsCert;
    }

    /**
     * Return the TLS key file.
     *
     * @return the location of the TLS key file to use
     */
    public String getTLSKey() {
        return tlsKey;
    }

    /**
     * Return the TLS CA certs file.
     *
     * @return the TLS CA certs file
     */
    public String getTLSClientCerts() {
        return tlsCaCert;
    }

    /**
     * Return an instance of builder.
     *
     * @return an instance of builder
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
    public static SslConfiguration create(Config config) {
        return builder(config).build();
    }

    /**
     * Builds the configuration for ssl.
     */
    static class Builder implements io.helidon.common.Builder<SslConfiguration> {
        private boolean jdkSSL;
        private String tlsCert = null;
        private String tlsKey = null;
        private String tlsCaCert = null;

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
        }

        /**
         * Sets the type of SSL implementation to be used.
         */
        public Builder jdkSSL(boolean jdkSSL) {
            this.jdkSSL = jdkSSL;
            return this;
        }

        /**
         * Sets the tls certificate file.
         */
        public Builder tlsCert(String tlsCert) {
            this.tlsCert = tlsCert;
            return this;
        }

        /**
         * Sets the tls key file.
         */
        public Builder tlsKey(String tlsKey) {
            this.tlsKey = tlsKey;
            return this;
        }

        /**
         * Sets the tls CA file.
         */
        public Builder tlsCaCert(String tlsCaCert) {
            this.tlsCaCert = tlsCaCert;
            return this;
        }

        @Override
        public SslConfiguration build() {
            return new SslConfiguration(jdkSSL, tlsCert, tlsKey, tlsCaCert);
        }
    }
}


