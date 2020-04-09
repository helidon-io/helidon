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

/**
 * A Helidon service implementation that implements CORS for endpoints in the application or in built-in Helidon services such
 * as OpenAPI and metrics.
 * <p>
 *     The caller can set up the {@code CrossOriginService} in a combination of these ways:
 *     <ul>
 *         <li>from the {@value CrossOriginConfig#CORS_CONFIG_KEY} node in the application's default config,</li>
 *         <li>from a {@link Config} node supplied programmatically, and</li>
 *         <li>from one or more {@link CrossOriginConfig} objects supplied programmatically, each associated with a path to which
 *         it applies.</li>
 *     </ul>
 *     See the {@link Builder#build} method for how the builder resolves conflicts among these sources.
 * </p>
 * <p>
 *     If none of these sources is used, the {@code CrossOriginService} applies defaults as described for
 *     {@link CrossOriginConfig}.
 * </p>
 */
public class CrossOriginService implements Service {

    private final Map<String, CrossOriginConfig> crossOriginConfigs;

    private CrossOriginService(Builder builder) {
        crossOriginConfigs = builder.crossOriginConfigs();
    }

    /**
     * Creates a {@code CrossOriginService} which supports the default CORS set-up.
     *
     * @return the service
     */
    public static CrossOriginService create() {
        return builder().build();
    }

    /**
     * Returns a {@code CrossOriginService} set up using the supplied {@link Config} node.
     *
     * @param config the config node containing CORS information
     * @return the initialized service
     */
    public static CrossOriginService fromConfig(Config config) {
        return builder().config(config).build();
    }

    /**
     * Creates a {@code CrossOriginService} set up using the {@value CrossOriginConfig#CORS_CONFIG_KEY} node in the
     * application's default config.
     *
     * @return the initialized service
     */
    public static CrossOriginService fromConfig() {
        return fromConfig(Config.create().get(CORS_CONFIG_KEY));
    }

    /**
     * Creates a {@code Builder} for assembling a {@code CrossOriginService}.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Config config) {
        return builder().config(config);
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
     * Builder for {@code CrossOriginService} instances.
     */
    public static class Builder implements io.helidon.common.Builder<CrossOriginService> {

        private final Map<String, CrossOriginConfig> crossOriginConfigs = new HashMap<>();

        private Config corsConfig = Config.empty();
        private Optional<CrossOriginConfig.Builder> crossOriginConfigBuilder = Optional.empty(); // CrossOriginConfig.builder();

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
            this.corsConfig = config;
            return this;
        }

        /**
         * Initializes the builder's CORS config from the {@value CrossOriginConfig#CORS_CONFIG_KEY} node from the default
         * application config.
         *
         * @return the updated builder
         */
        public Builder config() {
            corsConfig = Config.create().get(CORS_CONFIG_KEY);
            return this;
        }

        /**
         * Returns the aggregation of CORS-related information supplied to the builder, constructed in this order (in case of
         * conflicts, last wins):
         * <ol>
         *     <li>from {@code Config} supplied using {@link #config(Config)}or inferred using {@link #config()}, then</li>
         *     <li>from {@code CrossOriginConfig} instances added using {@link #addCrossOrigin(String, CrossOriginConfig)}.</li>
         * </ol>
         *
         * @return list of CrossOriginConfig instances, each describing a path and its associated constraints or permissions
         */
        Map<String, CrossOriginConfig> crossOriginConfigs() {
            Map<String, CrossOriginConfig> result = corsConfig
                    .as(new CrossOriginConfigMapper())
                    .get();
            result.putAll(crossOriginConfigs);
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
            crossOriginConfigs.put(normalize(path), crossOrigin);
            return this;
        }
    }
}
