/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
 */

package io.helidon.webclient.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;

/**
 * Annotations and APIs for type safe REST clients.
 * <p>
 * The type safe rest client is backed by Helidon {@link io.helidon.webclient.api.WebClient}.
 */
public final class RestClient {
    private RestClient() {
    }

    /**
     * Definition of the rest client API. An implementation of this interface (MUST be an interface) can
     * be injected when using Helidon Injection.
     * <p>
     * Configuration options for rest clients (prefixed by {@link #configKey()}:
     * <table class="config">
     *    <caption>REST Client Configuration Options</caption>
     *    <tr>
     *      <th>Key</th>
     *      <th>Default Value</th>
     *      <th>Description</th>
     *    </tr>
     *    <tr>
     *      <th>{@code uri}</th>
     *      <th>&nbsp;</th>
     *      <th>URI of this service</th>
     *    </tr>
     *    <tr>
     *      <th>{@code client}</th>
     *      <th>&nbsp;</th>
     *      <th>Client configuration, see
     *      <a href="https://helidon.io/docs/v4/config/io_helidon_webclient_api_WebClient">Proxy</a>
     *      (Base URI will always be overridden by the uri defined on this level)</th>
     *    </tr>
     * </table>
     *
     * In case key {@code client} node exists under the configuration node of this API, a new client will be created for this
     * instance (this always wins).
     * In case the {@link #clientName()} is defined, and an instance of that name is available in registry, it will be used
     * for this instance.
     * Then we use an unnamed client instance from the registry (if any).
     * The last resort is to create a new client that would be used for this API.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Endpoint {
        /**
         * The URI of this API. When left blank (default), the URI must be specified in configuration.
         * The configuration key is either {@link #configKey()}, and if that value is empty, the fully
         * qualified class name of the annotated interface, with suffix {@code .uri}, such as
         * {@code io.helidon.application.MyClient.uri}.
         * <p>
         * Note that {@link io.helidon.http.Http.Path} annotation on the API (or its super interface) is added to this value.
         *
         * @return endpoint URI of the generated client
         */
        String value() default "";

        /**
         * Configuration key base to use when looking up options for the generated client.
         *
         * @return configuration key prefix
         */
        String configKey() default "";

        /**
         * Name of a named instance of {@link io.helidon.webclient.api.WebClient} we attempt to get from registry.
         *
         * @return client name
         */
        String clientName() default "";
    }

    /**
     * Qualifier for injection points of generated typed REST clients.
     * This qualifier makes sure that if there are multiple implementations of the interface, this
     * injection point is satisfied by a rest client implementation.
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
    @Documented
    @Service.Qualifier
    public @interface Client {
    }

    /**
     * Definition of an outbound header (sent with every request).
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Repeatable(Headers.class)
    @Documented
    public @interface Header {
        /**
         * Name of the header, see {@link io.helidon.http.HeaderNames} constants with {@code _STRING}.
         *
         * @return header name
         */
        String name();

        /**
         * Value of the header.
         *
         * @return header value
         */
        String value();
    }

    /**
     * Container for {@link io.helidon.webclient.api.RestClient.Header} repeated annotation.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Documented
    public @interface Headers {
        /**
         * Headers to add o request.
         *
         * @return headers
         */
        Header[] value();
    }

    /**
     * Definition of an outbound header (sent with every request).
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Documented
    @Repeatable(ComputedHeaders.class)
    public @interface ComputedHeader {
        /**
         * Name of the header, see {@link io.helidon.http.HeaderNames} constants with {@code _STRING}.
         *
         * @return header name
         */
        String name();

        /**
         * A producer type, must be a {@link io.helidon.service.registry.ServiceRegistry} service.
         *
         * @return producer to get header value from
         */
        Class<? extends HeaderProducer> producerClass();
    }

    /**
     * Container for {@link io.helidon.webclient.api.RestClient.ComputedHeader} repeated annotation.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Documented
    public @interface ComputedHeaders {
        /**
         * Headers to add o request.
         *
         * @return headers
         */
        ComputedHeader[] value();
    }

    /**
     * Header producer, to use with {@link io.helidon.webclient.api.RestClient.ComputedHeader#producerClass()}.
     */
    @Service.Contract
    public interface HeaderProducer {
        /**
         * Produce an instance of a named header.
         *
         * @param name name to create
         * @return value for the header
         */
        Optional<String> produceHeader(HeaderName name);
    }

    /**
     * Error handler, must be a {@link io.helidon.service.registry.ServiceRegistry} service.
     * Handles a response, and (possibly) returns an exception to be thrown.
     */
    @Service.Contract
    public interface ErrorHandler {
        /**
         * By default, we expect error handlers to handle exceptional responses.
         *
         * @param requestUri     requested URI
         * @param requestHeaders request headers
         * @param status         status to check
         * @param headers        header to check
         * @return {@code true} in case this handler should be invoked
         */
        default boolean handles(String requestUri,
                                ClientRequestHeaders requestHeaders,
                                Status status,
                                ClientResponseHeaders headers) {
            return status.family() == Status.Family.CLIENT_ERROR
                    || status.family() == Status.Family.SERVER_ERROR;
        }

        /**
         * Handle a response.
         *
         * @param requestUri     requested URI
         * @param requestHeaders request headers
         * @param response       response we received from the server
         * @return possible exception to throw, if empty, the invocation will be considered a success, even if the
         *         status denoted an error
         */
        Optional<? extends RuntimeException> handleError(String requestUri,
                                                         ClientRequestHeaders requestHeaders,
                                                         HttpClientResponse response);

        /**
         * Handle a response.
         *
         * @param requestUri     requested URI
         * @param requestHeaders request headers
         * @param typedResponse  response we received from the server
         * @param type           entity class (type of the typed response)
         * @return possible exception to throw, if empty, the invocation will be considered a success, even if the
         *         status denoted an error
         */
        Optional<? extends RuntimeException> handleError(String requestUri,
                                                         ClientRequestHeaders requestHeaders,
                                                         ClientResponseTyped<?> typedResponse,
                                                         Class<?> type);
    }

    /**
     * Error handling is used by the typed REST client to error handle responses. Default implementation is part of
     * Helidon, and a custom implementation is not required, unless you want to handle responses differently.
     */
    @Service.Contract
    public interface ErrorHandling {
        /**
         * Handle untyped client response.
         *
         * @param uri requested URI
         * @param requestHeaders request headers
         * @param response response
         */
        void handle(String uri, ClientRequestHeaders requestHeaders, HttpClientResponse response);

        /**
         * Handle an exception for a typed response.
         *
         * @param uri invoked URI
         * @param requestHeaders headers of the request
         * @param response response
         * @param type type of the response
         */
        void handle(String uri, ClientRequestHeaders requestHeaders, ClientResponseTyped<?> response, Class<?> type);
    }

}
