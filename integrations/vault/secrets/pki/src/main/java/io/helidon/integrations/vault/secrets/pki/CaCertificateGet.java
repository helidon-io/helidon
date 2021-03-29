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

package io.helidon.integrations.vault.secrets.pki;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import io.helidon.common.LazyValue;
import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * request and response.
 */
public final class CaCertificateGet {
    private CaCertificateGet() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends VaultRequest<Request> {
        private PkiFormat format = PkiFormat.DER;

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

        public Request format(PkiFormat format) {
            this.format = format;
            return this;
        }

        public PkiFormat format() {
            return format;
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static final class Response extends ApiEntityResponse {
        private byte[] certBytes;
        private final LazyValue<X509Certificate> cert = LazyValue.create(() -> {
            try {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                return (X509Certificate) factory
                        .generateCertificate(new ByteArrayInputStream(certBytes));
            } catch (CertificateException e) {
                throw new VaultApiException("Failed to parse certificate from Vault response", e);
            }
        });

        private Response(Builder builder) {
            super(builder);
            this.certBytes = builder.entity();
        }

        static Builder builder() {
            return new Builder();
        }

        public X509Certificate toCertificate() {
            return cert.get();
        }

        public byte[] toBytes() {
            return certBytes;
        }

        static final class Builder extends ApiEntityResponse.Builder<Builder, Response, byte[]> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }
}
