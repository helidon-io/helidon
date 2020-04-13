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
import io.helidon.webserver.cors.internal.InternalCORSSupportBuilder;
import io.helidon.webserver.cors.internal.RequestAdapter;
import io.helidon.webserver.cors.internal.ResponseAdapter;

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

    private <B extends Builder<T, U, B>> CORSSupport(Builder<T, U, B> builder) {
        helper = builder.helperBuilder.build();
    }

    /**
     * Creates a {@code CORSSupport} which supports the default CORS set-up.
     *
     * @param <T> type of request wrapped by the request adapter
     * @param <U> type of response wrapped by the response adapter
     * @param <B> type of the builder
     * @return the service
     */
    public static <T, U, B extends Builder<T, U, B>> CORSSupport<T, U> create() {
        Builder<T, U, B> b = builder();
        return b.build();
    }

    /**
     * Returns a {@code CORSSupport} set up using the supplied {@link Config} node.
     *
     * @param config the config node containing CORS information
     * @param <T> type of request wrapped by the request adapter
     * @param <U> type of response wrapped by the response adapter
     * @param <B> type of the builder
     * @return the initialized service
     */
    public static <T, U, B extends Builder<T, U, B>> CORSSupport<T, U> create(Config config) {
        Builder<T, U, B> b = builder();
        return b.config(config).build();
    }

    /**
     * Creates a {@code Builder} for assembling a {@code CORSSupport}.
     *
     * @param <T> type of request wrapped by the request adapter
     * @param <U> type of response wrapped by the response adapter
     * @param <B> type of the builder
     * @return the builder
     */
    public static <T, U, B extends Builder<T, U, B>> Builder<T, U, B> builder() {
        return new Builder<>();
    }

    /**
     * Creates an internal builder - one that knows about the secondary cross-origin config supplier.
     *
     * @param <T> type of request wrapped by the request adapter
     * @param <U> type of response wrapped by the response adapter
     * @return the builder
     */
    public static <T, U> InternalCORSSupportBuilder<T, U> internalBuilder() {
        return InternalCORSSupportBuilder.create();
    }

    /**
     * Creates a {@code Builder} initialized with the CORS information from the specified configuration node.
     *
     * @param config node containing CORS information
     * @return builder initialized with the CORS set-up from the config
     * @param <T> type of request wrapped by the request adapter
     * @param <U> type of response wrapped by the response adapter
     * @param <B> type of the builder
     */
    public static <T, U, B extends Builder<T, U, B>> Builder<T, U, B> builder(Config config) {
        Builder<T, U, B> b = builder();
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
     *
     * @param <T> type of the request wrapped by the adapter
     * @param <U> type of the response wrapped by the adapter
     * @param <B> type of the builder
     */
    public static class Builder<T, U, B extends Builder<T, U, B>> implements io.helidon.common.Builder<CORSSupport<T, U>>,
            Setter<Builder<T, U, B>> {

        private final CrossOriginHelper.Builder helperBuilder = CrossOriginHelper.builder();
        private final CrossOriginConfigAggregator aggregator = helperBuilder.aggregator();

        protected Builder() {
        }

        @SuppressWarnings("unchecked")
        protected B me() {
            return (B) this;
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
        public B config(Config config) {
            aggregator.config(config);
            return me();
        }

        /**
         * Sets whether CORS support should be enabled or not.
         *
         * @param value whether to use CORS support
         * @return updated builder
         */
        public B enabled(boolean value) {
            aggregator.enabled(value);
            return me();
        }

        /**
         * Adds cross origin information associated with a given path.
         *
         * @param path the path to which the cross origin information applies
         * @param crossOrigin the cross origin information
         * @return updated builder
         */
        public B  addCrossOrigin(String path, CrossOriginConfig crossOrigin) {
            aggregator.addCrossOrigin(path, crossOrigin);
            return me();
        }

        @Override
        public B allowOrigins(String... origins) {
            aggregator.allowOrigins(origins);
            return me();
        }

        @Override
        public B allowHeaders(String... allowHeaders) {
            aggregator.allowHeaders(allowHeaders);
            return me();
        }

        @Override
        public B exposeHeaders(String... exposeHeaders) {
            aggregator.exposeHeaders(exposeHeaders);
            return me();
        }

        @Override
        public B allowMethods(String... allowMethods) {
            aggregator.allowMethods(allowMethods);
            return me();
        }

        @Override
        public B allowCredentials(boolean allowCredentials) {
            aggregator.allowCredentials(allowCredentials);
            return me();
        }

        @Override
        public B maxAge(long maxAge) {
            aggregator.maxAge(maxAge);
            return me();
        }

//        /**
//         * <em>Not for developer use.</em> Sets a back-up way to provide a {@code CrossOriginConfig} instance if, during
//         * look-up for a given request, none is found from the aggregator.
//         *
//         * @param secondaryLookupSupplier supplier of a CrossOriginConfig
//         * @return updated builder
//         */
//        public Builder<T, U> secondaryLookupSupplier(Supplier<Optional<CrossOriginConfig>> secondaryLookupSupplier) {
//            helperBuilder.secondaryLookupSupplier(secondaryLookupSupplier);
//            return this;
//        }

        protected CrossOriginHelper.Builder helperBuilder() {
            return helperBuilder;
        }
    }
}
