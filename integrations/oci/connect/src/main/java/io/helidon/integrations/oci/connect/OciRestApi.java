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

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonObject;

import io.helidon.common.Version;
import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.pki.KeyConfig;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiRequest;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.common.rest.RestApiBase;
import io.helidon.security.Security;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.httpsign.HttpSignProvider;
import io.helidon.security.providers.httpsign.OutboundTargetDefinition;
import io.helidon.security.providers.httpsign.SignedHeadersConfig;
import io.helidon.security.util.TokenHandler;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webclient.security.WebClientSecurity;

/**
 * OCI specific REST API.
 * This class uses HTTP Signatures to sign requests.
 */
public class OciRestApi extends RestApiBase {
    private static final Logger LOGGER = Logger.getLogger(OciRestApi.class.getName());
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

    private final BiFunction<String, String, String> formatFunction;

    private OciRestApi(Builder builder) {
        super(builder);

        this.formatFunction = builder.formatFunction;
    }

    /**
     * A new builder to configure instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create API with default configuration.
     *
     * @return a new REST API with default configuration
     */
    public static OciRestApi create() {
        return builder().build();
    }

    /**
     * Create OCI REST API from configuration.
     *
     * @param config configuration
     * @return a new REST API configured from config
     */
    public static OciRestApi create(Config config) {
        return builder().config(config).build();
    }

    @Override
    protected Throwable readErrorFailedEntity(String path,
                                              ApiRequest<?> request,
                                              Http.RequestMethod method,
                                              String requestId,
                                              WebClientResponse response,
                                              Throwable it) {
        String messagePrefix = "Failed to " + method + " on path " + path + " " + response.status().code() + " " + response;
        OciRestException.Builder errorBuilder = OciRestException.builder()
                .cause(it)
                .requestId(requestId)
                .headers(response.headers())
                .status(response.status());
        return errorBuilder
                .message(messagePrefix + ". Failed to read OCI error, failed with: " + it.getMessage())
                .apiSpecificError("Unknown.")
                .build();
    }

    @Override
    protected Throwable readError(String path,
                                  ApiRequest<?> request,
                                  Http.RequestMethod method,
                                  String requestId,
                                  WebClientResponse response) {

        String messagePrefix = "Failed to " + method + " on path " + path + " " + response.status().code();
        OciRestException.Builder errorBuilder = OciRestException.builder()
                .requestId(requestId)
                .headers(response.headers())
                .status(response.status());
        return errorBuilder
                .message(messagePrefix + ". Failed to read OCI error, no response entity")
                .apiSpecificError("Unknown.")
                .build();
    }

    @Override
    protected Throwable readError(String path,
                                  ApiRequest<?> request,
                                  Http.RequestMethod method,
                                  String requestId,
                                  WebClientResponse response,
                                  String entity) {
        String messagePrefix = "Failed to " + method + " on path " + path + " " + response.status().code();
        OciRestException.Builder errorBuilder = OciRestException.builder()
                .requestId(requestId)
                .headers(response.headers())
                .status(response.status());
        return errorBuilder
                .message(messagePrefix + ". Failed to read OCI error, original entity: " + URLEncoder
                        .encode(entity, StandardCharsets.UTF_8))
                .apiSpecificError("Unknown.")
                .build();
    }

    @Override
    protected Throwable readError(String path,
                                  ApiRequest<?> request,
                                  Http.RequestMethod method,
                                  String requestId,
                                  WebClientResponse response,
                                  JsonObject json) {

        String messagePrefix = "Failed to " + method + " on path " + path + " " + response.status();
        OciRestException.Builder errorBuilder = OciRestException.builder()
                .requestId(requestId)
                .headers(response.headers())
                .status(response.status());

        try {
            String errorCode = json.getString("code");
            String errorMessage = json.getString("message");
            return errorBuilder
                    .message(messagePrefix + ". OCI code: " + errorCode + ", OCI message: " + errorMessage)
                    .apiSpecificError("OCI code: " + errorCode + ", OCI message: " + errorMessage)
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .build();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to read error response", e);
            return errorBuilder
                    .cause(e)
                    .message(messagePrefix + ". Failed to read OCI error, original entity: " + URLEncoder
                            .encode(json.toString(), StandardCharsets.UTF_8))
                    .apiSpecificError("Unknown.")
                    .build();
        }
    }

    @Override
    protected Single<WebClientRequestBuilder> updateRequestBuilder(WebClientRequestBuilder requestBuilder,
                                                                   String path,
                                                                   ApiRequest<?> request,
                                                                   Http.RequestMethod method,
                                                                   String requestId) {
        // signatures not needed
        updateRequestBuilder(requestBuilder, request, requestId);
        return Single.just(requestBuilder);
    }

