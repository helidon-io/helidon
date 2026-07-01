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
import io.helidon.codegen.TypeHierarchy;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.common.DeclarativeElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceContracts;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.tracing.TracingTypes.ANNOTATION_TAG_PARAM;
import static io.helidon.declarative.codegen.tracing.TracingTypes.ANNOTATION_TRACED;

class TracingExtension implements RegistryCodegenExtension {
    static final String DEFAULT_NAME_TEMPLATE = "%1$s.%2$s";
    static final String DEFAULT_SPAN_KIND = "INTERNAL";
    static final TypeName GENERATOR = TypeName.create(TracingExtension.class);
    private final RegistryCodegenContext ctx;

    TracingExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        // collect all typeInfo + method that is traced
        Collection<TypeInfo> roundTypes = roundContext.types();
        Map<TypeName, TypeInfo> effectiveTypes = new HashMap<>();
        roundTypes.forEach(it -> effectiveTypes.put(it.typeName(), it));

        Collection<TypeInfo> annotatedTypes = roundContext.annotatedTypes(ANNOTATION_TRACED);

        Map<TypeName, TracedElements> tracedElements = new HashMap<>();

        for (TypeInfo type : annotatedTypes) {
            if (type.kind() == ElementKind.INTERFACE) {
                continue;
            }
            TracedElements tracedElementValue = tracedElements(tracedElements, type);
            TypeInfo effectiveType = effectiveTypes.getOrDefault(type.typeName(), type);

            addTypeTracedMethods(tracedElementValue,
                                 effectiveType.elementInfo(),
                                 type.findAnnotation(ANNOTATION_TRACED),
                                 type.typeName());
        }

        for (TypeInfo type : roundTypes) {
            if (type.kind() == ElementKind.INTERFACE) {
                continue;
            }

            Set<ResolvedType> contracts = new HashSet<>();
            ServiceContracts serviceContracts = ServiceContracts.create(ctx.options(), roundContext::typeInfo, type);
            serviceContracts.addContracts(contracts, new HashSet<>(), type);

            contracts.stream()
                    .sorted(Comparator.comparing(contract -> contract.type().resolvedName()))
                    .flatMap(contract -> {
                        TypeName resolvedContractType = serviceContracts.resolveContractType(contracts,
                                                                                             contract.type());
                        return roundContext.typeInfo(resolvedContractType)
                                .or(() -> roundContext.typeInfo(resolvedContractType.genericTypeName()))
                                .map(it -> Map.entry(ResolvedType.create(resolvedContractType), it))
                                .stream();
                    })
                    .filter(contract -> contract.getValue().hasAnnotation(ANNOTATION_TRACED))
                    .forEach(contractEntry -> {
                        ResolvedType resolvedContract = contractEntry.getKey();
                        TypeInfo contract = contractEntry.getValue();
                        Map<String, TypeName> typeArgumentMapping = typeArgumentMapping(contract, resolvedContract.type());
                        Set<ElementSignature> contractMethods = contract.elementInfo()
                                .stream()
                                .filter(ElementInfoPredicates::isMethod)
                                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                                .map(element -> resolveTypeArguments(element, typeArgumentMapping))
                                .map(TypeHierarchy::methodSignature)
                                .collect(Collectors.toUnmodifiableSet());

                        var contractAnnotation = contract.findAnnotation(ANNOTATION_TRACED);
                        var serviceMethods = type.elementInfo()
                                .stream()
                                .filter(it -> ElementInfoPredicates.isMethod(it)
                                        && contractMethods.contains(TypeHierarchy.methodSignature(it)))
                                .toList();
                        addTypeTracedMethods(tracedElements(tracedElements, type),
                                             serviceMethods,
                                             contractAnnotation,
                                             contract.typeName());
                    });
        }

