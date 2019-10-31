/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * CrossOrigin annotation.
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@Documented
public @interface CrossOrigin {

    /**
     * Header Origin.
     */
    String ORIGIN = "Origin";

    /**
     * Header Access-Control-Request-Method.
     */
    String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";

    /**
     * Header Access-Control-Request-Headers.
     */
    String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";

    /**
     * Header Access-Control-Allow-Origin.
     */
    String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";

    /**
     * Header Access-Control-Expose-Headers.
     */
    String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    /**
     * Header Access-Control-Max-Age.
     */
    String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";

    /**
     * Header Access-Control-Allow-Credentials.
     */
    String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";

    /**
     * Header Access-Control-Allow-Methods.
     */
    String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";

    /**
     * Header Access-Control-Allow-Headers.
     */
    String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";

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
    long maxAge() default 3600;
}
