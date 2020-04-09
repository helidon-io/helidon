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
 */

package io.helidon.microprofile.cors;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static io.helidon.webserver.cors.CrossOriginConfig.DEFAULT_AGE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * CrossOrigin annotation.
 */
@Target(METHOD)
@Retention(RUNTIME)
@Documented
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
    long maxAge() default DEFAULT_AGE;
}