    @Override
    protected Single<WebClientRequestBuilder> updateRequestBuilderBytesPayload(WebClientRequestBuilder requestBuilder,
                                                                               String path,
                                                                               ApiRequest<?> request,
                                                                               Http.RequestMethod method,
                                                                               String requestId) {
        // signatures not needed
        updateRequestBuilder(requestBuilder, request, requestId);
        return Single.just(requestBuilder);
    }

    @Override
    protected Supplier<Single<WebClientResponse>> requestJsonPayload(String path,
                                                                     ApiRequest<?> request,
                                                                     Http.RequestMethod method,
                                                                     String requestId,
                                                                     WebClientRequestBuilder requestBuilder,
                                                                     JsonObject jsonObject) {

        requestBuilder.accept(request.responseMediaType().orElse(MediaType.APPLICATION_JSON));
        requestBuilder.contentType(request.requestMediaType().orElse(MediaType.APPLICATION_JSON));
        // common stuff
        updateRequestBuilder(requestBuilder, request, requestId);
        // signature requirement

        // this requires a content length and hash
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonWriterFactory().createWriter(baos).write(jsonObject);

        byte[] requestBytes = baos.toByteArray();
        String sha256 = computeSha256(requestBytes);
        requestBuilder.headers(headers -> {
            headers.contentLength(requestBytes.length);
            headers.add("x-content-sha256", sha256);
            return headers;
        }).contentType(request.requestMediaType().orElse(MediaType.APPLICATION_JSON));

        return () -> requestBuilder.submit(requestBytes);
    }

    @Override
    protected Single<WebClientRequestBuilder> updateRequestBuilderCommon(WebClientRequestBuilder requestBuilder,
                                                                         String path,
                                                                         ApiRequest<?> request,
                                                                         Http.RequestMethod method,
                                                                         String requestId) {
        updateRequestBuilder(requestBuilder, request, requestId);
        return Single.just(requestBuilder);
    }

    private void updateRequestBuilder(WebClientRequestBuilder requestBuilder, ApiRequest<?> request, String requestId) {
        requestBuilder.headers().add("opc-request-id", requestId);

        if (request instanceof OciRequestBase) {
            OciRequestBase<?> ociRequest = (OciRequestBase<?>) request;
            ociRequest.retryToken().ifPresent(it -> requestBuilder.headers().add("opc-retry-token", it));
            Optional<String> maybeAddress = ociRequest.endpoint();
            URI ociUri;

            if (maybeAddress.isPresent()) {
                String address = maybeAddress.get();
                requestBuilder.uri(address);
                ociUri = URI.create(address);
            } else {
                String prefix = ociRequest.hostPrefix();
                String address = formatFunction.apply(ociRequest.hostFormat(), prefix);
                requestBuilder.uri(address);
                ociUri = URI.create(address);
            }

            // this is a required change - Host was failing with secret bundles, host works
            requestBuilder.headers().add("host", ociUri.getHost());
        } else {
            throw new OciApiException("Cannot handle non-OCI requests. Request: " + request.getClass().getName());
        }
    }

