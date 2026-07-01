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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.Api;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;

/**
 * Shared support for OpenAPI version document mappers.
 */
@Api.Internal
public final class OpenApiDocumentMapperSupport {
    private static final Set<String> REFERENCE_FIELDS = Set.of("$ref",
                                                               "summary",
                                                               "description");

    private OpenApiDocumentMapperSupport() {
    }

    /**
     * Convert key-to-value data into a JSON object.
     *
     * @param source source values
     * @return JSON object
     */
    public static JsonObject jsonObject(Map<String, Object> source) {
        Objects.requireNonNull(source);
        JsonObject.Builder builder = JsonObject.builder();
        source.forEach((key, value) -> builder.set(Objects.requireNonNull(key), jsonValueOrNull(value)));
        return builder.build();
    }

    /**
     * Convert a value into a JSON value.
     *
     * @param value source value
     * @return JSON value
     */
    public static JsonValue jsonValue(Object value) {
        Objects.requireNonNull(value);
        if (value instanceof JsonValue jsonValue) {
            return jsonValue;
        }
        if (value instanceof Map<?, ?> map) {
            return jsonObject(objectMap(map));
        }
        if (value instanceof List<?> list) {
            return JsonArray.create(list.stream()
                                            .map(OpenApiDocumentMapperSupport::jsonValueOrNull)
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
            return jsonNumber(number);
        }
        return JsonString.create(String.valueOf(value));
    }

    /**
     * Convert a number into a JSON number without reducing precision.
     *
     * @param number number
     * @return JSON number
     */
    public static JsonNumber jsonNumber(Number number) {
        Objects.requireNonNull(number);
        if (number instanceof Byte
                || number instanceof Short
                || number instanceof Integer
                || number instanceof Long) {
            return JsonNumber.create(number.longValue());
        }
        if (number instanceof BigInteger bigInteger) {
            return JsonNumber.create(new BigDecimal(bigInteger));
        }
        return JsonNumber.create(new BigDecimal(number.toString()));
    }

    /**
     * Copy allowed key-to-value fields.
     *
     * @param source source values
     * @param allowedFields allowed field names
     * @return copied values
     */
    public static Map<String, Object> copyAllowed(Map<String, ?> source, Set<String> allowedFields) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(allowedFields);
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (allowed(key, allowedFields)) {
                result.put(key, copyValue(value));
            }
        });
        return result;
    }

    /**
     * Copy a source field to a target.
     *
     * @param target target values
     * @param key field key
     * @param source source values
     */
    public static void copyField(Map<String, Object> target, String key, Map<String, ?> source) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(key);
        Objects.requireNonNull(source);
        if (!source.containsKey(key)) {
            throw new IllegalArgumentException("Source field does not exist: " + key);
        }
        target.put(key, copyValue(source.get(key)));
    }

    /**
     * Copy a source field value.
     *
     * @param key field key
     * @param source source values
     * @return copied value
     */
    public static Object copyFieldValue(String key, Map<String, ?> source) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(source);
        if (!source.containsKey(key)) {
            throw new IllegalArgumentException("Source field does not exist: " + key);
        }
        return copyValue(source.get(key));
    }

    /**
     * Check if a field is allowed.
     *
     * @param key field name
     * @param allowedFields allowed field names
     * @return whether the field is allowed
     */
    public static boolean allowed(String key, Set<String> allowedFields) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(allowedFields);
        return allowedFields.contains(key) || key.startsWith("x-");
    }

    /**
     * Deep-copy key-to-value or list values.
     *
     * @param value value to copy
     * @return copied value
     */
    public static Object copy(Object value) {
        Objects.requireNonNull(value);
        return copyValue(value);
    }

    private static Object copyValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(Objects.requireNonNull(key)), copyValue(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(copyValue(item)));
            return result;
        }
        return value;
    }

    /**
     * Convert arbitrary map keys to strings.
     *
     * @param source source values
     * @return string-keyed values
     */
    public static Map<String, Object> objectMap(Map<?, ?> source) {
        Objects.requireNonNull(source);
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(Objects.requireNonNull(key)),
                                                  value));
        return result;
    }

    /**
     * Convert a JSON object to key-to-value data.
     *
     * @param object JSON object
     * @return key-to-value data
     */
    public static Map<String, Object> objectMap(JsonObject object) {
        Objects.requireNonNull(object);
        Map<String, Object> result = new LinkedHashMap<>();
        object.keysAsStrings()
                .forEach(key -> object.value(key)
                        .ifPresent(value -> result.put(key, javaValue(value))));
        return result;
    }

    private static Object javaValue(JsonValue value) {
        Objects.requireNonNull(value);
        return switch (value.type()) {
        case OBJECT -> objectMap(value.asObject());
        case ARRAY -> value.asArray()
                .values()
                .stream()
                .map(OpenApiDocumentMapperSupport::javaValue)
                .toList();
        case STRING -> value.asString().value();
        case NUMBER -> value.asNumber().bigDecimalValue();
        case BOOLEAN -> value.asBoolean().value();
        case NULL -> null;
        case UNKNOWN -> value.toString();
        };
    }

    private static JsonValue jsonValueOrNull(Object value) {
        return value == null ? JsonNull.instance() : jsonValue(value);
    }

    /**
     * Run a consumer if the value is key-to-value data.
     *
     * @param value value to inspect
     * @param consumer consumer
     */
    public static void object(Object value, Consumer<Map<String, Object>> consumer) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(consumer);
        if (value instanceof Map<?, ?> map) {
            consumer.accept(objectMap(map));
        }
    }

    /**
     * Map a list of key-to-value data values.
     *
     * @param value source value
     * @param mapper item mapper
     * @return mapped list
     */
    public static List<Object> objectList(Object value, Function<Map<String, Object>, Map<String, Object>> mapper) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(mapper);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        list.forEach(item -> object(item, object -> result.add(mapper.apply(object))));
        return result;
    }

    /**
     * Filter and normalize an OpenAPI 3.x document using version-specific field rules.
     *
     * @param source source document
     * @param rules version-specific rules
     * @return filtered document
     */
    public static Map<String, Object> document3x(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.documentFields())) {
                return;
            }
            switch (key) {
            case "info" -> object(value, object -> result.put(key, info(object, rules)));
            case "servers" -> result.put(key, serverList(value, rules));
            case "paths" -> object(value, object -> result.put(key, paths(object, rules, true)));
            case "webhooks" -> object(value, object -> result.put(key, paths(object, rules, false)));
            case "components" -> object(value, object -> result.put(key, components(object, rules)));
            case "tags" -> result.put(key, tagList(value, rules));
            case "externalDocs" -> object(value, object -> result.put(key, copyAllowed(object, rules.externalDocsFields())));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> info(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.infoFields())) {
                return;
            }
            switch (key) {
            case "contact" -> object(value, object -> result.put(key, copyAllowed(object, rules.contactFields())));
            case "license" -> object(value, object -> result.put(key, copyAllowed(object, rules.licenseFields())));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static List<Object> serverList(Object value, OpenApi3xMapperRules rules) {
        return objectList(value, server -> server(server, rules));
    }

    private static Map<String, Object> server(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.serverFields())) {
                return;
            }
            if ("variables".equals(key)) {
                object(value, object -> result.put(key, serverVariables(object, rules)));
            } else {
                copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> serverVariables(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value,
                                             object -> result.put(key,
                                                                  copyAllowed(object, rules.serverVariableFields()))));
        return result;
    }

    private static List<Object> tagList(Object value, OpenApi3xMapperRules rules) {
        return objectList(value, tag -> {
            Map<String, Object> result = new LinkedHashMap<>();
            tag.forEach((key, item) -> {
                if (!allowed(key, rules.tagFields())) {
                    return;
                }
                if ("externalDocs".equals(key)) {
                    object(item, object -> result.put(key, copyAllowed(object, rules.externalDocsFields())));
                } else {
                    copyField(result, key, tag);
                }
            });
            return result;
        });
    }

    private static Map<String, Object> paths(Map<String, ?> source,
                                             OpenApi3xMapperRules rules,
                                             boolean containerExtensions) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (containerExtensions && key.startsWith("x-")) {
                copyField(result, key, source);
            } else {
                object(value, object -> result.put(key, pathItem(object, rules)));
            }
        });
        return result;
    }

    private static Map<String, Object> pathItem(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.pathItemFields())) {
                return;
            }
            if (rules.fixedPathOperationFields().contains(key)) {
                object(value, object -> result.put(key, operation(object, rules)));
                return;
            }
            switch (key) {
            case "additionalOperations" -> object(value, object -> result.put(key, additionalOperations(object, rules)));
            case "servers" -> result.put(key, serverList(value, rules));
            case "parameters" -> result.put(key, parameters(value, rules));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> additionalOperations(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, operation(object, rules))));
        return result;
    }

    private static Map<String, Object> operation(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.operationFields())) {
                return;
            }
            switch (key) {
            case "parameters" -> result.put(key, parameters(value, rules));
            case "requestBody" -> object(value, object -> result.put(key, requestBody(object, rules)));
            case "responses" -> object(value, object -> result.put(key, responses(object, rules, true)));
            case "callbacks" -> object(value, object -> result.put(key, callbacks(object, rules)));
            case "servers" -> result.put(key, serverList(value, rules));
            case "externalDocs" -> object(value, object -> result.put(key, copyAllowed(object, rules.externalDocsFields())));
            default -> copyField(result, key, source);
            }
        });
        if (rules.operationResponsesRequired() && !result.containsKey("responses")) {
            throw new IllegalStateException("OpenAPI " + rules.targetVersion() + " operation requires responses.");
        }
        return result;
    }

    private static List<Object> parameters(Object value, OpenApi3xMapperRules rules) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            object(item, object -> {
                Map<String, Object> parameter = parameter(object, rules);
                if (!parameter.isEmpty()) {
                    result.add(parameter);
                }
            });
        }
        return result;
    }

    private static Map<String, Object> parameter(Map<String, ?> source, OpenApi3xMapperRules rules) {
        if (!source.containsKey("$ref") && !rules.parameterLocations().contains(String.valueOf(source.get("in")))) {
            return Map.of();
        }
        if (source.containsKey("$ref")) {
            return reference(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.parameterFields())) {
                return;
            }
            switch (key) {
            case "content" -> object(value, object -> result.put(key, content(object, rules)));
            case "examples" -> result.put(key, examples(value, rules));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> requestBody(Map<String, ?> source, OpenApi3xMapperRules rules) {
        if (source.containsKey("$ref")) {
            return reference(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.requestBodyFields())) {
                return;
            }
            if ("content".equals(key)) {
                object(value, object -> result.put(key, content(object, rules)));
            } else {
                copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> responses(Map<String, ?> source,
                                                 OpenApi3xMapperRules rules,
                                                 boolean containerExtensions) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (containerExtensions && key.startsWith("x-")) {
                copyField(result, key, source);
                return;
            }
            object(value, object -> result.put(key, response(object, rules)));
        });
        return result;
    }

    private static Map<String, Object> response(Map<String, ?> source, OpenApi3xMapperRules rules) {
        if (source.containsKey("$ref")) {
            return reference(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.responseFields())) {
                return;
            }
            switch (key) {
            case "headers" -> object(value, object -> result.put(key, headers(object, rules)));
            case "content" -> object(value, object -> result.put(key, content(object, rules)));
            case "links" -> object(value, object -> result.put(key, links(object, rules)));
            default -> copyField(result, key, source);
            }
        });
        if (rules.responseDescriptionRequired()) {
            Object description = result.get("description");
            if (!(description instanceof String)) {
                throw new IllegalStateException("OpenAPI " + rules.targetVersion() + " response requires description.");
            }
        }
        return result;
    }

    private static Map<String, Object> headers(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, header(object, rules))));
        return result;
    }

    private static Map<String, Object> header(Map<String, ?> source, OpenApi3xMapperRules rules) {
        if (source.containsKey("$ref")) {
            return reference(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.headerFields())) {
                return;
            }
            switch (key) {
            case "content" -> object(value, object -> result.put(key, content(object, rules)));
            case "examples" -> result.put(key, examples(value, rules));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> content(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, mediaType(object, rules))));
        return result;
    }

    private static Map<String, Object> mediaType(Map<String, ?> source, OpenApi3xMapperRules rules) {
        if (source.containsKey("$ref")) {
            if (rules.mediaTypeFields().contains("$ref")) {
                return reference(source);
            }
            throw unsupported(rules, "media type reference", String.valueOf(source.get("$ref")), "mediaType");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.mediaTypeFields())) {
                return;
            }
            switch (key) {
            case "examples" -> result.put(key, examples(value, rules));
            case "encoding" -> object(value, object -> result.put(key, encodings(object, rules)));
            case "itemEncoding" -> object(value, object -> result.put(key, encoding(object, rules)));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> encoding(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.encodingFields())) {
                return;
            }
            switch (key) {
            case "headers" -> object(value, object -> result.put(key, headers(object, rules)));
            case "encoding" -> object(value, object -> result.put(key, encodings(object, rules)));
            case "itemEncoding" -> object(value, object -> result.put(key, encoding(object, rules)));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> encodings(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, encoding(object, rules))));
        return result;
    }

    private static Map<String, Object> components(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.componentsFields())) {
                return;
            }
            switch (key) {
            case "responses" -> object(value, object -> result.put(key, responses(object, rules, false)));
            case "parameters" -> object(value, object -> result.put(key, parameterMap(object, rules)));
            case "examples" -> result.put(key, examples(value, rules));
            case "requestBodies" -> object(value, object -> result.put(key, requestBodyMap(object, rules)));
            case "headers" -> object(value, object -> result.put(key, headers(object, rules)));
            case "securitySchemes" -> object(value, object -> result.put(key, securitySchemes(object, rules)));
            case "links" -> object(value, object -> result.put(key, links(object, rules)));
            case "callbacks" -> object(value, object -> result.put(key, callbacks(object, rules)));
            case "pathItems" -> object(value, object -> result.put(key, paths(object, rules, false)));
            case "mediaTypes" -> object(value, object -> result.put(key, mediaTypes(object, rules)));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> mediaTypes(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, mediaType(object, rules))));
        return result;
    }

    private static Map<String, Object> parameterMap(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> {
            Map<String, Object> parameter = parameter(object, rules);
            if (!parameter.isEmpty()) {
                result.put(key, parameter);
            }
        }));
        return result;
    }

    private static Map<String, Object> requestBodyMap(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, requestBody(object, rules))));
        return result;
    }

    private static Map<String, Object> securitySchemes(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, securityScheme(key, object, rules))));
        return result;
    }

    private static Map<String, Object> securityScheme(String name,
                                                      Map<String, ?> source,
                                                      OpenApi3xMapperRules rules) {
        if (source.containsKey("$ref")) {
            return reference(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!allowed(key, rules.securitySchemeFields())) {
                return;
            }
            switch (key) {
            case "type" -> {
                String type = String.valueOf(value);
                if (!rules.securitySchemeTypes().contains(type)) {
                    throw unsupported(rules, "security scheme type", type, securitySchemePath(name));
                }
                copyField(result, key, source);
            }
            case "flows" -> object(value, object -> result.put(key,
                                                               oauthFlows(securitySchemePath(name) + ".flows",
                                                                          object,
                                                                          rules)));
            default -> copyField(result, key, source);
            }
        });
        return result;
    }

    private static Map<String, Object> oauthFlows(String path, Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key.startsWith("x-")) {
                copyField(result, key, source);
                return;
            }
            if (!allowed(key, rules.oauthFlowsFields())) {
                throw unsupported(rules, "OAuth flow", key, path);
            }
            object(value, object -> result.put(key, copyAllowed(object, rules.oauthFlowFields())));
        });
        return result;
    }

    private static IllegalStateException unsupported(OpenApi3xMapperRules rules,
                                                     String kind,
                                                     String value,
                                                     String path) {
        return new IllegalStateException("Unsupported OpenAPI "
                                                 + rules.targetVersion()
                                                 + " "
                                                 + kind
                                                 + " '"
                                                 + value
                                                 + "' at "
                                                 + path);
    }

    private static String securitySchemePath(String name) {
        return "components.securitySchemes." + name;
    }

    private static Map<String, Object> links(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> {
            if (object.containsKey("$ref")) {
                result.put(key, reference(object));
                return;
            }
            Map<String, Object> link = new LinkedHashMap<>();
            object.forEach((linkKey, linkValue) -> {
                if (!allowed(linkKey, rules.linkFields())) {
                    return;
                }
                if ("server".equals(linkKey)) {
                    object(linkValue, server -> link.put(linkKey, server(server, rules)));
                } else {
                    copyField(link, linkKey, object);
                }
            });
            result.put(key, link);
        }));
        return result;
    }

    private static Map<String, Object> callbacks(Map<String, ?> source, OpenApi3xMapperRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> object(value, object -> result.put(key, callback(object, rules))));
        return result;
    }

    private static Map<String, Object> callback(Map<String, ?> source, OpenApi3xMapperRules rules) {
        if (source.containsKey("$ref")) {
            return reference(source);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key.startsWith("x-")) {
                copyField(result, key, source);
            } else {
                object(value, object -> result.put(key, pathItem(object, rules)));
            }
        });
        return result;
    }

    private static Map<String, Object> examples(Object value, OpenApi3xMapperRules rules) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> object(item, object -> {
            if (object.containsKey("$ref")) {
                result.put(String.valueOf(key), reference(object));
            } else {
                result.put(String.valueOf(key), copyAllowed(object, rules.exampleFields()));
            }
        }));
        return result;
    }

    private static Map<String, Object> reference(Map<String, ?> source) {
        return copyAllowed(source, REFERENCE_FIELDS);
    }
}
