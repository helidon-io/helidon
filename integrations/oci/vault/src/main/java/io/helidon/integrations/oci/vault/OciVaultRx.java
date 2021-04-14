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

import java.util.function.Consumer;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.connect.OciRestApi;

/**
 * Reactive APIs for OCI Vault.
 */
public interface OciVaultRx {
    /**
     * Default endpoint format for KMS.
     */
    String ENDPOINT_FORMAT = "%s://%s.%s.%s";

    /**
     * Default endpoint format for secrets.
     */
    String OCI_ENDPOINT_FORMAT = "%s://%s.%s.oci.%s";

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
     * Host name prefix for key management service (KMS).
     */
    String KMS_HOST_PREFIX = "kms";

    /**
     * Host name prefix for secrets retrieval. This is added before the {@link #VAULTS_HOST_PREFIX}.
     */
    String RETRIEVAL_HOST_PREFIX = "secrets";

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
    static OciVaultRx create() {
        return builder().build();
    }

    /**
     * Create OCI metrics based on configuration.
     *
     * @param config configuration on the node of OCI configuration
     * @return OCI metrics instance configured from the configuration
     * @see OciVaultRx.Builder#config(io.helidon.config.Config)
     */
    static OciVaultRx create(Config config) {
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

    /**
     * Get key metadata.
     *
     * @param request get key request
     * @return get key response
     */
    Single<ApiOptionalResponse<GetKey.Response>> getKey(GetKey.Request request);

    /**
     * Get Vault metadata.
     *
     * @param request get vault request
     * @return get vault response
     */
    Single<ApiOptionalResponse<GetVault.Response>> getVault(GetVault.Request request);

    /**
     * Fluent API builder for {@link io.helidon.integrations.oci.vault.OciVaultRx}.
     */
    class Builder implements io.helidon.common.Builder<OciVaultRx> {
        private final OciRestApi.Builder accessBuilder = OciRestApi.builder();

        private String secretApiVersion = SECRET_API_VERSION;
        private String secretBundleApiVersion = SECRET_BUNDLE_API_VERSION;
        private String vaultPrefix = VAULTS_HOST_PREFIX;
        private String retrievalPrefix = RETRIEVAL_HOST_PREFIX;
        private String kmsPrefix = KMS_HOST_PREFIX;
        private String vaultEndpointFormat = OCI_ENDPOINT_FORMAT;
        private String kmsEndpointFormat = ENDPOINT_FORMAT;
        private String retrievalEndpointFormat = OCI_ENDPOINT_FORMAT;
        private String vaultEndpoint;
        private String retrievalEndpoint;
        private String kmsEndpoint;
        private String cryptographicEndpoint;
        private String managementEndpoint;
        private OciRestApi restApi;

        private Builder() {
        }

        @Override
        public OciVaultRx build() {
            if (restApi == null) {
                restApi = accessBuilder.build();
            }
            return new OciVaultRxImpl(this);
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
            config.get("vault.secret-api-version").asString().ifPresent(this::secretApiVersion);
            config.get("vault.secret-bundle-api-version").asString().ifPresent(this::secretBundleApiVersion);
            config.get("vault.cryptographic-endpoint").asString().ifPresent(this::cryptographicEndpoint);
            config.get("vault.management-endpoint").asString().ifPresent(this::managementEndpoint);
            config.get("vault.vault-endpoint").asString().ifPresent(this::vaultEndpoint);
            config.get("vault.retrieval-endpoint").asString().ifPresent(this::retrievalEndpoint);
            config.get("vault.kms-endpoint").asString().ifPresent(this::kmsEndpoint);
            config.get("vault.endpoint-format").asString().ifPresent(this::vaultEndpointFormat);
            config.get("vault.vault-prefix").asString().ifPresent(this::vaultPrefix);
            config.get("vault.retrieval-prefix").asString().ifPresent(this::retrievalPrefix);
            return this;
        }

        public Builder vaultPrefix(String vaultPrefix) {
            this.vaultPrefix = vaultPrefix;
            return this;
        }

        public Builder retrievalPrefix(String retrievalHostPrefix) {
            this.retrievalPrefix = retrievalHostPrefix;
            return this;
        }

        public Builder vaultEndpoint(String vaultEndpoint) {
            this.vaultEndpoint = vaultEndpoint;
            return this;
        }

        public Builder retrievalEndpoint(String retrievalEndpoint) {
            this.retrievalEndpoint = retrievalEndpoint;
            return this;
        }

        public Builder kmsPrefix(String kmsPrefix) {
            this.kmsPrefix = kmsPrefix;
            return this;
        }

        public Builder kmsEndpoint(String kmsEndpoint) {
            this.kmsEndpoint = kmsEndpoint;
            return this;
        }

        public Builder vaultEndpointFormat(String endpointFormat) {
            this.vaultEndpointFormat = endpointFormat;
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

        public Builder managementEndpoint(String managementEndpoint) {
            this.managementEndpoint = managementEndpoint;
            return this;
        }

        public Builder restApi(OciRestApi restApi) {
            this.restApi = restApi;
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

        OciRestApi restApi() {
            return restApi;
        }

        String cryptographicEndpoint() {
            return cryptographicEndpoint;
        }

        String managementEndpoint() {
            return managementEndpoint;
        }

        String vaultPrefix() {
            return vaultPrefix;
        }

        String retrievalPrefix() {
            return retrievalPrefix;
        }

        String kmsPrefix() {
            return kmsPrefix;
        }

        String vaultEndpointFormat() {
            return vaultEndpointFormat;
        }

        String kmsEndpointFormat() {
            return kmsEndpointFormat;
        }

        String retrievalEndpointFormat() {
            return retrievalEndpointFormat;
        }

        String vaultEndpoint() {
            return vaultEndpoint;
        }

        String retrievalEndpoint() {
            return retrievalEndpoint;
        }

        String kmsEndpoint() {
            return kmsEndpoint;
        }
    }
}
