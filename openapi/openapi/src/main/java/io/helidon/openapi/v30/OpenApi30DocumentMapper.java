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

package io.helidon.openapi.v30;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.openapi.OpenApiDocument;

import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.allowed;
import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.copy;
import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.copyAllowed;
import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.copyField;
import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.copyFieldValue;
import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.jsonObject;
import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.object;
import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.objectList;
import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.objectMap;

final class OpenApi30DocumentMapper {
    private static final Set<String> REFERENCE_FIELDS = Set.of("$ref");

    private static final Set<String> DOCUMENT_FIELDS = Set.of("openapi",
                                                              "info",
                                                              "servers",
                                                              "paths",
                                                              "components",
                                                              "security",
                                                              "tags",
                                                              "externalDocs");
    private static final Set<String> INFO_FIELDS = Set.of("title",
                                                          "description",
                                                          "termsOfService",
                                                          "contact",
                                                          "license",
                                                          "version");
    private static final Set<String> CONTACT_FIELDS = Set.of("name",
                                                             "url",
                                                             "email");
    private static final Set<String> LICENSE_FIELDS = Set.of("name",
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
                                                                "callbacks");
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
    private static final Set<String> SCHEMA_FIELDS = Set.of("$ref",
                                                            "title",
                                                            "multipleOf",
                                                            "maximum",
                                                            "exclusiveMaximum",
                                                            "minimum",
                                                            "exclusiveMinimum",
                                                            "maxLength",
                                                            "minLength",
                                                            "pattern",
                                                            "maxItems",
                                                            "minItems",
                                                            "uniqueItems",
                                                            "maxProperties",
                                                            "minProperties",
                                                            "required",
                                                            "enum",
                                                            "type",
                                                            "allOf",
                                                            "oneOf",
                                                            "anyOf",
                                                            "not",
                                                            "items",
                                                            "properties",
                                                            "additionalProperties",
                                                            "description",
                                                            "format",
                                                            "default",
                                                            "nullable",
                                                            "discriminator",
                                                            "readOnly",
                                                            "writeOnly",
                                                            "xml",
                                                            "externalDocs",
                                                            "example",
                                                            "deprecated");
    private static final Set<String> PARAMETER_LOCATIONS = Set.of("query",
                                                                  "header",
                                                                  "path",
                                                                  "cookie");

    private OpenApi30DocumentMapper() {
    }

    static OpenApiDocument parse(Map<String, ?> document) {
        validateOpenApi30(document.get("openapi"));
        return OpenApiDocumentReader.read(jsonObject(document(document, SchemaMode.CANONICAL)));
    }

    static Map<String, Object> render(OpenApiDocument document, String version) {
        Map<String, Object> rendered = document(objectMap(document.toJsonObject()), SchemaMode.OPENAPI30);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openapi", version);
        rendered.forEach((key, value) -> {
            if (!"openapi".equals(key)) {
                result.put(key, value);
            }
        });
        return result;
    }

