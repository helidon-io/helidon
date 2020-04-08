/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.cors;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.cors.CrossOriginHelperInternal.RequestAdapter;
import io.helidon.cors.CrossOriginHelperInternal.ResponseAdapter;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.helidon.cors.CrossOriginConfig.CORS_CONFIG_KEY;
import static io.helidon.cors.CrossOriginHelperInternal.prepareResponse;
import static io.helidon.cors.CrossOriginHelperInternal.processRequest;

/**
 * Performs CORS request and response handling according to a single {@link CrossOriginConfig} instance.
 */
public class CrossOriginHandler implements io.helidon.webserver.Handler {

    /**
     * Creates a handler according to the previously prepared {@code CrossOriginConfig}.
     *
     * @param crossOriginConfig the cross origin config to use for the handler
     * @return the configured handler
     */
    public static CrossOriginHandler create(CrossOriginConfig crossOriginConfig) {
        return Builder.create(crossOriginConfig).build();
    }

    /**
     * Creates a handler using default settings, as defined by the defaults in {@code CrossOriginConfig}.
     *
     * @return the configured handler
     */
    public static CrossOriginHandler create() {
        return Builder.create(CrossOriginConfig.builder().build()).build();
    }

    /**
     * Creates a handler by looking in the provided Helidon {@code Config} node using the path as the key and interpreting the
     * subtree there as {@code CrossOriginConfig} values.
     *
     * @param corsConfig config node to be interpreted as CORS information
     * @param path the path to use in looking for the CORS information
     * @return a handler initialized with the specified config information
     */
    public static CrossOriginHandler create(Config corsConfig, String path) {

        Map<String, CrossOriginConfig> fromConfig = corsConfig
                .as(new CrossOriginConfig.CrossOriginConfigMapper()).get();
        return fromConfig.containsKey(path) ? create(fromConfig.get(path)) : create();
    }

    /**
     * Creates a handler by looking in the application's default configuration for a node with key
     * {@value CrossOriginConfig#CORS_CONFIG_KEY}, then looking within that subtree for a key matching the provided path.
     *
     * @param path the path to use in looking for the CORS information in the {@value CrossOriginConfig#CORS_CONFIG_KEY} 
     * @return
     */
    public static CrossOriginHandler create(String path) {
        return create(Config.create().get(CORS_CONFIG_KEY), path);
    }

    /**
     * Returns a builder which allows the caller to set individual items of the CORS behavior without having to construct a full
     * {@code CrossOriginConfig} instance first.
     *
     * @return a builder initialized with default CORS configuration
     */
    public static CrossOriginHandler.Builder builder() {
        return Builder.create();
    }

    private final CrossOriginConfig crossOriginConfig;

    private CrossOriginHandler(Builder builder) {
        crossOriginConfig = builder.crossOriginConfigBuilder.build();
    }

    /**
     * Builder for {@code CORSSupport.Handler} instances.
     * <p>
     *     This builder is basically a shortcut which allows callers to set cross origin data directly on the handler without
     *     constructing a {@code CrossOriginConfig} first and then passing it to the handler builder.
     * </p>
     */
    public static class Builder implements CrossOriginConfig.Setter<Builder>, io.helidon.common.Builder<CrossOriginHandler> {

        private final CrossOriginConfig.Builder crossOriginConfigBuilder = CrossOriginConfig.builder();

        private Builder() {
        }

        private Builder(CrossOriginConfig crossOriginConfig) {
            crossOriginConfigBuilder.allowCredentials(crossOriginConfig.allowCredentials());
            crossOriginConfigBuilder.allowHeaders(crossOriginConfig.allowHeaders());
            crossOriginConfigBuilder.allowMethods(crossOriginConfig.allowMethods());
            crossOriginConfigBuilder.allowOrigins(crossOriginConfig.allowOrigins());
            crossOriginConfigBuilder.exposeHeaders(crossOriginConfig.exposeHeaders());
            crossOriginConfigBuilder.maxAge(crossOriginConfig.maxAge());
        }

