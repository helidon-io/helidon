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

import io.helidon.builder.api.Option;
import io.helidon.service.registry.Service;

/**
 * APIs to define a declarative server endpoint.
 *
 * @deprecated this API is part of incubating features of Helidon. This API may change including backward incompatible changes
 *         and full removal. We welcome feedback for incubating features.
 */
@Deprecated
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
         * Headers to add to request.
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
         * A named producer function. A named service implementing {@link io.helidon.http.Http.HeaderFunction}
         * must exist in the registry that will be used to compute the header.
         *
         * @return name of a header function service to get header value from
         */
        String function();
    }

    /**
     * Container for {@link io.helidon.webserver.http.RestServer.ComputedHeader} repeated annotation.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Documented
    public @interface ComputedHeaders {
        /**
         * Headers to add to request.
         *
         * @return headers
         */
        ComputedHeader[] value();
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
        @Option.Configured("server.listener")
        String value();
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
