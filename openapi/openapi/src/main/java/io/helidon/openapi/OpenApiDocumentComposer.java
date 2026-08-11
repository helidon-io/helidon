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

import java.util.ArrayList;
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
    private static final Set<String> DYNAMIC_REF_DIALECTS = Set.of(
            "https://json-schema.org/draft/2020-12/schema",
            "https://spec.openapis.org/oas/3.1/dialect/base");

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

        Optional<OpenApiDocument> mergeDocument = mode == OpenApiGeneratedMode.MERGE && hasStaticContent
                ? Optional.of(staticDocument.orElseThrow().get())
                : Optional.empty();
        GeneratedDocument generatedResult = generatedDocument(context, sources, mergeDocument);
        OpenApiDocument generated = generatedResult.document();
        if (generated.isEmpty()) {
            if (mode == OpenApiGeneratedMode.GENERATED_ONLY) {
                return "";
            }
            if (mode == OpenApiGeneratedMode.MERGE && hasStaticContent) {
                OpenApiDocument composed = mergeDocument.orElseThrow();
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
                    .merge(mergeDocument.orElseThrow());
            mergeGeneratedDocument(context,
                                   builder,
                                   generated,
                                   false,
                                   Map.of(),
                                   generatedResult.dynamicRefDialect());
            OpenApiDocument merged = builder.build();
            validateComposedDocument(context, merged);
            return context.openApiVersion().render(context, merged);
        }

        return staticContent;
    }

    private static GeneratedDocument generatedDocument(OpenApiDocumentContext context,
                                                       List<OpenApiDocumentSource> sources,
                                                       Optional<OpenApiDocument> mergeDocument) {
        List<OpenApiDocument> sourceDocuments = new ArrayList<>();
        for (OpenApiDocumentSource source : sources) {
            if (source.supports(context)) {
                OpenApiDocument.Builder sourceBuilder = OpenApiDocument.builder();
                source.describe(context, sourceBuilder);
                sourceDocuments.add(sourceBuilder.build());
            }
        }
        Optional<String> documentDialect = mergeDocument.flatMap(OpenApiDocument::jsonSchemaDialect);
        if (documentDialect.isEmpty()) {
            for (OpenApiDocument sourceDocument : sourceDocuments) {
                documentDialect = sourceDocument.jsonSchemaDialect();
                if (documentDialect.isPresent()) {
                    break;
                }
            }
        }
        boolean dynamicRefDialect = documentDialect.map(DYNAMIC_REF_DIALECTS::contains).orElse(true);
        OpenApiDocument.Builder builder = OpenApiDocument.builder();
        Map<Object, String> schemaNamesByValue = new HashMap<>();
        for (OpenApiDocument sourceDocument : sourceDocuments) {
            mergeGeneratedDocument(context,
                                   builder,
                                   sourceDocument,
                                   true,
                                   schemaNamesByValue,
                                   dynamicRefDialect);
        }
        return new GeneratedDocument(builder.build(), dynamicRefDialect);
    }

    private static void mergeGeneratedDocument(OpenApiDocumentContext context,
                                               OpenApiDocument.Builder targetBuilder,
                                               OpenApiDocument source,
                                               boolean reuseEquivalentSchemas,
                                               Map<Object, String> schemaNamesByValue,
                                               boolean dynamicRefDialect) {
        Map<String, Object> targetNode = targetBuilder.node();
        Map<String, Object> sourceNode = source.mutableNode();
        Map<String, String> schemaNames = rewriteSchemaNames(targetNode,
                                                             sourceNode,
                                                             reuseEquivalentSchemas,
                                                             schemaNamesByValue);
        boolean supportsDynamicRef = "3.1".equals(context.openApiVersion().type())
                || "3.2".equals(context.openApiVersion().type());
        rewriteSchemaRefs(sourceNode, schemaNames, supportsDynamicRef, dynamicRefDialect);
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
                rewriteSchemaRefs(sourceNode, equivalentSchemaNames, supportsDynamicRef, dynamicRefDialect);
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
    private static void rewriteSchemaRefs(Object value,
                                          Map<String, String> schemaNames,
                                          boolean supportsDynamicRef,
                                          boolean dynamicRefDialect) {
        if (schemaNames.isEmpty()) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            schemas((Map<String, Object>) map).values()
                    .forEach(schema -> rewriteSchemaValueRefs(schema,
                                                              schemaNames,
                                                              supportsDynamicRef,
                                                              dynamicRefDialect,
                                                              true,
                                                              true));
        }
        rewriteOpenApiSchemaRefs(value,
                                 schemaNames,
                                 InlineSchemaContext.OPEN_API_OBJECT,
                                 supportsDynamicRef,
                                 dynamicRefDialect);
    }

    private static void rewriteOpenApiSchemaRefs(Object value,
                                                 Map<String, String> schemaNames,
                                                 InlineSchemaContext context,
                                                 boolean supportsDynamicRef,
                                                 boolean dynamicRefDialect) {
        if (value instanceof Map<?, ?> map) {
            switch (context) {
            case NAMED_OPEN_API_OBJECTS:
                map.values().forEach(item -> rewriteOpenApiSchemaRefs(
                        item,
                        schemaNames,
                        InlineSchemaContext.OPEN_API_OBJECT,
                        supportsDynamicRef,
                        dynamicRefDialect));
                return;
            case EXTENSIBLE_NAMED_OPEN_API_OBJECTS:
                map.forEach((key, item) -> {
                    if (key instanceof String field && !field.startsWith("x-")) {
                        rewriteOpenApiSchemaRefs(item,
                                                 schemaNames,
                                                 InlineSchemaContext.OPEN_API_OBJECT,
                                                 supportsDynamicRef,
                                                 dynamicRefDialect);
                    }
                });
                return;
            case CALLBACKS:
                map.values().forEach(item -> rewriteOpenApiSchemaRefs(
                        item,
                        schemaNames,
                        InlineSchemaContext.EXTENSIBLE_NAMED_OPEN_API_OBJECTS,
                        supportsDynamicRef,
                        dynamicRefDialect));
                return;
            case LINKS:
                map.values().forEach(item -> rewriteOpenApiSchemaRefs(
                        item,
                        schemaNames,
                        InlineSchemaContext.LINK_OBJECT,
                        supportsDynamicRef,
                        dynamicRefDialect));
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
                    rewriteOpenApiSchemaRefs(item,
                                             schemaNames,
                                             childContext,
                                             supportsDynamicRef,
                                             dynamicRefDialect);
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
                        || "dataValue".equals(field)
                        || "value".equals(field)
                        || (context == InlineSchemaContext.LINK_OBJECT
                                && ("parameters".equals(field) || "requestBody".equals(field)))
                        || field.startsWith("x-")) {
                    return;
                }
                if ("schema".equals(field) || "itemSchema".equals(field)) {
                    rewriteSchemaValueRefs(item,
                                           schemaNames,
                                           supportsDynamicRef,
                                           dynamicRefDialect,
                                           true,
                                           true);
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
                    rewriteOpenApiSchemaRefs(item,
                                             schemaNames,
                                             childContext,
                                             supportsDynamicRef,
                                             dynamicRefDialect);
                }
            });
        } else if (value instanceof List<?> list) {
            list.forEach(it -> rewriteOpenApiSchemaRefs(
                    it,
                    schemaNames,
                    InlineSchemaContext.OPEN_API_OBJECT,
                    supportsDynamicRef,
                    dynamicRefDialect));
        }
    }

    @SuppressWarnings("unchecked")
    private static void rewriteSchemaValueRefs(Object value,
                                               Map<String, String> schemaNames,
                                               boolean supportsDynamicRef,
                                               boolean dynamicRefDialect,
                                               boolean openApiDocumentResource,
                                               boolean schemaResourceRoot) {
        if (value instanceof Map<?, ?> map) {
            boolean currentSchemaResourceRoot = schemaResourceRoot || map.containsKey("$id");
            boolean currentDynamicRefDialect = currentSchemaResourceRoot && map.containsKey("$schema")
                    ? map.get("$schema") instanceof String dialect && DYNAMIC_REF_DIALECTS.contains(dialect)
                    : dynamicRefDialect;
            boolean currentOpenApiDocumentResource = openApiDocumentResource && !map.containsKey("$id");
            Object ref = map.get("$ref");
            if (currentOpenApiDocumentResource
                    && ref instanceof String refValue
                    && refValue.startsWith(OpenApiSourceBase.SCHEMA_REF_PREFIX)) {
                ((Map<String, Object>) map).put("$ref", rewriteSchemaRef(refValue, schemaNames));
            }
            Object dynamicRef = map.get("$dynamicRef");
            if (supportsDynamicRef
                    && currentDynamicRefDialect
                    && currentOpenApiDocumentResource
                    && dynamicRef instanceof String refValue
                    && refValue.startsWith(OpenApiSourceBase.SCHEMA_REF_PREFIX)) {
                ((Map<String, Object>) map).put("$dynamicRef", rewriteSchemaRef(refValue, schemaNames));
            }
            Object discriminator = map.get("discriminator");
            if (discriminator instanceof Map<?, ?> discriminatorMap) {
                Object mapping = discriminatorMap.get("mapping");
                if (mapping instanceof Map<?, ?> mappingMap) {
                    ((Map<String, Object>) mappingMap).replaceAll((_, mappingValue) -> {
                        if (mappingValue instanceof String mappingRef
                                && (currentOpenApiDocumentResource
                                        || !mappingRef.startsWith(OpenApiSourceBase.SCHEMA_REF_PREFIX))) {
                            return rewriteSchemaRef(mappingRef, schemaNames);
                        }
                        return mappingValue;
                    });
                }
                Object defaultMapping = discriminatorMap.get("defaultMapping");
                if (defaultMapping instanceof String mappingRef
                        && (currentOpenApiDocumentResource
                                || !mappingRef.startsWith(OpenApiSourceBase.SCHEMA_REF_PREFIX))) {
                    ((Map<String, Object>) discriminatorMap).put("defaultMapping",
                                                                 rewriteSchemaRef(mappingRef, schemaNames));
                }
            }
            map.forEach((key, item) -> {
                if (!(key instanceof String field)) {
                    return;
                }
                if (SCHEMA_VALUE_FIELDS.contains(field)) {
                    rewriteSchemaValueRefs(item,
                                           schemaNames,
                                           supportsDynamicRef,
                                           currentDynamicRefDialect,
                                           currentOpenApiDocumentResource,
                                           false);
                } else if (SCHEMA_MAP_FIELDS.contains(field) && item instanceof Map<?, ?> schemaMap) {
                    schemaMap.values().forEach(schema -> rewriteSchemaValueRefs(schema,
                                                                                schemaNames,
                                                                                supportsDynamicRef,
                                                                                currentDynamicRefDialect,
                                                                                currentOpenApiDocumentResource,
                                                                                false));
                }
            });
        } else if (value instanceof List<?> list) {
            list.forEach(it -> rewriteSchemaValueRefs(it,
                                                      schemaNames,
                                                      supportsDynamicRef,
                                                      dynamicRefDialect,
                                                      openApiDocumentResource,
                                                      schemaResourceRoot));
        }
    }

    private static String rewriteSchemaRef(String refValue, Map<String, String> schemaNames) {
        String prefix = "";
        String sourceName = refValue;
        String suffix = "";
        if (refValue.startsWith(OpenApiSourceBase.SCHEMA_REF_PREFIX)) {
            prefix = OpenApiSourceBase.SCHEMA_REF_PREFIX;
            int suffixStart = refValue.indexOf('/', prefix.length());
            if (suffixStart < 0) {
                sourceName = refValue.substring(prefix.length());
            } else {
                sourceName = refValue.substring(prefix.length(), suffixStart);
                suffix = refValue.substring(suffixStart);
            }
        }
        String targetName = schemaNames.get(sourceName);
        return targetName == null ? refValue : prefix + targetName + suffix;
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

    private record GeneratedDocument(OpenApiDocument document, boolean dynamicRefDialect) {
    }
}
