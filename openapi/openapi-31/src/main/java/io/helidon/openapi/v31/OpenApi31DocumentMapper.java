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

package io.helidon.openapi.v31;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import io.helidon.openapi.OpenApiDocument;
import io.helidon.openapi.v30.OpenApi3xMapperRules;
import io.helidon.openapi.v30.OpenApiDocumentReader;

import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.document3x;
import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.jsonObject;
import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.objectMap;

final class OpenApi31DocumentMapper {
    private static final Set<String> DOCUMENT_FIELDS = Set.of("openapi",
                                                              "info",
                                                              "jsonSchemaDialect",
                                                              "servers",
                                                              "paths",
                                                              "webhooks",
                                                              "components",
                                                              "security",
                                                              "tags",
                                                              "externalDocs");
    private static final Set<String> INFO_FIELDS = Set.of("title",
                                                          "summary",
                                                          "description",
                                                          "termsOfService",
                                                          "contact",
                                                          "license",
                                                          "version");
    private static final Set<String> CONTACT_FIELDS = Set.of("name",
                                                             "url",
                                                             "email");
    private static final Set<String> LICENSE_FIELDS = Set.of("name",
                                                             "identifier",
                                                             "url");
    private static final Set<String> SERVER_FIELDS = Set.of("url",
                                                            "description",
                                                            "variables");
    private static final Set<String> SERVER_VARIABLE_FIELDS = Set.of("enum",
                                                                     "default",
                                                                     "description");
    private static final Set<String> TAG_FIELDS = Set.of("name",
                                                         "description",
                                                         "externalDocs");
    private static final Set<String> PATH_ITEM_FIELDS = Set.of("$ref",
                                                               "summary",
                                                               "description",
                                                               "get",
                                                               "put",
                                                               "post",
                                                               "delete",
                                                               "options",
                                                               "head",
                                                               "patch",
                                                               "trace",
                                                               "servers",
                                                               "parameters");
    private static final Set<String> FIXED_PATH_OPERATION_FIELDS = Set.of("get",
                                                                          "put",
                                                                          "post",
                                                                          "delete",
                                                                          "options",
                                                                          "head",
                                                                          "patch",
                                                                          "trace");
    private static final Set<String> OPERATION_FIELDS = Set.of("tags",
                                                               "summary",
                                                               "description",
                                                               "externalDocs",
                                                               "operationId",
                                                               "parameters",
                                                               "requestBody",
                                                               "responses",
                                                               "callbacks",
                                                               "deprecated",
                                                               "security",
                                                               "servers");
    private static final Set<String> PARAMETER_FIELDS = Set.of("$ref",
                                                               "name",
                                                               "in",
                                                               "description",
                                                               "required",
                                                               "deprecated",
                                                               "allowEmptyValue",
                                                               "style",
                                                               "explode",
                                                               "allowReserved",
                                                               "schema",
                                                               "example",
                                                               "examples",
                                                               "content");
    private static final Set<String> HEADER_FIELDS = Set.of("$ref",
                                                            "description",
                                                            "required",
                                                            "deprecated",
                                                            "allowEmptyValue",
                                                            "style",
                                                            "explode",
                                                            "allowReserved",
                                                            "schema",
                                                            "example",
                                                            "examples",
                                                            "content");
    private static final Set<String> REQUEST_BODY_FIELDS = Set.of("$ref",
                                                                  "description",
                                                                  "content",
                                                                  "required");
    private static final Set<String> RESPONSE_FIELDS = Set.of("$ref",
                                                              "description",
                                                              "headers",
                                                              "content",
                                                              "links");
    private static final Set<String> MEDIA_TYPE_FIELDS = Set.of("schema",
                                                                "example",
                                                                "examples",
                                                                "encoding");
    private static final Set<String> ENCODING_FIELDS = Set.of("contentType",
                                                              "headers",
                                                              "style",
                                                              "explode",
                                                              "allowReserved");
    private static final Set<String> COMPONENTS_FIELDS = Set.of("schemas",
                                                                "responses",
                                                                "parameters",
                                                                "examples",
                                                                "requestBodies",
                                                                "headers",
                                                                "securitySchemes",
                                                                "links",
                                                                "callbacks",
                                                                "pathItems");
    private static final Set<String> SECURITY_SCHEME_FIELDS = Set.of("$ref",
                                                                     "type",
                                                                     "description",
                                                                     "name",
                                                                     "in",
                                                                     "scheme",
                                                                     "bearerFormat",
                                                                     "flows",
                                                                     "openIdConnectUrl");
    private static final Set<String> SECURITY_SCHEME_TYPES = Set.of("apiKey",
                                                                    "http",
                                                                    "mutualTLS",
                                                                    "oauth2",
                                                                    "openIdConnect");
    private static final Set<String> OAUTH_FLOWS_FIELDS = Set.of("implicit",
                                                                 "password",
                                                                 "clientCredentials",
                                                                 "authorizationCode");
    private static final Set<String> OAUTH_FLOW_FIELDS = Set.of("authorizationUrl",
                                                                "tokenUrl",
                                                                "refreshUrl",
                                                                "scopes");
    private static final Set<String> LINK_FIELDS = Set.of("$ref",
                                                          "operationRef",
                                                          "operationId",
                                                          "parameters",
                                                          "requestBody",
                                                          "description",
                                                          "server");
    private static final Set<String> EXAMPLE_FIELDS = Set.of("$ref",
                                                             "summary",
                                                             "description",
                                                             "value",
                                                             "externalValue");
    private static final Set<String> EXTERNAL_DOCS_FIELDS = Set.of("description",
                                                                   "url");
    private static final Set<String> PARAMETER_LOCATIONS = Set.of("query",
                                                                  "header",
                                                                  "path",
                                                                  "cookie");
    private static final OpenApi3xMapperRules MAPPER_RULES = OpenApi3xMapperRules.builder()
            .targetVersion("3.1")
            .addDocumentFields(DOCUMENT_FIELDS)
            .addInfoFields(INFO_FIELDS)
            .addContactFields(CONTACT_FIELDS)
            .addLicenseFields(LICENSE_FIELDS)
            .addServerFields(SERVER_FIELDS)
            .addServerVariableFields(SERVER_VARIABLE_FIELDS)
            .addTagFields(TAG_FIELDS)
            .addPathItemFields(PATH_ITEM_FIELDS)
            .addFixedPathOperationFields(FIXED_PATH_OPERATION_FIELDS)
            .addOperationFields(OPERATION_FIELDS)
            .addParameterFields(PARAMETER_FIELDS)
            .addParameterLocations(PARAMETER_LOCATIONS)
            .addHeaderFields(HEADER_FIELDS)
            .addRequestBodyFields(REQUEST_BODY_FIELDS)
            .addResponseFields(RESPONSE_FIELDS)
            .addMediaTypeFields(MEDIA_TYPE_FIELDS)
            .addEncodingFields(ENCODING_FIELDS)
            .addComponentsFields(COMPONENTS_FIELDS)
            .addSecuritySchemeFields(SECURITY_SCHEME_FIELDS)
            .addSecuritySchemeTypes(SECURITY_SCHEME_TYPES)
            .addOauthFlowsFields(OAUTH_FLOWS_FIELDS)
            .addOauthFlowFields(OAUTH_FLOW_FIELDS)
            .addLinkFields(LINK_FIELDS)
            .addExampleFields(EXAMPLE_FIELDS)
            .addExternalDocsFields(EXTERNAL_DOCS_FIELDS)
            .build();

    private OpenApi31DocumentMapper() {
    }

    static OpenApiDocument parse(Map<String, ?> document) {
        validateOpenApi31(document.get("openapi"));
        return OpenApiDocumentReader.read(jsonObject(document3x(document, MAPPER_RULES)));
    }

    static Map<String, Object> render(OpenApiDocument document, String version) {
        Map<String, Object> rendered = document3x(objectMap(document.toJsonObject()), MAPPER_RULES);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openapi", version);
        rendered.forEach((key, value) -> {
            if (!"openapi".equals(key)) {
                result.put(key, value);
            }
        });
        return result;
    }

    private static void validateOpenApi31(Object version) {
        if (!(version instanceof String string) || !OpenApi31Version.isSupportedVersion(string)) {
            throw new IllegalStateException("OpenAPI 3.1 parser requires a 3.1 document, got: " + version);
        }
    }
}
