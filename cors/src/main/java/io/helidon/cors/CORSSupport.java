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
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.cors.CrossOriginConfig.CrossOriginConfigMapper;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import static io.helidon.cors.CrossOriginHelperInternal.normalize;
import static io.helidon.cors.CrossOriginHelperInternal.prepareResponse;
import static io.helidon.cors.CrossOriginHelperInternal.processRequest;

/**
 * A Helidon service and handler implementation that implements CORS, for both the application and for built-in Helidon
 * services (such as OpenAPI and metrics).
 * <p>
 *     The caller can set up the {@code CORSSupport} in a combination of these ways:
 *     <ul>
 *         <li>from the {@value CORSSupport#CORS_CONFIG_KEY} node in the application's default config,</li>
 *         <li>from a {@link Config} node supplied programmatically,</li>
 *         <li>from one or more {@link CrossOriginConfig} objects supplied programmatically, each associated with a path to which
 *         it applies, and</li>
 *         <li>by setting individual CORS-related attributes on the {@link Builder} (which affects the CORS behavior for the
 *         "/" path).</li>
 *     </ul>
 *     See the {@link Builder#build} method for how the builder resolves conflicts among these sources.
 * </p>
 * <p>
 *     If none of these sources is used, the {@code CORSSupport} applies defaults as described for
 *     {@link CrossOriginConfig}.
 * </p>
 */
public class CORSSupport implements Service, Handler {

    /**
     * Key used for retrieving CORS-related configuration from application- or service-level configuration.
     */
    public static final String CORS_CONFIG_KEY = "cors";

    private final Map<String, CrossOriginConfig> crossOriginConfigs;

    private CORSSupport(Builder builder) {
        crossOriginConfigs = builder.crossOriginConfigs();
    }

    /**
     * Creates a {@code CORSSupport} which supports the default CORS set-up.
     *
     * @return the service
     */
    public static CORSSupport create() {
        return builder().build();
    }

    /**
     * Returns a {@code CORSSupport} set up using the supplied {@link Config} node.
     *
     * @param config the config node containing CORS information
     * @return the initialized service
     */
    public static CORSSupport fromConfig(Config config) {
        return builder().config(config).build();
    }

    /**
     * Creates a {@code CORSSupport} set up using the {@value CORSSupport#CORS_CONFIG_KEY} node in the
     * application's default config.
     *
     * @return the initialized service
     */
    public static CORSSupport fromConfig() {
        return fromConfig(Config.create().get(CORS_CONFIG_KEY));
    }

    /**
     * Creates a {@code Builder} for assembling a {@code CORSSupport}.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a {@code Builder} initialized with the CORS information from the specified configuration node. The config node
     * should contain the actual CORS settings, <em>not</em> a {@value CORS_CONFIG_KEY} node which contains them.
     *
     * @param config node containing CORS information
     * @return builder initialized with the CORS set-up from the config
     */
    public static Builder builder(Config config) {
        return builder().config(config);
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

        responseOpt.ifPresentOrElse(ServerResponse::send, () -> prepareCORSResponseAndContinue(requestAdapter, responseAdapter));
    }

    private void prepareCORSResponseAndContinue(RequestAdapter<ServerRequest> requestAdapter,
            ResponseAdapter<ServerResponse> responseAdapter) {
        prepareResponse(
                crossOriginConfigs,
                Optional::empty,
                requestAdapter,
                responseAdapter);

        requestAdapter.request().next();
    }

    /**
     * Builder for {@code CORSSupport} instances.
     */
    public static class Builder implements io.helidon.common.Builder<CORSSupport>, CrossOriginConfig.Setter<Builder> {

        private final Map<String, CrossOriginConfig> crossOriginConfigs = new HashMap<>();

        private Config corsConfig = Config.empty();
        private Optional<CrossOriginConfig.Builder> crossOriginConfigBuilderOpt = Optional.empty();

        @Override
        public CORSSupport build() {
            return new CORSSupport(this);
        }

        /**
         * Saves CORS config information derived from the {@code Config}. Typically, the app or component will retrieve the
         * provided {@code Config} instance from its own config using the key {@value CORSSupport#CORS_CONFIG_KEY}.
         *
         * @param config the CORS config
         * @return the updated builder
         */
        public Builder config(Config config) {
            this.corsConfig = config;
            return this;
        }

        /**
         * Initializes the builder's CORS config from the {@value CORSSupport#CORS_CONFIG_KEY} node from the default
         * application config.
         *
         * @return the updated builder
         */
        public Builder config() {
            corsConfig = Config.create().get(CORS_CONFIG_KEY);
            return this;
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

        @Override
        public Builder allowOrigins(String... origins) {
            crossOriginConfigBuilder().allowOrigins(origins);
            return this;
        }

        @Override
        public Builder allowHeaders(String... allowHeaders) {
            crossOriginConfigBuilder().allowHeaders(allowHeaders);
            return this;
        }

        @Override
        public Builder exposeHeaders(String... exposeHeaders) {
            crossOriginConfigBuilder().exposeHeaders(exposeHeaders);
            return this;
        }

        @Override
        public Builder allowMethods(String... allowMethods) {
            crossOriginConfigBuilder().allowMethods(allowMethods);
            return this;
        }

        @Override
        public Builder allowCredentials(boolean allowCredentials) {
            crossOriginConfigBuilder().allowCredentials(allowCredentials);
            return this;
        }

        @Override
        public Builder maxAge(long maxAge) {
            crossOriginConfigBuilder().maxAge(maxAge);
            return this;
        }

        /**
         * Returns the aggregation of CORS-related information supplied to the builder, constructed in this order (in case of
         * conflicts, later steps override earlier ones):
         * <ol>
         *     <li>from {@code CrossOriginConfig} instances added using {@link #addCrossOrigin(String, CrossOriginConfig)},</li>
         *     <li>from invocations of the setter methods from {@link CrossOriginConfig} to set behavior for the "/" path,</li>
         *     <li>from {@code Config} supplied using {@link #config(Config)}or inferred using {@link #config()}.</li>
         * </ol>
         *
         * @return map of CrossOriginConfig instances, each entry describing a path and its associated CORS set-up
         */
        Map<String, CrossOriginConfig> crossOriginConfigs() {
            final Map<String, CrossOriginConfig> result = new HashMap<>(crossOriginConfigs);
            crossOriginConfigBuilderOpt.ifPresent(opt -> result.put("/", opt.get()));
            result.putAll(corsConfig
                    .as(new CrossOriginConfigMapper())
                    .get());
            return result;
        }

        private CrossOriginConfig.Builder crossOriginConfigBuilder() {
            if (crossOriginConfigBuilderOpt.isEmpty()) {
                crossOriginConfigBuilderOpt = Optional.of(CrossOriginConfig.builder());
            }
            return crossOriginConfigBuilderOpt.get();
        }
    }

}
