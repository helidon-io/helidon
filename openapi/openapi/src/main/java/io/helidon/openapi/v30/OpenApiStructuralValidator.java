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
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class OpenApiStructuralValidator {
    private static final Set<String> API_KEY_LOCATIONS = Set.of("query", "header", "cookie");
    private static final Pattern COMPONENT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    private final OpenApiDialect dialect;
    private final OpenApiReferenceResolver resolver;
    private final IdentityHashMap<Map<String, Object>, ResolvedPathItem> pathItemResolutions = new IdentityHashMap<>();

    private OpenApiStructuralValidator(Map<String, Object> document, OpenApiDialect dialect) {
        this.dialect = dialect;
        this.resolver = OpenApiReferenceResolver.create(document);
    }

    static void validate(Map<String, Object> document, OpenApiDialect dialect) {
        OpenApiStructuralValidator validator = new OpenApiStructuralValidator(document, dialect);
        validator.validatePaths(document.get("paths"));
        validator.validateComponents(document.get("components"));
        OpenApiDocumentWalker.walk(document, dialect, validator::validateNode);
    }

    static void validateRoot(Map<String, Object> document, String targetVersion) {
        if (!(document.get("info") instanceof Map<?, ?>)) {
            throw new IllegalStateException("OpenAPI " + targetVersion + " document requires Info metadata");
        }
        if (targetVersion.startsWith("3.0")) {
            if (!(document.get("paths") instanceof Map<?, ?>)) {
                throw new IllegalStateException("OpenAPI " + targetVersion + " document requires a paths field");
            }
        } else if (!document.containsKey("paths")
                && !document.containsKey("components")
                && !document.containsKey("webhooks")) {
            throw new IllegalStateException("OpenAPI " + targetVersion
                                                    + " document requires at least one of paths, components, or webhooks");
        }
    }

    private static boolean isReferenceAlternative(OpenApiDocumentWalker.Node node) {
        if (!node.value().containsKey("$ref")) {
            return false;
        }
        return switch (node.kind()) {
        case CALLBACK, PARAMETER, HEADER, REQUEST_BODY, RESPONSE, MEDIA_TYPE, SECURITY_SCHEME, EXAMPLE, LINK -> true;
        default -> false;
        };
    }

    private static boolean isPathLiteral(char value) {
        return value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9'
                || "-._~!$&'()*+,;=:@".indexOf(value) >= 0;
    }

    private static boolean isHexDigit(char value) {
        return value >= '0' && value <= '9'
                || value >= 'a' && value <= 'f'
                || value >= 'A' && value <= 'F';
    }

    private static String displayName(OpenApiDocumentWalker.Kind kind) {
        return switch (kind) {
        case DOCUMENT -> "document";
        case INFO -> "Info";
        case CONTACT -> "Contact";
        case LICENSE -> "License";
        case EXTERNAL_DOCS -> "ExternalDocs";
        case SERVER -> "Server";
        case SERVER_VARIABLE -> "ServerVariable";
        case TAG -> "Tag";
        case COMPONENTS -> "Components";
        case PATH_ITEM -> "PathItem";
        case OPERATION -> "Operation";
        case CALLBACK -> "Callback";
        case PARAMETER -> "Parameter";
        case HEADER -> "Header";
        case REQUEST_BODY -> "RequestBody";
        case RESPONSE -> "Response";
        case MEDIA_TYPE -> "MediaType";
        case ENCODING -> "Encoding";
        case SECURITY_SCHEME -> "SecurityScheme";
        case OAUTH_FLOWS -> "OAuthFlows";
        case OAUTH_FLOW -> "OAuthFlow";
        case EXAMPLE -> "Example";
        case LINK -> "Link";
        };
    }

    private static ValueType fieldType(OpenApiDocumentWalker.Kind kind, String field) {
        return switch (kind) {
        case DOCUMENT -> switch (field) {
            case "openapi", "$self", "jsonSchemaDialect" -> ValueType.STRING;
            case "info", "paths", "webhooks", "components", "externalDocs" -> ValueType.OBJECT;
            case "servers", "security", "tags" -> ValueType.ARRAY;
            default -> ValueType.ANY;
        };
        case INFO -> switch (field) {
            case "title", "summary", "description", "termsOfService", "version" -> ValueType.STRING;
            case "contact", "license" -> ValueType.OBJECT;
            default -> ValueType.ANY;
        };
        case CONTACT -> switch (field) {
            case "name", "url", "email" -> ValueType.STRING;
            default -> ValueType.ANY;
        };
        case LICENSE -> switch (field) {
            case "name", "identifier", "url" -> ValueType.STRING;
            default -> ValueType.ANY;
        };
        case EXTERNAL_DOCS -> switch (field) {
            case "description", "url" -> ValueType.STRING;
            default -> ValueType.ANY;
        };
        case SERVER -> switch (field) {
            case "url", "description", "name" -> ValueType.STRING;
            case "variables" -> ValueType.OBJECT;
            default -> ValueType.ANY;
        };
        case SERVER_VARIABLE -> switch (field) {
            case "default", "description" -> ValueType.STRING;
            case "enum" -> ValueType.STRING_ARRAY;
            default -> ValueType.ANY;
        };
        case TAG -> switch (field) {
            case "name", "summary", "description", "parent", "kind" -> ValueType.STRING;
            case "externalDocs" -> ValueType.OBJECT;
            default -> ValueType.ANY;
        };
        case PATH_ITEM -> switch (field) {
            case "$ref", "summary", "description" -> ValueType.STRING;
            case "get", "put", "post", "delete", "options", "head", "patch", "trace", "query",
                 "additionalOperations" -> ValueType.OBJECT;
            case "servers", "parameters" -> ValueType.ARRAY;
            default -> ValueType.ANY;
        };
        case OPERATION -> switch (field) {
            case "summary", "description", "operationId" -> ValueType.STRING;
            case "externalDocs", "requestBody", "responses", "callbacks" -> ValueType.OBJECT;
            case "tags" -> ValueType.STRING_ARRAY;
            case "parameters", "security", "servers" -> ValueType.ARRAY;
            case "deprecated" -> ValueType.BOOLEAN;
            default -> ValueType.ANY;
        };
        case PARAMETER -> switch (field) {
            case "$ref", "summary", "description", "name", "in", "style" -> ValueType.STRING;
            case "required", "deprecated", "allowEmptyValue", "explode", "allowReserved" -> ValueType.BOOLEAN;
            case "schema" -> ValueType.SCHEMA;
            case "examples", "content" -> ValueType.OBJECT;
            default -> ValueType.ANY;
        };
        case HEADER -> switch (field) {
            case "$ref", "summary", "description", "style" -> ValueType.STRING;
            case "required", "deprecated", "explode", "allowReserved" -> ValueType.BOOLEAN;
            case "schema" -> ValueType.SCHEMA;
            case "examples", "content" -> ValueType.OBJECT;
            default -> ValueType.ANY;
        };
        case REQUEST_BODY -> switch (field) {
            case "$ref", "summary", "description" -> ValueType.STRING;
            case "content" -> ValueType.OBJECT;
            case "required" -> ValueType.BOOLEAN;
            default -> ValueType.ANY;
        };
        case RESPONSE -> switch (field) {
            case "$ref", "summary", "description" -> ValueType.STRING;
            case "headers", "content", "links" -> ValueType.OBJECT;
            default -> ValueType.ANY;
        };
        case MEDIA_TYPE -> switch (field) {
            case "$ref" -> ValueType.STRING;
            case "schema", "itemSchema" -> ValueType.SCHEMA;
            case "examples", "encoding", "itemEncoding" -> ValueType.OBJECT;
            case "prefixEncoding" -> ValueType.ARRAY;
            default -> ValueType.ANY;
        };
        case ENCODING -> switch (field) {
            case "contentType", "style" -> ValueType.STRING;
            case "headers", "encoding", "itemEncoding" -> ValueType.OBJECT;
            case "prefixEncoding" -> ValueType.ARRAY;
            case "explode", "allowReserved" -> ValueType.BOOLEAN;
            default -> ValueType.ANY;
        };
        case COMPONENTS -> switch (field) {
            case "schemas", "responses", "parameters", "examples", "requestBodies", "headers",
                 "securitySchemes", "links", "callbacks", "pathItems", "mediaTypes" -> ValueType.OBJECT;
            default -> ValueType.ANY;
        };
        case SECURITY_SCHEME -> switch (field) {
            case "$ref", "summary", "description", "type", "name", "in", "scheme", "bearerFormat",
                 "openIdConnectUrl", "oauth2MetadataUrl" -> ValueType.STRING;
            case "flows" -> ValueType.OBJECT;
            case "deprecated" -> ValueType.BOOLEAN;
            default -> ValueType.ANY;
        };
        case OAUTH_FLOWS -> switch (field) {
            case "implicit", "password", "clientCredentials", "authorizationCode", "deviceAuthorization" ->
                    ValueType.OBJECT;
            default -> ValueType.ANY;
        };
        case OAUTH_FLOW -> switch (field) {
            case "authorizationUrl", "deviceAuthorizationUrl", "tokenUrl", "refreshUrl" -> ValueType.STRING;
            case "scopes" -> ValueType.OBJECT;
            default -> ValueType.ANY;
        };
        case EXAMPLE -> switch (field) {
            case "$ref", "summary", "description", "externalValue", "serializedValue" -> ValueType.STRING;
            default -> ValueType.ANY;
        };
        case LINK -> switch (field) {
            case "$ref", "operationRef", "operationId", "description" -> ValueType.STRING;
            case "parameters", "server" -> ValueType.OBJECT;
            default -> ValueType.ANY;
        };
        default -> ValueType.ANY;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }

    private boolean validateNode(OpenApiDocumentWalker.Node node) {
        if (!node.hasObjectValue()) {
            throw new IllegalStateException(description(node) + " must be an object");
        }
        if (isReferenceAlternative(node)) {
            validateReferenceFields(node);
            return true;
        }
        if (node.kind() == OpenApiDocumentWalker.Kind.PATH_ITEM && node.value().containsKey("$ref")) {
            validateReferenceUri(node);
        }
        validateFieldTypes(node);
        validateRequiredFields(node);
        return true;
    }

    private void validateFieldTypes(OpenApiDocumentWalker.Node node) {
        Set<String> allowedFields = dialect.fields(node.kind());
        node.value().forEach((field, value) -> {
            if (!field.startsWith("x-") && allowedFields.contains(field)) {
                ValueType type = fieldType(node.kind(), field);
                if (!type.matches(value)) {
                    throw new IllegalStateException(description(node) + " field " + field
                                                            + " must be " + type.description());
                }
            }
        });
    }

    private void validateReferenceFields(OpenApiDocumentWalker.Node node) {
        if (!node.value().containsKey("$ref")) {
            return;
        }
        validateReferenceUri(node);
        if (!dialect.version().startsWith("3.0")) {
            for (String field : List.of("summary", "description")) {
                if (node.value().containsKey(field) && !(node.value().get(field) instanceof String)) {
                    throw new IllegalStateException(description(node) + " field " + field + " must be a string");
                }
            }
        }
    }

    private void validateReferenceUri(OpenApiDocumentWalker.Node node) {
        if (!(node.value().get("$ref") instanceof String reference)) {
            throw new IllegalStateException(description(node) + " field $ref must be a string");
        }
        if (OpenApiReferenceResolver.hasIpvFutureHost(reference)) {
            throw new IllegalStateException(description(node)
                                                    + " field $ref uses an IPvFuture host literal, which is not supported: "
                                                    + reference);
        }
        if (!OpenApiReferenceResolver.isUriReference(reference)) {
            throw new IllegalStateException(description(node) + " field $ref must be a URI: " + reference);
        }
    }

    private void validateRequiredFields(OpenApiDocumentWalker.Node node) {
        switch (node.kind()) {
        case INFO -> {
            requireString(node, "title", false);
            requireString(node, "version", false);
        }
        case LICENSE -> requireString(node, "name", false);
        case EXTERNAL_DOCS -> requireString(node, "url", false);
        case SERVER -> requireString(node, "url", false);
        case SERVER_VARIABLE -> validateServerVariable(node);
        case TAG -> requireString(node, "name", false);
        case PATH_ITEM -> validateParameterUniqueness(node);
        case OPERATION -> {
            validateOperation(node);
            validateParameterUniqueness(node);
        }
        case PARAMETER -> {
            validateParameter(node);
            validateExampleFields(node);
        }
        case HEADER -> {
            validateSchemaOrContent(node);
            validateExampleFields(node);
        }
        case REQUEST_BODY -> requireObject(node, "content");
        case RESPONSE -> {
            if (dialect.responseDescriptionRequired()) {
                requireString(node, "description", false);
            }
        }
        case SECURITY_SCHEME -> validateSecurityScheme(node);
        case OAUTH_FLOW -> validateOAuthFlow(node);
        case MEDIA_TYPE -> validateExampleFields(node);
        case EXAMPLE -> validateExample(node);
        case LINK -> validateLink(node);
        default -> {
        }
        }
    }

    private void validateServerVariable(OpenApiDocumentWalker.Node node) {
        requireString(node, "default", false);
        if (node.value().get("enum") instanceof List<?> values) {
            if (values.isEmpty()) {
                throw new IllegalStateException(description(node) + " enum must contain at least one value");
            }
            if (!values.contains(node.value().get("default"))) {
                throw new IllegalStateException(description(node) + " enum must contain its default value");
            }
        }
    }

    private void validateOperation(OpenApiDocumentWalker.Node node) {
        if (dialect.operationResponsesRequired()) {
            requireObject(node, "responses");
        }
        if (node.value().get("responses") instanceof Map<?, ?> responses
                && responses.keySet().stream()
                        .map(String::valueOf)
                        .noneMatch(OpenApiDocumentMapperSupport::isResponseCode)) {
            throw new IllegalStateException(description(node) + " responses require at least one response code");
        }
    }

    private void validateParameterUniqueness(OpenApiDocumentWalker.Node node) {
        if (!(node.value().get("parameters") instanceof List<?> parameters)) {
            return;
        }
        Set<List<String>> identities = new HashSet<>();
        for (Object parameterValue : parameters) {
            if (!(parameterValue instanceof Map<?, ?> parameter)) {
                continue;
            }
            OpenApiReferenceResolver.Resolution resolution = resolver.resolveReferenceChain(object(parameter));
            if (resolution.status() != OpenApiReferenceResolver.Status.RESOLVED
                    || !(resolution.value().get("name") instanceof String name)
                    || name.isBlank()
                    || !(resolution.value().get("in") instanceof String location)
                    || !dialect.parameterLocations().contains(location)) {
                continue;
            }
            String normalizedName = "header".equals(location) ? name.toLowerCase(Locale.ROOT) : name;
            if (!identities.add(List.of(normalizedName, location))) {
                throw new IllegalStateException(description(node) + " parameters contain duplicate "
                                                        + location + " parameter " + name);
            }
        }
    }

    private void validateParameter(OpenApiDocumentWalker.Node node) {
        requireString(node, "name", false);
        String location = requireString(node, "in", true);
        if (!dialect.parameterLocations().contains(location)) {
            throw new IllegalStateException(description(node) + " has unsupported location " + location);
        }
        if ("path".equals(location) && !Boolean.TRUE.equals(node.value().get("required"))) {
            throw new IllegalStateException(description(node) + " path parameter requires required: true");
        }
        if (!"querystring".equals(location)) {
            validateSchemaOrContent(node);
        }
    }

    private void validatePaths(Object value) {
        if (!(value instanceof Map<?, ?> paths)) {
            return;
        }
        paths.forEach((key, pathItemValue) -> {
            String path = String.valueOf(key);
            if (path.startsWith("x-")) {
                return;
            }
            Set<String> templateExpressions = validatePath(path);
            if (!templateExpressions.isEmpty() && pathItemValue instanceof Map<?, ?> pathItem) {
                validatePathParameters(path, templateExpressions, object(pathItem));
            }
        });
    }

    private Set<String> validatePath(String path) {
        if (!path.startsWith("/")) {
            throw invalidPath(path, "must start with /");
        }
        boolean validateTemplate = dialect.version().startsWith("3.2");
        Set<String> expressions = new HashSet<>();
        int expressionStart = -1;
        boolean segmentContent = false;
        for (int i = 1; i < path.length(); i++) {
            switch (path.charAt(i)) {
            case '{' -> {
                if (expressionStart >= 0) {
                    throw invalidPath(path, "must not contain nested path template expressions");
                }
                expressionStart = i + 1;
            }
            case '}' -> {
                if (expressionStart < 0) {
                    throw invalidPath(path, "contains an unmatched path template expression end");
                }
                String expression = path.substring(expressionStart, i);
                if (validateTemplate && expression.isEmpty()) {
                    throw invalidPath(path, "must not contain an empty path template expression");
                }
                if (!expressions.add(expression) && validateTemplate) {
                    throw invalidPath(path, "must not repeat path template expression {" + expression + "}");
                }
                expressionStart = -1;
                segmentContent = true;
            }
            case '?' -> {
                if (expressionStart < 0) {
                    throw invalidPath(path, "must not include a query string");
                }
            }
            case '#' -> {
                if (expressionStart < 0) {
                    throw invalidPath(path, "must not include a fragment");
                }
            }
            case '/' -> {
                if (validateTemplate && expressionStart < 0) {
                    if (!segmentContent) {
                        throw invalidPath(path, "must not contain an empty path segment");
                    }
                    segmentContent = false;
                }
            }
            case '%' -> {
                if (validateTemplate && expressionStart < 0) {
                    if (i + 2 >= path.length()
                            || !isHexDigit(path.charAt(i + 1))
                            || !isHexDigit(path.charAt(i + 2))) {
                        throw invalidPath(path, "contains an invalid percent-encoded path literal");
                    }
                    i += 2;
                    segmentContent = true;
                }
            }
            default -> {
                if (validateTemplate && expressionStart < 0) {
                    char pathLiteral = path.charAt(i);
                    if (!isPathLiteral(pathLiteral)) {
                        throw invalidPath(path, "contains invalid path literal character at index " + i);
                    }
                    segmentContent = true;
                }
            }
            }
        }
        if (expressionStart >= 0) {
            throw invalidPath(path, "contains an unclosed path template expression");
        }
        return expressions;
    }

    private void validatePathParameters(String path,
                                        Set<String> templateExpressions,
                                        Map<String, Object> pathItem) {
        ResolvedPathItem resolvedPathItem = resolvePathItem(pathItem);
        Map<String, Object> effectivePathItem = resolvedPathItem.value();
        PathParameters pathParameters = pathParameters(effectivePathItem.get("parameters"));
        boolean pathIndeterminate = resolvedPathItem.indeterminate() || pathParameters.indeterminate();

        for (String operationName : dialect.fixedPathOperationFields()) {
            if (effectivePathItem.containsKey(operationName)) {
                validateOperationPathParameters(path,
                                                operationName,
                                                effectivePathItem.get(operationName),
                                                templateExpressions,
                                                pathParameters.names(),
                                                pathIndeterminate);
            }
        }
        if (dialect.fields(OpenApiDocumentWalker.Kind.PATH_ITEM).contains("additionalOperations")
                && effectivePathItem.get("additionalOperations") instanceof Map<?, ?> additionalOperations) {
            additionalOperations.forEach((name, operation) -> validateOperationPathParameters(
                    path,
                    String.valueOf(name),
                    operation,
                    templateExpressions,
                    pathParameters.names(),
                    pathIndeterminate));
        }
    }

    private void validateOperationPathParameters(String path,
                                                 String operationName,
                                                 Object operationValue,
                                                 Set<String> templateExpressions,
                                                 Set<String> pathParameterNames,
                                                 boolean pathIndeterminate) {
        if (!(operationValue instanceof Map<?, ?> operation)) {
            return;
        }
        PathParameters operationParameters = pathParameters(operation.get("parameters"));
        Set<String> effectivePathParameterNames = new HashSet<>(pathParameterNames);
        effectivePathParameterNames.addAll(operationParameters.names());
        for (String expression : templateExpressions) {
            if (!effectivePathParameterNames.contains(expression)
                    && !pathIndeterminate
                    && !operationParameters.indeterminate()) {
                throw invalidPath(path, "operation " + operationName + " requires path parameter " + expression
                        + " for template expression {" + expression + "}");
            }
        }
    }

    private PathParameters pathParameters(Object value) {
        if (!(value instanceof List<?> parameters)) {
            return new PathParameters(Set.of(), false);
        }
        Set<String> names = new HashSet<>();
        boolean indeterminate = false;
        for (Object parameterValue : parameters) {
            if (!(parameterValue instanceof Map<?, ?> parameter)) {
                continue;
            }
            OpenApiReferenceResolver.Resolution resolution = resolver.resolveReferenceChain(object(parameter));
            if (resolution.status() != OpenApiReferenceResolver.Status.RESOLVED) {
                indeterminate = true;
            } else if ("path".equals(resolution.value().get("in"))
                    && resolution.value().get("name") instanceof String name) {
                names.add(name);
            }
        }
        return new PathParameters(names, indeterminate);
    }

    private ResolvedPathItem resolvePathItem(Map<String, Object> pathItem) {
        ResolvedPathItem cached = pathItemResolutions.get(pathItem);
        if (cached != null) {
            return cached;
        }
        Set<Map<String, Object>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Map<String, Object>> references = new ArrayList<>();
        Map<String, Object> current = pathItem;
        ResolvedPathItem resolved = null;
        boolean cacheable = true;
        while (true) {
            cached = pathItemResolutions.get(current);
            if (cached != null) {
                resolved = cached;
                break;
            }
            if (!visited.add(current)) {
                resolved = new ResolvedPathItem(Map.of(), false);
                cacheable = false;
                break;
            }
            references.add(current);
            OpenApiReferenceResolver.Resolution resolution = resolver.resolveReference(current);
            if (resolution.status() == OpenApiReferenceResolver.Status.EXTERNAL) {
                resolved = new ResolvedPathItem(current, true);
                break;
            }
            if (resolution.status() != OpenApiReferenceResolver.Status.RESOLVED || resolution.value() == current) {
                resolved = new ResolvedPathItem(current, false);
                break;
            }
            current = resolution.value();
        }

        for (int i = references.size() - 1; i >= 0; i--) {
            Map<String, Object> effective = new LinkedHashMap<>(resolved.value());
            references.get(i).forEach((name, value) -> {
                if (!"$ref".equals(name)) {
                    effective.put(name, value);
                }
            });
            resolved = new ResolvedPathItem(effective, resolved.indeterminate());
            if (cacheable) {
                pathItemResolutions.put(references.get(i), resolved);
            }
        }
        return resolved;
    }

    private IllegalStateException invalidPath(String path, String reason) {
        return new IllegalStateException("OpenAPI " + dialect.version() + " path " + path + " " + reason);
    }

    private void validateComponents(Object value) {
        if (!(value instanceof Map<?, ?> components)) {
            return;
        }
        Set<String> componentFields = dialect.fields(OpenApiDocumentWalker.Kind.COMPONENTS);
        components.forEach((field, entries) -> {
            String fieldName = String.valueOf(field);
            if (!componentFields.contains(fieldName) || !(entries instanceof Map<?, ?> namedComponents)) {
                return;
            }
            namedComponents.keySet().forEach(key -> {
                if (!(key instanceof String name) || !COMPONENT_NAME_PATTERN.matcher(name).matches()) {
                    throw new IllegalStateException("OpenAPI " + dialect.version() + " Components " + fieldName
                                                            + " name " + key + " must match [A-Za-z0-9._-]+");
                }
            });
        });
    }

    private void validateSecurityScheme(OpenApiDocumentWalker.Node node) {
        String type = requireString(node, "type", true);
        if (!dialect.securitySchemeTypes().contains(type)) {
            throw new IllegalStateException(description(node) + " has unsupported type " + type);
        }
        switch (type) {
        case "apiKey" -> {
            requireString(node, "name", true);
            String location = requireString(node, "in", true);
            if (!API_KEY_LOCATIONS.contains(location)) {
                throw new IllegalStateException(description(node) + " has unsupported API key location " + location);
            }
        }
        case "http" -> requireString(node, "scheme", true);
        case "oauth2" -> requireObject(node, "flows");
        case "openIdConnect" -> requireString(node, "openIdConnectUrl", false);
        default -> {
        }
        }
    }

    private void validateOAuthFlow(OpenApiDocumentWalker.Node node) {
        switch (node.name()) {
        case "implicit" -> requireString(node, "authorizationUrl", false);
        case "password", "clientCredentials" -> requireString(node, "tokenUrl", false);
        case "authorizationCode" -> {
            requireString(node, "authorizationUrl", false);
            requireString(node, "tokenUrl", false);
        }
        case "deviceAuthorization" -> {
            requireString(node, "deviceAuthorizationUrl", false);
            requireString(node, "tokenUrl", false);
        }
        default -> {
        }
        }
        requireObject(node, "scopes");
        ((Map<?, ?>) node.value().get("scopes")).forEach((name, scopeDescription) -> {
            if (!(scopeDescription instanceof String)) {
                throw new IllegalStateException(description(node) + " scope " + name + " must have a string description");
            }
        });
    }

    private void validateExample(OpenApiDocumentWalker.Node node) {
        validateMutuallyExclusive(node, "value", "externalValue");
        if (dialect.fields(OpenApiDocumentWalker.Kind.EXAMPLE).contains("dataValue")) {
            validateMutuallyExclusive(node, "value", "dataValue");
            validateMutuallyExclusive(node, "value", "serializedValue");
            validateMutuallyExclusive(node, "serializedValue", "externalValue");
        }
    }

    private void validateExampleFields(OpenApiDocumentWalker.Node node) {
        validateMutuallyExclusive(node, "example", "examples");
    }

    private void validateMutuallyExclusive(OpenApiDocumentWalker.Node node, String first, String second) {
        if (node.value().containsKey(first) && node.value().containsKey(second)) {
            throw new IllegalStateException(description(node) + " cannot combine " + first + " with " + second);
        }
    }

    private void validateLink(OpenApiDocumentWalker.Node node) {
        boolean hasOperationRef = node.value().containsKey("operationRef");
        boolean hasOperationId = node.value().containsKey("operationId");
        if (hasOperationRef == hasOperationId) {
            throw new IllegalStateException(description(node) + " requires exactly one of operationRef or operationId");
        }
    }

    private void validateSchemaOrContent(OpenApiDocumentWalker.Node node) {
        boolean hasSchema = node.value().containsKey("schema");
        boolean hasContent = node.value().containsKey("content");
        if (hasSchema == hasContent) {
            throw new IllegalStateException(description(node) + " requires exactly one of schema or content");
        }
        if (node.value().get("content") instanceof Map<?, ?> content && content.size() != 1) {
            throw new IllegalStateException(description(node) + " content must contain exactly one entry");
        }
    }

    private String requireString(OpenApiDocumentWalker.Node node, String field, boolean nonBlank) {
        Object value = node.value().get(field);
        if (!(value instanceof String string) || (nonBlank && string.isBlank())) {
            throw new IllegalStateException(description(node) + " requires " + field);
        }
        return string;
    }

    private void requireObject(OpenApiDocumentWalker.Node node, String field) {
        if (!(node.value().get(field) instanceof Map<?, ?>)) {
            throw new IllegalStateException(description(node) + " requires " + field);
        }
    }

    private String description(OpenApiDocumentWalker.Node node) {
        String location = node.location().isEmpty() ? "document" : node.location();
        return "OpenAPI " + dialect.version() + " " + displayName(node.kind()) + " at " + location;
    }

    private enum ValueType {
        ANY("a value") {
            @Override
            boolean matches(Object value) {
                return true;
            }
        },
        STRING("a string") {
            @Override
            boolean matches(Object value) {
                return value instanceof String;
            }
        },
        BOOLEAN("a boolean") {
            @Override
            boolean matches(Object value) {
                return value instanceof Boolean;
            }
        },
        OBJECT("an object") {
            @Override
            boolean matches(Object value) {
                return value instanceof Map<?, ?>;
            }
        },
        ARRAY("an array") {
            @Override
            boolean matches(Object value) {
                return value instanceof List<?>;
            }
        },
        STRING_ARRAY("an array of strings") {
            @Override
            boolean matches(Object value) {
                return value instanceof List<?> list && list.stream().allMatch(String.class::isInstance);
            }
        },
        SCHEMA("a schema") {
            @Override
            boolean matches(Object value) {
                return value instanceof Map<?, ?> || value instanceof Boolean;
            }
        };

        private final String description;

        ValueType(String description) {
            this.description = description;
        }

        abstract boolean matches(Object value);

        String description() {
            return description;
        }
    }

    private record PathParameters(Set<String> names, boolean indeterminate) {
    }

    private record ResolvedPathItem(Map<String, Object> value, boolean indeterminate) {
    }
}
