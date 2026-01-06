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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
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
        Map<TypeName, TypeInfo> usedTypes = new HashMap<>();
        for (TypeInfo roundType : roundTypes) {
            usedTypes.put(roundType.typeName(), roundType);
        }

        Collection<TypeInfo> annotatedTypes = roundContext.annotatedTypes(ANNOTATION_TRACED);
        Collection<TypedElementInfo> annotatedElements = roundContext.annotatedElements(ANNOTATION_TRACED);

        Map<TypeName, TracedElements> tracedElements = new HashMap<>();

        for (TypeInfo type : annotatedTypes) {
            var tracedElementValue = tracedElements.computeIfAbsent(type.typeName(),
                                                                    k -> new TracedElements(type, new HashMap<>()));
            var map = tracedElementValue.elements();

            type.elementInfo()
                    .stream()
                    .filter(ElementInfoPredicates::isMethod)
                    .filter(Predicate.not(ElementInfoPredicates::isStatic))
                    .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                    .forEach(element -> map.put(element.signature(), element));
        }

        for (TypedElementInfo element : annotatedElements) {
            TypeInfo type = typeForElement(roundContext, usedTypes, element);
            tracedElements.computeIfAbsent(type.typeName(), k -> new TracedElements(type, new HashMap<>()))
                    .elements()
                    .put(element.signature(), element);
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
            Map<ElementSignature, TypedElementInfo> elements = tracedElementValue.elements();
            int index = 0;

            for (TypedElementInfo element : elements.values()) {
                processElement(handler, serviceTypeInfo, serviceType, element, index);
                index++;
            }
        }
    }

    private void processElement(TracedHandler handler,
                                TypeInfo serviceTypeInfo,
                                TypeName serviceType,
                                TypedElementInfo element,
                                int index) {
        // collect tags, kind etc. from all annotations

        String nameTemplate = DEFAULT_NAME_TEMPLATE;
        Map<String, String> tags = new LinkedHashMap<>();
        String spanKind = DEFAULT_SPAN_KIND;

        // first gather data from type annotation
        var maybeTraced = serviceTypeInfo.findAnnotation(ANNOTATION_TRACED);
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
                tagParams.add(new TagParam(i, element.typeName(), tagName));
            }
            i++;
        }

        handler.handle(serviceType, element, index, spanName, spanKind, tags, tagParams);
    }

    private TypeInfo typeForElement(RegistryRoundContext roundContext,
                                    Map<TypeName, TypeInfo> usedTypes,
                                    TypedElementInfo element) {
        var enclosing = element.enclosingType()
                .orElseThrow(() -> new CodegenException("Annotated element does not have an enclosing type",
                                                        element.originatingElementValue()));

        var typeInfo = usedTypes.get(enclosing);
        if (typeInfo != null) {
            return typeInfo;
        }
        typeInfo = roundContext.typeInfo(enclosing)
                .orElseThrow(() -> new CodegenException("Cannot find enclosing type " + enclosing.fqName(),
                                                        element.originatingElementValue()));
        usedTypes.put(enclosing, typeInfo);
        return typeInfo;
    }

    private record TracedElements(TypeInfo type, Map<ElementSignature, TypedElementInfo> elements) {
    }

    record TagParam(int index, TypeName type, String name) {
    }
}