        /**
         * Creates a builder initialized with default cross origin information.
         *
         * @return initialized builder
         */
        public static Builder create() {
            return new Builder();
        }

        /**
         * Creates a builder initialized with the specified {@code CrossOriginConfig} data.
         *
         * @param crossOriginConfig the cross origin config to use for initializing the builder
         * @return the initialized builder
         */
        public static Builder create(CrossOriginConfig crossOriginConfig) {
            return new Builder(crossOriginConfig);
        }

        @Override
        public CrossOriginHandler build() {
            return new CrossOriginHandler(this);
        }

        @Override
        public Builder allowOrigins(String... origins) {
            crossOriginConfigBuilder.allowOrigins(origins);
            return this;
        }

        @Override
        public Builder allowHeaders(String... allowHeaders) {
            crossOriginConfigBuilder.allowHeaders(allowHeaders);
            return this;
        }

        @Override
        public Builder exposeHeaders(String... exposeHeaders) {
            crossOriginConfigBuilder.exposeHeaders(exposeHeaders);;
            return this;
        }

        @Override
        public Builder allowMethods(String... allowMethods) {
            crossOriginConfigBuilder.allowMethods(allowMethods);;
            return this;
        }

        @Override
        public Builder allowCredentials(boolean allowCredentials) {
            crossOriginConfigBuilder.allowCredentials(allowCredentials);;
            return this;
        }

        @Override
        public Builder maxAge(long maxAge) {
            crossOriginConfigBuilder.maxAge(maxAge);;
            return this;
        }
    }

    @Override
    public void accept(ServerRequest request, ServerResponse response) {
        RequestAdapter<ServerRequest> requestAdapter = new SERequestAdapter(request);
        ResponseAdapter<ServerResponse> responseAdapter = new SEResponseAdapter(response);

        Optional<ServerResponse> responseOpt = processRequest(
                crossOriginConfig,
                requestAdapter,
                responseAdapter);

        responseOpt.ifPresentOrElse(ServerResponse::send, () -> prepareCORSResponseAndContinue(requestAdapter, response));
    }

    private void prepareCORSResponseAndContinue(RequestAdapter<ServerRequest> requestAdapter, ServerResponse response) {
        prepareResponse(
                crossOriginConfig,
                requestAdapter,
                new SEResponseAdapter(response));

        requestAdapter.request().next();
    }

    static class SERequestAdapter implements RequestAdapter<ServerRequest> {

        private final ServerRequest request;

        SERequestAdapter(ServerRequest request) {
            this.request = request;
        }

        @Override
        public String path() {
            return request.path().toString();
        }

        @Override
        public Optional<String> firstHeader(String key) {
            return request.headers().first(key);
        }

        @Override
        public boolean headerContainsKey(String key) {
            return firstHeader(key).isPresent();
        }

        @Override
        public List<String> allHeaders(String key) {
            return request.headers().all(key);
        }

        @Override
        public String method() {
            return request.method().name();
        }

        @Override
        public ServerRequest request() {
            return request;
        }
    }

    static class SEResponseAdapter implements ResponseAdapter<ServerResponse> {

        private final ServerResponse serverResponse;

        SEResponseAdapter(ServerResponse serverResponse) {
            this.serverResponse = serverResponse;
        }

        @Override
        public ResponseAdapter<ServerResponse> header(String key, String value) {
            serverResponse.headers().add(key, value);
            return this;
        }

        @Override
        public ResponseAdapter<ServerResponse> header(String key, Object value) {
            serverResponse.headers().add(key, value.toString());
            return this;
        }

        @Override
        public ServerResponse forbidden(String message) {
            serverResponse.status(Http.ResponseStatus.create(Http.Status.FORBIDDEN_403.code(), message));
            return serverResponse;
        }

        @Override
        public ServerResponse ok() {
            serverResponse.status(Http.Status.OK_200.code());
            return serverResponse;
        }
    }
}
