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

package io.helidon.declarative.codegen.http;

import io.helidon.common.types.TypeName;

/**
 * HTTP related types for code generation.
 */
public final class HttpTypes {
    /**
     * HTTP Prologue.
     */
    public static final TypeName HTTP_PROLOGUE = TypeName.create("io.helidon.http.HttpPrologue");
    /**
     * HTTP Method.
     */
    public static final TypeName HTTP_METHOD = TypeName.create("io.helidon.http.Method");
    /**
     * HTTP Status.
     */
    public static final TypeName HTTP_STATUS = TypeName.create("io.helidon.http.Status");
    /**
     * HTTP HeaderName.
     */
    public static final TypeName HTTP_HEADER_NAME = TypeName.create("io.helidon.http.HeaderName");
    /**
     * HTTP HeaderNames.
     */
    public static final TypeName HTTP_HEADER_NAMES = TypeName.create("io.helidon.http.HeaderNames");
    /**
     * HTTP Header.
     */
    public static final TypeName HTTP_HEADER = TypeName.create("io.helidon.http.Header");
    /**
     * HTTP Headers.
     */
    public static final TypeName HTTP_HEADERS = TypeName.create("io.helidon.http.Headers");
    /**
     * HTTP HeaderValues.
     */
    public static final TypeName HTTP_HEADER_VALUES = TypeName.create("io.helidon.http.HeaderValues");
    /**
     * HTTP support methods used from generated code.
     */
    public static final TypeName HTTP_SUPPORT = TypeName.create("io.helidon.http.HttpSupport");
    /**
     * Http.Path annotation.
     */
    public static final TypeName HTTP_PATH_ANNOTATION = TypeName.create("io.helidon.http.Http.Path");
    /**
     * Http.HttpMethod annotation.
     */
    public static final TypeName HTTP_METHOD_ANNOTATION = TypeName.create("io.helidon.http.Http.HttpMethod");
    /**
     * Http.Produces annotation.
     */
    public static final TypeName HTTP_PRODUCES_ANNOTATION = TypeName.create("io.helidon.http.Http.Produces");
    /**
     * Http.Consumes annotation.
     */
    public static final TypeName HTTP_CONSUMES_ANNOTATION = TypeName.create("io.helidon.http.Http.Consumes");
    /**
     * Http.PathParam annotation.
     */
    public static final TypeName HTTP_PATH_PARAM_ANNOTATION = TypeName.create("io.helidon.http.Http.PathParam");
    /**
     * Http.CookieParam annotation.
     */
    public static final TypeName HTTP_COOKIE_PARAM_ANNOTATION = TypeName.create("io.helidon.http.Http.CookieParam");
    /**
     * Http.QueryParam annotation.
     */
    public static final TypeName HTTP_QUERY_PARAM_ANNOTATION = TypeName.create("io.helidon.http.Http.QueryParam");
    /**
     * Http.FormParam annotation.
     */
    public static final TypeName HTTP_FORM_PARAM_ANNOTATION = TypeName.create("io.helidon.http.Http.FormParam");
    /**
     * Http.HeaderParam annotation.
     */
    public static final TypeName HTTP_HEADER_PARAM_ANNOTATION = TypeName.create("io.helidon.http.Http.HeaderParam");
    /**
     * Http.Entity annotation.
     */
    public static final TypeName HTTP_ENTITY_ANNOTATION = TypeName.create("io.helidon.http.Http.Entity");
    /**
     * Http.RequestParams annotation.
     */
    public static final TypeName HTTP_REQUEST_PARAMS_ANNOTATION = TypeName.create("io.helidon.http.Http.RequestParams");
    /**
     * HTTP Header function.
     */
    public static final TypeName HTTP_HEADER_FUNCTION = TypeName.create("io.helidon.http.Http.HeaderFunction");
    /**
     * HTTP media type.
     */
    public static final TypeName HTTP_MEDIA_TYPE = TypeName.create("io.helidon.http.HttpMediaType");
    /**
     * Bad request exception.
     */
    public static final TypeName BAD_REQUEST_EXCEPTION = TypeName.create("io.helidon.http.BadRequestException");

    private HttpTypes() {
    }
}
