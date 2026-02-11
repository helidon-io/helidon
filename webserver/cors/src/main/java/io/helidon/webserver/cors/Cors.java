/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.cors;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Container class for CORS related annotations and types.
 * <p>
 * Annotations on this type are expected to be placed on Helidon Declarative HTTP:
 * <ul>
 *     <li>Endpoint Class - this will add an options method handling for pre-flight checks for all
 *     sub-paths of the current endpoint</li>
 *     <li>Options method - this will only handle the path specified for the options method itself</li>
 * </ul>
 *
 * These annotations cannot be anywhere else and the compilation will fail if defined anywhere else.
 */
public final class Cors {
    /*
    Retention is runtime, as this is also used from Microprofile
     */

    /**
     * String used to allow all origins, headers, and methods.
     */
    public static final String ALLOW_ALL = "*";
    static final long DEFAULT_MAX_AGE = 3600;

    private Cors() {
    }

    /**
     * Enable support for CORS for an endpoint method (such as an OPTIONS handler) and
     * allow default CORS behavior (any origin, any header, any method, no expose headers,
     * do not allow credentials, and max age is set to 1 hour.
     * <p>
     * The method can be annotated with additional annotations from this type to restrict some aspects.
     * <p>
     * If any other annotation from this type is configured on a method, this annotation is implied.
     */
    public @interface Defaults {
    }

    /**
     * Allowed origins.
     */
    @Target({METHOD, TYPE})
    @Retention(RUNTIME)
    @Documented
    public @interface AllowedOrigins {
        /**
         * A list of origins that are allowed such as {@code "http://foo.com"} or
         * {@code "*"} to allow all origins. Corresponds to header {@code Access-Control-Allow-Origin}.
         *
         * @return allowed origins
         */
        String[] value();
    }

    /**
     * Allowed headers.
     */
    @Target({METHOD, TYPE})
    @Retention(RUNTIME)
    @Documented
    public @interface AllowedHeaders {
        /**
         * A list of request headers that are allowed or {@code "*"} to allow all headers.
         * Corresponds to {@code Access-Control-Allow-Headers}.
         *
         * @return allowed headers
         */
        String[] value();
    }

    /**
     * Expose headers.
     */
    @Target({METHOD, TYPE})
    @Retention(RUNTIME)
    @Documented
    public @interface ExposeHeaders {
        /**
         * A list of response headers allowed for clients other than the "standard"
         * ones. Corresponds to {@code Access-Control-Expose-Headers}.
         *
         * @return exposed headers
         */
        String[] value();
    }

    /**
     * Allowed methods.
     */
    @Target({METHOD, TYPE})
    @Retention(RUNTIME)
    @Documented
    public @interface AllowedMethods {
        /**
         * A list of supported HTTP request methods. In response to pre-flight
         * requests. Corresponds to {@code Access-Control-Allow-Methods}.
         *
         * @return allowed methods
         */
        String[] value();
    }

    /**
     * Allowed credentials.
     */
    @Target({METHOD, TYPE})
    @Retention(RUNTIME)
    @Documented
    public @interface AllowCredentials {
        /**
         * Whether the client can send cookies or credentials. Corresponds to {@code
         * Access-Control-Allow-Credentials}.
         *
         * @return allowed credentials
         */
        boolean value();
    }

    /**
     * Max age.
     */
    @Target({METHOD, TYPE})
    @Retention(RUNTIME)
    @Documented
    public @interface MaxAgeSeconds {
        /**
         * Pre-flight response duration. After time expires, a new pre-flight
         * request is required. Corresponds to {@code Access-Control-Max-Age}.
         * If not annotated, max age defaults to 1 hour.
         *
         * @return max age as a number of seconds
         */
        long value();
    }
}
