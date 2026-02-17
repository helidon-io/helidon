/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

package io.helidon.microprofile.cors;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * CrossOrigin annotation.
 *
 * @deprecated use annotations from {@link io.helidon.webserver.cors.Cors} to configure CORS; each method in this annotation
 * is equivalent to an annotation class in the referenced class. I.e. {@link #value()} is replaced with
 * {@link io.helidon.webserver.cors.Cors.AllowOrigins}; this class will be removed from a future version of Helidon.
 */
@SuppressWarnings("removal")
@Target(METHOD)
@Retention(RUNTIME)
@Documented
@Deprecated(forRemoval = true, since = "4.4.0")
public @interface CrossOrigin {

    /**
     * A list of origins that are allowed such as {@code "http://foo.com"} or
     * {@code "*"} to allow all origins. Corresponds to header {@code
     * Access-Control-Allow-Origin}.
     *
     * @return Allowed origins.
     */
    String[] value() default {"*"};

    /**
     * A list of request headers that are allowed or {@code "*"} to allow all headers.
     * Corresponds to {@code Access-Control-Allow-Headers}.
     *
     * @return Allowed headers.
     */
    String[] allowHeaders() default {"*"};

    /**
     * A list of response headers allowed for clients other than the "standard"
     * ones. Corresponds to {@code Access-Control-Expose-Headers}.
     *
     * @return Exposed headers.
     */
    String[] exposeHeaders() default {};

    /**
     * A list of supported HTTP request methods. In response to pre-flight
     * requests. Corresponds to {@code Access-Control-Allow-Methods}.
     *
     * @return Allowed methods.
     */
    String[] allowMethods() default {"*"};

    /**
     * Whether the client can send cookies or credentials. Corresponds to {@code
     * Access-Control-Allow-Credentials}.
     *
     * @return Allowed credentials.
     */
    boolean allowCredentials() default false;

    /**
     * Pre-flight response duration in seconds. After time expires, a new pre-flight
     * request is required. Corresponds to {@code Access-Control-Max-Age}.
     *
     * @return Max age.
     */
    long maxAge() default io.helidon.cors.CrossOriginConfig.DEFAULT_AGE;
}
