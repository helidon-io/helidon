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

package io.helidon.integrations.oci.connect;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.Base64Value;
import io.helidon.common.crypto.Signature;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.providers.httpsign.HttpSignatureException;
import io.helidon.security.providers.httpsign.SignedHeadersConfig;

/**
 * Class wrapping signature and fields needed to build and validate it.
 */
class OciHttpSignature {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static final Logger LOGGER = Logger.getLogger(OciHttpSignature.class.getName());
    private static final String ALGORITHM = "rsa-sha256";

    private final String keyId;
    private final List<String> headers;
    private String base64Signature;

    OciHttpSignature(String keyId, List<String> headers) {
        this.keyId = keyId;
        this.headers = headers;
    }

    static OciHttpSignature sign(SignatureRequest request) {
        OciHttpSignature signature = new OciHttpSignature(request.keyId,
                                                          request.headersToSign);

        Base64Value signedValue = signature.signRsaSha256(request.env,
                                                          request.privateKey,
                                                          request.newHeaders);
        signature.base64Signature = signedValue.toBase64();

        return signature;
    }

    String toSignatureHeader() {
        return "keyId=\"" + keyId + "\","
                + "algorithm=\"" + ALGORITHM + "\","
                + "headers=\"" + String.join(" ", headers) + "\","
                + "signature=\"" + base64Signature + "\"";
    }

    private Base64Value signRsaSha256(SecurityEnvironment env, RSAPrivateKey privateKey, Map<String, List<String>> newHeaders) {
        Signature signature = Signature.builder()
                .privateKey(privateKey)
                .algorithm(Signature.ALGORITHM_SHA256_RSA)
                .build();

        return signature.digest(Base64Value.create(getBytesToSign(env, newHeaders)));
    }

    private byte[] getBytesToSign(SecurityEnvironment env, Map<String, List<String>> newHeaders) {
        return getSignedString(newHeaders, env).getBytes(StandardCharsets.UTF_8);
    }

    String getSignedString(Map<String, List<String>> newHeaders, SecurityEnvironment env) {
        Map<String, List<String>> requestHeaders = env.headers();
        List<String> linesToSign = new LinkedList<>();

        for (String header : this.headers) {
            if ("(request-target)".equals(header)) {
                //special case
                linesToSign.add(header
                                        + ": " + env.method().toLowerCase()
                                        + " " + env.path().orElse("/"));
            } else {
                List<String> headerValues = requestHeaders.get(header);
                if (null == headerValues && null == newHeaders) {
                    // we do not support creation of new headers, just throw an exception
                    throw new HttpSignatureException("Header " + header + " is required for signature, yet not defined in "
                                                             + "request");
                }
                if (null == headerValues) {
                    // there are two headers we understand and may want to add to request
                    if ("date".equalsIgnoreCase(header)) {
                        String date = ZonedDateTime.now(ZoneId.of("GMT")).format(DATE_FORMATTER);
                        headerValues = List.of(date);
                        newHeaders.put("date", headerValues);

                        LOGGER.finest(() -> "Added date header to request: " + date);
                    } else if ("host".equalsIgnoreCase(header)) {
                        URI uri = env.targetUri();

                        String host = uri.getHost() + ":" + uri.getPort();
                        headerValues = List.of(host);
                        newHeaders.put("host", headerValues);

                        LOGGER.finest(() -> "Added host header to request: " + host);
                    } else {
                        throw new HttpSignatureException("Header " + header + " is required for signature, yet not defined in "
                                                                 + "request");
                    }
                }

                linesToSign.add(header + ": " + String.join(" ", headerValues));
            }
        }

        // 2.3.  Signature String Construction
        // If value is not the last value then append an ASCII newline `\n`.
        String toSign = String.join("\n", linesToSign);

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("Data to sign: " + toSign);
        }

        return toSign;
    }

    public static class SignatureRequest {
        private final SecurityEnvironment env;
        private final Map<String, List<String>> newHeaders;
        private final String keyId;
        private final List<String> headersToSign;
        private final RSAPrivateKey privateKey;

        private SignatureRequest(Builder builder) {
            this.env = builder.env;
            this.newHeaders = builder.newHeaders;
            this.keyId = builder.keyId;
            this.headersToSign = builder.headersToSign;
            this.privateKey = builder.privateKey;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder implements io.helidon.common.Builder<SignatureRequest> {
            private RSAPrivateKey privateKey;
            private SecurityEnvironment env;
            private Map<String, List<String>> newHeaders;
            private String keyId;
            private SignedHeadersConfig headersConfig;
            private List<String> headersToSign;

            private Builder() {
            }

            @Override
            public SignatureRequest build() {
                this.headersToSign = headersConfig.headers(env.method(), env.headers());
                return new SignatureRequest(this);
            }

            public Builder env(SecurityEnvironment env) {
                this.env = env;
                return this;
            }

            public Builder newHeaders(Map<String,
                    List<String>> newHeaders) {
                this.newHeaders = newHeaders;
                return this;
            }

            public Builder keyId(String keyId) {
                this.keyId = keyId;
                return this;
            }

            public Builder headersConfig(SignedHeadersConfig headersConfig) {
                this.headersConfig = headersConfig;
                return this;
            }

            public Builder privateKey(RSAPrivateKey privateKey) {
                this.privateKey = privateKey;
                return this;
            }
        }
    }
}
