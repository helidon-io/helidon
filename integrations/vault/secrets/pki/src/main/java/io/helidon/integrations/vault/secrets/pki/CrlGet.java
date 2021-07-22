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
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.Arrays;

import io.helidon.common.LazyValue;
import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Get CRL (Certificate revoke list) request and response.
 */
public final class CrlGet {
    private CrlGet() {
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

        /**
         * Format of the CRL.
         *
         * @param format format to get
         * @return updated request
         */
        public Request format(PkiFormat format) {
            this.format = format;
            return this;
        }

        /**
         * Configured format.
         *
         * @return configured format
         */
        public PkiFormat format() {
            return format;
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static final class Response extends ApiEntityResponse {
        private byte[] crlBytes;
        private LazyValue<X509CRL> crl = LazyValue.create(() -> {
            try {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                return (X509CRL) factory
                        .generateCRL(new ByteArrayInputStream(crlBytes));
            } catch (CRLException | CertificateException e) {
                throw new VaultApiException("Failed to parse CRL from Vault response", e);
            }
        });

        private Response(Builder builder) {
            super(builder);
            this.crlBytes = builder.entity();
        }

        static Builder builder() {
            return new Builder();
        }

        /**
         * Get the CRL as X.509 CRL.
         *
         * @return X.509 CRL
         */
        public X509CRL toCrl() {
            return crl.get();
        }

        /**
         * Get the CRL bytes in the format requested.
         *
         * @return bytes of the CRL
         */
        public byte[] toBytes() {
            return Arrays.copyOf(crlBytes, crlBytes.length);
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
