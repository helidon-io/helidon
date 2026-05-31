/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

package io.helidon.openapi.v32;

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
import io.helidon.openapi.v30.OpenApiDocumentReader;

final class OpenApi32DocumentMapper {
    private static final Set<String> DOCUMENT_FIELDS = Set.of("openapi",
                                                              "$self",
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
                                                            "name",
                                                            "variables");
    private static final Set<String> SERVER_VARIABLE_FIELDS = Set.of("enum",
                                                                     "default",
                                                                     "description");
    private static final Set<String> TAG_FIELDS = Set.of("name",
                                                         "summary",
                                                         "description",
                                                         "externalDocs",
                                                         "parent",
                                                         "kind");
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
                                                               "query",
                                                               "additionalOperations",
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
                                                              "summary",
                                                              "description",
                                                              "headers",
                                                              "content",
                                                              "links");
    private static final Set<String> MEDIA_TYPE_FIELDS = Set.of("schema",
                                                                "itemSchema",
                                                                "example",
                                                                "examples",
                                                                "encoding",
                                                                "prefixEncoding",
                                                                "itemEncoding");
    private static final Set<String> ENCODING_FIELDS = Set.of("contentType",
                                                              "headers",
                                                              "encoding",
                                                              "prefixEncoding",
                                                              "itemEncoding",
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
                                                                "pathItems",
                                                                "mediaTypes");
    private static final Set<String> SECURITY_SCHEME_FIELDS = Set.of("$ref",
                                                                     "type",
                                                                     "description",
                                                                     "name",
                                                                     "in",
                                                                     "scheme",
                                                                     "bearerFormat",
                                                                     "flows",
                                                                     "openIdConnectUrl",
                                                                     "oauth2MetadataUrl",
                                                                     "deprecated");
    private static final Set<String> OAUTH_FLOWS_FIELDS = Set.of("implicit",
                                                                 "password",
                                                                 "clientCredentials",
                                                                 "authorizationCode",
                                                                 "deviceAuthorization");
    private static final Set<String> OAUTH_FLOW_FIELDS = Set.of("authorizationUrl",
                                                                "tokenUrl",
                                                                "refreshUrl",
                                                                "scopes",
                                                                "deviceAuthorizationUrl");
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
                                                             "externalValue",
                                                             "dataValue",
                                                             "serializedValue");
    private static final Set<String> EXTERNAL_DOCS_FIELDS = Set.of("description",
                                                                   "url");
    private static final Set<String> PARAMETER_LOCATIONS = Set.of("query",
                                                                  "header",
                                                                  "path",
                                                                  "cookie",
                                                                  "querystring");

    private OpenApi32DocumentMapper() {
    }

    static OpenApiDocument parse(Map<String, ?> document) {
        validateOpenApi32(document.get("openapi"));
        return OpenApiDocumentReader.read(jsonObject(document(document)));
    }

    static Map<String, Object> render(OpenApiDocument document, String version) {
        Map<String, Object> rendered = document(objectMap(document.toJsonObject()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openapi", version);
        rendered.forEach((key, value) -> {
            if (!"openapi".equals(key)) {
                result.put(key, value);
            }
        });
        return result;
    }

    private static Map<String, Object> document(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, DOCUMENT_FIELDS)) {
                return;
            }
            switch (key) {
            case "info" -> object(value, object -> result.put(key, info(object)));
            case "servers" -> result.put(key, serverList(value));
            case "paths", "webhooks" -> object(value, object -> result.put(key, paths(object)));
            case "components" -> object(value, object -> result.put(key, components(object)));
            case "tags" -> result.put(key, tagList(value));
            case "externalDocs" -> object(value, object -> result.put(key, copyAllowed(object, EXTERNAL_DOCS_FIELDS)));
            default -> result.put(key, copy(value));
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
            default -> result.put(key, copy(value));
            }
        });
        return result;
    }

    private static List<Object> serverList(Object value) {
        return objectList(value, OpenApi32DocumentMapper::server);
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

    private static Map<String, Object> paths(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, pathItem(object))));
        return result;
    }

    private static Map<String, Object> pathItem(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, PATH_ITEM_FIELDS)) {
                return;
            }
            if (isFixedPathOperationField(key)) {
                object(value, object -> result.put(key, operation(object)));
                return;
            }
            switch (key) {
            case "additionalOperations" -> object(value, object -> result.put(key, additionalOperations(object)));
            case "servers" -> result.put(key, serverList(value));
            case "parameters" -> result.put(key, parameters(value));
            default -> result.put(key, copy(value));
            }
        });
        return result;
    }

    private static Map<String, Object> additionalOperations(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, operation(object))));
        return result;
    }

    private static Map<String, Object> operation(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, OPERATION_FIELDS)) {
                return;
            }
            switch (key) {
            case "parameters" -> result.put(key, parameters(value));
            case "requestBody" -> object(value, object -> result.put(key, requestBody(object)));
            case "responses" -> object(value, object -> result.put(key, responses(object)));
            case "callbacks" -> object(value, object -> result.put(key, callbacks(object)));
            case "servers" -> result.put(key, serverList(value));
            case "externalDocs" -> object(value, object -> result.put(key, copyAllowed(object, EXTERNAL_DOCS_FIELDS)));
            default -> result.put(key, copy(value));
            }
        });
        return result;
    }

    private static List<Object> parameters(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            object(item, object -> {
                Map<String, Object> parameter = parameter(object);
                if (!parameter.isEmpty()) {
                    result.add(parameter);
                }
            });
        }
        return result;
    }

    private static Map<String, Object> parameter(Map<String, ?> source) {
        if (!source.containsKey("$ref") && !PARAMETER_LOCATIONS.contains(String.valueOf(source.get("in")))) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, PARAMETER_FIELDS)) {
                return;
            }
            switch (key) {
            case "content" -> object(value, object -> result.put(key, content(object)));
            case "examples" -> result.put(key, examples(value));
            default -> result.put(key, copy(value));
            }
        });
        return result;
    }

    private static Map<String, Object> requestBody(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, REQUEST_BODY_FIELDS)) {
                return;
            }
            if ("content".equals(key)) {
                object(value, object -> result.put(key, content(object)));
            } else {
                result.put(key, copy(value));
            }
        });
        return result;
    }

    private static Map<String, Object> responses(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, response(object))));
        return result;
    }

    private static Map<String, Object> response(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, RESPONSE_FIELDS)) {
                return;
            }
            switch (key) {
            case "headers" -> object(value, object -> result.put(key, headers(object)));
            case "content" -> object(value, object -> result.put(key, content(object)));
            case "links" -> object(value, object -> result.put(key, links(object)));
            default -> result.put(key, copy(value));
            }
        });
        return result;
    }

    private static Map<String, Object> headers(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, header(object))));
        return result;
    }

    private static Map<String, Object> header(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, HEADER_FIELDS)) {
                return;
            }
            switch (key) {
            case "content" -> object(value, object -> result.put(key, content(object)));
            case "examples" -> result.put(key, examples(value));
            default -> result.put(key, copy(value));
            }
        });
        return result;
    }

    private static Map<String, Object> content(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, mediaType(object))));
        return result;
    }

    private static Map<String, Object> mediaType(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, MEDIA_TYPE_FIELDS)) {
                return;
            }
            switch (key) {
            case "examples" -> result.put(key, examples(value));
            case "encoding" -> object(value, object -> result.put(key, encoding(object)));
            case "itemEncoding" -> object(value, object -> result.put(key, encoding(object)));
            default -> result.put(key, copy(value));
            }
        });
        return result;
    }

    private static Map<String, Object> encoding(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, ENCODING_FIELDS)) {
                return;
            }
            switch (key) {
            case "headers" -> object(value, object -> result.put(key, headers(object)));
            case "encoding" -> object(value, object -> result.put(key, encodings(object)));
            case "itemEncoding" -> object(value, object -> result.put(key, encoding(object)));
            default -> result.put(key, copy(value));
            }
        });
        return result;
    }

    private static Map<String, Object> encodings(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, encoding(object))));
        return result;
    }

    private static Map<String, Object> components(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, COMPONENTS_FIELDS)) {
                return;
            }
            switch (key) {
            case "responses" -> object(value, object -> result.put(key, responses(object)));
            case "parameters" -> object(value, object -> result.put(key, parameterMap(object)));
            case "examples" -> result.put(key, examples(value));
            case "requestBodies" -> object(value, object -> result.put(key, requestBodyMap(object)));
            case "headers" -> object(value, object -> result.put(key, headers(object)));
            case "securitySchemes" -> object(value, object -> result.put(key, securitySchemes(object)));
            case "links" -> object(value, object -> result.put(key, links(object)));
            case "callbacks" -> object(value, object -> result.put(key, callbacks(object)));
            case "pathItems" -> object(value, object -> result.put(key, paths(object)));
            case "mediaTypes" -> object(value, object -> result.put(key, mediaTypes(object)));
            default -> result.put(key, copy(value));
            }
        });
        return result;
    }

    private static Map<String, Object> mediaTypes(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, mediaType(object))));
        return result;
    }

    private static Map<String, Object> parameterMap(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> {
            Map<String, Object> parameter = parameter(object);
            if (!parameter.isEmpty()) {
                result.put(key, parameter);
            }
        }));
        return result;
    }

    private static Map<String, Object> requestBodyMap(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, requestBody(object))));
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

    private static Map<String, Object> callbacks(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, pathItem(object))));
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
                                            .map(OpenApi32DocumentMapper::jsonValue)
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
                .map(OpenApi32DocumentMapper::value)
                .toList();
        case STRING -> value.asString().value();
        case NUMBER -> value.asNumber().bigDecimalValue();
        case BOOLEAN -> value.asBoolean().value();
        case NULL -> null;
        case UNKNOWN -> value.toString();
        };
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

    private static void validateOpenApi32(Object version) {
        if (!(version instanceof String string) || (!string.equals("3.2") && !string.startsWith("3.2."))) {
            throw new IllegalStateException("OpenAPI 3.2 parser requires a 3.2 document, got: " + version);
        }
    }

    private static boolean isFixedPathOperationField(String value) {
        return switch (value) {
        case "get", "put", "post", "delete", "options", "head", "patch", "trace", "query" -> true;
        default -> false;
        };
    }
}
