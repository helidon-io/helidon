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

package io.helidon.declarative.codegen.openapi;

import io.helidon.common.types.TypeName;

final class OpenApiCodegenTypes {
    static final TypeName OPENAPI_DOCUMENT_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Document");
    static final TypeName OPENAPI_INFO_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Info");
    static final TypeName OPENAPI_CONTACT_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Contact");
    static final TypeName OPENAPI_LICENSE_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.License");
    static final TypeName OPENAPI_SERVER_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Server");
    static final TypeName OPENAPI_SERVERS_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Servers");
    static final TypeName OPENAPI_TAG_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Tag");
    static final TypeName OPENAPI_TAGS_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Tags");
    static final TypeName OPENAPI_EXTERNAL_DOCS_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.ExternalDocs");
    static final TypeName OPENAPI_EXTENSION_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Extension");
    static final TypeName OPENAPI_EXTENSIONS_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Extensions");
    static final TypeName OPENAPI_SECURITY_SCHEME_ANNOTATION =
            TypeName.create("io.helidon.openapi.OpenApi.SecurityScheme");
    static final TypeName OPENAPI_SECURITY_SCHEMES_ANNOTATION =
            TypeName.create("io.helidon.openapi.OpenApi.SecuritySchemes");
    static final TypeName OPENAPI_SECURITY_REQUIREMENT_ANNOTATION =
            TypeName.create("io.helidon.openapi.OpenApi.SecurityRequirement");
    static final TypeName OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION =
            TypeName.create("io.helidon.openapi.OpenApi.SecurityRequirements");
    static final TypeName OPENAPI_OPERATION_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Operation");
    static final TypeName OPENAPI_PARAMETER_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Parameter");
    static final TypeName OPENAPI_PARAMETERS_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Parameters");
    static final TypeName OPENAPI_REQUEST_BODY_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.RequestBody");
    static final TypeName OPENAPI_RESPONSE_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Response");
    static final TypeName OPENAPI_RESPONSES_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Responses");
    static final TypeName OPENAPI_HIDDEN_ANNOTATION = TypeName.create("io.helidon.openapi.OpenApi.Hidden");

    static final TypeName OPENAPI_SOURCE_BASE = TypeName.create("io.helidon.openapi.OpenApiSourceBase");
    static final TypeName OPENAPI_DOCUMENT_SOURCE = TypeName.create("io.helidon.openapi.spi.OpenApiDocumentSource");
    static final TypeName OPENAPI_DOCUMENT_CONTEXT = TypeName.create("io.helidon.openapi.OpenApiDocumentContext");
    static final TypeName OPENAPI_DOCUMENT_BUILDER = TypeName.create("io.helidon.openapi.OpenApiDocument.Builder");
    static final TypeName OPENAPI_DOCUMENT_INFO = TypeName.create("io.helidon.openapi.OpenApiDocument.Info");
    static final TypeName OPENAPI_DOCUMENT_SERVER = TypeName.create("io.helidon.openapi.OpenApiDocument.Server");
    static final TypeName OPENAPI_DOCUMENT_TAG = TypeName.create("io.helidon.openapi.OpenApiDocument.Tag");
    static final TypeName OPENAPI_DOCUMENT_OPERATION = TypeName.create("io.helidon.openapi.OpenApiDocument.Operation");
    static final TypeName OPENAPI_DOCUMENT_PARAMETER = TypeName.create("io.helidon.openapi.OpenApiDocument.Parameter");
    static final TypeName OPENAPI_DOCUMENT_REQUEST_BODY =
            TypeName.create("io.helidon.openapi.OpenApiDocument.RequestBody");
    static final TypeName OPENAPI_DOCUMENT_RESPONSE = TypeName.create("io.helidon.openapi.OpenApiDocument.Response");
    static final TypeName OPENAPI_DOCUMENT_MEDIA_TYPE_OBJECT =
            TypeName.create("io.helidon.openapi.OpenApiDocument.MediaTypeObject");
    static final TypeName OPENAPI_DOCUMENT_EXAMPLE = TypeName.create("io.helidon.openapi.OpenApiDocument.Example");

    static final TypeName JSON_OBJECT = TypeName.create("io.helidon.json.JsonObject");
    static final TypeName JSON_STRING = TypeName.create("io.helidon.json.JsonString");
    static final TypeName JSON_SCHEMA_PROVIDER = TypeName.create("io.helidon.json.schema.spi.JsonSchemaProvider");
    static final TypeName WEB_SERVER = TypeName.create("io.helidon.webserver.WebServer");

    private OpenApiCodegenTypes() {
    }
}
