/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;

/**
 * A Helidon service and handler implementation that implements CORS, for both the application and for built-in Helidon
 * services (such as OpenAPI and metrics).
 * <p>
 *     The caller can set up the {@code CorsSupportBase} in a combination of these ways:
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
 *     If none of these sources is used, the {@code CorsSupportBase} applies defaults as described for
 *     {@link CrossOriginConfig}.
 * </p>
 *
 * @param <Q> request type wrapped by request adapter
 * @param <R> response type wrapped by response adapter
 * @param <T> concrete subclass of {@code CorsSupportBase}
 * @param <B> builder for concrete type {@code <T>}
 */
public abstract class CorsSupportBase<Q, R, T extends CorsSupportBase<Q, R, T, B>,
        B extends CorsSupportBase.Builder<Q, R, T, B>> {

    private static final Logger LOGGER = Logger.getLogger(CorsSupportBase.class.getName());

    private final String name;
    private final CorsSupportHelper<Q, R> helper;

    /**
     * Constructor.
     * @param builder builder to construct from
     */
    protected CorsSupportBase(Builder<Q, R, T, B> builder) {
        name = builder.name;
        builder.helperBuilder.name(builder.name);
        if (builder.requestDefaultBehaviorIfNone) {
            builder.helperBuilder.requestDefaultBehaviorIfNone();
        }
        helper = builder.helperBuilder.build();
    }

    /**
     * <em>Not for developer use.</em> Submits a request adapter and response adapter for CORS processing.
     *
     * @param requestAdapter wrapper around the request
     * @param responseAdapter wrapper around the response
     * @return Optional of the response type U; present if the response should be returned, empty if request processing should
     * continue
     */
    protected Optional<R> processRequest(RequestAdapter<Q> requestAdapter, ResponseAdapter<R> responseAdapter) {
        return helper.processRequest(requestAdapter, responseAdapter);
    }

    /**
     * <em>Not for developer user.</em> Gets a response ready to participate in the CORS protocol.
     *
     * @param requestAdapter wrapper around the request
     * @param responseAdapter wrapper around the reseponse
     */
    protected void prepareResponse(RequestAdapter<Q> requestAdapter, ResponseAdapter<R> responseAdapter) {
        helper.prepareResponse(requestAdapter, responseAdapter);
    }

    /**
     * Get helper.
     * @return helper
     */
    protected CorsSupportHelper<Q, R> helper() {
        return helper;
    }

    /**
     * Get partial description.
     * @return description
     */
    protected String describe() {
        // Partial toString implementation for use by subclasses
        return helper.toString();
    }

    /**
     * Get name.
     * @return name
     */
    protected String name() {
        return name;
    }

    /**
     * Builder for {@code CorsSupportBase} instances.
     *
     * @param <Q> request type wrapped by request adapter
     * @param <R> response type wrapped by response adapter
     * @param <T> specific subtype of {@code CorsSupportBase} the builder creates
     * @param <B> type of the builder
     */
    public abstract static class Builder<Q, R, T extends CorsSupportBase<Q, R, T, B>, B extends Builder<Q, R, T, B>>
            implements io.helidon.common.Builder<CorsSupportBase<Q, R, T, B>>, CorsSetter<Builder<Q, R, T, B>> {

        private String name = "";
        private final CorsSupportHelper.Builder<Q, R> helperBuilder = CorsSupportHelper.builder();
        private final Aggregator.Builder aggregatorBuilder = helperBuilder.aggregatorBuilder();
        private boolean requestDefaultBehaviorIfNone = false;

        /**
         * Constructor.
         */
        protected Builder() {
        }

        /**
         * Return builder.
         * @return builder
         */
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
            reportUseOfMissingConfig(config);
            helperBuilder.config(config);
            return me();
        }

        /**
         * Merges mapped CORS config information. Typically, the app or component will retrieve the provided {@code Config}
         * instance from its own config.
         *
         * @param config the mapped CORS config information
         * @return the updated builder
         */
        public B mappedConfig(Config config) {
            reportUseOfMissingConfig(config);
            helperBuilder.mappedConfig(config);
            return me();
        }

        /**
         * Sets whether CORS support should be enabled or not.
         *
         * @param value whether to use CORS support
         * @return updated builder
         */
        public B enabled(boolean value) {
            aggregatorBuilder.enabled(value);
            return me();
        }

        /**
         * Adds cross origin information associated with a given path.
         *
         * @param path the path to which the cross origin information applies
         * @param crossOrigin the cross origin information
         * @return updated builder
         */
        public B addCrossOrigin(String path, CrossOriginConfig crossOrigin) {
            aggregatorBuilder.addCrossOrigin(path, crossOrigin);
            return me();
        }

        /**
         * Adds cross origin information associated with the default path.
         *
         * @param crossOrigin the cross origin information
         * @return updated builder
         */
        public B addCrossOrigin(CrossOriginConfig crossOrigin) {
            aggregatorBuilder.addPathlessCrossOrigin(crossOrigin);
            return me();
        }

        /**
         * Sets the name to be used for the CORS support instance.
         *
         * @param name name to use
         * @return updated builder
         */
        public B name(String name) {
            Objects.requireNonNull(name, "CorsSupport name is optional but cannot be null");
            this.name = name;
            helperBuilder.name(name);
            return me();
        }

        @Override
        public B allowOrigins(String... origins) {
            aggregatorBuilder.allowOrigins(origins);
            return me();
        }

        @Override
        public B allowHeaders(String... allowHeaders) {
            aggregatorBuilder.allowHeaders(allowHeaders);
            return me();
        }

        @Override
        public B exposeHeaders(String... exposeHeaders) {
            aggregatorBuilder.exposeHeaders(exposeHeaders);
            return me();
        }

        @Override
        public B allowMethods(String... allowMethods) {
            aggregatorBuilder.allowMethods(allowMethods);
            return me();
        }

        @Override
        public B allowCredentials(boolean allowCredentials) {
            aggregatorBuilder.allowCredentials(allowCredentials);
            return me();
        }

        @Override
        public B maxAgeSeconds(long maxAgeSeconds) {
            aggregatorBuilder.maxAgeSeconds(maxAgeSeconds);
            return me();
        }

        /**
         * <em>Not for developer use.</em> Sets a back-up way to provide a {@code CrossOriginConfig} instance if, during
         * look-up for a given request, none is found from the aggregator.
         *
         * @param secondaryLookupSupplier supplier of a CrossOriginConfig
         * @return updated builder
         */
        protected Builder<Q, R, T, B> secondaryLookupSupplier(Supplier<Optional<CrossOriginConfig>> secondaryLookupSupplier) {
            helperBuilder.secondaryLookupSupplier(secondaryLookupSupplier);
            return this;
        }

        /**
         * Enables requestDefaultBehaviorIfNone.
         * @return updated builder
         */
        protected Builder requestDefaultBehaviorIfNone() {
            requestDefaultBehaviorIfNone = true;
            return this;
        }

        private void reportUseOfMissingConfig(Config config) {
            if (!config.exists()) {
                LOGGER.log(Level.INFO,
                        String.format(
                                "Attempt to load %s using empty config with key '%s'; continuing with default CORS information",
                                getClass().getSuperclass().getSimpleName(), config.key().toString()));
            }
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

        /**
         * Returns the status of the response.
         *
         * @return HTTP status code.
         */
        int status();
    }
}
