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

import javax.json.JsonObject;

import io.helidon.common.LazyValue;
import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.vault.VaultApiException;

/**
 * request and response.
 */
public final class SignCsr {
    private SignCsr() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends PkiCertificateRequest<Request> {

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
         * Certification request (CSR) in PEM format.
         *
         * @param csr
         * @return
         */
        public Request csr(String csr) {
            return add("csr", csr);
        }

    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static final class Response extends RawCertificateResponse {
        private final LazyValue<X509Certificate> cert = LazyValue.create(() -> {
            try {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                return (X509Certificate) factory
                        .generateCertificate(new ByteArrayInputStream(certificate()));
            } catch (CertificateException e) {
                throw new VaultApiException("Failed to parse certificate from Vault response", e);
            }
        });

        private Response(PkiFormat format, Builder builder) {
            super(format, builder);
        }

        static Response.Builder builder() {
            return new Builder();
        }

        public X509Certificate toCertificate() {
            return cert.get();
        }

        static final class Builder extends ApiEntityResponse.Builder<Builder, Response, JsonObject> {
            private PkiFormat format;

            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(format, this);
            }

            Builder format(PkiFormat format) {
                this.format = format;
                return this;
            }
        }
    }
}
