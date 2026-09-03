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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class OpenApiQueryStringValidator {
    private static final Set<String> QUERYSTRING_SCHEMA_FIELDS = Set.of("allowEmptyValue",
                                                                        "style",
                                                                        "explode",
                                                                        "allowReserved",
                                                                        "schema");

    private final OpenApiDialect dialect;
    private final OpenApiReferenceResolver resolver;

    private OpenApiQueryStringValidator(Map<String, Object> document, OpenApiDialect dialect) {
        this.dialect = dialect;
        this.resolver = OpenApiReferenceResolver.create(document);
    }

    static void validate(Map<String, Object> document, OpenApiDialect dialect) {
        OpenApiQueryStringValidator validator = new OpenApiQueryStringValidator(document, dialect);
        validator.validate(document);
    }

    private void validate(Map<String, Object> document) {
        if (!dialect.supportsQueryStringParameters()) {
            return;
        }
        object(object(document.get("components")).get("parameters"))
                .forEach((name, parameter) -> validateQueryStringParameter(
                        "components.parameters." + name,
                        object(parameter)));
        OpenApiDocumentWalker.walk(document, dialect, this::validateNode);
    }

    private boolean validateNode(OpenApiDocumentWalker.Node node) {
        switch (node.kind()) {
        case PATH_ITEM -> parameters(node.location() + ".parameters", node.value().get("parameters"));
        case OPERATION -> {
            List<Map<String, Object>> pathParameters = parameters(
                    node.parent().location() + ".parameters",
                    node.parent().value().get("parameters"));
            List<Map<String, Object>> operationParameters = parameters(
                    node.location() + ".parameters",
                    node.value().get("parameters"));
            validateEffectiveParameterLocations(node.location(), pathParameters, operationParameters);
        }
        default -> {
        }
        }
        return true;
    }

    private List<Map<String, Object>> parameters(String location, Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> parameter = object(list.get(i));
            OpenApiReferenceResolver.Resolution resolution = resolver.resolveComponent(parameter, "parameters");
            if (resolution.status() == OpenApiReferenceResolver.Status.RESOLVED && !resolution.value().isEmpty()) {
                validateQueryStringParameter(location + "[" + i + "]", resolution.value());
                result.add(resolution.value());
            }
        }
        validateParameterLocations(location, result);
        return result;
    }

    private void validateQueryStringParameter(String location, Map<String, Object> parameter) {
        if (!"querystring".equals(parameter.get("in"))) {
            return;
        }
        for (String field : QUERYSTRING_SCHEMA_FIELDS) {
            if (parameter.containsKey(field)) {
                throw new IllegalStateException("OpenAPI " + dialect.version() + " querystring parameter at " + location
                                                        + " cannot use schema-mode field " + field);
            }
        }
        if (object(parameter.get("content")).size() != 1) {
            throw new IllegalStateException("OpenAPI " + dialect.version() + " querystring parameter at " + location
                                                    + " must define exactly one content entry");
        }
    }

    private void validateEffectiveParameterLocations(String location,
                                                     List<Map<String, Object>> pathParameters,
                                                     List<Map<String, Object>> operationParameters) {
        Map<List<String>, Map<String, Object>> effective = new LinkedHashMap<>();
        pathParameters.forEach(parameter -> addEffectiveParameter(effective, parameter));
        operationParameters.forEach(parameter -> addEffectiveParameter(effective, parameter));
        validateParameterLocations(location, List.copyOf(effective.values()));
    }

    private static void addEffectiveParameter(Map<List<String>, Map<String, Object>> effective,
                                              Map<String, Object> parameter) {
        if (parameter.get("name") instanceof String name && parameter.get("in") instanceof String in) {
            effective.put(List.of(in, name), parameter);
        }
    }

    private void validateParameterLocations(String location, List<Map<String, Object>> parameters) {
        int queryStringCount = 0;
        boolean hasQuery = false;
        for (Map<String, Object> parameter : parameters) {
            if ("querystring".equals(parameter.get("in"))) {
                queryStringCount++;
            } else if ("query".equals(parameter.get("in"))) {
                hasQuery = true;
            }
        }
        if (queryStringCount > 1) {
            throw new IllegalStateException("OpenAPI " + dialect.version() + " parameters at " + location
                                                    + " cannot define more than one querystring parameter");
        }
        if (queryStringCount == 1 && hasQuery) {
            throw new IllegalStateException("OpenAPI " + dialect.version() + " parameters at " + location
                                                    + " cannot combine query and querystring parameters");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
}
