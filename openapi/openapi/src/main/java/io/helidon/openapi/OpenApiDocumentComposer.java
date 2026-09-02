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

import java.net.URI;
import java.net.URISyntaxException;
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

import static io.helidon.openapi.v30.OpenApiDocumentMapperSupport.validateOperationIds;

final class OpenApiDocumentComposer {
    private static final Set<String> SCHEMA_VALUE_FIELDS = Set.of("additionalProperties",
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

        Optional<OpenApiDocument> mergeDocument = mode == OpenApiGeneratedMode.MERGE && hasStaticContent
                ? Optional.of(staticDocument.orElseThrow().get())
                : Optional.empty();
        OpenApiDocument generated = generatedDocument(context, sources);
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
            return context.openApiVersion().render(context, generated);
        }

        if (mode == OpenApiGeneratedMode.MERGE) {
            OpenApiDocument.Builder builder = OpenApiDocument.builder()
                    .merge(mergeDocument.orElseThrow());
            mergeGeneratedDocument(context,
                                   builder,
                                   generated,
                                   false,
                                   Map.of());
            OpenApiDocument merged = builder.build();
            validateComposedDocument(context, merged);
            return context.openApiVersion().render(context, merged);
        }

        return staticContent;
    }

    private static OpenApiDocument generatedDocument(OpenApiDocumentContext context,
                                                     List<OpenApiDocumentSource> sources) {
        List<OpenApiDocument> sourceDocuments = new ArrayList<>();
        for (OpenApiDocumentSource source : sources) {
            if (source.supports(context)) {
                OpenApiDocument.Builder sourceBuilder = OpenApiDocument.builder();
                source.describe(context, sourceBuilder);
                sourceDocuments.add(sourceBuilder.build());
            }
        }
        OpenApiDocument.Builder builder = OpenApiDocument.builder();
        Map<Object, String> schemaNamesByValue = new HashMap<>();
        for (OpenApiDocument sourceDocument : sourceDocuments) {
            mergeGeneratedDocument(context,
                                   builder,
                                   sourceDocument,
                                   true,
                                   schemaNamesByValue);
        }
        return builder.build();
    }

    private static void mergeGeneratedDocument(OpenApiDocumentContext context,
                                               OpenApiDocument.Builder targetBuilder,
                                               OpenApiDocument source,
                                               boolean reuseEquivalentSchemas,
                                               Map<Object, String> schemaNamesByValue) {
        Map<String, Object> targetNode = targetBuilder.node();
        Map<String, Object> sourceNode = source.mutableNode();
        Object sourceSelf = sourceNode.containsKey("$self") ? sourceNode.get("$self") : targetNode.get("$self");
        URI sourceDocumentUri = documentUri(sourceSelf, context.webContext());
        Map<String, String> originalNamesByRenamedName = new HashMap<>();
        Map<String, String> schemaNames = rewriteSchemaNames(targetNode,
                                                             sourceNode,
                                                             reuseEquivalentSchemas,
                                                             schemaNamesByValue,
                                                             originalNamesByRenamedName);
        boolean supportsDynamicRef = "3.1".equals(context.openApiVersion().type())
                || "3.2".equals(context.openApiVersion().type());
        boolean additionalItemsHasSchemaValue = "3.0".equals(context.openApiVersion().type());
        if (reuseEquivalentSchemas && !schemaNamesByValue.isEmpty()) {
            Map<String, Object> sourceSchemas = schemas(sourceNode);
            Map<String, Set<String>> dependentSchemasByName = new HashMap<>();
            sourceSchemas.forEach((name, schema) -> {
                Set<String> referencedSchemaNames = new HashSet<>();
                rewriteSchemaValueRefs(schema,
                                       schemaNames,
                                       sourceDocumentUri,
                                       supportsDynamicRef,
                                       additionalItemsHasSchemaValue,
                                       new SchemaResource(sourceDocumentUri, true),
                                       referencedSchemaNames);
                referencedSchemaNames.forEach(referencedName -> dependentSchemasByName
                        .computeIfAbsent(referencedName, _ -> new LinkedHashSet<>())
                        .add(name));
            });

            Map<String, String> rewrittenSchemaNames = new LinkedHashMap<>(schemaNames);
            List<String> resolvedSchemaNames = new ArrayList<>();
            for (Map.Entry<String, Object> entry : List.copyOf(sourceSchemas.entrySet())) {
                String matchingName = schemaNamesByValue.get(entry.getValue());
                if (matchingName != null) {
                    sourceSchemas.remove(entry.getKey());
                    rewrittenSchemaNames.put(entry.getKey(), matchingName);
                    String originalName = originalNamesByRenamedName.get(entry.getKey());
                    if (originalName != null) {
                        rewrittenSchemaNames.put(originalName, matchingName);
                    }
                    resolvedSchemaNames.add(entry.getKey());
                }
            }

            while (!resolvedSchemaNames.isEmpty()) {
                Map<String, Map<String, String>> rewritesByDependentSchema = new LinkedHashMap<>();
                for (String resolvedSchemaName : resolvedSchemaNames) {
                    String matchingName = rewrittenSchemaNames.get(resolvedSchemaName);
                    for (String dependentSchemaName : dependentSchemasByName.getOrDefault(resolvedSchemaName,
                                                                                           Set.of())) {
                        if (sourceSchemas.containsKey(dependentSchemaName)) {
                            rewritesByDependentSchema
                                    .computeIfAbsent(dependentSchemaName, _ -> new LinkedHashMap<>())
                                    .put(resolvedSchemaName, matchingName);
                        }
                    }
                }
                resolvedSchemaNames.clear();
                rewritesByDependentSchema.forEach((dependentSchemaName, dependentRewrites) -> {
                    Object dependentSchema = sourceSchemas.get(dependentSchemaName);
                    rewriteSchemaValueRefs(dependentSchema,
                                           dependentRewrites,
                                           sourceDocumentUri,
                                           supportsDynamicRef,
                                           additionalItemsHasSchemaValue,
                                           new SchemaResource(sourceDocumentUri, true));
                    String matchingName = schemaNamesByValue.get(dependentSchema);
                    if (matchingName != null) {
                        sourceSchemas.remove(dependentSchemaName);
                        rewrittenSchemaNames.put(dependentSchemaName, matchingName);
                        String originalName = originalNamesByRenamedName.get(dependentSchemaName);
                        if (originalName != null) {
                            rewrittenSchemaNames.put(originalName, matchingName);
                        }
                        resolvedSchemaNames.add(dependentSchemaName);
                    }
                });
            }
            rewriteOpenApiSchemaRefs(sourceNode,
                                     rewrittenSchemaNames,
                                     sourceDocumentUri,
                                     InlineSchemaContext.OPEN_API_OBJECT,
                                     supportsDynamicRef,
                                     additionalItemsHasSchemaValue);
        } else {
            rewriteSchemaRefs(sourceNode,
                              schemaNames,
                              sourceDocumentUri,
                              supportsDynamicRef,
                              additionalItemsHasSchemaValue);
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
                                                          Map<Object, String> schemaNamesByValue,
                                                          Map<String, String> originalNamesByRenamedName) {
        Map<String, Object> targetSchemas = schemas(targetNode);
        Map<String, Object> sourceSchemas = schemas(sourceNode);
        if (targetSchemas.isEmpty() || sourceSchemas.isEmpty()) {
            return Map.of();
        }

        Set<String> usedNames = null;
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
                if (matchingName == null && usedNames == null) {
                    usedNames = new LinkedHashSet<>(targetSchemas.keySet());
                    usedNames.addAll(sourceSchemas.keySet());
                }
                String targetName = matchingName == null ? uniqueSchemaName(sourceName, usedNames) : matchingName;
                result.put(sourceName, targetName);
                if (!targetSchemas.containsKey(targetName)) {
                    renamedSchemas.put(targetName, sourceSchema);
                    originalNamesByRenamedName.put(targetName, sourceName);
                }
                if (usedNames != null) {
                    usedNames.add(targetName);
                }
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
                                          URI sourceDocumentUri,
                                          boolean supportsDynamicRef,
                                          boolean additionalItemsHasSchemaValue) {
        if (schemaNames.isEmpty()) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            schemas((Map<String, Object>) map).values()
                    .forEach(schema -> rewriteSchemaValueRefs(schema,
                                                              schemaNames,
                                                              sourceDocumentUri,
                                                              supportsDynamicRef,
                                                              additionalItemsHasSchemaValue,
                                                              new SchemaResource(sourceDocumentUri, true)));
        }
        rewriteOpenApiSchemaRefs(value,
                                 schemaNames,
                                 sourceDocumentUri,
                                 InlineSchemaContext.OPEN_API_OBJECT,
                                 supportsDynamicRef,
                                 additionalItemsHasSchemaValue);
    }

    private static void rewriteOpenApiSchemaRefs(Object value,
                                                 Map<String, String> schemaNames,
                                                 URI sourceDocumentUri,
                                                 InlineSchemaContext context,
                                                 boolean supportsDynamicRef,
                                                 boolean additionalItemsHasSchemaValue) {
        if (value instanceof Map<?, ?> map) {
            switch (context) {
            case NAMED_OPEN_API_OBJECTS:
                map.values().forEach(item -> rewriteOpenApiSchemaRefs(
                        item,
                        schemaNames,
                        sourceDocumentUri,
                        InlineSchemaContext.OPEN_API_OBJECT,
                        supportsDynamicRef,
                        additionalItemsHasSchemaValue));
                return;
            case EXTENSIBLE_NAMED_OPEN_API_OBJECTS:
                map.forEach((key, item) -> {
                    if (key instanceof String field && !field.startsWith("x-")) {
                        rewriteOpenApiSchemaRefs(item,
                                                 schemaNames,
                                                 sourceDocumentUri,
                                                 InlineSchemaContext.OPEN_API_OBJECT,
                                                 supportsDynamicRef,
                                                 additionalItemsHasSchemaValue);
                    }
                });
                return;
            case CALLBACKS:
                map.values().forEach(item -> rewriteOpenApiSchemaRefs(
                        item,
                        schemaNames,
                        sourceDocumentUri,
                        InlineSchemaContext.EXTENSIBLE_NAMED_OPEN_API_OBJECTS,
                        supportsDynamicRef,
                        additionalItemsHasSchemaValue));
                return;
            case LINKS:
                map.values().forEach(item -> rewriteOpenApiSchemaRefs(
                        item,
                        schemaNames,
                        sourceDocumentUri,
                        InlineSchemaContext.LINK_OBJECT,
                        supportsDynamicRef,
                        additionalItemsHasSchemaValue));
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
                                             sourceDocumentUri,
                                             childContext,
                                             supportsDynamicRef,
                                             additionalItemsHasSchemaValue);
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
                                           sourceDocumentUri,
                                           supportsDynamicRef,
                                           additionalItemsHasSchemaValue,
                                           new SchemaResource(sourceDocumentUri, true));
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
                                             sourceDocumentUri,
                                             childContext,
                                             supportsDynamicRef,
                                             additionalItemsHasSchemaValue);
                }
            });
        } else if (value instanceof List<?> list) {
            list.forEach(it -> rewriteOpenApiSchemaRefs(
                    it,
                    schemaNames,
                    sourceDocumentUri,
                    InlineSchemaContext.OPEN_API_OBJECT,
                    supportsDynamicRef,
                    additionalItemsHasSchemaValue));
        }
    }

    @SuppressWarnings("unchecked")
    private static void rewriteSchemaValueRefs(Object value,
                                               Map<String, String> schemaNames,
                                               URI sourceDocumentUri,
                                               boolean supportsDynamicRef,
                                               boolean additionalItemsHasSchemaValue,
                                               SchemaResource resource) {
        rewriteSchemaValueRefs(value,
                               schemaNames,
                               sourceDocumentUri,
                               supportsDynamicRef,
                               additionalItemsHasSchemaValue,
                               resource,
                               null);
    }

    @SuppressWarnings("unchecked")
    private static void rewriteSchemaValueRefs(Object value,
                                               Map<String, String> schemaNames,
                                               URI sourceDocumentUri,
                                               boolean supportsDynamicRef,
                                               boolean additionalItemsHasSchemaValue,
                                               SchemaResource resource,
                                               Set<String> referencedSchemaNames) {
        if (value instanceof Map<?, ?> map) {
            SchemaResource currentResource = map.containsKey("$id")
                    ? new SchemaResource(resolveDocumentUri(map.get("$id"), resource.baseUri()), false)
                    : resource;
            Object ref = map.get("$ref");
            if (ref instanceof String refValue
                    && schemaReference(refValue, currentResource, sourceDocumentUri).isPresent()) {
                String rewrittenRef = rewriteSchemaRef(refValue, schemaNames, currentResource, sourceDocumentUri);
                ((Map<String, Object>) map).put("$ref", rewrittenRef);
                if (referencedSchemaNames != null) {
                    referencedSchemaNames.add(schemaRefName(rewrittenRef, currentResource, sourceDocumentUri));
                }
            }
            Object dynamicRef = map.get("$dynamicRef");
            if (supportsDynamicRef
                    && dynamicRef instanceof String refValue
                    && schemaReference(refValue, currentResource, sourceDocumentUri).isPresent()) {
                String rewrittenRef = rewriteSchemaRef(refValue, schemaNames, currentResource, sourceDocumentUri);
                ((Map<String, Object>) map).put("$dynamicRef", rewrittenRef);
                if (referencedSchemaNames != null) {
                    referencedSchemaNames.add(schemaRefName(rewrittenRef, currentResource, sourceDocumentUri));
                }
            }
            Object discriminator = map.get("discriminator");
            if (discriminator instanceof Map<?, ?> discriminatorMap) {
                Object mapping = discriminatorMap.get("mapping");
                if (mapping instanceof Map<?, ?> mappingMap) {
                    ((Map<String, Object>) mappingMap).replaceAll((_, mappingValue) -> {
                        if (mappingValue instanceof String mappingRef
                                && (schemaNames.containsKey(mappingRef)
                                        || schemaReference(mappingRef, currentResource, sourceDocumentUri).isPresent())) {
                            String rewrittenRef = rewriteSchemaRef(mappingRef,
                                                                   schemaNames,
                                                                   currentResource,
                                                                   sourceDocumentUri);
                            if (referencedSchemaNames != null) {
                                referencedSchemaNames.add(schemaRefName(rewrittenRef,
                                                                        currentResource,
                                                                        sourceDocumentUri));
                            }
                            return rewrittenRef;
                        }
                        return mappingValue;
                    });
                }
                Object defaultMapping = discriminatorMap.get("defaultMapping");
                if (defaultMapping instanceof String mappingRef
                        && (schemaNames.containsKey(mappingRef)
                                || schemaReference(mappingRef, currentResource, sourceDocumentUri).isPresent())) {
                    String rewrittenRef = rewriteSchemaRef(mappingRef,
                                                           schemaNames,
                                                           currentResource,
                                                           sourceDocumentUri);
                    ((Map<String, Object>) discriminatorMap).put("defaultMapping", rewrittenRef);
                    if (referencedSchemaNames != null) {
                        referencedSchemaNames.add(schemaRefName(rewrittenRef, currentResource, sourceDocumentUri));
                    }
                }
            }
            map.forEach((key, item) -> {
                if (!(key instanceof String field)) {
                    return;
                }
                if (SCHEMA_VALUE_FIELDS.contains(field)
                        || (additionalItemsHasSchemaValue && "additionalItems".equals(field))) {
                    rewriteSchemaValueRefs(item,
                                           schemaNames,
                                           sourceDocumentUri,
                                           supportsDynamicRef,
                                           additionalItemsHasSchemaValue,
                                           currentResource,
                                           referencedSchemaNames);
                } else if (SCHEMA_MAP_FIELDS.contains(field) && item instanceof Map<?, ?> schemaMap) {
                    schemaMap.values().forEach(schema -> rewriteSchemaValueRefs(schema,
                                                                                schemaNames,
                                                                                sourceDocumentUri,
                                                                                supportsDynamicRef,
                                                                                additionalItemsHasSchemaValue,
                                                                                currentResource,
                                                                                referencedSchemaNames));
                }
            });
        } else if (value instanceof List<?> list) {
            list.forEach(it -> rewriteSchemaValueRefs(it,
                                                      schemaNames,
                                                      sourceDocumentUri,
                                                      supportsDynamicRef,
                                                      additionalItemsHasSchemaValue,
                                                      resource,
                                                      referencedSchemaNames));
        }
    }

    private static String schemaRefName(String refValue,
                                        SchemaResource resource,
                                        URI sourceDocumentUri) {
        return schemaReference(refValue, resource, sourceDocumentUri)
                .map(SchemaReference::sourceName)
                .orElse(refValue);
    }

    private static String rewriteSchemaRef(String refValue,
                                           Map<String, String> schemaNames,
                                           SchemaResource resource,
                                           URI sourceDocumentUri) {
        Optional<SchemaReference> schemaReference = schemaReference(refValue, resource, sourceDocumentUri);
        String sourceName = schemaReference
                .map(SchemaReference::sourceName)
                .orElse(refValue);
        String targetName = schemaNames.get(sourceName);
        if (targetName == null) {
            return refValue;
        }
        if (schemaReference.isEmpty()) {
            return targetName;
        }
        try {
            String fragment = OpenApiSourceBase.SCHEMA_REF_PREFIX.substring(1)
                    + targetName
                    + schemaReference.get().suffix();
            return schemaReference.get().prefix() + new URI(null, null, fragment).toASCIIString();
        } catch (URISyntaxException _) {
            return refValue;
        }
    }

    private static Optional<SchemaReference> schemaReference(String refValue,
                                                             SchemaResource resource,
                                                             URI sourceDocumentUri) {
        URI reference;
        try {
            reference = URI.create(refValue);
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
        if (refValue.startsWith("#")) {
            if (!resource.openApiDocumentResource()
                    && !Objects.equals(resource.baseUri(), sourceDocumentUri)) {
                return Optional.empty();
            }
        } else {
            if (sourceDocumentUri == null) {
                return Optional.empty();
            }
            if (!reference.isAbsolute()) {
                if (resource.baseUri() == null) {
                    return Optional.empty();
                }
                reference = resource.baseUri().resolve(reference);
            }
            if (!sourceDocumentUri.equals(documentUri(reference))) {
                return Optional.empty();
            }
        }
        String fragment = reference.getFragment();
        String prefix = OpenApiSourceBase.SCHEMA_REF_PREFIX.substring(1);
        if (fragment == null || !fragment.startsWith(prefix)) {
            return Optional.empty();
        }
        int fragmentStart = refValue.indexOf('#');
        String referencePrefix = fragmentStart < 0 ? "" : refValue.substring(0, fragmentStart);
        int suffixStart = fragment.indexOf('/', prefix.length());
        return suffixStart < 0
                ? Optional.of(new SchemaReference(referencePrefix, fragment.substring(prefix.length()), ""))
                : Optional.of(new SchemaReference(referencePrefix,
                                                  fragment.substring(prefix.length(), suffixStart),
                                                  fragment.substring(suffixStart)));
    }

    private static URI documentUri(Object value) {
        if (!(value instanceof String uri)) {
            return null;
        }
        try {
            return documentUri(URI.create(uri));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static URI documentUri(Object value, String webContext) {
        URI uri = documentUri(value);
        if (uri == null || uri.isAbsolute()) {
            return uri;
        }
        String basePath = webContext.startsWith("/") ? webContext : "/" + webContext;
        try {
            URI baseUri = URI.create(basePath);
            if (uri.getRawAuthority() == null && uri.getRawPath().isEmpty()) {
                return documentUri(uri.getRawQuery() == null
                                           ? baseUri
                                           : URI.create(basePath + "?" + uri.getRawQuery()));
            }
            return documentUri(baseUri.resolve(uri));
        } catch (IllegalArgumentException _) {
            return uri;
        }
    }

    private static URI resolveDocumentUri(Object value, URI baseUri) {
        URI uri = documentUri(value);
        if (uri == null || uri.isAbsolute() || baseUri == null) {
            return uri;
        }
        return documentUri(baseUri.resolve(uri));
    }

    private static URI documentUri(URI uri) {
        String value = uri.toString();
        int fragment = value.indexOf('#');
        return URI.create(fragment < 0 ? value : value.substring(0, fragment)).normalize();
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

    private enum InlineSchemaContext {
        OPEN_API_OBJECT,
        COMPONENTS,
        NAMED_OPEN_API_OBJECTS,
        EXTENSIBLE_NAMED_OPEN_API_OBJECTS,
        CALLBACKS,
        LINKS,
        LINK_OBJECT
    }

    private record SchemaResource(URI baseUri, boolean openApiDocumentResource) {
    }

    private record SchemaReference(String prefix, String sourceName, String suffix) {
    }

}