    private static Map<String, Object> document(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, DOCUMENT_FIELDS)) {
                return;
            }
            switch (key) {
            case "info" -> object(value, object -> result.put(key, info(object)));
            case "servers" -> result.put(key, serverList(value));
            case "paths" -> object(value, object -> result.put(key, paths(object, mode)));
            case "components" -> object(value, object -> result.put(key, components(object, mode)));
            case "tags" -> result.put(key, tagList(value));
            case "externalDocs" -> object(value, object -> result.put(key, copyAllowed(object, EXTERNAL_DOCS_FIELDS)));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> info(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, INFO_FIELDS)) {
                return;
            }
            switch (key) {
            case "contact" -> object(value, object -> result.put(key, copyAllowed(object, CONTACT_FIELDS)));
            case "license" -> object(value, object -> result.put(key, copyAllowed(object, LICENSE_FIELDS)));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static List<Object> serverList(Object value) {
        return objectList(value, OpenApi30DocumentMapper::server);
    }

    private static Map<String, Object> server(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, SERVER_FIELDS)) {
                return;
            }
            if ("variables".equals(key)) {
                object(value, object -> result.put(key, serverVariables(object)));
            } else {
                copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> serverVariables(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value,
                                             object -> result.put(key, copyAllowed(object, SERVER_VARIABLE_FIELDS))));
        return result;
    }

    private static List<Object> tagList(Object value) {
        return objectList(value, tag -> {
            Map<String, Object> result = new LinkedHashMap<>();
            tag.forEach((key, item) -> {
                if (!allowed(key, TAG_FIELDS)) {
                    return;
                }
                if ("externalDocs".equals(key)) {
                    object(item, object -> result.put(key, copyAllowed(object, EXTERNAL_DOCS_FIELDS)));
                } else {
                    copyField(result, key, tag);
                }
            });
            return result;
        });
    }

    private static Map<String, Object> paths(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, pathItem(object, mode))));
        return result;
    }

    private static Map<String, Object> pathItem(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, PATH_ITEM_FIELDS)) {
                return;
            }
            if (isFixedPathOperationField(key)) {
                object(value, object -> result.put(key, operation(object, mode)));
                return;
            }
            switch (key) {
            case "servers" -> result.put(key, serverList(value));
            case "parameters" -> result.put(key, parameters(value, mode));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> operation(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, OPERATION_FIELDS)) {
                return;
            }
            switch (key) {
            case "parameters" -> result.put(key, parameters(value, mode));
            case "requestBody" -> object(value, object -> result.put(key, requestBody(object, mode)));
            case "responses" -> object(value, object -> result.put(key, responses(object, mode)));
            case "callbacks" -> object(value, object -> result.put(key, callbacks(object, mode)));
            case "servers" -> result.put(key, serverList(value));
            case "externalDocs" -> object(value, object -> result.put(key, copyAllowed(object, EXTERNAL_DOCS_FIELDS)));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static List<Object> parameters(Object value, SchemaMode mode) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            object(item, object -> {
                Map<String, Object> parameter = parameter(object, mode);
                if (!parameter.isEmpty()) {
                    result.add(parameter);
                }
            });
        }
        return result;
    }

    private static Map<String, Object> parameter(Map<String, ?> source, SchemaMode mode) {
        if (!source.containsKey("$ref") && !PARAMETER_LOCATIONS.contains(String.valueOf(source.get("in")))) {
            return Map.of();
        }
        if (source.containsKey("$ref")) {
            return reference(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, PARAMETER_FIELDS)) {
                return;
            }
            switch (key) {
            case "schema" -> result.put(key, schema(value, mode));
            case "content" -> object(value, object -> result.put(key, content(object, mode)));
            case "examples" -> result.put(key, examples(value));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> requestBody(Map<String, ?> source, SchemaMode mode) {
        if (source.containsKey("$ref")) {
            return reference(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, REQUEST_BODY_FIELDS)) {
                return;
            }
            if ("content".equals(key)) {
                object(value, object -> result.put(key, content(object, mode)));
            } else {
                copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> responses(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, response(object, mode))));
        return result;
    }

    private static Map<String, Object> response(Map<String, ?> source, SchemaMode mode) {
        if (source.containsKey("$ref")) {
            return reference(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, RESPONSE_FIELDS)) {
                return;
            }
            switch (key) {
            case "headers" -> object(value, object -> result.put(key, headers(object, mode)));
            case "content" -> object(value, object -> result.put(key, content(object, mode)));
            case "links" -> object(value, object -> result.put(key, links(object)));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> headers(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, header(object, mode))));
        return result;
    }

    private static Map<String, Object> header(Map<String, ?> source, SchemaMode mode) {
        if (source.containsKey("$ref")) {
            return reference(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, HEADER_FIELDS)) {
                return;
            }
            switch (key) {
            case "schema" -> result.put(key, schema(value, mode));
            case "content" -> object(value, object -> result.put(key, content(object, mode)));
            case "examples" -> result.put(key, examples(value));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> content(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, mediaType(object, mode))));
        return result;
    }

    private static Map<String, Object> mediaType(Map<String, ?> source, SchemaMode mode) {
        if (source.containsKey("$ref")) {
            throw unsupported("media type reference", String.valueOf(source.get("$ref")), "mediaType");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, MEDIA_TYPE_FIELDS)) {
                return;
            }
            switch (key) {
            case "schema" -> result.put(key, schema(value, mode));
            case "examples" -> result.put(key, examples(value));
            case "encoding" -> object(value, object -> result.put(key, encodings(object, mode)));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> encodings(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, encoding(object, mode))));
        return result;
    }

    private static Map<String, Object> encoding(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, ENCODING_FIELDS)) {
                return;
            }
            if ("headers".equals(key)) {
                object(value, object -> result.put(key, headers(object, mode)));
            } else {
                copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> components(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, COMPONENTS_FIELDS)) {
                return;
            }
            switch (key) {
            case "schemas" -> object(value, object -> result.put(key, schemaMap(object, mode)));
            case "responses" -> object(value, object -> result.put(key, responses(object, mode)));
            case "parameters" -> object(value, object -> result.put(key, parameterMap(object, mode)));
            case "examples" -> result.put(key, examples(value));
            case "requestBodies" -> object(value, object -> result.put(key, requestBodyMap(object, mode)));
            case "headers" -> object(value, object -> result.put(key, headers(object, mode)));
            case "securitySchemes" -> object(value, object -> result.put(key, securitySchemes(object)));
            case "links" -> object(value, object -> result.put(key, links(object)));
            case "callbacks" -> object(value, object -> result.put(key, callbacks(object, mode)));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> schemaMap(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, schema(value, mode)));
        return result;
    }

    private static Map<String, Object> parameterMap(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> {
            Map<String, Object> parameter = parameter(object, mode);
            if (!parameter.isEmpty()) {
                result.put(key, parameter);
            }
        }));
        return result;
    }

    private static Map<String, Object> requestBodyMap(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, requestBody(object, mode))));
        return result;
    }

    private static Map<String, Object> securitySchemes(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, securityScheme(key, object))));
        return result;
    }

    private static Map<String, Object> securityScheme(String name, Map<String, ?> source) {
        if (source.containsKey("$ref")) {
            return reference(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, SECURITY_SCHEME_FIELDS)) {
                return;
            }
            switch (key) {
            case "type" -> {
                String type = String.valueOf(value);
                if (!SECURITY_SCHEME_TYPES.contains(type)) {
                    throw unsupported("security scheme type", type, securitySchemePath(name));
                }
                copyField(result, key, source);
            }
            case "flows" -> object(value,
                                    object -> result.put(key,
                                                         oauthFlows(securitySchemePath(name) + ".flows", object)));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> oauthFlows(String path, Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, OAUTH_FLOWS_FIELDS)) {
                throw unsupported("OAuth flow", key, path);
            }
            object(value, object -> result.put(key, copyAllowed(object, OAUTH_FLOW_FIELDS)));
        });
        return result;
    }

    private static IllegalStateException unsupported(String kind, String value, String path) {
        return new IllegalStateException("Unsupported OpenAPI 3.0 "
                                                 + kind
                                                 + " '"
                                                 + value
                                                 + "' at "
                                                 + path);
    }

    private static String securitySchemePath(String name) {
        return "components.securitySchemes." + name;
    }

    private static Map<String, Object> reference(Map<String, ?> source) {
        return copyAllowed(source, REFERENCE_FIELDS);
    }

    private static Map<String, Object> links(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> {
            if (object.containsKey("$ref")) {
                result.put(key, reference(object));
                return;
            }
            Map<String, Object> link = new LinkedHashMap<>();
            object.forEach((linkKey, linkValue) -> {
                if (!allowed(linkKey, LINK_FIELDS)) {
                    return;
                }
                if ("server".equals(linkKey)) {
                    object(linkValue, server -> link.put(linkKey, server(server)));
                } else {
                    copyField(link, linkKey, object);
                }
            });
            result.put(key, link);
        }));
        return result;
    }

    private static Map<String, Object> callbacks(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, pathItem(object, mode))));
        return result;
    }

    private static Map<String, Object> examples(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> object(item, object -> {
            if (object.containsKey("$ref")) {
                result.put(String.valueOf(key), reference(object));
            } else {
                result.put(String.valueOf(key), copyAllowed(object, EXAMPLE_FIELDS));
            }
        }));
        return result;
    }

    private static Object schema(Object value, SchemaMode mode) {
        if (value instanceof Boolean bool) {
            return mode == SchemaMode.OPENAPI30 ? booleanSchema(bool) : bool;
        }
        if (!(value instanceof Map<?, ?> map)) {
            return copy(value);
        }
        Map<String, Object> source = objectMap(map);
        Map<String, Object> result = new LinkedHashMap<>();
        boolean nullable = false;
        Object enumValue = null;
        boolean hasEnum = false;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object item = entry.getValue();
            if ("nullable".equals(key)) {
                nullable = Boolean.TRUE.equals(item);
                continue;
            }
            if ("type".equals(key)) {
                TypeMapping typeMapping = mode == SchemaMode.CANONICAL ? canonicalType(item) : openApi30Type(item);
                nullable |= typeMapping.nullable();
                typeMapping.put(result);
                continue;
            }
            if ("enum".equals(key)) {
                enumValue = item;
                hasEnum = true;
                continue;
            }
            if ("const".equals(key)) {
                if (mode == SchemaMode.OPENAPI30 && !source.containsKey("enum")) {
                    enumValue = singleValueList(copyFieldValue(key, source));
                    hasEnum = true;
                }
                continue;
            }
            if (!allowed(key, SCHEMA_FIELDS)) {
                continue;
            }
            switch (key) {
            case "maximum" -> bound(result, source, key, "exclusiveMaximum", item, mode);
            case "minimum" -> bound(result, source, key, "exclusiveMinimum", item, mode);
            case "exclusiveMaximum" -> exclusiveBound(result, source, "maximum", key, item, mode);
            case "exclusiveMinimum" -> exclusiveBound(result, source, "minimum", key, item, mode);
            case "allOf", "oneOf", "anyOf" -> result.put(key, schemaList(item, mode));
            case "not", "items" -> result.put(key, schema(item, mode));
            case "properties" -> object(item, object -> result.put(key, schemaMap(object, mode)));
            case "additionalProperties" -> result.put(key, additionalProperties(item, mode));
            case "externalDocs" -> object(item, object -> result.put(key, copyAllowed(object, EXTERNAL_DOCS_FIELDS)));
            default -> copyField(result, key, source);
            }
        }
        if (hasEnum) {
            result.put("enum", enumValue(enumValue, mode, nullable));
        }
        if (nullable) {
            if (mode == SchemaMode.CANONICAL) {
                addNullType(result);
                addNullEnum(result);
            } else if (result.containsKey("oneOf")) {
                addNullOneOf(result);
            } else if (!result.containsKey("oneOf")) {
                removeNullEnum(result);
                result.put("nullable", true);
            }
        }
        return result;
    }

    private static void bound(Map<String, Object> target,
                              Map<String, Object> source,
                              String boundName,
                              String exclusiveBoundName,
                              Object value,
                              SchemaMode mode) {
        if (mode == SchemaMode.CANONICAL && Boolean.TRUE.equals(source.get(exclusiveBoundName))) {
            return;
        }
        Object exclusiveBound = source.get(exclusiveBoundName);
        if (mode == SchemaMode.OPENAPI30 && exclusiveBound instanceof Number exclusiveNumber) {
            if (!(value instanceof Number inclusiveNumber)
                    || exclusiveBoundWins(boundName, inclusiveNumber, exclusiveNumber)) {
                return;
            }
            target.remove(exclusiveBoundName);
        }
        target.put(boundName, copy(value));
    }

    private static void exclusiveBound(Map<String, Object> target,
                                       Map<String, Object> source,
                                       String boundName,
                                       String exclusiveBoundName,
                                       Object value,
                                       SchemaMode mode) {
        if (mode == SchemaMode.OPENAPI30 && value instanceof Number) {
            Object inclusiveBound = source.get(boundName);
            if (inclusiveBound instanceof Number inclusiveNumber
                    && !exclusiveBoundWins(boundName, inclusiveNumber, (Number) value)) {
                target.put(boundName, copy(inclusiveBound));
                target.remove(exclusiveBoundName);
                return;
            }
            target.put(boundName, copy(value));
            target.put(exclusiveBoundName, true);
        } else if (mode == SchemaMode.CANONICAL && value instanceof Boolean exclusive) {
            if (exclusive) {
                Object bound = source.get(boundName);
                if (bound != null) {
                    target.put(exclusiveBoundName, copy(bound));
                }
            }
        } else {
            target.put(exclusiveBoundName, copy(value));
        }
    }

    private static boolean exclusiveBoundWins(String boundName, Number inclusiveBound, Number exclusiveBound) {
        int compare = decimal(inclusiveBound).compareTo(decimal(exclusiveBound));
        return switch (boundName) {
        case "maximum" -> compare >= 0;
        case "minimum" -> compare <= 0;
        default -> true;
        };
    }

    private static BigDecimal decimal(Number number) {
        if (number instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(number.toString());
    }

    private static Object additionalProperties(Object value, SchemaMode mode) {
        if (value instanceof Boolean) {
            return value;
        }
        return schema(value, mode);
    }

    private static List<Object> schemaList(Object value, SchemaMode mode) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        list.forEach(item -> result.add(schema(item, mode)));
        return result;
    }

    private static Map<String, Object> booleanSchema(boolean value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!value) {
            result.put("not", new LinkedHashMap<String, Object>());
        }
        return result;
    }

    private static TypeMapping canonicalType(Object value) {
        return new TypeMapping(copy(value), null, false);
    }

    private static TypeMapping openApi30Type(Object value) {
        if (!(value instanceof List<?> list)) {
            if ("null".equals(value)) {
                return new TypeMapping(nullOnlySchema(), null, false);
            }
            return new TypeMapping(copy(value), null, false);
        }
        List<Object> types = new ArrayList<>();
        boolean nullable = false;
        for (Object type : list) {
            if ("null".equals(type)) {
                nullable = true;
            } else {
                types.add(copy(type));
            }
        }
        if (types.isEmpty()) {
            return nullable ? new TypeMapping(nullOnlySchema(), null, false) : new TypeMapping(null, null, false);
        }
        if (types.size() == 1) {
            return new TypeMapping(types.getFirst(), null, nullable);
        }
        List<Object> oneOf = new ArrayList<>();
        types.forEach(type -> oneOf.add(Map.of("type", type)));
        if (nullable) {
            oneOf.add(nullOnlySchema());
        }
        return new TypeMapping(null, oneOf, false);
    }

    private static Object enumValue(Object value, SchemaMode mode, boolean nullable) {
        if (!(value instanceof List<?> list)) {
            return copy(value);
        }
        List<Object> result = new ArrayList<>();
        list.forEach(result::add);
        if (mode == SchemaMode.CANONICAL && nullable && !result.contains(null)) {
            result.add(null);
        }
        if (mode == SchemaMode.OPENAPI30 && nullable) {
            boolean hadNull = result.removeIf(item -> item == null);
            if (hadNull && result.isEmpty()) {
                return singleValueList(null);
            }
        }
        return result;
    }

    private static void addNullType(Map<String, Object> schema) {
        Object type = schema.get("type");
        if (type == null) {
            return;
        }
        if (type instanceof List<?> list) {
            if (!list.contains("null")) {
                List<Object> result = new ArrayList<>(list);
                result.add("null");
                schema.put("type", result);
            }
            return;
        }
        if (!"null".equals(type)) {
            List<Object> result = new ArrayList<>();
            result.add(type);
            result.add("null");
            schema.put("type", result);
        }
    }

    private static void addNullEnum(Map<String, Object> schema) {
        Object value = schema.get("enum");
        if (!(value instanceof List<?> list) || list.contains(null)) {
            return;
        }
        List<Object> result = new ArrayList<>(list);
        result.add(null);
        schema.put("enum", result);
    }

    private static void removeNullEnum(Map<String, Object> schema) {
        Object value = schema.get("enum");
        if (!(value instanceof List<?> list) || !list.contains(null)) {
            return;
        }
        List<Object> result = new ArrayList<>();
        list.stream()
                .filter(item -> item != null)
                .forEach(result::add);
        if (result.isEmpty()) {
            schema.put("type", "object");
            schema.put("enum", singleValueList(null));
            return;
        }
        schema.put("enum", result);
    }

    private static void addNullOneOf(Map<String, Object> schema) {
        Object value = schema.get("oneOf");
        if (!(value instanceof List<?> list)) {
            return;
        }
        Map<String, Object> nullOnlySchema = nullOnlySchema();
        if (list.contains(nullOnlySchema)) {
            return;
        }
        List<Object> result = new ArrayList<>(list);
        result.add(nullOnlySchema);
        schema.put("oneOf", result);
    }

    private static List<Object> singleValueList(Object value) {
        List<Object> result = new ArrayList<>();
        result.add(value);
        return result;
    }

    private static Map<String, Object> nullOnlySchema() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        result.put("nullable", true);
        result.put("enum", singleValueList(null));
        return result;
    }

    private static void validateOpenApi30(Object openapi) {
        if (openapi == null) {
            throw new IllegalStateException("Static OpenAPI document must declare an openapi version.");
        }
        String version = String.valueOf(openapi);
        if (!OpenApi30Version.TYPE.equals(version) && !version.startsWith(OpenApi30Version.TYPE + ".")) {
            throw new IllegalStateException("OpenAPI 3.0 version implementation cannot parse static OpenAPI document version "
                                                    + version + ".");
        }
    }

    private static boolean isFixedPathOperationField(String value) {
        return switch (value) {
        case "get", "put", "post", "delete", "options", "head", "patch", "trace" -> true;
        default -> false;
        };
    }

    private enum SchemaMode {
        CANONICAL,
        OPENAPI30
    }

    private record TypeMapping(Object type, List<Object> oneOf, boolean nullable) {
        void put(Map<String, Object> target) {
            if (type instanceof Map<?, ?> map) {
                map.forEach((key, value) -> target.put(String.valueOf(key), value));
            } else if (type != null) {
                target.put("type", type);
            }
            if (oneOf != null) {
                target.put("oneOf", oneOf);
            }
        }
    }
}
