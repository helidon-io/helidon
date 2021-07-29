/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.atp;

import java.util.function.Consumer;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.connect.OciRestApi;

/**
 * Reactive API for OCI ATP.
 */
public interface OciAutonomousDBRx {
    /**
     * Version of ATP API supported by this client.
     */
    String API_VERSION = "20160918";

    /**
     * Host name prefix.
     */
    String API_HOST_PREFIX = "autonomousDatabases";

    /**
     * Host format of API server.
     */
    String API_HOST_FORMAT = "%s://%s.%s.%s";

    /**
     * Create a new fluent API builder for OCI ATP.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create OCI ATP using the default {@link io.helidon.integrations.oci.connect.OciRestApi}.
     *
     * @return OCI ATP instance connecting based on {@code DEFAULT} profile
     */
    static OciAutonomousDBRx create() {
        return builder().build();
    }

    /**
     * Create OCI ATP based on configuration.
     *
     * @param config configuration on the node of OCI configuration
     * @return OCI ATP instance configured from the configuration
     * @see OciAutonomousDBRx.Builder#config(io.helidon.config.Config)
     */
    static OciAutonomousDBRx create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Gets the metadata and body of Wallet.
     *
     * @param request get object request
     * @return future with response or error
     */
    Single<ApiOptionalResponse<GenerateAutonomousDatabaseWallet.Response>> getWallet(GenerateAutonomousDatabaseWallet.Request request);

    /**
     * Fluent API Builder for {@link io.helidon.integrations.oci.atp.OciAutonomousDBRx}.
     */
    class Builder implements io.helidon.common.Builder<OciAutonomousDBRx> {
        private final OciRestApi.Builder apiBuilder = OciRestApi.builder();

        private String hostPrefix = API_HOST_PREFIX;
        private String endpoint;
        private String ocid;
        private String walletPassword;
        private OciRestApi restApi;

        private Builder() {
        }

        @Override
        public OciAutonomousDBRx build() {
            if (restApi == null) {
                restApi = apiBuilder.build();
            }
            return new OciAutonomousDBRxImpl(this);
        }

        /**
         * Update from configuration. The configuration must be located on the {@code OCI} root configuration
         * node.
         *
         * @param config configuration
         * @return updated builder
         */
        public Builder config(Config config) {
            apiBuilder.config(config);
            config.get("atp.host-prefix").asString().ifPresent(this::hostPrefix);
            config.get("atp.endpoint").asString().ifPresent(this::endpoint);
            config.get("atp.ocid").asString().ifPresent(this::ocid);
            config.get("atp.walletPassword").asString().ifPresent(this::walletPassword);
            return this;
        }

        /**
         * Instance of rest API to use.
         *
         * @param restApi rest API
         * @return updated builder
         */
        public Builder restApi(OciRestApi restApi) {
            this.restApi = restApi;
            return this;
        }

        /**
         * Host prefix to use for object storage,
         * defaults to {@value API_HOST_PREFIX}.
         *
         * @param prefix prefix to use
         * @return updated builder
         */
        public Builder hostPrefix(String prefix) {
            this.hostPrefix = prefix;
            return this;
        }

        /**
         * Explicit endpoint to use.
         *
         * @param endpoint endpoint
         * @return updated builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Explicit ocid of ATP to use.
         *
         * @param ocid endpoint
         * @return updated builder
         */
        public Builder ocid(String ocid) {
            this.ocid = ocid;
            return this;
        }

        /**
         * Set explicit password to encrypt the keys inside the wallet
         *
         * @param walletPassword endpoint
         * @return updated builder
         */
        public Builder walletPassword(String walletPassword) {
            this.walletPassword = walletPassword;
            return this;
        }

        /**
         * Update the rest access builder to modify defaults.
         *
         * @param builderConsumer consumer of the builder
         * @return updated builder
         */
        public Builder updateRestApi(Consumer<OciRestApi.Builder> builderConsumer) {
            builderConsumer.accept(apiBuilder);
            return this;
        }

        OciRestApi restApi() {
            return restApi;
        }

        String hostPrefix() {
            return hostPrefix;
        }

        String endpoint() {
            return endpoint;
        }

        String ocid() {
            return ocid;
        }

        String walletPassword() {
            return walletPassword;
        }

    }
}