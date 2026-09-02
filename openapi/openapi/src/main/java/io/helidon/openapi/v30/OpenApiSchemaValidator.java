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

import java.util.List;
import java.util.Map;
import java.util.Set;

final class OpenApiSchemaValidator {
    private static final Set<String> SCHEMA_FIELDS = Set.of("contains",
                                                            "contentSchema",
                                                            "else",
                                                            "if",
                                                            "items",
                                                            "not",
                                                            "propertyNames",
                                                            "then",
                                                            "unevaluatedItems",
                                                            "unevaluatedProperties");
    private static final Set<String> SCHEMA_ARRAY_FIELDS = Set.of("allOf",
                                                                  "anyOf",
                                                                  "oneOf",
                                                                  "prefixItems");
    private static final Set<String> SCHEMA_MAP_FIELDS = Set.of("$defs",
                                                                "definitions",
                                                                "dependentSchemas",
                                                                "patternProperties",
                                                                "properties");

    private final OpenApiDialect dialect;

    private OpenApiSchemaValidator(OpenApiDialect dialect) {
        this.dialect = dialect;
    }

    static void validate(Map<String, Object> document, OpenApiDialect dialect) {
        OpenApiSchemaValidator validator = new OpenApiSchemaValidator(dialect);
        OpenApiDocumentWalker.walk(document, dialect, validator::validateNode);
    }

    private boolean validateNode(OpenApiDocumentWalker.Node node) {
        if (node.value().containsKey("$ref")
                && (node.kind() == OpenApiDocumentWalker.Kind.PARAMETER
                        || node.kind() == OpenApiDocumentWalker.Kind.HEADER
                        || node.kind() == OpenApiDocumentWalker.Kind.MEDIA_TYPE)) {
            return false;
        }
        if (node.kind() == OpenApiDocumentWalker.Kind.COMPONENTS) {
            object(node.value().get("schemas")).forEach((name, schema) -> validateSchema(
                    schema,
                    node.location() + ".schemas." + name,
                    false));
        }
        if (node.kind() == OpenApiDocumentWalker.Kind.PARAMETER
                || node.kind() == OpenApiDocumentWalker.Kind.HEADER
                || node.kind() == OpenApiDocumentWalker.Kind.MEDIA_TYPE) {
            if (node.value().containsKey("schema")) {
                validateSchema(node.value().get("schema"), node.location() + ".schema", false);
            }
            if (node.value().containsKey("itemSchema")) {
                validateSchema(node.value().get("itemSchema"), node.location() + ".itemSchema", false);
            }
        }
        return true;
    }

    private void validateSchema(Object value, String location, boolean booleanAllowedInOpenApi30) {
        if (value instanceof Boolean) {
            if (!dialect.supportsBooleanSchemas() && !booleanAllowedInOpenApi30) {
                throw new IllegalStateException("OpenAPI " + dialect.version() + " schema at " + location
                                                        + " must be an object");
            }
            return;
        }
        if (!(value instanceof Map<?, ?> rawSchema)) {
            throw new IllegalStateException("OpenAPI " + dialect.version() + " schema at " + location
                                                    + " must be an object or boolean");
        }
        Map<String, Object> schema = object(rawSchema);
        if (schema.containsKey("$ref")) {
            validateReference(schema.get("$ref"), location);
            if (dialect.schemaReferenceSiblingsIgnored()) {
                return;
            }
        }
        for (String field : SCHEMA_FIELDS) {
            if (schema.containsKey(field)) {
                validateSchema(schema.get(field), location + "." + field, false);
            }
        }
        if (dialect.additionalItemsHasSchemaValue() && schema.containsKey("additionalItems")) {
            validateSchema(schema.get("additionalItems"), location + ".additionalItems", false);
        }
        for (String field : SCHEMA_ARRAY_FIELDS) {
            if (schema.get(field) instanceof List<?> schemas) {
                for (int i = 0; i < schemas.size(); i++) {
                    validateSchema(schemas.get(i), location + "." + field + "[" + i + "]", false);
                }
            }
        }
        for (String field : SCHEMA_MAP_FIELDS) {
            object(schema.get(field)).forEach((name, nested) -> validateSchema(
                    nested,
                    location + "." + field + "." + name,
                    false));
        }
        if (schema.containsKey("additionalProperties")) {
            validateSchema(schema.get("additionalProperties"),
                           location + ".additionalProperties",
                           true);
        }
    }

    private void validateReference(Object value, String location) {
        if (!(value instanceof String reference)) {
            throw new IllegalStateException("OpenAPI " + dialect.version() + " Reference Object at " + location
                                                    + " field $ref must be a string");
        }
        if (OpenApiReferenceResolver.hasIpvFutureHost(reference)) {
            throw new IllegalStateException("OpenAPI " + dialect.version() + " Reference Object at " + location
                                                    + " field $ref uses an IPvFuture host literal, which is not supported: "
                                                    + reference);
        }
        if (!OpenApiReferenceResolver.isUriReference(reference)) {
            throw new IllegalStateException("OpenAPI " + dialect.version() + " Reference Object at " + location
                                                    + " field $ref must be a URI: " + reference);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
}
