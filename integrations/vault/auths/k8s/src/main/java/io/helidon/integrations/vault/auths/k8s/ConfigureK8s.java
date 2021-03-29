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

package io.helidon.integrations.vault.auths.k8s;

import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Configure k8s method request.
 */
public final class ConfigureK8s {
    private ConfigureK8s() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends VaultRequest<Request> {
        private Request() {
        }

        /**
         * Fluent API builder for configuring a request.
         * The request builder is passed as is, without a build method.
         * The equivalent of a build method is {@link #toJson(javax.json.JsonBuilderFactory)}
         * used by the {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @return new request builder
         */
        public static Request builder() {
            return new Request();
        }

        /**
         * Host string, host:port pair, or URL to the base of the Kubernetes API server.
         * Required.
         *
         * @param address k8s API server address
         * @return updated request
         */
        public Request address(String address) {
            return add("kubernetes_host", address);
        }

        /**
         * PEM encoded CA cert for use by the TLS client used to talk with the Kubernetes API. NOTE: Every line must end with a
         * newline.
         *
         * @param caCertPem certification authority certificate, available at
         *      {@code /var/run/secrets/kubernetes.io/serviceaccount/ca.crt} when running in a pod
         * @return updated request
         */
        public Request k8sCaCert(String caCertPem) {
            return add("kubernetes_ca_cert", caCertPem);
        }

        /**
         * A service account JWT used to access the TokenReview API to validate other JWTs during login. If not set, the JWT
         * submitted in the login payload will be used to access the Kubernetes TokenReview API.
         *
         * @param token token to use
         * @return updated request
         */
        public Request tokenReviewerJwt(String token) {
            return add("token_reviewer_jwt", token);
        }

        /**
         * Add PEM formatted public key or certificate used to verify the signatures of Kubernetes service account JWTs. If a
         * certificate is given, its public key will be extracted. Not every installation of Kubernetes exposes these keys.
         *
         * @param pemKey pem encoded key to add
         * @return updated request
         */
        public Request addPemKey(String pemKey) {
            return addToArray("pem_keys", pemKey);
        }

        /**
         * Optional JWT issuer. If no issuer is specified, then this plugin will use {@code kubernetes/serviceaccount} as the
         * default issuer.
         *
         * @param issuer issuer of tokens when validating the issuer
         * @return updated request
         */
        public Request issuer(String issuer) {
            return add("issuer", issuer);
        }

        /**
         * Disable JWT issuer validation. Allows to skip ISS validation.
         *
         * @param disable whether to disable issuer validation
         * @return updated request
         */
        public Request disableIssuerValidation(boolean disable) {
            return add("disable_iss_validation", disable);
        }

        /**
         * Disable defaulting to the local CA cert and service account JWT when running in a Kubernetes pod.
         *
         * @param disable whether to disable using local CA cert and service account
         * @return updated request
         */
        public Request disableLocalCaJwt(boolean disable) {
            return add("disable_local_ca_jwt", disable);
        }
    }

    /**
     * Create role response.
     */
    public static final class Response extends ApiResponse {
        // we could use a single response object for all responses without entity
        // but that would hinder future extensibility, as this allows us to add any field to this
        // class without impacting the API

        private Response(Builder builder) {
            super(builder);
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder extends ApiResponse.Builder<Builder, Response> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }
}
