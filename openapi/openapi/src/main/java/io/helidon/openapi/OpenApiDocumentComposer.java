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

package io.helidon.openapi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.openapi.spi.OpenApiDocumentSource;

final class OpenApiDocumentComposer {
    private static final Set<String> SCHEMA_VALUE_FIELDS = Set.of("additionalItems",
                                                                  "additionalProperties",
                                                                  "allOf",
                                                                  "anyOf",
                                                                  "contains",
                                                                  "contentSchema",
                                                                  "else",
                                                                  "if",
                                                                  "items",
                                                                  "not",
                                                                  "oneOf",
                                                                  "prefixItems",
                                                                  "propertyNames",
                                                                  "then",
                                                                  "unevaluatedItems",
                                                                  "unevaluatedProperties");
    private static final Set<String> SCHEMA_MAP_FIELDS = Set.of("$defs",
                                                                "definitions",
                                                                "dependencies",
                                                                "dependentSchemas",
                                                                "patternProperties",
                                                                "properties");

    private OpenApiDocumentComposer() {
    }

    static String compose(OpenApiDocumentContext context,
                          Optional<Supplier<OpenApiDocument>> staticDocument,
                          String staticContent,
                          List<OpenApiDocumentSource> sources) {
        boolean hasStaticContent = !staticContent.isBlank();
        OpenApiGeneratedMode mode = context.generatedMode();
        if (mode == OpenApiGeneratedMode.STATIC_ONLY
                || (hasStaticContent && mode == OpenApiGeneratedMode.STATIC_FIRST)) {
            return staticContent;
        }
        if (sources.isEmpty()) {
            return mode == OpenApiGeneratedMode.GENERATED_ONLY ? "" : staticContent;
        }

        OpenApiDocument generated = generatedDocument(context, sources);
        if (generated.isEmpty()) {
            if (mode == OpenApiGeneratedMode.GENERATED_ONLY) {
                return "";
            }
            if (mode == OpenApiGeneratedMode.MERGE && hasStaticContent) {
                OpenApiDocument composed = staticDocument.orElseThrow().get();
                validateComposedDocument(context, composed);
                return context.openApiVersion().render(context, composed);
            }
            return staticContent;
        }

        if (mode == OpenApiGeneratedMode.GENERATED_ONLY || !hasStaticContent) {
            validateComposedDocument(context, generated);
            return renderGenerated(context, generated);
        }

        if (mode == OpenApiGeneratedMode.MERGE) {
            OpenApiDocument.Builder builder = OpenApiDocument.builder()
                    .merge(staticDocument.orElseThrow().get());
            mergeGeneratedDocument(builder, generated, false, Map.of());
            OpenApiDocument merged = builder.build();
            validateComposedDocument(context, merged);
            return context.openApiVersion().render(context, merged);
        }

        return staticContent;
    }

    private static OpenApiDocument generatedDocument(OpenApiDocumentContext context, List<OpenApiDocumentSource> sources) {
        OpenApiDocument.Builder builder = OpenApiDocument.builder();
        Map<Object, String> schemaNamesByValue = new HashMap<>();
        for (OpenApiDocumentSource source : sources) {
            if (source.supports(context)) {
                OpenApiDocument.Builder sourceBuilder = OpenApiDocument.builder();
                source.describe(context, sourceBuilder);
                mergeGeneratedDocument(builder, sourceBuilder.build(), true, schemaNamesByValue);
            }
        }
        return builder.build();
    }

    private static void mergeGeneratedDocument(OpenApiDocument.Builder targetBuilder,
                                               OpenApiDocument source,
                                               boolean reuseEquivalentSchemas,
                                               Map<Object, String> schemaNamesByValue) {
        Map<String, Object> sourceNode = source.mutableNode();
        Map<String, String> schemaNames = rewriteSchemaNames(targetBuilder.node(),
                                                             sourceNode,
                                                             reuseEquivalentSchemas,
                                                             schemaNamesByValue);
        rewriteSchemaRefs(sourceNode, schemaNames);
        if (reuseEquivalentSchemas) {
            while (true) {
                Map<String, Object> sourceSchemas = schemas(sourceNode);
                Map<String, String> equivalentSchemaNames = new LinkedHashMap<>();
                sourceSchemas.forEach((name, schema) -> {
                    String matchingName = schemaNamesByValue.get(schema);
                    if (matchingName != null) {
                        equivalentSchemaNames.put(name, matchingName);
                    }
                });
                if (equivalentSchemaNames.isEmpty()) {
                    break;
                }
                equivalentSchemaNames.keySet().forEach(sourceSchemas::remove);
                rewriteSchemaRefs(sourceNode, equivalentSchemaNames);
            }
        }
        targetBuilder.mergeNode(sourceNode);
        if (reuseEquivalentSchemas) {
            // Schema values are structural hash keys, so index them only after reference rewriting is complete.
            schemas(sourceNode).forEach((name, schema) -> schemaNamesByValue.putIfAbsent(schema, name));
        }
    }

