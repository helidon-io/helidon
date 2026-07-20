/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.Api;
import io.helidon.service.registry.Service;

/**
 * APIs to define a declarative server endpoint.
 * <p>
 * Declarative response metadata, such as {@link Status}, {@link Header}, {@link ComputedHeader}, and
 * {@link io.helidon.http.Http.Produces}, is configured on {@link ServerResponse} by generated routing code.
 * For endpoint methods that accept {@link ServerResponse}, generated code configures this metadata before invoking the
 * method, so it is available to {@link ServerResponse#outputStream()} and other response-handling APIs.
 * For endpoint methods that do not accept {@link ServerResponse}, generated code configures this metadata after the
 * method returns and before the generated response is sent.
 * <p>
 * Declarative response metadata has the same lifecycle as metadata configured imperatively on {@link ServerResponse}:
 * it remains on the current response unless later route or error handling changes it.
 */
@Api.Incubating
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
     * Definition of outbound response header metadata.
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
         * Headers to add to response.
         *
         * @return headers
         */
        Header[] value();
    }

    /**
     * Definition of computed outbound response header metadata.
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
         * Headers to add to response.
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
        String value();
    }

    /**
     * HTTP status to configure for a declarative server response.
     * <p>
     * For endpoint methods that accept {@link ServerResponse}, generated code configures this status before invoking
     * the method. The configured status has the same lifecycle as status set imperatively through
     * {@link ServerResponse#status(io.helidon.http.Status)}: it remains on the response unless later route or error
     * handling changes it.
     * <p>
     * For methods that do not accept {@link ServerResponse}, generated code configures this status after the method
     * returns and before the generated response is sent.
     * <p>
     * You can use {@code _CODE} constants from {@link io.helidon.http.Status} for {@link #value()}.
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
