/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.tracing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceContracts;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.tracing.TracingTypes.ANNOTATION_TAG_PARAM;
import static io.helidon.declarative.codegen.tracing.TracingTypes.ANNOTATION_TRACED;

class TracingExtension implements RegistryCodegenExtension {
    public static final String DEFAULT_NAME_TEMPLATE = "%1$s.%2$s";
    public static final String DEFAULT_SPAN_KIND = "INTERNAL";
    static final TypeName GENERATOR = TypeName.create(TracingExtension.class);
    private final RegistryCodegenContext ctx;

    TracingExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        // collect all typeInfo + method that is traced
        Collection<TypeInfo> roundTypes = roundContext.types();

        Collection<TypeInfo> annotatedTypes = roundContext.annotatedTypes(ANNOTATION_TRACED);

        Map<TypeName, TracedElements> tracedElements = new HashMap<>();

        for (TypeInfo type : annotatedTypes) {
            if (type.kind() == ElementKind.INTERFACE) {
                continue;
            }
            TracedElements tracedElementValue = tracedElements(tracedElements, type);

            addTypeTracedMethods(tracedElementValue, type.elementInfo(), type.findAnnotation(ANNOTATION_TRACED));
        }

        for (TypeInfo type : roundTypes) {
            if (type.kind() == ElementKind.INTERFACE) {
                continue;
            }

            Set<ResolvedType> contracts = new HashSet<>();
            ServiceContracts.create(ctx.options(), roundContext::typeInfo, type)
                    .addContracts(contracts, new HashSet<>(), type);

            contracts.stream()
                    .map(ResolvedType::type)
                    .sorted(Comparator.comparing(TypeName::resolvedName))
                    .flatMap(contract -> roundContext.typeInfo(contract)
                            .or(() -> roundContext.typeInfo(contract.genericTypeName()))
                            .stream())
                    .filter(contract -> contract.hasAnnotation(ANNOTATION_TRACED))
                    .forEach(contract -> {
                        Set<ElementSignature> contractMethods = contract.elementInfo()
                                .stream()
                                .filter(ElementInfoPredicates::isMethod)
                                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                                .map(TypedElementInfo::signature)
                                .collect(Collectors.toUnmodifiableSet());

                        var contractAnnotation = contract.findAnnotation(ANNOTATION_TRACED);
                        var serviceMethods = type.elementInfo()
                                .stream()
                                .filter(it -> contractMethods.contains(it.signature()))
                                .toList();
                        addTypeTracedMethods(tracedElements(tracedElements, type), serviceMethods, contractAnnotation);
                    });
        }

        for (TypeInfo type : roundTypes) {
            if (type.kind() == ElementKind.INTERFACE) {
                continue;
            }
            for (TypedElementInfo element : type.elementInfo()) {
                if (!ElementInfoPredicates.isMethod(element)
                        || ElementInfoPredicates.isStatic(element)
                        || ElementInfoPredicates.isPrivate(element)
                        || !element.hasAnnotation(ANNOTATION_TRACED)) {
                    continue;
                }

                addTracedElement(tracedElements(tracedElements, type).elements(),
                                 element,
                                 Optional.empty());
            }
        }

        TracedHandler handler = new TracedHandler(roundContext);