    private static Map<String, String> rewriteSchemaNames(Map<String, Object> targetNode,
                                                          Map<String, Object> sourceNode,
                                                          boolean reuseEquivalentSchemas,
                                                          Map<Object, String> schemaNamesByValue) {
        Map<String, Object> targetSchemas = schemas(targetNode);
        Map<String, Object> sourceSchemas = schemas(sourceNode);
        if (targetSchemas.isEmpty() || sourceSchemas.isEmpty()) {
            return Map.of();
        }

        Set<String> usedNames = new LinkedHashSet<>(targetSchemas.keySet());
        usedNames.addAll(sourceSchemas.keySet());
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, Object> renamedSchemas = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : List.copyOf(sourceSchemas.entrySet())) {
            String sourceName = entry.getKey();
            Object sourceSchema = entry.getValue();
            String matchingName = reuseEquivalentSchemas
                    ? schemaNamesByValue.get(sourceSchema)
                    : null;
            if (targetSchemas.containsKey(sourceName)) {
                if (Objects.equals(targetSchemas.get(sourceName), sourceSchema)) {
                    continue;
                }
                String targetName = matchingName == null ? uniqueSchemaName(sourceName, usedNames) : matchingName;
                result.put(sourceName, targetName);
                if (!targetSchemas.containsKey(targetName)) {
                    renamedSchemas.put(targetName, sourceSchema);
                }
                usedNames.add(targetName);
            } else if (matchingName != null) {
                result.put(sourceName, matchingName);
            }
        }

