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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.media.type.MediaType;
import io.helidon.openapi.spi.OpenApiDocumentSource;
import io.helidon.openapi.spi.OpenApiVersion;

final class OpenApiDocumentComposer {
    private OpenApiDocumentComposer() {
    }

    static String compose(OpenApiDocumentContext context,
                          OpenApiVersion staticOpenApiVersion,
                          String staticContent,
                          MediaType staticContentMediaType,
                          List<OpenApiDocumentSource> sources) {
        Optional<Supplier<OpenApiDocument>> staticDocument = Optional.of(() -> {
            OpenApiDocumentContext staticContext = new OpenApiDocumentContextImpl(context.featureName(),
                                                                                  context.webContext(),
                                                                                  context.listener(),
                                                                                  context.generatedMode(),
                                                                                  staticOpenApiVersion);
            return staticOpenApiVersion.parse(staticContext, staticContent, staticContentMediaType);
        });
        return compose(context, staticDocument, staticContent, sources);
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
                return context.openApiVersion().render(context, staticDocument.orElseThrow().get());
            }
            return staticContent;
        }

        if (mode == OpenApiGeneratedMode.GENERATED_ONLY || !hasStaticContent) {
            validateComposedDocument(generated);
            return renderGenerated(context, generated);
        }

        if (mode == OpenApiGeneratedMode.MERGE) {
            OpenApiDocument.Builder builder = OpenApiDocument.builder()
                    .merge(staticDocument.orElseThrow().get());
            mergeGeneratedDocument(builder, generated, false);
            OpenApiDocument merged = builder.build();
            validateComposedDocument(merged);
            return context.openApiVersion().render(context, merged);
        }

        return staticContent;
    }

    private static OpenApiDocument generatedDocument(OpenApiDocumentContext context, List<OpenApiDocumentSource> sources) {
        OpenApiDocument.Builder builder = OpenApiDocument.builder();
        for (OpenApiDocumentSource source : sources) {
            if (source.supports(context)) {
                OpenApiDocument.Builder sourceBuilder = OpenApiDocument.builder();
                source.describe(context, sourceBuilder);
                mergeGeneratedDocument(builder, sourceBuilder.build(), true);
            }
        }
        return builder.build();
    }

    private static void mergeGeneratedDocument(OpenApiDocument.Builder targetBuilder,
                                               OpenApiDocument source,
                                               boolean reuseEquivalentSchemas) {
        Map<String, Object> sourceNode = source.mutableNode();
        Map<String, String> schemaNames = rewriteSchemaNames(targetBuilder.node(), sourceNode, reuseEquivalentSchemas);
        rewriteSchemaRefs(sourceNode, schemaNames);
        targetBuilder.mergeNode(sourceNode);
    }

    private static Map<String, String> rewriteSchemaNames(Map<String, Object> targetNode,
                                                          Map<String, Object> sourceNode,
                                                          boolean reuseEquivalentSchemas) {
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
                    ? matchingSchemaName(targetSchemas, sourceSchema).orElse(null)
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

    private static Optional<String> matchingSchemaName(Map<String, Object> schemas, Object schema) {
        return schemas.entrySet()
                .stream()
                .filter(entry -> Objects.equals(entry.getValue(), schema))
                .map(Map.Entry::getKey)
                .findFirst();
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
        if (value instanceof Map<?, ?> map) {
            Object ref = map.get("$ref");
            if (ref instanceof String refValue && refValue.startsWith(OpenApiSourceBase.SCHEMA_REF_PREFIX)) {
                String sourceName = refValue.substring(OpenApiSourceBase.SCHEMA_REF_PREFIX.length());
                String targetName = schemaNames.get(sourceName);
                if (targetName != null) {
                    ((Map<String, Object>) map).put("$ref", OpenApiSourceBase.SCHEMA_REF_PREFIX + targetName);
                }
            }
            map.values().forEach(it -> rewriteSchemaRefs(it, schemaNames));
        } else if (value instanceof List<?> list) {
            list.forEach(it -> rewriteSchemaRefs(it, schemaNames));
        }
    }

    private static String renderGenerated(OpenApiDocumentContext context, OpenApiDocument generated) {
        return context.openApiVersion().render(context, generated);
    }

    private static void validateComposedDocument(OpenApiDocument document) {
        validateOperationIds(document);
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
}
