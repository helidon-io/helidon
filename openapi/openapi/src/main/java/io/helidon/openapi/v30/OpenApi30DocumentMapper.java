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

import io.helidon.json.JsonArray;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;
import io.helidon.openapi.OpenApiDocument;

final class OpenApi30DocumentMapper {
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
            default -> result.put(key, copy(value));
            }
        });
        return result;
    }

    private static Map<String, Object> info(Map<String, ?> source) {
        return copyAllowed(source, INFO_FIELDS);
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
                result.put(key, copy(value));
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
                    result.put(key, copy(item));
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
            default -> result.put(key, copy(value));
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
            default -> result.put(key, copy(value));
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
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, PARAMETER_FIELDS)) {
                return;
            }
            switch (key) {
            case "schema" -> result.put(key, schema(value, mode));
            case "content" -> object(value, object -> result.put(key, content(object, mode)));
            case "examples" -> result.put(key, examples(value));
            default -> result.put(key, copy(value));
            }
        });
        return result;
    }

    private static Map<String, Object> requestBody(Map<String, ?> source, SchemaMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, REQUEST_BODY_FIELDS)) {
                return;
            }
            if ("content".equals(key)) {
                object(value, object -> result.put(key, content(object, mode)));
            } else {
                result.put(key, copy(value));
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
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, RESPONSE_FIELDS)) {
                return;
            }
            switch (key) {
            case "headers" -> object(value, object -> result.put(key, headers(object, mode)));
            case "content" -> object(value, object -> result.put(key, content(object, mode)));
            case "links" -> object(value, object -> result.put(key, links(object)));
            default -> result.put(key, copy(value));
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
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, HEADER_FIELDS)) {
                return;
            }
            switch (key) {
            case "schema" -> result.put(key, schema(value, mode));
            case "content" -> object(value, object -> result.put(key, content(object, mode)));
            case "examples" -> result.put(key, examples(value));
            default -> result.put(key, copy(value));
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
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, MEDIA_TYPE_FIELDS)) {
                return;
            }
            switch (key) {
            case "schema" -> result.put(key, schema(value, mode));
            case "examples" -> result.put(key, examples(value));
            case "encoding" -> object(value, object -> result.put(key, encoding(object, mode)));
            default -> result.put(key, copy(value));
            }
        });
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
                result.put(key, copy(value));
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
            default -> result.put(key, copy(value));
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
        source.forEach((key, value) -> object(value, object -> result.put(key, securityScheme(object))));
        return result;
    }

    private static Map<String, Object> securityScheme(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, SECURITY_SCHEME_FIELDS)) {
                return;
            }
            if ("flows".equals(key)) {
                object(value, object -> result.put(key, oauthFlows(object)));
            } else {
                result.put(key, copy(value));
            }
        });
        return result;
    }

    private static Map<String, Object> oauthFlows(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (allowed(key, OAUTH_FLOWS_FIELDS)) {
                object(value, object -> result.put(key, copyAllowed(object, OAUTH_FLOW_FIELDS)));
            }
        });
        return result;
    }

    private static Map<String, Object> links(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> {
            Map<String, Object> link = new LinkedHashMap<>();
            object.forEach((linkKey, linkValue) -> {
                if (!allowed(linkKey, LINK_FIELDS)) {
                    return;
                }
                if ("server".equals(linkKey)) {
                    object(linkValue, server -> link.put(linkKey, server(server)));
                } else {
                    link.put(linkKey, copy(linkValue));
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
        map.forEach((key, item) -> object(item,
                                          object -> result.put(String.valueOf(key), copyAllowed(object, EXAMPLE_FIELDS))));
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
                    enumValue = singleValueList(copy(item));
                    hasEnum = true;
                }
                continue;
            }
            if (!allowed(key, SCHEMA_FIELDS)) {
                continue;
            }
            switch (key) {
            case "allOf", "oneOf", "anyOf" -> result.put(key, schemaList(item, mode));
            case "not", "items" -> result.put(key, schema(item, mode));
            case "properties" -> object(item, object -> result.put(key, schemaMap(object, mode)));
            case "additionalProperties" -> result.put(key, additionalProperties(item, mode));
            case "externalDocs" -> object(item, object -> result.put(key, copyAllowed(object, EXTERNAL_DOCS_FIELDS)));
            default -> result.put(key, copy(item));
            }
        }
        if (hasEnum) {
            result.put("enum", enumValue(enumValue, mode, nullable));
        }
        if (nullable) {
            if (mode == SchemaMode.CANONICAL) {
                addNullType(result);
                addNullEnum(result);
            } else {
                removeNullEnum(result);
                result.put("nullable", true);
            }
        }
        return result;
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
                return new TypeMapping(null, null, true);
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
            return new TypeMapping(null, null, nullable);
        }
        if (types.size() == 1) {
            return new TypeMapping(types.getFirst(), null, nullable);
        }
        List<Object> oneOf = new ArrayList<>();
        types.forEach(type -> oneOf.add(Map.of("type", type)));
        return new TypeMapping(null, oneOf, nullable);
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
            result.removeIf(item -> item == null);
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
        schema.put("enum", result);
    }

    private static List<Object> singleValueList(Object value) {
        List<Object> result = new ArrayList<>();
        result.add(value);
        return result;
    }

    private static JsonObject jsonObject(Map<String, Object> source) {
        JsonObject.Builder builder = JsonObject.builder();
        source.forEach((key, value) -> builder.set(key, jsonValue(value)));
        return builder.build();
    }

    private static JsonValue jsonValue(Object value) {
        if (value instanceof JsonValue jsonValue) {
            return jsonValue;
        }
        if (value instanceof Map<?, ?> map) {
            return jsonObject(objectMap(map));
        }
        if (value instanceof List<?> list) {
            return JsonArray.create(list.stream()
                                            .map(OpenApi30DocumentMapper::jsonValue)
                                            .toList());
        }
        if (value instanceof String string) {
            return JsonString.create(string);
        }
        if (value instanceof Boolean bool) {
            return JsonBoolean.create(bool);
        }
        if (value instanceof BigDecimal number) {
            return JsonNumber.create(number);
        }
        if (value instanceof Number number) {
            return JsonNumber.create(BigDecimal.valueOf(number.doubleValue()));
        }
        if (value == null) {
            return JsonNull.instance();
        }
        return JsonString.create(String.valueOf(value));
    }

    private static Map<String, Object> copyAllowed(Map<String, ?> source, Set<String> allowedFields) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (allowed(key, allowedFields)) {
                result.put(key, copy(value));
            }
        });
        return result;
    }

    private static boolean allowed(String key, Set<String> allowedFields) {
        return allowedFields.contains(key) || key.startsWith("x-");
    }

    private static Object copy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), copy(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(copy(item)));
            return result;
        }
        return value;
    }

    private static Map<String, Object> objectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Map<String, Object> objectMap(JsonObject object) {
        Map<String, Object> result = new LinkedHashMap<>();
        object.keysAsStrings()
                .forEach(key -> object.value(key)
                        .ifPresent(value -> result.put(key, value(value))));
        return result;
    }

    private static Object value(JsonValue value) {
        return switch (value.type()) {
        case OBJECT -> objectMap(value.asObject());
        case ARRAY -> value.asArray()
                .values()
                .stream()
                .map(OpenApi30DocumentMapper::value)
                .toList();
        case STRING -> value.asString().value();
        case NUMBER -> value.asNumber().bigDecimalValue();
        case BOOLEAN -> value.asBoolean().value();
        case NULL -> null;
        case UNKNOWN -> value.toString();
        };
    }

    private static void object(Object value, java.util.function.Consumer<Map<String, Object>> consumer) {
        if (value instanceof Map<?, ?> map) {
            consumer.accept(objectMap(map));
        }
    }

    private static List<Object> objectList(Object value,
                                           java.util.function.Function<Map<String, Object>, Map<String, Object>> mapper) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        list.forEach(item -> object(item, object -> result.add(mapper.apply(object))));
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
            if (type != null) {
                target.put("type", type);
            }
            if (oneOf != null) {
                target.put("oneOf", oneOf);
            }
        }
    }
}
