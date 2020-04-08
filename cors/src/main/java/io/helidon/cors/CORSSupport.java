/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.cors.CrossOriginConfig.CrossOriginConfigMapper;
import io.helidon.cors.CrossOriginHelperInternal.RequestAdapter;
import io.helidon.cors.CrossOriginHelperInternal.ResponseAdapter;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import static io.helidon.cors.CrossOriginConfig.CORS_CONFIG_KEY;
import static io.helidon.cors.CrossOriginHelperInternal.normalize;
import static io.helidon.cors.CrossOriginHelperInternal.prepareResponse;
import static io.helidon.cors.CrossOriginHelperInternal.processRequest;

/**
 * Provides support for CORS in an application or a built-in Helidon service.
 * <p>
 * The application uses the {@link Builder} to set CORS-related values.
 * </p>
 */
public class CORSSupport implements Service, Handler {

    /**
     * Creates a {@code CORSSupport} instance based on only the {@value CrossOriginConfig#CORS_CONFIG_KEY} config node in the
     * default configuration.
     *
     * @return new {@code CORSSupport} instance set up default configuration
     */
    public static CORSSupport create() {
        return builder().build();
    }

    /**
     * Creates a {@code CORSSupport} instance based on only the specified configuration.
     *
     * @param config the config node containing CORS-related info; typically obtained by retrieving config using the
     *               "{@value CrossOriginConfig#CORS_CONFIG_KEY}" key from some containing configuration source
     * @return configured {@code CORSSupport} instance
     */
    public static CORSSupport create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Returns a builder for constructing a {@code CORSSupport} instance.
     *
     * @return the new builder
     */
    public static CORSSupport.Builder builder() {
        return new Builder();
    }

    private static final Logger LOGGER = Logger.getLogger(CORSSupport.class.getName());

    static {
        HelidonFeatures.register(HelidonFlavor.SE, "CORS");
    }

    private final Map<String, CrossOriginConfig> crossOriginConfigs;

    private CORSSupport(Builder builder) {
        crossOriginConfigs = builder.crossOriginConfigs();
    }

    @Override
    public void update(Routing.Rules rules) {
        if (!crossOriginConfigs.isEmpty()) {
            rules.any(this::accept);
        }
    }

    @Override
    public void accept(ServerRequest request, ServerResponse response) {
        RequestAdapter<ServerRequest> requestAdapter = new SERequestAdapter(request);
        ResponseAdapter<ServerResponse> responseAdapter = new SEResponseAdapter(response);

        Optional<ServerResponse> responseOpt = processRequest(crossOriginConfigs,
                Optional::empty,
                requestAdapter,
                responseAdapter);

        responseOpt.ifPresentOrElse(ServerResponse::send, () -> prepareCORSResponseAndContinue(requestAdapter, response));
    }

    private void prepareCORSResponseAndContinue(RequestAdapter<ServerRequest> requestAdapter, ServerResponse response) {
        prepareResponse(
                crossOriginConfigs,
                Optional::empty,
                requestAdapter,
                new SEResponseAdapter(response));

        requestAdapter.request().next();
    }

    /**
     * Builder for {@code CORSSupport} instances.
     */
    public static class Builder implements io.helidon.common.Builder<CORSSupport> {

        private Optional<Config> corsConfig = Optional.empty();
        private final Map<String, CrossOriginConfig> crossOrigins = new HashMap<>();

        @Override
        public CORSSupport build() {
            return new CORSSupport(this);
        }

        /**
         * Saves CORS config information derived from the {@code Config}. Typically, the app or component will retrieve the
         * provided {@code Config} instance from its own config using the key {@value CrossOriginConfig#CORS_CONFIG_KEY}.
         *
         * @param config the CORS config
         * @return the updated builder
         */
        public Builder config(Config config) {
            this.corsConfig = Optional.of(config);
            return this;
        }

        /**
         * Returns CORS-related information supplied to the builder. If no config was supplied to the builder, the builder uses
         * the {@value CrossOriginConfig#CORS_CONFIG_KEY} node, if any, from the application's config.
         *
         * @return list of CrossOriginConfig instances, each describing a path and its associated constraints or permissions
         */
        Map<String, CrossOriginConfig> crossOriginConfigs() {
            Map<String, CrossOriginConfig> result = corsConfig
                    .orElse(Config.create().get(CORS_CONFIG_KEY))
                    .as(new CrossOriginConfigMapper()).get();
            result.putAll(crossOrigins);
            return result;
        }

        /**
         * Adds cross origin information associated with a given path.
         *
         * @param path the path to which the cross origin information applies
         * @param crossOrigin the cross origin information
         * @return updated builder
         */
        public Builder addCrossOrigin(String path, CrossOriginConfig crossOrigin) {
            crossOrigins.put(normalize(path), crossOrigin);
            return this;
        }
    }

    private static class SERequestAdapter implements RequestAdapter<ServerRequest> {

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

    private static class SEResponseAdapter implements ResponseAdapter<ServerResponse> {

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