        /*
         and now let's process everything
         for each traced method, we need to generated an element interceptor
         */
        for (var tracedEntry : tracedElements.entrySet()) {
            TypeName serviceType = tracedEntry.getKey();
            TracedElements tracedElementValue = tracedEntry.getValue();
            TypeInfo serviceTypeInfo = tracedElementValue.type();
            Map<ElementSignature, TracedElement> elements = tracedElementValue.elements();
            int index = 0;

            for (TracedElement element : elements.values()) {
                processElement(handler,
                               serviceTypeInfo,
                               serviceType,
                               element.typeAnnotation(),
                               element.element(),
                               index);
                index++;
            }
        }
    }

    private TracedElements tracedElements(Map<TypeName, TracedElements> tracedElements,
                                          TypeInfo type) {
        return tracedElements.computeIfAbsent(type.typeName(),
                                              key -> new TracedElements(type, new HashMap<>()));
    }

    private void addTypeTracedMethods(TracedElements tracedElementValue,
                                      Collection<TypedElementInfo> elements,
                                      Optional<Annotation> typeAnnotation) {
        Map<ElementSignature, TracedElement> map = tracedElementValue.elements();

        elements
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .forEach(element -> addTracedElement(map, element, typeAnnotation));
    }

    private void addTracedElement(Map<ElementSignature, TracedElement> map,
                                  TypedElementInfo element,
                                  Optional<Annotation> typeAnnotation) {
        map.compute(element.signature(), (key, existing) -> {
            if (existing == null) {
                return new TracedElement(element, typeAnnotation);
            }
            if (existing.typeAnnotation().isEmpty() && typeAnnotation.isPresent()) {
                return new TracedElement(existing.element(), typeAnnotation);
            }
            return existing;
        });
    }

    private void processElement(TracedHandler handler,
                                TypeInfo serviceTypeInfo,
                                TypeName serviceType,
                                Optional<Annotation> typeAnnotation,
                                TypedElementInfo element,
                                int index) {
        // collect tags, kind etc. from all annotations

        String nameTemplate = DEFAULT_NAME_TEMPLATE;
        Map<String, String> tags = new LinkedHashMap<>();
        String spanKind = DEFAULT_SPAN_KIND;

        // first gather data from type annotation
        var maybeTraced = typeAnnotation.or(() -> serviceTypeInfo.findAnnotation(ANNOTATION_TRACED));
        if (maybeTraced.isPresent()) {
            var traced = maybeTraced.get();
            nameTemplate = traced.value()
                    .filter(Predicate.not(DEFAULT_NAME_TEMPLATE::equals))
                    .filter(Predicate.not(String::isBlank))
                    .orElse(nameTemplate);
            spanKind = traced.stringValue("kind")
                    .filter(Predicate.not(DEFAULT_SPAN_KIND::equals))
                    .orElse(spanKind);

            List<Annotation> tagsAnnotations = traced.annotationValues("tags")
                    .orElseGet(List::of);
            for (Annotation tagsAnnotation : tagsAnnotations) {
                String key = tagsAnnotation.stringValue("key")
                        .orElseThrow(() -> new CodegenException("Missing key in tags annotation",
                                                                element.originatingElementValue()));
                String value = tagsAnnotation.stringValue("value")
                        .orElseThrow(() -> new CodegenException("Missing value in tags annotation",
                                                                element.originatingElementValue()));
                // we will always overwrite existing
                tags.put(key, value);
            }
        }

        // then from method annotation
        maybeTraced = element.findAnnotation(ANNOTATION_TRACED);
        if (maybeTraced.isPresent()) {
            var traced = maybeTraced.get();
            nameTemplate = traced.value()
                    .filter(Predicate.not(DEFAULT_NAME_TEMPLATE::equals))
                    .filter(Predicate.not(String::isBlank))
                    .orElse(nameTemplate);
            spanKind = traced.stringValue("kind")
                    .filter(Predicate.not(DEFAULT_SPAN_KIND::equals))
                    .orElse(spanKind);

            List<Annotation> tagsAnnotations = traced.annotationValues("tags")
                    .orElseGet(List::of);
            for (Annotation tagsAnnotation : tagsAnnotations) {
                String key = tagsAnnotation.stringValue("key")
                        .orElseThrow(() -> new CodegenException("Missing key in tags annotation",
                                                                element.originatingElementValue()));
                String value = tagsAnnotation.stringValue("value")
                        .orElseThrow(() -> new CodegenException("Missing value in tags annotation",
                                                                element.originatingElementValue()));
                // we will always overwrite existing
                tags.put(key, value);
            }
        }

        String className = serviceType.fqName();
        String methodName = element.elementName();
        String spanName = String.format(nameTemplate, className, methodName);
        List<TagParam> tagParams = new ArrayList<>();

        // there may still be an annotation on a parameter
        int i = 0;
        for (TypedElementInfo param : element.parameterArguments()) {
            if (param.hasAnnotation(ANNOTATION_TAG_PARAM)) {
                String tagName = param.elementName();
                var tagParam = param.annotation(ANNOTATION_TAG_PARAM);
                tagName = tagParam.value()
                        .filter(Predicate.not(String::isBlank))
                        .orElse(tagName);
                tagParams.add(new TagParam(i, param.typeName(), tagName));
            }
            i++;
        }

        handler.handle(serviceType, element, index, spanName, spanKind, tags, tagParams);
    }

    private record TracedElements(TypeInfo type,
                                  Map<ElementSignature, TracedElement> elements) {
    }

    private record TracedElement(TypedElementInfo element,
                                 Optional<Annotation> typeAnnotation) {
    }

    record TagParam(int index, TypeName type, String name) {
    }
}
