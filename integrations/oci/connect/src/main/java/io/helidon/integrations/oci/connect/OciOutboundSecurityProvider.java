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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import io.helidon.common.reactive.Single;
import io.helidon.integrations.oci.connect.OciHttpSignature.SignatureRequest;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityException;
import io.helidon.security.SecurityResponse;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.httpsign.SignedHeadersConfig;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.util.TokenHandler;

class OciOutboundSecurityProvider implements OutboundSecurityProvider {
    private static final SignedHeadersConfig SIGNED_HEADERS = SignedHeadersConfig.builder()
            .defaultConfig(SignedHeadersConfig.HeadersConfig
                                   .create(List.of("date", SignedHeadersConfig.REQUEST_TARGET, "host")))
            .config("put", SignedHeadersConfig.HeadersConfig
                    .create(List.of(SignedHeadersConfig.REQUEST_TARGET,
                                    "host",
                                    "date"),
                            List.of(
                                    "x-content-sha256",
                                    "content-type",
                                    "content-length")))
            .config("post", SignedHeadersConfig.HeadersConfig
                    .create(List.of(SignedHeadersConfig.REQUEST_TARGET,
                                    "host",
                                    "date"),
                            List.of(
                                    "x-content-sha256",
                                    "content-type",
                                    "content-length")))
            .build();

    private static final SignedHeadersConfig OBJECT_STORAGE_UPLOAD_SIGNED_HEADERS = SignedHeadersConfig.builder()
            .defaultConfig(SignedHeadersConfig.HeadersConfig
                                   .create(List.of("date", SignedHeadersConfig.REQUEST_TARGET, "host")))
            .build();

    private static final TokenHandler TOKEN_HANDLER = TokenHandler.builder()
            .tokenPrefix("Signature version=\"1\",")
            .tokenHeader("Authorization")
            .build();

    private static final Logger LOGGER = Logger.getLogger(OciOutboundSecurityProvider.class.getName());

    private final AtomicReference<OciSignatureData> signatureData = new AtomicReference<>();
    private final OutboundConfig outboundConfig;

    OciOutboundSecurityProvider(OciSignatureData signatureData) {
        this.signatureData.set(signatureData);
        this.outboundConfig = outboundConfig();
    }

    static OciOutboundSecurityProvider create(OciSignatureData signatureData) {
        return new OciOutboundSecurityProvider(signatureData);
    }

    private static OutboundConfig outboundConfig() {
        return OutboundConfig.builder()
                .addTarget(outboundTargetUpload())
                .addTarget(outboundTargetFull())
                .build();
    }

    private static OutboundTarget outboundTargetUpload() {
        return OutboundTarget.builder("oci-object-storage")
                .customObject(SignatureTarget.class, SignatureTarget.create(OBJECT_STORAGE_UPLOAD_SIGNED_HEADERS))
                .addMethod("PUT")
                .addHost("objectstorage.*")
                .addHost("*")
                .addPath("/n/.+/b/.+/o/.+")
                .build();
    }

    private static OutboundTarget outboundTargetFull() {
        return OutboundTarget.builder("oci")
                .customObject(SignatureTarget.class, SignatureTarget.create(SIGNED_HEADERS))
                .addHost("*")
                .build();
    }

    @Override
    public CompletionStage<OutboundSecurityResponse> outboundSecurity(ProviderRequest providerRequest,
                                                                      SecurityEnvironment outboundEnv,
                                                                      EndpointConfig outboundConfig) {
        return Single.just(sign(outboundEnv));
    }

    void updateSignatureData(OciSignatureData data) {
        this.signatureData.set(data);
    }

    // we are not running any asynchronous stuff in here, we can just return synchronously
    private OutboundSecurityResponse sign(SecurityEnvironment outboundEnv) {
        return this.outboundConfig.findTarget(outboundEnv)
                .map(it -> sign(outboundEnv, it))
                .orElseGet(OutboundSecurityResponse::empty);
    }

    private OutboundSecurityResponse sign(SecurityEnvironment outboundEnv, OutboundTarget target) {
        SignatureTarget signatureTarget = target.customObject(SignatureTarget.class)
                .orElseThrow(() -> new SecurityException("Failed to find signature configuration for target " + target.name()));

        Map<String, List<String>> newHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        newHeaders.putAll(outboundEnv.headers());

        OciSignatureData sigData = signatureData.get();
        LOGGER.finest("Creating request signature with kid: " + sigData.keyId());

        OciHttpSignature signature = OciHttpSignature.sign(SignatureRequest.builder()
                .env(outboundEnv)
                .privateKey(sigData.privateKey())
                .keyId(sigData.keyId())
                .headersConfig(signatureTarget.signedHeadersConfig)
                .newHeaders(newHeaders)
                .build());

        TOKEN_HANDLER.addHeader(newHeaders, signature.toSignatureHeader());

        return OutboundSecurityResponse.builder()
                .requestHeaders(newHeaders)
                .status(SecurityResponse.SecurityStatus.SUCCESS)
                .build();
    }

    private static class SignatureTarget {
        private final SignedHeadersConfig signedHeadersConfig;

        private SignatureTarget(SignedHeadersConfig signedHeadersConfig) {
            this.signedHeadersConfig = signedHeadersConfig;
        }

        private static SignatureTarget create(SignedHeadersConfig signedHeaders) {
            return new SignatureTarget(signedHeaders);
        }
    }
}
