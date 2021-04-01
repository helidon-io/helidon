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
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonObject;

import io.helidon.common.Version;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiRequest;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.common.rest.RestApiBase;
import io.helidon.security.Security;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webclient.security.WebClientSecurity;

/**
 * OCI specific REST API.
 * This class uses HTTP Signatures to sign requests.
 */
public class OciRestApi extends RestApiBase {
    private static final Logger LOGGER = Logger.getLogger(OciRestApi.class.getName());

    private final BiFunction<String, String, String> formatFunction;
    private final OciOutboundSecurityProvider outboundProvider;
    private final OciConfigProvider configProvider;

    private OciRestApi(Builder builder) {
        super(builder);

        this.formatFunction = builder.formatFunction;
        this.outboundProvider = builder.outboundProvider;
        this.configProvider = builder.ociConfigProvider;
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

    private static ConfigType guessConfigType() {
        // attempt to guess what config type this is, let's start from the most "obscure" one and
        // end with local environment
        if (OciConfigResourcePrincipal.isAvailable()) {
            LOGGER.fine("OCI Resource Principal configuration is available.");
            return ConfigType.RESOURCE_PRINCIPAL;
        }
        if (OciConfigInstancePrincipal.isAvailable()) {
            LOGGER.fine("OCI Instance Principal configuration is available.");
            return ConfigType.INSTANCE_PRINCIPAL;
        }

        LOGGER.fine("Using OCI Profile based configuration, as neither resource nor instance principal is available");
        return ConfigType.OCI_PROFILE;
    }

    @Override
    protected Supplier<Single<WebClientResponse>> responseSupplier(Http.RequestMethod method,
                                                                   String path,
                                                                   ApiRequest<?> request,
                                                                   String requestId) {
        Supplier<Single<WebClientResponse>> originalSupplier = super.responseSupplier(method, path, request, requestId);

        return wrapSupplierInSecurityRetry(originalSupplier);
    }

    @Override
    protected Supplier<Single<WebClientResponse>> requestBytesPayload(String path,
                                                                      ApiRequest<?> request,
                                                                      Http.RequestMethod method,
                                                                      String requestId,
                                                                      WebClientRequestBuilder requestBuilder,
                                                                      Flow.Publisher<DataChunk> publisher) {
        requestBuilder.disableChunked();
        Supplier<Single<WebClientResponse>> originalSupplier = super
                .requestBytesPayload(path, request, method, requestId, requestBuilder, publisher);

        return wrapSupplierInSecurityRetry(originalSupplier);
    }

    private Supplier<Single<WebClientResponse>> wrapSupplierInSecurityRetry(Supplier<Single<WebClientResponse>> originalSupplier) {
        // to handle timed-out token(s) when using instance or resource identity, we need to retry in case we get 401
        // and the token refreshes successfully
        return () -> {
            OciSignatureData ociSignatureData = configProvider.signatureData();

            return originalSupplier.get()
                    .flatMapSingle(clientResponse -> {
                        if (clientResponse.status() == Http.Status.UNAUTHORIZED_401) {
                            // maybe this is an timed-out token
                            return configProvider.refresh()
                                    .flatMapSingle(newSignatureData -> {
                                        if (newSignatureData == ociSignatureData) {
                                            // no change in signature data, just return
                                            return Single.just(clientResponse);
                                        } else {
                                            // signature data modified, let's retry
                                            return originalSupplier.get();
                                        }
                                    });
                        } else {
                            return Single.just(clientResponse);
                        }
                    });
        };
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

    public enum ConfigType {
        INSTANCE_PRINCIPAL,
        RESOURCE_PRINCIPAL,
        OCI_PROFILE
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

        private ConfigType configType = guessConfigType();

        private OciConfigProvider ociConfigProvider;
        private BiFunction<String, String, String> formatFunction;
        private String scheme = DEFAULT_SCHEME;
        private String domain = DEFAULT_DOMAIN;
        private OciOutboundSecurityProvider outboundProvider;

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

            config.get("scheme").asString().ifPresent(this::scheme);
            config.get("domain").asString().ifPresent(this::domain);

            Config configProviderConfig = config.get("config");

            Config instancePrincipal = configProviderConfig.get("instance-principal");
            Config resourcePrincipal = configProviderConfig.get("resource-principal");
            Config profileConfig = configProviderConfig.get("oci-profile");

            if (instancePrincipal.exists() && instancePrincipal.get("enabled").asBoolean().orElse(true)) {
                // use instance principal
                configType = ConfigType.INSTANCE_PRINCIPAL;
            } else if (resourcePrincipal.exists() && resourcePrincipal.get("enabled").asBoolean().orElse(true)) {
                // use resource principal
                configType = ConfigType.RESOURCE_PRINCIPAL;
            }
            if (profileConfig.exists() && profileConfig.get("enabled").asBoolean().orElse(true)) {
                // use config profile
                configType = ConfigType.OCI_PROFILE;
                configProvider(OciConfigProfile.create(profileConfig));
            }

            return this;
        }

        public Builder configType(ConfigType configType) {
            this.configType = configType;
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
         * Cloud connectivity configuration to use.
         *
         * @param ociConfigProvider profile config, such as {@link OciConfigProfile}
         * @return updated builder
         */
        public Builder configProvider(OciConfigProvider ociConfigProvider) {
            this.ociConfigProvider = ociConfigProvider;
            return this;
        }

        @Override
        protected void preBuild() {
            super.preBuild();
            if (ociConfigProvider == null) {
                LOGGER.finest("Config provider is not configured explicitly. Config type: " + configType);
                switch (configType) {
                case INSTANCE_PRINCIPAL:
                    configProvider(OciConfigInstancePrincipal.create());
                    break;
                case RESOURCE_PRINCIPAL:
                    configProvider(OciConfigResourcePrincipal.create());
                    break;
                case OCI_PROFILE:
                    configProvider(OciConfigProfile.create());
                    break;
                }
            }

            String scheme = this.scheme;
            String region = this.ociConfigProvider.region();
            String domain = this.ociConfigProvider.domain().orElse(this.domain);

            formatFunction = (format, hostPrefix) -> String.format(format, scheme, hostPrefix, region, domain);

            this.outboundProvider = OciOutboundSecurityProvider.create(ociConfigProvider.signatureData());

            // this must happen only once
            webClientSecurity();
        }

        @Override
        protected OciRestApi doBuild() {
            return new OciRestApi(this);
        }

        private void webClientSecurity() {
            Security security = Security.builder()
                    .addOutboundSecurityProvider(outboundProvider)
                    .build();

            super.webClientBuilder(builder -> {
                builder.addHeader("opc-client-info", "Helidon/" + Version.VERSION);
                builder.addService(WebClientSecurity.create(security));
            });
        }
    }
}