        for (TypeInfo type : roundTypes) {
            if (type.kind() == ElementKind.INTERFACE) {
                continue;
            }
            for (TypedElementInfo element : type.elementInfo()) {
                if (!DeclarativeElementInfo.belongsToService(type, element)
                        || !ElementInfoPredicates.isMethod(element)
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
                               element.typeAnnotation().map(TracedTypeAnnotation::annotation),
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
                                      Optional<Annotation> typeAnnotation,
                                      TypeName annotationSource) {
        Map<ElementSignature, TracedElement> map = tracedElementValue.elements();
        Optional<TracedTypeAnnotation> tracedTypeAnnotation = typeAnnotation
                .map(it -> new TracedTypeAnnotation(it, annotationSource));

        elements
                .stream()
                .filter(element -> DeclarativeElementInfo.belongsToService(tracedElementValue.type(), element))
                .filter(ElementInfoPredicates::isMethod)
                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .forEach(element -> addTracedElement(map, element, tracedTypeAnnotation));
    }

    private void addTracedElement(Map<ElementSignature, TracedElement> map,
                                  TypedElementInfo element,
                                  Optional<TracedTypeAnnotation> typeAnnotation) {
        map.compute(TypeHierarchy.methodSignature(element), (key, existing) -> {
            if (existing == null) {
                return new TracedElement(element, typeAnnotation);
            }

            TypedElementInfo selectedElement = existing.element();
            if (!selectedElement.hasAnnotation(ANNOTATION_TRACED) && element.hasAnnotation(ANNOTATION_TRACED)) {
                selectedElement = element;
            }

            Optional<TracedTypeAnnotation> selectedTypeAnnotation = existing.typeAnnotation();
            if (selectedTypeAnnotation.isEmpty()) {
                selectedTypeAnnotation = typeAnnotation;
            } else if (typeAnnotation.isPresent()) {
                TracedTypeAnnotation existingAnnotation = selectedTypeAnnotation.orElseThrow();
                TracedTypeAnnotation candidateAnnotation = typeAnnotation.orElseThrow();
                if (isSubtype(candidateAnnotation.source(), existingAnnotation.source())) {
                    selectedTypeAnnotation = typeAnnotation;
                } else if (!isSubtype(existingAnnotation.source(), candidateAnnotation.source())
                        && !existingAnnotation.annotation().equals(candidateAnnotation.annotation())) {
                    throw new CodegenException("Ambiguous tracing type annotations for " + element.signature().text()
                                                       + " inherited from unrelated contracts "
                                                       + existingAnnotation.source().fqName() + " and "
                                                       + candidateAnnotation.source().fqName(),
                                               element.originatingElementValue());
                }
            }

            return new TracedElement(selectedElement, selectedTypeAnnotation);
        });
    }

    private boolean isSubtype(TypeName candidate, TypeName expectedSupertype) {
        return isSubtype(candidate.genericTypeName(), expectedSupertype.genericTypeName(), new HashSet<>());
    }

    private boolean isSubtype(TypeName candidate, TypeName expectedSupertype, Set<TypeName> processed) {
        if (candidate.equals(expectedSupertype)) {
            return true;
        }
        if (!processed.add(candidate)) {
            return false;
        }

        Optional<TypeInfo> candidateInfo = ctx.typeInfo(candidate)
                .or(() -> ctx.typeInfo(candidate.genericTypeName()));
        if (candidateInfo.isEmpty()) {
            return false;
        }

        TypeInfo typeInfo = candidateInfo.orElseThrow();
        for (TypeInfo interfaceType : typeInfo.interfaceTypeInfo()) {
            if (isSubtype(interfaceType.typeName(), expectedSupertype, processed)) {
                return true;
            }
        }
        return typeInfo.superTypeInfo()
                .map(it -> isSubtype(it.typeName(), expectedSupertype, processed))
                .orElse(false);
    }

    private static Map<String, TypeName> typeArgumentMapping(TypeInfo contract, TypeName resolvedContract) {
        List<String> typeParameters = contract.typeName().typeParameters();
        List<TypeName> typeArguments = resolvedContract.typeArguments();
        if (typeArguments.isEmpty()) {
            typeArguments = contract.typeName().typeArguments();
        }
        if (typeParameters.isEmpty()) {
            typeParameters = contract.declaredType()
                    .typeArguments()
                    .stream()
                    .filter(TypeName::generic)
                    .filter(Predicate.not(TypeName::wildcard))
                    .map(TypeName::className)
                    .map(TracingExtension::genericTypeName)
                    .toList();
        }
        if (typeParameters.isEmpty() || typeArguments.isEmpty()) {
            return Map.of();
        }

        Map<String, TypeName> result = new HashMap<>();
        for (int i = 0; i < Math.min(typeParameters.size(), typeArguments.size()); i++) {
            result.put(genericTypeName(typeParameters.get(i)), typeArguments.get(i));
        }
        return Map.copyOf(result);
    }

    private TypedElementInfo resolveTypeArguments(TypedElementInfo element, Map<String, TypeName> typeArguments) {
        if (typeArguments.isEmpty()) {
            return element;
        }

        Map<String, TypeName> elementTypeArguments = typeArguments;
        if (!element.typeParameters().isEmpty()) {
            elementTypeArguments = new HashMap<>(typeArguments);
            for (TypeName typeParameter : element.typeParameters()) {
                elementTypeArguments.remove(typeParameter.className());
            }
        }
        Map<String, TypeName> substitutions = elementTypeArguments;

        List<TypedElementInfo> parameters = element.parameterArguments()
                .stream()
                .map(it -> TypedElementInfo.builder(it)
                        .typeName(resolveTypeArguments(it.typeName(), substitutions))
                        .build())
                .toList();
        List<TypeName> typeParameters = element.typeParameters()
                .stream()
                .map(it -> TypeName.builder(it)
                        .lowerBounds(it.lowerBounds()
                                             .stream()
                                             .map(bound -> resolveTypeArguments(bound, substitutions))
                                             .toList())
                        .upperBounds(it.upperBounds()
                                             .stream()
                                             .map(bound -> resolveTypeArguments(bound, substitutions))
                                             .toList())
                        .build())
                .toList();

        return TypedElementInfo.builder(element)
                .typeName(resolveTypeArguments(element.typeName(), substitutions))
                .parameterArguments(parameters)
                .typeParameters(typeParameters)
                .build();
    }

    private static String genericTypeName(String typeParameter) {
        String name = typeParameter.trim();
        int index = name.indexOf(' ');
        return index == -1 ? name : name.substring(0, index);
    }

    private TypeName resolveTypeArguments(TypeName typeName, Map<String, TypeName> typeArguments) {
        if (typeName.generic()) {
            TypeName resolved = typeArguments.get(typeName.className().trim());
            if (resolved != null) {
                return resolved;
            }
        }

        List<TypeName> resolvedTypeArguments = typeName.typeArguments()
                .stream()
                .map(it -> resolveTypeArguments(it, typeArguments))
                .toList();
        List<TypeName> resolvedLowerBounds = typeName.lowerBounds()
                .stream()
                .map(it -> resolveTypeArguments(it, typeArguments))
                .toList();
        List<TypeName> resolvedUpperBounds = typeName.upperBounds()
                .stream()
                .map(it -> resolveTypeArguments(it, typeArguments))
                .toList();
        Optional<TypeName> resolvedComponentType = typeName.componentType()
                .map(it -> resolveTypeArguments(it, typeArguments));

        if (sameResolvedTypes(resolvedTypeArguments, typeName.typeArguments())
                && sameResolvedTypes(resolvedLowerBounds, typeName.lowerBounds())
                && sameResolvedTypes(resolvedUpperBounds, typeName.upperBounds())
                && resolvedComponentType.map(ResolvedType::create)
                .equals(typeName.componentType().map(ResolvedType::create))) {
            return typeName;
        }

        TypeName.Builder builder = TypeName.builder(typeName)
                .typeArguments(resolvedTypeArguments)
                .lowerBounds(resolvedLowerBounds)
                .upperBounds(resolvedUpperBounds);
        resolvedComponentType.ifPresent(builder::componentType);
        return builder.build();
    }

    private static boolean sameResolvedTypes(List<TypeName> first, List<TypeName> second) {
        return first.stream().map(ResolvedType::create).toList()
                .equals(second.stream().map(ResolvedType::create).toList());
    }

    private String parameterTagName(TypeInfo serviceTypeInfo,
                                    TypedElementInfo element,
                                    TypedElementInfo effectiveParameter,
                                    int parameterIndex) {
        ElementSignature methodSignature = TypeHierarchy.methodSignature(element);
        TypeInfo declaredService = ctx.typeInfo(serviceTypeInfo.typeName())
                .or(() -> ctx.typeInfo(serviceTypeInfo.typeName().genericTypeName()))
                .orElse(serviceTypeInfo);

        for (TypedElementInfo declaredMethod : declaredService.elementInfo()) {
            if (!ElementInfoPredicates.isMethod(declaredMethod)
                    || !methodSignature.equals(TypeHierarchy.methodSignature(declaredMethod))
                    || declaredMethod.parameterArguments().size() <= parameterIndex) {
                continue;
            }
            TypedElementInfo declaredParameter = declaredMethod.parameterArguments().get(parameterIndex);
            if (declaredParameter.hasAnnotation(ANNOTATION_TAG_PARAM)) {
                return declaredParameter.annotation(ANNOTATION_TAG_PARAM)
                        .value()
                        .filter(Predicate.not(String::isBlank))
                        .orElse(declaredParameter.elementName());
            }
        }

        ServiceContracts serviceContracts = ServiceContracts.create(ctx.options(), ctx::typeInfo, declaredService);
        Set<ResolvedType> contracts = new HashSet<>();
        serviceContracts.addContracts(contracts, new HashSet<>(), declaredService);

        ParameterTagSource selected = null;
        for (ResolvedType unresolvedContract : contracts.stream()
                .sorted(Comparator.comparing(it -> it.type().resolvedName()))
                .toList()) {
            TypeName resolvedContract = serviceContracts.resolveContractType(contracts, unresolvedContract.type());
            Optional<TypeInfo> maybeContract = ctx.typeInfo(resolvedContract)
                    .or(() -> ctx.typeInfo(resolvedContract.genericTypeName()));
            if (maybeContract.isEmpty()) {
                continue;
            }

            TypeInfo contract = maybeContract.orElseThrow();
            Map<String, TypeName> typeArguments = typeArgumentMapping(contract, resolvedContract);
            for (TypedElementInfo contractMethod : contract.elementInfo()) {
                if (!ElementInfoPredicates.isMethod(contractMethod)
                        || ElementInfoPredicates.isStatic(contractMethod)
                        || ElementInfoPredicates.isPrivate(contractMethod)) {
                    continue;
                }

                TypedElementInfo resolvedMethod = resolveTypeArguments(contractMethod, typeArguments);
                if (!methodSignature.equals(TypeHierarchy.methodSignature(resolvedMethod))
                        || resolvedMethod.parameterArguments().size() <= parameterIndex) {
                    continue;
                }

                TypedElementInfo contractParameter = resolvedMethod.parameterArguments().get(parameterIndex);
                if (!contractParameter.hasAnnotation(ANNOTATION_TAG_PARAM)) {
                    continue;
                }

                Annotation annotation = contractParameter.annotation(ANNOTATION_TAG_PARAM);
                String name = annotation.value()
                        .filter(Predicate.not(String::isBlank))
                        .orElse(contractParameter.elementName());
                ParameterTagSource candidate = new ParameterTagSource(contract.typeName(), annotation, name);
                if (selected == null || isSubtype(candidate.source(), selected.source())) {
                    selected = candidate;
                } else if (!isSubtype(selected.source(), candidate.source())
                        && (!selected.annotation().equals(candidate.annotation()) || !selected.name().equals(candidate.name()))) {
                    throw new CodegenException("Ambiguous tracing parameter annotations for " + element.signature().text()
                                                       + " parameter " + parameterIndex
                                                       + " inherited from unrelated contracts "
                                                       + selected.source().fqName() + " and " + candidate.source().fqName(),
                                               element.originatingElementValue());
                }
            }
        }

        if (selected != null) {
            return selected.name();
        }
        return effectiveParameter.annotation(ANNOTATION_TAG_PARAM)
                .value()
                .filter(Predicate.not(String::isBlank))
                .orElse(effectiveParameter.elementName());
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
                String tagName = parameterTagName(serviceTypeInfo, element, param, i);
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
                                 Optional<TracedTypeAnnotation> typeAnnotation) {
    }

    private record TracedTypeAnnotation(Annotation annotation,
                                        TypeName source) {
    }

    private record ParameterTagSource(TypeName source,
                                      Annotation annotation,
                                      String name) {
    }

    record TagParam(int index, TypeName type, String name) {
    }
}
