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

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.cors.internal.CrossOriginConfigAggregator;
import io.helidon.webserver.cors.internal.CrossOriginHelper;
import io.helidon.webserver.cors.internal.CrossOriginHelper.RequestAdapter;
import io.helidon.webserver.cors.internal.CrossOriginHelper.ResponseAdapter;
import io.helidon.webserver.cors.internal.Setter;

/**
 * A Helidon service and handler implementation that implements CORS, for both the application and for built-in Helidon
 * services (such as OpenAPI and metrics).
 * <p>
 *     The caller can set up the {@code CORSSupport} in a combination of these ways:
 * </p>
 *     <ul>
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

    private final CrossOriginHelper helper;

    private CORSSupport(Builder builder) {
        CrossOriginHelper.Builder helperBuilder = CrossOriginHelper.builder().aggregator(builder.aggregator);
        helper = helperBuilder.build();
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
    public static CORSSupport create(Config config) {
        return builder().config(config).build();
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
     * Creates a {@code Builder} initialized with the CORS information from the specified configuration node.
     *
     * @param config node containing CORS information
     * @return builder initialized with the CORS set-up from the config
     */
    public static Builder builder(Config config) {
        return builder().config(config);
    }

    @Override
    public void update(Routing.Rules rules) {
        if (helper.isActive()) {
            rules.any(this);
        }
    }

    @Override
    public void accept(ServerRequest request, ServerResponse response) {
        if (!helper.isActive()) {
            request.next();
            return;
        }
        RequestAdapter<ServerRequest> requestAdapter = new SERequestAdapter(request);
        ResponseAdapter<ServerResponse> responseAdapter = new SEResponseAdapter(response);

        Optional<ServerResponse> responseOpt = helper.processRequest(requestAdapter, responseAdapter);

        responseOpt.ifPresentOrElse(ServerResponse::send, () -> prepareCORSResponseAndContinue(requestAdapter, responseAdapter));
    }

    private void prepareCORSResponseAndContinue(RequestAdapter<ServerRequest> requestAdapter,
            ResponseAdapter<ServerResponse> responseAdapter) {
        helper.prepareResponse(requestAdapter, responseAdapter);

        requestAdapter.request().next();
    }

    /**
     * Builder for {@code CORSSupport} instances.
     */
    public static class Builder implements io.helidon.common.Builder<CORSSupport>, Setter<Builder> {

        private final CrossOriginConfigAggregator aggregator = CrossOriginConfigAggregator.create();

        Builder() {
        }

        @Override
        public CORSSupport build() {
            return new CORSSupport(this);
        }

        /**
         * Merges CORS config information. Typically, the app or component will retrieve the provided {@code Config} instance
         * from its own config.
         *
         * @param config the CORS config
         * @return the updated builder
         */
        public Builder config(Config config) {
            aggregator.config(config);
            return this;
        }

        /**
         * Sets whether CORS support should be enabled or not.
         *
         * @param value whether to use CORS support
         * @return updated builder
         */
        public Builder enabled(boolean value) {
            aggregator.enabled(value);
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
            aggregator.addCrossOrigin(path, crossOrigin);
            return this;
        }

        @Override
        public Builder allowOrigins(String... origins) {
            aggregator.allowOrigins(origins);
            return this;
        }

        @Override
        public Builder allowHeaders(String... allowHeaders) {
            aggregator.allowHeaders(allowHeaders);
            return this;
        }

        @Override
        public Builder exposeHeaders(String... exposeHeaders) {
            aggregator.exposeHeaders(exposeHeaders);
            return this;
        }

        @Override
        public Builder allowMethods(String... allowMethods) {
            aggregator.allowMethods(allowMethods);
            return this;
        }

        @Override
        public Builder allowCredentials(boolean allowCredentials) {
            aggregator.allowCredentials(allowCredentials);
            return this;
        }

        @Override
        public Builder maxAge(long maxAge) {
            aggregator.maxAge(maxAge);
            return this;
        }
    }
}