    private String computeSha256(byte[] requestBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(requestBytes);
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new OciApiException("Failed to generate message digest", e);
        }
    }

    /**
     * Fluent API builder for {@link io.helidon.integrations.oci.connect.OciRestApi}.
     * <p>
     * The final host of each endpoint is computed based on a format provided by each request (usually the
     * same format for a certain area, such as Vault, ObjectStorage etc.), and a prefix also received with each
     * request.
     * <p>
     * The template {@code %s://%s.%s.%s} would resolve into
     * {@code ${scheme}://${hostPrefix}.${region}.oci.${domain}}.
     * <p>
     * Let's consider the following configuration:
     * <ul>
     *     <li>{@code scheme}: {@value #DEFAULT_DOMAIN}</li>
     *     <li>{@code hostPrefix}: {@code vaults}</li>
     *     <li>{@code region}: {@code eu-frankfurt-1}</li>
     *     <li>{@code domain}: {@value #DEFAULT_DOMAIN}</li>
     * </ul>
     * we would get {@code https://vaults.eu-frankfurt-1.oraclecloud.com} as the endpoint.
     *
     * In case we need to connect to a local docker image, or a testing environment, an explicit
     * address can be configured for each domain specific API, or parts of the template can be modified
     * if the final address matches the expected structure.
     */
    public static class Builder extends RestApi.Builder<Builder, OciRestApi> {
        private static final String DEFAULT_SCHEME = "https";
        private static final String DEFAULT_DOMAIN = "oraclecloud.com";

        private OciProfileConfig profileConfig;
        private BiFunction<String, String, String> formatFunction;
        private String scheme = DEFAULT_SCHEME;
        private String domain = DEFAULT_DOMAIN;

        private Builder() {
        }

        /**
         * Update builder from configuration.
         *
         * @param config config located on the node of OCI configuration
         * @return updated builder
         */
        public Builder config(Config config) {
            super.config(config);
            config.get("config-profile").ifExists(it -> ociProfileConfig(OciProfileConfig.create(it)));
            config.get("scheme").asString().ifPresent(this::scheme);
            config.get("domain").asString().ifPresent(this::domain);

            return this;
        }

        /**
         * Scheme to use when constructing endpoint address.
         * Defaults to {@value #DEFAULT_SCHEME}.
         *
         * @param scheme scheme to use (most likely either {@code http} or {@code https}
         * @return updated builder
         */
        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        /**
         * Domain to use when constructing endpoint address.
         * Defaults to {@value #DEFAULT_DOMAIN}.
         *
         * @param domain domain to use
         * @return updated builder
         */
        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * Profile configuration to use.
         *
         * @param profileConfig profile config
         * @return updated builder
         */
        public Builder ociProfileConfig(OciProfileConfig profileConfig) {
            this.profileConfig = profileConfig;
            return this;
        }

        @Override
        protected void preBuild() {
            super.preBuild();
            if (profileConfig == null) {
                ociProfileConfig(OciProfileConfig.create());
            }

            String scheme = this.scheme;
            String region = this.profileConfig.region();
            String domain = this.domain;

            formatFunction = (format, hostPrefix) -> String.format(format, scheme, hostPrefix, region, domain);

            // this must happen only once
            webClientSecurity(profileConfig);
        }

        @Override
        protected OciRestApi doBuild() {
            return new OciRestApi(this);
        }

        private OutboundTarget outboundTargetUpload(OciProfileConfig profile) {
            return OutboundTarget.builder("oci-object-storage")
                    .customObject(OutboundTargetDefinition.class,
                                  buildSignatureTargetStorageUpload(profile))
                    .addMethod("PUT")
                    .addHost("objectstorage.*")
                    .addHost("*")
                    .addPath("/n/.+/b/.+/o/.+")
                    .build();
        }

        private OutboundTarget outboundTargetFull(OciProfileConfig profile) {
            return OutboundTarget.builder("oci")
                    .customObject(OutboundTargetDefinition.class,
                                  buildSignatureTargetFull(profile))
                    .addHost("*")
                    .build();
        }

        private OutboundTargetDefinition buildSignatureTargetStorageUpload(OciProfileConfig profile) {
            TokenHandler outboundTokenHandler = TokenHandler.builder()
                    .tokenPrefix("Signature version=\"1\",")
                    .tokenHeader("Authorization")
                    .build();

            String keyId = profile.tenancyOcid()
                    + "/" + profile.userOcid()
                    + "/" + profile.keyFingerprint();

            return OutboundTargetDefinition.builder(keyId)
                    .privateKeyConfig(KeyConfig.pemBuilder()
                                              .key(Resource.create("private key from profile", profile.privateKey()))
                                              .build())
                    .algorithm("rsa-sha256")
                    .signedHeaders(OBJECT_STORAGE_UPLOAD_SIGNED_HEADERS)
                    .tokenHandler(outboundTokenHandler)
                    .backwardCompatibleEol(false)
                    .build();
        }

        private OutboundTargetDefinition buildSignatureTargetFull(OciProfileConfig profile) {
            TokenHandler outboundTokenHandler = TokenHandler.builder()
                    .tokenPrefix("Signature version=\"1\",")
                    .tokenHeader("Authorization")
                    .build();

            String keyId = profile.tenancyOcid()
                    + "/" + profile.userOcid()
                    + "/" + profile.keyFingerprint();

            return OutboundTargetDefinition.builder(keyId)
                    .privateKeyConfig(KeyConfig.pemBuilder()
                                              .key(Resource.create("private key from profile", profile.privateKey()))
                                              .build())
                    .algorithm("rsa-sha256")
                    .signedHeaders(SIGNED_HEADERS)
                    .tokenHandler(outboundTokenHandler)
                    .backwardCompatibleEol(false)
                    .build();
        }

        private void webClientSecurity(OciProfileConfig ociProfile) {
            HttpSignProvider provider = HttpSignProvider.builder()
                    .backwardCompatibleEol(false)
                    .outbound(outboundConfig(ociProfile))
                    .build();

            Security security = Security.builder()
                    .addOutboundSecurityProvider(provider)
                    .build();

            super.webClientBuilder(builder -> {
                builder.addHeader("opc-client-info", "Helidon/" + Version.VERSION);
                builder.addService(WebClientSecurity.create(security));
            });

        }

        private OutboundConfig outboundConfig(OciProfileConfig profile) {
            return OutboundConfig.builder()
                    .addTarget(outboundTargetUpload(profile))
                    .addTarget(outboundTargetFull(profile))
                    .build();
        }

    }
}
