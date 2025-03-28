/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

import io.helidon.http.HeaderName;
import io.helidon.service.registry.Service;

/**
 * APIs to define a declarative server endpoint.
 */
public final class RestServer {
    private RestServer() {
    }

    /**
     * Definition of a server endpoint.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    @Service.Singleton
    public @interface Endpoint {
    }

    /**
     * Definition of an outbound header (sent with every response).
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
     * Container for {@link io.helidon.webserver.http.RestServer.Header} repeated annotation.
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
     * Container for {@link io.helidon.webserver.http.RestServer.ComputedHeader} repeated annotation.
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
     * Header producer, to use with {@link io.helidon.webserver.http.RestServer.ComputedHeader#producerClass()}.
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
     * Listener socket assigned to this endpoint.
     * This only makes sense for server side, as it is binding endpoint to a server socket.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    public @interface Listener {
        /**
         * Name of a routing to bind this application/service to.
         *
         * @return name of a routing (or listener host/port) on WebServer
         */
        String value();

        /**
         * Set to true if the {@link #value()} MUST be configured.
         * <p>
         * The endpoint is bound to default listener if the {@link #value()} listener is not configured
         * on webserver, and this is set to {@code false}.
         *
         * @return {@code true} to enforce existence of the named routing
         */
        boolean required() default false;
    }

    /**
     * Status that should be returned. Only use when not setting it explicitly.
     * If an exception is thrown from the method, status is determined based on
     * error handling.
     * <p>
     * You can use {@code _CODE} constants from {@link io.helidon.http.Status} for
     * {@link #value()}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Inherited
    @Target(ElementType.METHOD)
    public @interface Status {
        /**
         * Status code to use.
         *
         * @return status code
         */
        int value();

        /**
         * If this is a non-standard status, add a custom reason to it.
         *
         * @return reason to use
         */
        String reason() default "";
    }
}
