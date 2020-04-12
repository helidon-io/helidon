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
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

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
 *
 * @param <T> type wrapped by RequestAdapter
 * @param <U> type wrapped by ResponseAdapter
 *
 */
public class CORSSupport<T, U> implements Service, Handler {

    private final CrossOriginHelper helper;

    private CORSSupport(Builder<T, U> builder) {
        helper = builder.helperBuilder.build();
    }

    /**
     * Creates a {@code CORSSupport} which supports the default CORS set-up.
     *
     * @param <T> type of request wrapped by the request adapter
     * @param <U> type of response wrapped by the response adapter
     * @return the service
     */
    public static <T, U> CORSSupport<T, U> create() {
        Builder<T, U> b = builder();
        return b.build();
    }

    /**
     * Returns a {@code CORSSupport} set up using the supplied {@link Config} node.
     *
     * @param config the config node containing CORS information
     * @param <T> type of request wrapped by the request adapter
     * @param <U> type of response wrapped by the response adapter
     * @return the initialized service
     */
    public static <T, U> CORSSupport<T, U> create(Config config) {
        Builder<T, U> b = builder();
        return b.config(config).build();
    }

    /**
     * Creates a {@code Builder} for assembling a {@code CORSSupport}.
     *
     * @param <T> type of request wrapped by the request adapter
     * @param <U> type of response wrapped by the response adapter
     * @return the builder
     */
    public static <T, U> Builder<T, U> builder() {
        return new Builder<>();
    }

    /**
     * Creates a {@code Builder} initialized with the CORS information from the specified configuration node.
     *
     * @param config node containing CORS information
     * @return builder initialized with the CORS set-up from the config
     * @param <T> type of request wrapped by the request adapter
     * @param <U> type of response wrapped by the response adapter
     */
    public static <T, U> Builder<T, U> builder(Config config) {
        Builder<T, U> b = builder();
        return b.config(config);
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

    /**
     * <em>Not for developer use.</em> Submits a request adapter and response adapter for CORS processing.
     *
     * @param requestAdapter wrapper around the request
     * @param responseAdapter wrapper around the response
     * @return Optional of the response type U; present if the response should be returned, empty if request processing should
     * continue
     */
    public Optional<U> processRequest(RequestAdapter<T> requestAdapter, ResponseAdapter<U> responseAdapter) {
        return helper.processRequest(requestAdapter, responseAdapter);
    }

    /**
     * <em>Not for developer user.</em> Gets a response ready to participate in the CORS protocol.
     *
     * @param requestAdapter wrapper around the request
     * @param responseAdapter wrapper around the reseponse
     */
    public void prepareResponse(RequestAdapter<T> requestAdapter, ResponseAdapter<U> responseAdapter) {
        helper.prepareResponse(requestAdapter, responseAdapter);
    }

    private void prepareCORSResponseAndContinue(RequestAdapter<ServerRequest> requestAdapter,
            ResponseAdapter<ServerResponse> responseAdapter) {
        helper.prepareResponse(requestAdapter, responseAdapter);

        requestAdapter.request().next();
    }

    /**
     * Builder for {@code CORSSupport} instances.
     */
    public static class Builder<T, U> implements io.helidon.common.Builder<CORSSupport<T, U>>,
            Setter<Builder<T, U>> {

        private final CrossOriginHelper.Builder helperBuilder = CrossOriginHelper.builder();
        private final CrossOriginConfigAggregator aggregator = helperBuilder.aggregator();

        private Builder() {
        }

        @Override
        public CORSSupport<T, U> build() {
            return new CORSSupport<>(this);
        }

        /**
         * Merges CORS config information. Typically, the app or component will retrieve the provided {@code Config} instance
         * from its own config.
         *
         * @param config the CORS config
         * @return the updated builder
         */
        public Builder<T, U> config(Config config) {
            aggregator.config(config);
            return this;
        }

        /**
         * Sets whether CORS support should be enabled or not.
         *
         * @param value whether to use CORS support
         * @return updated builder
         */
        public Builder<T, U>  enabled(boolean value) {
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
        public Builder<T, U>  addCrossOrigin(String path, CrossOriginConfig crossOrigin) {
            aggregator.addCrossOrigin(path, crossOrigin);
            return this;
        }

        @Override
        public Builder<T, U>  allowOrigins(String... origins) {
            aggregator.allowOrigins(origins);
            return this;
        }

        @Override
        public Builder<T, U>  allowHeaders(String... allowHeaders) {
            aggregator.allowHeaders(allowHeaders);
            return this;
        }

        @Override
        public Builder<T, U>  exposeHeaders(String... exposeHeaders) {
            aggregator.exposeHeaders(exposeHeaders);
            return this;
        }

        @Override
        public Builder<T, U>  allowMethods(String... allowMethods) {
            aggregator.allowMethods(allowMethods);
            return this;
        }

        @Override
        public Builder<T, U>  allowCredentials(boolean allowCredentials) {
            aggregator.allowCredentials(allowCredentials);
            return this;
        }

        @Override
        public Builder<T, U>  maxAge(long maxAge) {
            aggregator.maxAge(maxAge);
            return this;
        }

        /**
         * <em>Not for developer use.</em> Sets a back-up way to provide a {@code CrossOriginConfig} instance if, during
         * look-up for a given request, none is found from the aggregator.
         *
         * @param secondaryLookupSupplier supplier of a CrossOriginConfig
         * @return updated builder
         */
        public Builder<T, U> secondaryLookupSupplier(Supplier<Optional<CrossOriginConfig>> secondaryLookupSupplier) {
            helperBuilder.secondaryLookupSupplier(secondaryLookupSupplier);
            return this;
        }
    }
}
