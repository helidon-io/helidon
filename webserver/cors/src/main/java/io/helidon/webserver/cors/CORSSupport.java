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
package io.helidon.webserver.cors;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;

import io.helidon.config.Config;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.cors.internal.CrossOriginHelper.RequestAdapter;
import io.helidon.webserver.cors.internal.CrossOriginHelper.ResponseAdapter;

import static io.helidon.webserver.cors.internal.CrossOriginHelper.prepareResponse;
import static io.helidon.webserver.cors.internal.CrossOriginHelper.processRequest;

/**
 * A Helidon service and handler implementation that implements CORS, for both the application and for built-in Helidon
 * services (such as OpenAPI and metrics).
 * <p>
 *     The caller can set up the {@code CORSSupport} in a combination of these ways:
 * </p>
 *     <ul>
 *         <li>from the {@value CORSSupport#CORS_CONFIG_KEY} node in the application's default config,</li>
 *         <li>from a {@link Config} node supplied programmatically,</li>
 *         <li>from one or more {@link CrossOriginConfig} objects supplied programmatically, each associated with a path to which
 *         it applies, and</li>
 *         <li>by setting individual CORS-related attributes on the {@link Builder} (which affects the CORS behavior for the
 *         "/" path).</li>
 *     </ul>
 * <p>
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

    /**
     * Key for the node within the CORS config indicating whether CORS support is enabled.
     */
    public static final String CORS_ENABLED_CONFIG_KEY = "enabled";

    /**
     * Key for the node within the CORS config that contains the list of path information.
     */
    public static final String CORS_PATHS_CONFIG_KEY = "paths";


    private final Map<String, CrossOriginConfig> crossOriginConfigs;
    private final boolean isEnabled;

    private CORSSupport(Builder builder) {
        crossOriginConfigs = builder.crossOriginConfigs();
        isEnabled = builder.isEnabled;
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

    /**
     * Trim leading or trailing slashes of a path.
     *
     * @param path The path.
     * @return Normalized path.
     */
    public static String normalize(String path) {
        int length = path.length();
        int beginIndex = path.charAt(0) == '/' ? 1 : 0;
        int endIndex = path.charAt(length - 1) == '/' ? length - 1 : length;
        return (endIndex <= beginIndex) ? "" : path.substring(beginIndex, endIndex);
    }

    /**
     * Parse list header value as a set.
     *
     * @param header Header value as a list.
     * @return Set of header values.
     */
    public static Set<String> parseHeader(String header) {
        if (header == null) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        StringTokenizer tokenizer = new StringTokenizer(header, ",");
        while (tokenizer.hasMoreTokens()) {
            String value = tokenizer.nextToken().trim();
            if (value.length() > 0) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * Parse a list of list of headers as a set.
     *
     * @param headers Header value as a list, each a potential list.
     * @return Set of header values.
     */
    public static Set<String> parseHeader(List<String> headers) {
        if (headers == null) {
            return Collections.emptySet();
        }
        return parseHeader(headers.stream().reduce("", (a, b) -> a + "," + b));
    }

    @Override
    public void update(Routing.Rules rules) {
        if (isEnabled && !crossOriginConfigs.isEmpty()) {
            rules.any(this::accept);
        }
    }

    @Override
    public void accept(ServerRequest request, ServerResponse response) {
        if (!isEnabled || crossOriginConfigs.isEmpty()) {
            request.next();
            return;
        }
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

        private final Map<String, CrossOriginConfig> crossOriginConfigsAssembledFromConfigs = new HashMap<>();

        private Optional<CrossOriginConfig.Builder> crossOriginConfigBuilderOpt = Optional.empty();

        private boolean isEnabled = true;

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
            Config pathsConfig = config.get(CORS_PATHS_CONFIG_KEY);
            if (pathsConfig.exists()) {
                crossOriginConfigsAssembledFromConfigs
                        .putAll(pathsConfig.as(new CrossOriginConfig.CrossOriginConfigMapper()).get());
            }
            Config enabledConfig = config.get(CORS_ENABLED_CONFIG_KEY);
            if (enabledConfig.exists()) {
                isEnabled = enabledConfig.asBoolean().get();
            }
            return this;
        }

        /**
         * Initializes the builder's CORS config from the {@value CORSSupport#CORS_CONFIG_KEY} node from the default
         * application config.
         *
         * @return the updated builder
         */
        public Builder config() {
            config(Config.create().get(CORS_CONFIG_KEY));
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
            result.putAll(crossOriginConfigsAssembledFromConfigs);
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