        result.keySet().forEach(sourceSchemas::remove);
        sourceSchemas.putAll(renamedSchemas);
        return result;
    }

    private static String uniqueSchemaName(String name, Set<String> usedNames) {
        int index = 2;
        String candidate = name + index;
        while (usedNames.contains(candidate)) {
            index++;
            candidate = name + index;
        }
        return candidate;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemas(Map<String, Object> node) {
        Object components = node.get("components");
        if (!(components instanceof Map<?, ?> componentsMap)) {
            return Map.of();
        }
        Object schemas = componentsMap.get("schemas");
        if (!(schemas instanceof Map<?, ?> schemaMap)) {
            return Map.of();
        }
        return (Map<String, Object>) schemaMap;
    }

    @SuppressWarnings("unchecked")
    private static void rewriteSchemaRefs(Object value, Map<String, String> schemaNames) {
        if (schemaNames.isEmpty()) {
            return;
        }
        rewriteLocalSchemaRefs(value, schemaNames);
        if (value instanceof Map<?, ?> map) {
            schemas((Map<String, Object>) map).values()
                    .forEach(schema -> rewriteSchemaDiscriminatorRefs(schema, schemaNames));
        }
        rewriteInlineSchemaDiscriminatorRefs(value, schemaNames, InlineSchemaContext.OPEN_API_OBJECT);
    }

    @SuppressWarnings("unchecked")
    private static void rewriteLocalSchemaRefs(Object value, Map<String, String> schemaNames) {
        if (value instanceof Map<?, ?> map) {
            Object ref = map.get("$ref");
            if (ref instanceof String refValue && refValue.startsWith(OpenApiSourceBase.SCHEMA_REF_PREFIX)) {
                ((Map<String, Object>) map).put("$ref", rewriteSchemaRef(refValue, schemaNames));
            }
            map.values().forEach(it -> rewriteLocalSchemaRefs(it, schemaNames));
        } else if (value instanceof List<?> list) {
            list.forEach(it -> rewriteLocalSchemaRefs(it, schemaNames));
        }
    }

    private static void rewriteInlineSchemaDiscriminatorRefs(Object value,
                                                             Map<String, String> schemaNames,
                                                             InlineSchemaContext context) {
        if (value instanceof Map<?, ?> map) {
            switch (context) {
            case NAMED_OPEN_API_OBJECTS:
                map.values().forEach(item -> rewriteInlineSchemaDiscriminatorRefs(
                        item,
                        schemaNames,
                        InlineSchemaContext.OPEN_API_OBJECT));
                return;
            case EXTENSIBLE_NAMED_OPEN_API_OBJECTS:
                map.forEach((key, item) -> {
                    if (key instanceof String field && !field.startsWith("x-")) {
                        rewriteInlineSchemaDiscriminatorRefs(item,
                                                             schemaNames,
                                                             InlineSchemaContext.OPEN_API_OBJECT);
                    }
                });
                return;
            case CALLBACKS:
                map.values().forEach(item -> rewriteInlineSchemaDiscriminatorRefs(
                        item,
                        schemaNames,
                        InlineSchemaContext.EXTENSIBLE_NAMED_OPEN_API_OBJECTS));
                return;
            case LINKS:
                map.values().forEach(item -> rewriteInlineSchemaDiscriminatorRefs(
                        item,
                        schemaNames,
                        InlineSchemaContext.LINK_OBJECT));
                return;
            case COMPONENTS:
                map.forEach((key, item) -> {
                    if (!(key instanceof String field) || "schemas".equals(field) || field.startsWith("x-")) {
                        return;
                    }
                    InlineSchemaContext childContext = switch (field) {
                    case "callbacks" -> InlineSchemaContext.CALLBACKS;
                    case "links" -> InlineSchemaContext.LINKS;
                    default -> InlineSchemaContext.NAMED_OPEN_API_OBJECTS;
                    };
                    rewriteInlineSchemaDiscriminatorRefs(item, schemaNames, childContext);
                });
                return;
            case OPEN_API_OBJECT, LINK_OBJECT:
                break;
            default:
                throw new IllegalStateException("Unsupported inline schema context " + context);
            }
            map.forEach((key, item) -> {
                if (!(key instanceof String field)
                        || "example".equals(field)
                        || "value".equals(field)
                        || (context == InlineSchemaContext.LINK_OBJECT
                                && ("parameters".equals(field) || "requestBody".equals(field)))
                        || field.startsWith("x-")) {
                    return;
                }
                if ("schema".equals(field) || "itemSchema".equals(field)) {
                    rewriteSchemaDiscriminatorRefs(item, schemaNames);
                } else {
                    InlineSchemaContext childContext = switch (field) {
                    case "components" -> InlineSchemaContext.COMPONENTS;
                    case "paths", "responses" -> InlineSchemaContext.EXTENSIBLE_NAMED_OPEN_API_OBJECTS;
                    case "callbacks" -> InlineSchemaContext.CALLBACKS;
                    case "links" -> InlineSchemaContext.LINKS;
                    case "additionalOperations", "content", "encoding", "examples", "headers", "webhooks" ->
                            InlineSchemaContext.NAMED_OPEN_API_OBJECTS;
                    default -> InlineSchemaContext.OPEN_API_OBJECT;
                    };
                    rewriteInlineSchemaDiscriminatorRefs(item, schemaNames, childContext);
                }
            });
        } else if (value instanceof List<?> list) {
            list.forEach(it -> rewriteInlineSchemaDiscriminatorRefs(
                    it,
                    schemaNames,
                    InlineSchemaContext.OPEN_API_OBJECT));
        }
    }

    @SuppressWarnings("unchecked")
    private static void rewriteSchemaDiscriminatorRefs(Object value, Map<String, String> schemaNames) {
        if (value instanceof Map<?, ?> map) {
            Object discriminator = map.get("discriminator");
            if (discriminator instanceof Map<?, ?> discriminatorMap) {
                Object mapping = discriminatorMap.get("mapping");
                if (mapping instanceof Map<?, ?> mappingMap) {
                    ((Map<String, Object>) mappingMap).replaceAll((_, mappingValue) -> {
                        if (mappingValue instanceof String mappingRef) {
                            return rewriteSchemaRef(mappingRef, schemaNames);
                        }
                        return mappingValue;
                    });
                }
                Object defaultMapping = discriminatorMap.get("defaultMapping");
                if (defaultMapping instanceof String mappingRef) {
                    ((Map<String, Object>) discriminatorMap).put("defaultMapping",
                                                                 rewriteSchemaRef(mappingRef, schemaNames));
                }
            }
            map.forEach((key, item) -> {
                if (!(key instanceof String field)) {
                    return;
                }
                if (SCHEMA_VALUE_FIELDS.contains(field)) {
                    rewriteSchemaDiscriminatorRefs(item, schemaNames);
                } else if (SCHEMA_MAP_FIELDS.contains(field) && item instanceof Map<?, ?> schemaMap) {
                    schemaMap.values().forEach(schema -> rewriteSchemaDiscriminatorRefs(schema, schemaNames));
                }
            });
        } else if (value instanceof List<?> list) {
            list.forEach(it -> rewriteSchemaDiscriminatorRefs(it, schemaNames));
        }
    }

    private static String rewriteSchemaRef(String refValue, Map<String, String> schemaNames) {
        String prefix = "";
        String sourceName = refValue;
        if (refValue.startsWith(OpenApiSourceBase.SCHEMA_REF_PREFIX)) {
            prefix = OpenApiSourceBase.SCHEMA_REF_PREFIX;
            sourceName = refValue.substring(prefix.length());
        }
        String targetName = schemaNames.get(sourceName);
        return targetName == null ? refValue : prefix + targetName;
    }

    private static String renderGenerated(OpenApiDocumentContext context, OpenApiDocument generated) {
        return context.openApiVersion().render(context, generated);
    }

    private static void validateComposedDocument(OpenApiDocumentContext context, OpenApiDocument document) {
        validateOperationIds(document);
        if ("3.2".equals(context.openApiVersion().type())) {
            Map<String, String> tagParents = new LinkedHashMap<>();
            Set<String> tagNames = new LinkedHashSet<>();
            Object tags = document.mutableNode().get("tags");
            if (tags instanceof List<?> tagList) {
                for (Object tagNode : tagList) {
                    if (tagNode instanceof Map<?, ?> tag && tag.get("name") instanceof String tagName) {
                        tagNames.add(tagName);
                        if (tag.get("parent") instanceof String parentName) {
                            tagParents.put(tagName, parentName);
                        }
                    }
                }
            }
            tagParents.forEach((tagName, parentName) -> {
                if (!tagNames.contains(parentName)) {
                    throw new IllegalStateException("OpenAPI tag " + tagName
                                                            + " references missing parent tag " + parentName);
                }
                if (tagName.equals(parentName)) {
                    throw new IllegalStateException("OpenAPI tag " + tagName + " cannot be its own parent");
                }
            });
            Set<String> completedTagNames = new HashSet<>();
            for (String tagName : tagParents.keySet()) {
                if (completedTagNames.contains(tagName)) {
                    continue;
                }
                Set<String> path = new LinkedHashSet<>();
                String currentName = tagName;
                while (currentName != null
                        && !completedTagNames.contains(currentName)
                        && path.add(currentName)) {
                    currentName = tagParents.get(currentName);
                }
                if (currentName != null && !completedTagNames.contains(currentName)) {
                    List<String> pathNames = List.copyOf(path);
                    int cycleStart = pathNames.indexOf(currentName);
                    String cycle = String.join(" -> ", pathNames.subList(cycleStart, pathNames.size()))
                            + " -> " + currentName;
                    throw new IllegalStateException("OpenAPI tag parent cycle: " + cycle);
                }
                completedTagNames.addAll(path);
            }
        }
        if (document.info().isEmpty()) {
            throw new IllegalStateException("Composed OpenAPI document requires Info metadata. "
                                                    + "Add an @OpenApi.Document type with @OpenApi.Info, provide Info "
                                                    + "from an OpenApiDocumentSource, or merge static content with Info.");
        }
    }

    private static void validateOperationIds(OpenApiDocument document) {
        Map<String, String> operationIds = new LinkedHashMap<>();
        validatePathItems("paths", document.paths(), operationIds);
        validatePathItems("webhooks", document.webhooks(), operationIds);
    }

    private static void validatePathItems(String location,
                                          Map<String, OpenApiDocument.PathItem> pathItems,
                                          Map<String, String> operationIds) {
        pathItems.forEach((name, pathItem) -> validatePathItem(location + "." + name, pathItem, operationIds));
    }

    private static void validatePathItem(String location,
                                         OpenApiDocument.PathItem pathItem,
                                         Map<String, String> operationIds) {
        pathItem.operations()
                .forEach((method, operation) -> validateOperationId(location + "." + method, operation, operationIds));
        pathItem.additionalOperations()
                .forEach((method, operation) -> validateOperationId(location + ".additionalOperations." + method,
                                                                    operation,
                                                                    operationIds));
    }

    private static void validateOperationId(String location,
                                            OpenApiDocument.Operation operation,
                                            Map<String, String> operationIds) {
        operation.operationId().ifPresent(operationId -> {
            String previousLocation = operationIds.putIfAbsent(operationId, location);
            if (previousLocation != null) {
                throw new IllegalStateException("Duplicate OpenAPI operationId " + operationId
                                                        + " at " + previousLocation
                                                        + " and " + location);
            }
        });
        operation.callbacks()
                .forEach((name, callback) -> callback.expressions()
                        .forEach((expression, pathItem) -> validatePathItem(location
                                                                                   + ".callbacks."
                                                                                   + name
                                                                                   + "."
                                                                                   + expression,
                                                                           pathItem,
                                                                           operationIds)));
    }

    private enum InlineSchemaContext {
        OPEN_API_OBJECT,
        COMPONENTS,
        NAMED_OPEN_API_OBJECTS,
        EXTENSIBLE_NAMED_OPEN_API_OBJECTS,
        CALLBACKS,
        LINKS,
        LINK_OBJECT
    }
}
