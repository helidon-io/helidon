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
import java.util.function.Function;
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
import io.helidon.webclient.WebClient;
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

    private final Function<String, String> hostFunction;

    private OciRestApi(Builder builder) {
        super(builder);

        this.hostFunction = builder.hostFunction;
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
        OciRestException.Builder errorBuilder = OciRestException.ociBuilder()
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
        OciRestException.Builder errorBuilder = OciRestException.ociBuilder()
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
        OciRestException.Builder errorBuilder = OciRestException.ociBuilder()
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

        String messagePrefix = "Failed to " + method + " on path " + path + " " + response.status().code();
        OciRestException.Builder errorBuilder = OciRestException.ociBuilder()
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
            Optional<String> maybeAddress = ociRequest.address();
            URI ociUri;

            if (maybeAddress.isPresent()) {
                String address = maybeAddress.get();
                requestBuilder.uri(address);
                ociUri = URI.create(address);
            } else {
                String address = hostFunction.apply(ociRequest.hostPrefix());
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
     */
    public static class Builder extends RestApi.Builder<Builder, OciRestApi> {
        private static final String DEFAULT_HOST_FORMAT = "https://%s.%s.oci.oraclecloud.com";

        // host is constructed as https:// + prefix + . + region + .oci.oraclecloud.com
        private String hostTemplate = DEFAULT_HOST_FORMAT;
        private OciProfileConfig profileConfig;
        private WebClient webClient;
        private Function<String, String> hostFunction;

        private Builder() {
        }

        /**
         * Update builder from configuration.
         *
         * @param config config located on the node of OCI configuration
         * @return updated builder
         */
        // TODO add fault tolerance configuration - at least retry - to the default REST API.
        public Builder config(Config config) {
            super.config(config);
            config.get("config-profile").ifExists(it -> ociProfileConfig(OciProfileConfig.create(it)));
            config.get("host-format").asString().ifPresent(this::hostFormat);

            return this;
        }

        /**
         * Host template defaults to {@value DEFAULT_HOST_FORMAT}.
         * <p>
         * This can be updated through this method or through configuration, such as when connecting
         * to testing environments.
         * <p>
         * The first parameter to the format is {@link #hostPrefix}, second is region obtained
         * from {@link OciProfileConfig#region()}.
         *
         * @param format format of host including the scheme
         * @return updated builder
         */
        public Builder hostFormat(String format) {
            this.hostTemplate = format;
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

            hostFunction = hostPrefix -> String.format(hostTemplate, hostPrefix, profileConfig.region());

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
                    .build();
        }

        private void webClientSecurity(OciProfileConfig ociProfile) {
            HttpSignProvider provider = HttpSignProvider.builder()
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
