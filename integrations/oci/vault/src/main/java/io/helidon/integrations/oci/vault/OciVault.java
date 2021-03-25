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

package io.helidon.integrations.oci.vault;

import java.net.URI;
import java.util.function.Consumer;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.connect.OciRestApi;

/**
 * Reactive APIs for OCI Vault.
 */
public interface OciVault {
    /**
     * Version of Secret API supported by this client.
     */
    String SECRET_API_VERSION = "20180608";
    /**
     * Version of Secret Bundle API supported by this client.
     */
    String SECRET_BUNDLE_API_VERSION = "20190301";

    /**
     * Host name prefix.
     */
    String VAULTS_HOST_PREFIX = "vaults";

    /**
     * Host name prefix for secrets retrieval.
     */
    String RETRIEVAL_HOST_PREFIX = "secrets.vaults";

    /**
     * Create a new fluent API builder for OCI metrics.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create OCI metrics using the default {@link io.helidon.integrations.oci.connect.OciRestApi}.
     *
     * @return OCI metrics instance connecting based on {@code DEFAULT} profile
     */
    static OciVault create() {
        return builder().build();
    }

    /**
     * Create OCI metrics based on configuration.
     *
     * @param config configuration on the node of OCI configuration
     * @return OCI metrics instance configured from the configuration
     * @see io.helidon.integrations.oci.vault.OciVault.Builder#config(io.helidon.config.Config)
     */
    static OciVault create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Gets information about the specified secret.
     *
     * @param request get secret request
     * @return future with secret response or exception
     */
    Single<ApiOptionalResponse<Secret>> getSecret(GetSecret.Request request);

    /**
     * Create a new secret.
     *
     * @param request create secret request
     * @return future with create secret response or exception
     */
    Single<CreateSecret.Response> createSecret(CreateSecret.Request request);

    /**
     * Gets information about the specified secret.
     *
     * @param request get secret bundle request
     * @return future with response or error
     */
    Single<ApiOptionalResponse<GetSecretBundle.Response>> getSecretBundle(GetSecretBundle.Request request);

    /**
     * Schedules a secret deletion.
     *
     * @param request delete secret request
     * @return future with response or error
     */
    Single<DeleteSecret.Response> deleteSecret(DeleteSecret.Request request);

    /**
     * Encrypt data.
     *
     * @param request encryption request
     * @return future with encrypted data
     */
    Single<Encrypt.Response> encrypt(Encrypt.Request request);

    /**
     * Decrypt data.
     *
     * @param request decryption request
     * @return future with decrypted data
     */
    Single<Decrypt.Response> decrypt(Decrypt.Request request);

    /**
     * Sign a message.
     *
     * @param request signature request
     * @return signature response
     */
    Single<Sign.Response> sign(Sign.Request request);

    /**
     * Verify a message signature.
     *
     * @param request verification request
     * @return verification response
     */
    Single<Verify.Response> verify(Verify.Request request);

    class Builder implements io.helidon.common.Builder<OciVault> {
        private final OciRestApi.Builder accessBuilder = OciRestApi.builder()
                .hostPrefix(VAULTS_HOST_PREFIX);

        private String secretApiVersion = SECRET_API_VERSION;
        private String secretBundleApiVersion = SECRET_BUNDLE_API_VERSION;
        private String cryptographicEndpoint;

        private Builder() {
        }

        @Override
        public OciVault build() {
            return new OciVaultImpl(this);
        }

        /**
         * Update from configuration. The configuration must be located on the {@code OCI} root configuration
         * node.
         *
         * @param config configuration
         * @return updated metrics builder
         */
        public Builder config(Config config) {
            accessBuilder.config(config);
            config.get("vault.base-uri").as(URI.class).ifPresent(accessBuilder::baseUri);
            config.get("vault.host-format").asString().ifPresent(accessBuilder::hostFormat);
            config.get("vault.host-prefix").asString().ifPresent(accessBuilder::hostPrefix);
            config.get("vault.secret-api-version").asString().ifPresent(this::secretApiVersion);
            config.get("vault.secret-bundle-api-version").asString().ifPresent(this::secretBundleApiVersion);
            config.get("vault.cryptographic-endpoint").asString().ifPresent(this::cryptographicEndpoint);
            return this;
        }

        public Builder secretApiVersion(String apiVersion) {
            this.secretApiVersion = apiVersion;
            return this;
        }

        public Builder secretBundleApiVersion(String apiVersion) {
            this.secretBundleApiVersion = apiVersion;
            return this;
        }

        public Builder cryptographicEndpoint(String address) {
            this.cryptographicEndpoint = address;
            return this;
        }

        /**
         * Update the rest access builder to modify defaults.
         *
         * @param builderConsumer consumer of the builder
         * @return updated metrics builder
         */
        public Builder updateRestApi(Consumer<OciRestApi.Builder> builderConsumer) {
            builderConsumer.accept(accessBuilder);
            return this;
        }

        String secretApiVersion() {
            return secretApiVersion;
        }

        String secretBundleApiVersion() {
            return secretBundleApiVersion;
        }

        OciRestApi restAccess() {
            return accessBuilder.build();
        }

        String cryptographicEndpoint() {
            return cryptographicEndpoint;
        }
    }
}
