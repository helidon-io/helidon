/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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
import java.util.Optional;

import io.helidon.common.Api;
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
@Api.Incubating
@Api.Since("4.3.0")
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
     * <p>
     * Can also be used on {@link RequestParams} record components.
     */
    @Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Service.Qualifier
    public @interface Entity {
    }

    /**
     * Inject a request header into a server method parameter, or send a header from a declarative client method
     * parameter.
     * <p>
     * Can also be used on {@link RequestParams} record components.
     * <p>
     * Declarative server endpoints support {@code List<T>} for mandatory multi-value headers and
     * {@code Optional<List<T>>} for optional multi-value headers. A mandatory list is rejected when the named header is
     * missing, while an optional list is empty when the named header is missing. If the named header is present with no
     * values, the injected list is empty.
     * <p>
     * Declarative clients send every value from {@code List<T>} and send {@code Optional<List<T>>} only when the
     * optional is present.
     * <p>
     * Declarative client values must not be {@code null}. Use {@code Optional.empty()} to omit optional values.
     * Client list values must not contain {@code null} elements.
     */
    @Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
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
     * Inject a request cookie into a server method parameter, or send a cookie from a declarative client method
     * parameter. Cookie names are validated during declarative code generation. Declarative clients do not escape
     * cookie values; each value must already be a valid cookie-octet value.
     * <p>
     * Can also be used on {@link RequestParams} record components.
     * <p>
     * Declarative server endpoints support {@code List<T>} for mandatory multi-value cookies and
     * {@code Optional<List<T>>} for optional multi-value cookies. A mandatory list is rejected when the named cookie is
     * missing, while an optional list is empty when the named cookie is missing. Cookies are not represented as
     * {@code Parameters}; a named cookie cannot be present with no values.
     * <p>
     * Declarative clients send cookie parameters as a single {@code Cookie} header. They send every value from
     * {@code List<T>} and send {@code Optional<List<T>>} only when the optional is present. A mandatory client
     * {@code List<T>} cookie parameter must contain at least one value.
     * <p>
     * Declarative client values must not be {@code null}. Use {@code Optional.empty()} to omit optional values.
     * Client list values must not contain {@code null} elements.
     */
    @Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Service.Qualifier
    public @interface CookieParam {
        /**
         * Name of the cookie.
         *
         * @return name of the cookie
         */
        String value();
    }

    /**
     * Inject query parameter into a method parameter.
     * <p>
     * Can also be used on {@link RequestParams} record components.
     * <p>
     * Declarative server endpoints support {@code List<T>} for mandatory multi-value query parameters and
     * {@code Optional<List<T>>} for optional multi-value query parameters. A mandatory list is rejected when the named
     * query parameter is missing, while an optional list is empty when the named query parameter is missing. If the
     * query parameter name is present without any values, the injected list is empty.
     * <p>
     * Declarative clients send every value from {@code List<T>} and send {@code Optional<List<T>>} only when the
     * optional is present.
     * <p>
     * Declarative client values must not be {@code null}. Use {@code Optional.empty()} to omit optional values.
     * Client list values must not contain {@code null} elements.
     */
    @Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
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
     * Inject a URL encoded form parameter into a server method parameter, or send a form parameter from a declarative
     * client method parameter.
     * <p>
     * Can also be used on {@link RequestParams} record components.
     * <p>
     * Declarative endpoints and clients should use this annotation with
     * {@code @Http.Consumes("application/x-www-form-urlencoded")}. Form parameters use the request entity and cannot be
     * combined with {@link Entity} on the same declarative method.
     * <p>
     * Declarative server endpoints support {@code List<T>} for mandatory multi-value form parameters and
     * {@code Optional<List<T>>} for optional multi-value form parameters. A mandatory list is rejected when the named
     * form parameter is missing, while an optional list is empty when the named form parameter is missing. If the form
     * parameter name is present without any values, the injected list is empty.
     * <p>
     * Declarative clients send every value from {@code List<T>} and send {@code Optional<List<T>>} only when the
     * optional is present.
     * <p>
     * Declarative client values must not be {@code null}. Use {@code Optional.empty()} to omit optional values.
     * Client list values must not contain {@code null} elements.
     */
    @Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Service.Qualifier
    public @interface FormParam {
        /**
         * Name of the form parameter.
         *
         * @return name of the form parameter
         */
        String value();
    }

    /**
     * Inject a path parameter into a server method parameter, or provide a path template value from a declarative
     * client method parameter.
     * <p>
     * Server path parameters are obtained from the path template of the routing method.
     * <p>
     * Declarative server endpoints may use {@code Optional<T>}, but the optional cannot be empty for a matched route;
     * if the path parameter were missing, the route would not match.
     * <p>
     * Declarative clients use path parameter values as URI path text. They do not encode each value as a single path
     * segment before constructing the request URI.
     * <p>
     * Declarative client path parameter values must not be {@code null}. If {@code Optional<T>} is used, the optional
     * value itself must not be {@code null} and must be present; empty optional path values are rejected.
     * <p>
     * Can also be used on {@link RequestParams} record components.
     * <p>
     * Declarative server endpoints support {@code List<T>} for mandatory multi-value path parameters and
     * {@code Optional<List<T>>} for optional multi-value path parameters. A matched route provides the named path
     * parameter; if it were missing, the route would not match. If the named path parameter is present with no values,
     * the injected list is empty.
     */
    @Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
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
     * Inject grouped request parameters into a method parameter.
     * <p>
     * The annotated parameter type must be a record. This is a restriction on the supported parameter types for
     * declarative code generation. Each record component may have at most one supported request parameter annotation:
     * {@link HeaderParam}, {@link CookieParam}, {@link QueryParam}, {@link FormParam}, {@link PathParam}, or
     * {@link Entity}. At most one {@link Entity} component is supported. Form parameter components use the request
     * entity and cannot be combined with an entity component.
     * <p>
     * Declarative server endpoints may also use annotation-free record components whose type is a supported typed
     * endpoint parameter, such as {@code ServerRequest} or {@code ServerResponse}. Declarative clients require every
     * record component to use one supported request parameter annotation.
     * <p>
     * List-valued record components follow the same rules as list-valued endpoint method parameters.
     * Declarative client request-params arguments and named-value component values must not be {@code null}; use
     * {@code Optional.empty()} to omit optional named values.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Service.Qualifier
    public @interface RequestParams {
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
     * OPTIONS method of an HTTP endpoint.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @HttpMethod(Method.OPTIONS_NAME)
    public @interface OPTIONS {
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

    /**
     * Header producer, to use with rest client and rest server annotations.
     */
    @Service.Contract
    public interface HeaderFunction {
        /**
         * Produce an instance of a named header.
         *
         * @param name name to create
         * @return value for the header
         */
        Optional<Header> apply(HeaderName name);
    }
}
