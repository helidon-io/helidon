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
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.integrations.common.rest.ApiJsonParser;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

/**
 * Get Certificate request and response.
 */
public final class CertificateGet {
    private CertificateGet() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends VaultRequest<Request> {
        private PkiFormat format = PkiFormat.PEM;
        private String serialNumber;

        private Request() {
        }

        /**
         * Fluent API builder for configuring a request.
         * The request builder is passed as is, without a build method.
         * The equivalent of a build method is {@link #toJson(jakarta.json.JsonBuilderFactory)}
         * used by the {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @return new request builder
         */
        public static Request builder() {
            return new Request();
        }

        /**
         * Format of the certificate to get.
         *
         * @param format the required format
         * @return updated request
         */
        public Request format(PkiFormat format) {
            this.format = format;
            return this;
        }

        /**
         * Serial number of the certificate to get.
         *
         * @param serialNumber serial number
         * @return updated request
         */
        public Request serialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
            return this;
        }

        /**
         * Requested format.
         *
         * @return format that was requested
         */
        public PkiFormat format() {
            return format;
        }

        @Override
        public Optional<JsonObject> toJson(JsonBuilderFactory factory) {
            return Optional.empty();
        }

        String serialNumber() {
            if (serialNumber == null) {
                throw new VaultApiException("CertificateGet.Request serial number must be defined");
            }
            return serialNumber;
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static final class Response extends ApiJsonParser {
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

        private Response(JsonObject object) {
            this.certBytes = object.getJsonObject("data").getString("certificate").getBytes(StandardCharsets.UTF_8);
        }

        static Response create(JsonObject json) {
            return new Response(json);
        }

        /**
         * Get the certificate as an X.509 certificate.
         *
         * @return CA certificate
         */
        public X509Certificate toCertificate() {
            return cert.get();
        }

        /**
         * Get certificate bytes in the requested format.
         * @return bytes of the certificate
         */
        public byte[] toBytes() {
            return Arrays.copyOf(certBytes, certBytes.length);
        }
    }
}
