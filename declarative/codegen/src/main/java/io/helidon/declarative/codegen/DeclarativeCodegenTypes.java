/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen;

import io.helidon.common.types.TypeName;

final class DeclarativeCodegenTypes {
    static final TypeName COMMON_CONTEXT = TypeName.create("io.helidon.common.context.Context");
    static final TypeName COMMON_MAPPERS = TypeName.create("io.helidon.common.mapper.Mappers");

    static final TypeName SERVER_REQUEST = TypeName.create("io.helidon.webserver.http.ServerRequest");
    static final TypeName SERVER_RESPONSE = TypeName.create("io.helidon.webserver.http.ServerResponse");
    static final TypeName SERVER_HTTP_ROUTE = TypeName.create("io.helidon.webserver.http.HttpRoute");
    static final TypeName SERVER_HTTP_FEATURE = TypeName.create("io.helidon.webserver.http.HttpFeature");
    static final TypeName SERVER_HTTP_ROUTING_BUILDER = TypeName.create("io.helidon.webserver.http.HttpRouting.Builder");
    static final TypeName SERVER_HTTP_RULES = TypeName.create("io.helidon.webserver.http.HttpRules");

    static final TypeName MEDIA_TYPE = TypeName.create("io.helidon.common.media.type.MediaType");
    static final TypeName MEDIA_TYPES = TypeName.create("io.helidon.common.media.type.MediaTypes");

    static final TypeName HTTP_METHOD = TypeName.create("io.helidon.http.Method");
    static final TypeName HTTP_STATUS = TypeName.create("io.helidon.http.Status");
    static final TypeName HTTP_HEADER_NAME = TypeName.create("io.helidon.http.HeaderName");
    static final TypeName HTTP_HEADER_NAMES = TypeName.create("io.helidon.http.HeaderNames");
    static final TypeName HTTP_PATH_ANNOTATION = TypeName.create("io.helidon.http.Http.Path");
    static final TypeName HTTP_METHOD_ANNOTATION = TypeName.create("io.helidon.http.Http.HttpMethod");
    static final TypeName HTTP_STATUS_ANNOTATION = TypeName.create("io.helidon.http.Http.Status");
    static final TypeName HTTP_PRODUCES_ANNOTATION = TypeName.create("io.helidon.http.Http.Produces");
    static final TypeName HTTP_CONSUMES_ANNOTATION = TypeName.create("io.helidon.http.Http.Consumes");
    static final TypeName HTTP_PATH_PARAM_ANNOTATION = TypeName.create("io.helidon.http.Http.PathParam");
    static final TypeName HTTP_QUERY_PARAM_ANNOTATION = TypeName.create("io.helidon.http.Http.QueryParam");
    static final TypeName HTTP_HEADER_PARAM_ANNOTATION = TypeName.create("io.helidon.http.Http.HeaderParam");
    static final TypeName HTTP_ENTITY_PARAM_ANNOTATION = TypeName.create("io.helidon.http.Http.Entity");

    static final TypeName SERVICE_CONTEXT = TypeName.create("io.helidon.common.context.Context__ServiceDescriptor");
    static final TypeName SERVICE_PROLOGUE = TypeName.create("io.helidon.http.Prologue__ServiceDescriptor");
    static final TypeName SERVICE_HEADERS = TypeName.create("io.helidon.http.Headers__ServiceDescriptor");
    static final TypeName SERVICE_SERVER_REQUEST = TypeName.create("io.helidon.webserver.ServerRequest__ServiceDescriptor");
    static final TypeName SERVICE_SERVER_RESPONSE = TypeName.create("io.helidon.webserver.ServerResponse__ServiceDescriptor");

    static final TypeName INJECT_SCOPE = TypeName.create("io.helidon.service.inject.api.Scope");

    private DeclarativeCodegenTypes() {
    }
}
