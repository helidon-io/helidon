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

import java.util.List;
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
 *     The caller can set up the {@code CorsSupport} in a combination of these ways:
 * </p>
 *     <ul>
 *         <li>from a {@link Config} node supplied programmatically,</li>
 *         <li>from one or more {@link CrossOriginConfig} objects supplied programmatically, each associated with a path to which
 *         it applies, and</li>
 *         <li>by setting individual CORS-related attributes on the {@link Builder} (which affects the CORS behavior for the
 *         {@value Aggregator#PATHLESS_KEY} path).</li>
 *     </ul>
 * <p>
 *     See the {@link Builder#build} method for how the builder resolves conflicts among these sources.
 * </p>
 * <p>
 *     If none of these sources is used, the {@code CorsSupport} applies defaults as described for
 *     {@link CrossOriginConfig}.
 * </p>
 *
 */
public abstract class CorsSupport implements Service, Handler {

    private final CorsSupportHelper helper;

    protected <T extends CorsSupport, B extends Builder<T, B>> CorsSupport(Builder<T, B> builder) {
        helper = builder.helperBuilder.build();
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
        RequestAdapter<ServerRequest> requestAdapter = new RequestAdapterSe(request);
        ResponseAdapter<ServerResponse> responseAdapter = new ResponseAdapterSe(response);

        Optional<ServerResponse> responseOpt = helper.processRequest(requestAdapter, responseAdapter);

        responseOpt.ifPresentOrElse(ServerResponse::send, () -> prepareCORSResponseAndContinue(requestAdapter, responseAdapter));
    }

    /**
     * <em>Not for developer use.</em> Submits a request adapter and response adapter for CORS processing.
     *
     * @param requestAdapter wrapper around the request
     * @param responseAdapter wrapper around the response
     * @param <T> type of the request wrapped by the adapter
     * @param <U> type of the response wrapped by the adapter
     * @return Optional of the response type U; present if the response should be returned, empty if request processing should
     * continue
     */
    protected <T, U> Optional<U> processRequest(RequestAdapter<T> requestAdapter, ResponseAdapter<U> responseAdapter) {
        return helper.processRequest(requestAdapter, responseAdapter);
    }

    /**
     * <em>Not for developer user.</em> Gets a response ready to participate in the CORS protocol.
     *
     * @param requestAdapter wrapper around the request
     * @param responseAdapter wrapper around the reseponse
     * @param <T> type of the request wrapped by the adapter
     * @param <U> type of the response wrapped by the adapter
     */
    protected <T, U> void prepareResponse(RequestAdapter<T> requestAdapter, ResponseAdapter<U> responseAdapter) {
        helper.prepareResponse(requestAdapter, responseAdapter);
    }

    private void prepareCORSResponseAndContinue(RequestAdapter<ServerRequest> requestAdapter,
            ResponseAdapter<ServerResponse> responseAdapter) {
        helper.prepareResponse(requestAdapter, responseAdapter);

        requestAdapter.request().next();
    }

    /**
     * Builder for {@code CorsSupport} instances.
     *
     * @param <T> specific subtype of {@code CorsSupport} the builder creates
     * @param <B> type of the builder
     */
    public abstract static class Builder<T extends CorsSupport, B extends Builder<T, B>> implements io.helidon.common.Builder<CorsSupport>,
            CorsSetter<Builder<T, B>> {

        private final CorsSupportHelper.Builder helperBuilder = CorsSupportHelper.builder();
        private final Aggregator aggregator = helperBuilder.aggregator();

        protected Builder() {
        }

        protected abstract B me();

        @Override
        public abstract T build();

        /**
         * Merges CORS config information. Typically, the app or component will retrieve the provided {@code Config} instance
         * from its own config.
         *
         * @param config the CORS config
         * @return the updated builder
         */
        public B config(Config config) {
            aggregator.mappedConfig(config);
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

        /**
         * Adds cross origin information associated with the default path.
         *
         * @param crossOrigin the cross origin information
         * @return updated builder
         */
        public B addCrossOrigin(CrossOriginConfig crossOrigin) {
            aggregator.addPathlessCrossOrigin(crossOrigin);
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

        /**
         * <em>Not for developer use.</em> Sets a back-up way to provide a {@code CrossOriginConfig} instance if, during
         * look-up for a given request, none is found from the aggregator.
         *
         * @param secondaryLookupSupplier supplier of a CrossOriginConfig
         * @return updated builder
         */
        protected Builder<T, B> secondaryLookupSupplier(Supplier<Optional<CrossOriginConfig>> secondaryLookupSupplier) {
            helperBuilder.secondaryLookupSupplier(secondaryLookupSupplier);
            return this;
        }
    }

    /**
     * <em>Not for use by developers.</em>
     *
     * Minimal abstraction of an HTTP request.
     *
     * @param <T> type of the request wrapped by the adapter
     */
    protected interface RequestAdapter<T> {

        /**
         *
         * @return possibly unnormalized path from the request
         */
        String path();

        /**
         * Retrieves the first value for the specified header as a String.
         *
         * @param key header name to retrieve
         * @return the first header value for the key
         */
        Optional<String> firstHeader(String key);

        /**
         * Reports whether the specified header exists.
         *
         * @param key header name to check for
         * @return whether the header exists among the request's headers
         */
        boolean headerContainsKey(String key);

        /**
         * Retrieves all header values for a given key as Strings.
         *
         * @param key header name to retrieve
         * @return header values for the header; empty list if none
         */
        List<String> allHeaders(String key);

        /**
         * Reports the method name for the request.
         *
         * @return the method name
         */
        String method();

        /**
         * Processes the next handler/filter/request processor in the chain.
         */
        void next();

        /**
         * Returns the request this adapter wraps.
         *
         * @return the request
         */
        T request();
    }

    /**
     * <em>Not for use by developers.</em>
     *
     * Minimal abstraction of an HTTP response.
     *
     * <p>
     * Note to implementers: In some use cases, the CORS support code will invoke the {@code header} methods but not {@code ok}
     * or {@code forbidden}. See to it that header values set on the adapter via the {@code header} methods are propagated to the
     * actual response.
     * </p>
     *
     * @param <T> the type of the response wrapped by the adapter
     */
    protected interface ResponseAdapter<T> {

        /**
         * Arranges to add the specified header and value to the eventual response.
         *
         * @param key header name to add
         * @param value header value to add
         * @return the adapter
         */
        ResponseAdapter<T> header(String key, String value);

        /**
         * Arranges to add the specified header and value to the eventual response.
         *
         * @param key header name to add
         * @param value header value to add
         * @return the adapter
         */
        ResponseAdapter<T> header(String key, Object value);

        /**
         * Returns a response with the forbidden status and the specified error message, without any headers assigned
         * using the {@code header} methods.
         *
         * @param message error message to use in setting the response status
         * @return the factory
         */
        T forbidden(String message);

        /**
         * Returns a response with only the headers that were set on this adapter and the status set to OK.
         *
         * @return response instance
         */
        T ok();
    }
}
