/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for Helidon Declarative HTTP types.
 * <p>
 * All types that used to be in this class are moved to top-level classes.
 *
 * @see io.helidon.http.Method
 * @see io.helidon.http.Status
 * @see io.helidon.http.HeaderName
 * @see io.helidon.http.HeaderNames
 * @see io.helidon.http.Header
 * @see io.helidon.http.HeaderWriteable
 * @see io.helidon.http.HeaderValues
 * @see io.helidon.http.DateTime
 */
public final class Http {
    private Http() {
    }

    /**
     * Path of an endpoint, or sub-path of a method.
     * Path can be overridden using configuration for a class annotation (not for method annotations).
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface Path {
        /**
         * Configuration key of the routing path, appended after the fully qualified class name (does not contain the leading dot).
         */
        String CONFIG_KEY_PATH = "routing-path.path";

        /**
         * Path to use, defaults to {@code /}.
         *
         * @return path to use
         */
        String value() default "/";
    }

    /**
     * Listener socket assigned to this endpoint.
     * This only makes sense for server side, as it is binding endpoint to a server socket.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface Listener {
        /**
         * Configuration key of the routing name, appended after the fully qualified class name (does not contain the leading dot).
         */
        String CONFIG_KEY_NAME = "routing-name.name";
        /**
         * Configuration key of the routing name required flag,
         * appended after the fully qualified class name (does not contain the leading dot).
         */
        String CONFIG_KEY_REQUIRED = "routing-name.required";

        /**
         * Name of a routing to bind this application/service to.
         * @return name of a routing (or listener host/port) on WebServer
         */
        String value();

        /**
         * Set to true if the {@link #value()} MUST be configured.
         *
         * @return {@code true} to enforce existence of the named routing
         */
        boolean required() default false;
    }

    /**
     * Inject entity into a method parameter.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Entity {
    }

    /**
     * GET method of an HTTP endpoint.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @HttpMethod("GET")
    public @interface GET {

    }

    /**
     * Inject header into a method parameter.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface HeaderParam {
        /**
         * Name of the header.
         * @return name of the header
         */
        String value();
    }

    /**
     * HTTP Method. Can be used as a meta annotation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    public @interface HttpMethod {
        /**
         * Text of the HTTP method.
         * @return method
         */
        String value();
    }

    /**
     * Inject path parameter into a method parameter.
     * Path parameters are obtained from the path template of the routing method.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface PathParam {
        /**
         * Name of the parameter.
         * @return name of the path parameter
         */
        String value();
    }

    /**
     * POST method of an HTTP endpoint.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @HttpMethod("POST")
    public @interface POST {

    }

    /**
     * Inject query parameter into a method parameter.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface QueryParam {
        /**
         * Configured value meaning the annotation has a default value.
         */
        String NO_DEFAULT_VALUE = "io.helidon.http.HttpEndpoint.QueryParam_NO_DEFAULT_VALUE";

        /**
         * Name of the parameter.
         * @return name of the query parameter
         */
        String value();

        /**
         * Default value to use if the query parameter is not available.
         *
         * @return default value, if none specified, the query parameter is considered required
         */
        String defaultValue() default NO_DEFAULT_VALUE;
    }
}
