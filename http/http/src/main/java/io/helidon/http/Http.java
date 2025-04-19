/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.service.registry.Service;

/**
 * HTTP endpoint annotations.
 * <p>
 * In previous versions of Helidon, this class contained the following types:
 * <ul>
 * <li>{@link io.helidon.http.Method}</li>
 * <li>{@link io.helidon.http.Status}</li>
 * <li>{@link io.helidon.http.HeaderName}</li>
 * <li>{@link io.helidon.http.HeaderNames}</li>
 * <li>{@link io.helidon.http.Header}</li>
 * <li>{@link io.helidon.http.HeaderWriteable}</li>
 * <li>{@link io.helidon.http.HeaderValues}</li>
 * <li>{@link io.helidon.http.DateTime}</li>
 * </ul>
 */
public final class Http {
    private Http() {
    }

    /**
     * Path of an endpoint, or sub-path of a method.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    public @interface Path {
        /**
         * Path to use, defaults to {@code /}.
         *
         * @return path to use
         */
        String value() default "/";
    }

    /**
     * HTTP Method. Can be used as a meta annotation.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Service.EntryPoint
    public @interface HttpMethod {
        /**
         * Text of the HTTP method.
         *
         * @return method
         */
        String value();
    }

    /**
     * Inject entity into a method parameter.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Service.Qualifier
    public @interface Entity {
    }

    /**
     * Inject header into a method parameter.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Service.Qualifier
    public @interface HeaderParam {
        /**
         * Name of the header.
         *
         * @return name of the header
         */
        String value();
    }

    /**
     * Inject query parameter into a method parameter.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Service.Qualifier
    public @interface QueryParam {
        /**
         * Name of the query parameter.
         *
         * @return name of the query parameter
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
    @Service.Qualifier
    public @interface PathParam {
        /**
         * Name of the parameter.
         *
         * @return name of the path parameter
         */
        String value();
    }

    /**
     * GET method of an HTTP endpoint.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @HttpMethod(Method.GET_NAME)
    public @interface GET {
    }

    /**
     * POST method of an HTTP endpoint.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @HttpMethod(Method.POST_NAME)
    public @interface POST {
    }

    /**
     * PUT method of an HTTP endpoint.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @HttpMethod(Method.PUT_NAME)
    public @interface PUT {
    }

    /**
     * DELETE method of an HTTP endpoint.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @HttpMethod(Method.DELETE_NAME)
    public @interface DELETE {
    }

    /**
     * HEAD method of an HTTP endpoint.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @HttpMethod(Method.HEAD_NAME)
    public @interface HEAD {
    }

    /**
     * PATCH method of an HTTP endpoint.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @HttpMethod(Method.PATCH_NAME)
    public @interface PATCH {
    }

    /**
     * What media type(s) this method produces.
     * <p>
     * If the method may produce more than one type, the response headers must be crafted by hand. If it produces
     * exactly one type, that type will be set by Helidon on response.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    @Documented
    public @interface Produces {
        /**
         * Media types produced by this method.
         *
         * @return produced media types, such as {@code application/json}
         */
        String[] value();
    }

    /**
     * What media type(s) this method can consume.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    public @interface Consumes {
        /**
         * Media types acceptable by this method.
         *
         * @return consumed media types, such as {@code application/json}
         */
        String[] value();
    }
}
