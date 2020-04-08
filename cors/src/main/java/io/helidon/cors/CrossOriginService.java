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

import io.helidon.config.Config;
import io.helidon.cors.CrossOriginConfig.CrossOriginConfigMapper;
import io.helidon.cors.CrossOriginHandler.SERequestAdapter;
import io.helidon.cors.CrossOriginHandler.SEResponseAdapter;
import io.helidon.cors.CrossOriginHelperInternal.RequestAdapter;
import io.helidon.cors.CrossOriginHelperInternal.ResponseAdapter;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.helidon.cors.CrossOriginConfig.CORS_CONFIG_KEY;
import static io.helidon.cors.CrossOriginHelperInternal.normalize;
import static io.helidon.cors.CrossOriginHelperInternal.prepareResponse;
import static io.helidon.cors.CrossOriginHelperInternal.processRequest;

public class CrossOriginService implements Service {

    private final Map<String, CrossOriginConfig> crossOriginConfigs;

    private CrossOriginService(Builder builder) {
        crossOriginConfigs = builder.crossOriginConfigs();
    }

    public static CrossOriginService create() {
        return builder().build();
    }

    public static CrossOriginService create(Config config) {
        return builder(config).build();
    }

    public static Builder builder() {
        return Builder.create();
    }

    public static Builder builder(Config config) {
        return Builder.create(config);
    }

    @Override
    public void update(Routing.Rules rules) {
        if (!crossOriginConfigs.isEmpty()) {
            rules.any(this::accept);
        }
    }

//    @Override
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

    private static String ensureLeadingSlash(String path) {
        return "/" + normalize(path);
    }

    /**
     * Builder for {@code CORSSupport} instances.
     */
    public static class Builder implements io.helidon.common.Builder<CrossOriginService> {

        private Optional<Config> corsConfig = Optional.empty();
        private final Map<String, CrossOriginConfig> crossOrigins = new HashMap<>();

        public static Builder create() {
            return create(Config.create().get(CORS_CONFIG_KEY));
        }

        public static Builder create(Config config) {
            return new Builder().config(config);
        }

        @Override
        public CrossOriginService build() {
            return new CrossOriginService(this);
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
}
