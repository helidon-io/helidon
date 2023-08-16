/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.lang.model.element.ElementKind;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeValues;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.tools.CustomAnnotationTemplateRequest;
import io.helidon.inject.tools.CustomAnnotationTemplateResponse;
import io.helidon.inject.tools.GenericTemplateCreator;
import io.helidon.inject.tools.GenericTemplateCreatorRequest;
import io.helidon.inject.tools.ToolsException;
import io.helidon.inject.tools.spi.CustomAnnotationTemplateCreator;

/**
 * Annotation processor that generates a service for each method annotated with fallback annotation.
 * Service provider implementation of a {@link CustomAnnotationTemplateCreator}.
 */
public class FallbackMethodCreator extends FtMethodCreatorBase implements CustomAnnotationTemplateCreator {
    private static final String FALLBACK_ANNOTATION = "io.helidon.faulttolerance.FaultTolerance.Fallback";
    private static final TypeName FALLBACK_ANNOTATION_TYPE = TypeName.create(FALLBACK_ANNOTATION);
    private static final TypeName THROWABLE_TYPE = TypeName.create(Throwable.class);

    /**
     * Default constructor used by the {@link java.util.ServiceLoader}.
     */
    public FallbackMethodCreator() {
    }

    @Override
    public Set<String> annoTypes() {
        return Set.of(FALLBACK_ANNOTATION);
    }

    @Override
    public Optional<CustomAnnotationTemplateResponse> create(CustomAnnotationTemplateRequest request) {
        TypeInfo enclosingType = request.enclosingTypeInfo();

        if (!ElementKind.METHOD.name().equals(request.targetElement().elementTypeKind())) {
            // we are only interested in methods, not in classes
            throw new ToolsException(FALLBACK_ANNOTATION + " can only be defined on methods");
        }

        String classname = className(request.annoTypeName(), enclosingType.typeName(), request.targetElement().elementName());
        TypeName generatedTypeName = TypeName.builder()
                .packageName(enclosingType.typeName().packageName())
                .className(classname)
                .build();

        GenericTemplateCreator genericTemplateCreator = request.genericTemplateCreator();
        GenericTemplateCreatorRequest genericCreatorRequest = GenericTemplateCreatorRequest.builder()
                .customAnnotationTemplateRequest(request)
                .template(Templates.loadTemplate("fallback-method.java.hbs"))
                .generatedTypeName(generatedTypeName)
                .overrideProperties(addProperties(request, enclosingType))
                .build();
        return genericTemplateCreator.create(genericCreatorRequest);
    }

    private Map<String, Object> addProperties(CustomAnnotationTemplateRequest request, TypeInfo enclosingType) {
        FallbackDef fallback = new FallbackDef();
        fallback.beanType = enclosingType.typeName().name();

        TypedElementInfo targetElement = request.targetElement();
        Map<String, Object> response = new HashMap<>();
        TypeName expectedReturnType = targetElement.typeName();
        if ("void".equals(expectedReturnType.className())) {
            fallback.returnType = "Void";
            fallback.returnVoid = true;
        } else {
            fallback.returnType = expectedReturnType.name();
        }

        // http.methodName - name of the method in source code (not HTTP Method)
        fallback.methodName = targetElement.elementName();

        Annotation fallbackAnnotation = targetElement.findAnnotation(FALLBACK_ANNOTATION_TYPE)
                .orElseThrow(() -> new ToolsException("Annotation " + FALLBACK_ANNOTATION
                                                              + " must be defined on the processed type: "
                                                              + fallback.beanType));

        fallback.fallbackName = fallbackAnnotation.value()
                .orElseThrow(() -> new ToolsException("Missing value on " + FALLBACK_ANNOTATION
                                                              + " on type: " + fallback.beanType));
        fallback.applyOn = fallbackAnnotation.getValue("applyOn")
                .filter(Predicate.not(String::isBlank))
                .map(it -> List.of(it.split(",")))
                .orElseGet(List::of);
        fallback.skipOn = fallbackAnnotation.getValue("skipOn")
                .filter(Predicate.not(String::isBlank))
                .map(it -> List.of(it.split(",")))
                .orElseGet(List::of);

        // method parameters
        List<TypedElementInfo> expectedArguments = request.targetElementArgs();
        fallback.paramTypes = expectedArguments
                .stream()
                .map(TypedElementInfo::typeName)
                .map(TypeName::name)
                .toList();

        // now we need to locate the fallback method
        // we do have enclosing type, so we need to iterate through its elements and find the matching method(s)
        List<TypedElementInfo> allElements = new ArrayList<>(enclosingType.elementInfo());
        allElements.addAll(enclosingType.otherElementInfo());

        List<TypedElementInfo> matchingMethodsByName = allElements.stream()
                .filter(it -> TypeValues.KIND_METHOD.equals(it.elementTypeKind()))
                .filter(it -> fallback.fallbackName.equals(it.elementName()))
                .toList();

        if (matchingMethodsByName.isEmpty()) {
            throw new ToolsException("Could not find matching fallback method for name " + fallback.fallbackName + " in "
                                             + enclosingType.typeName() + ".");
        }

        boolean found = false;
        // matches by name, but not by return type or parameters
        List<BadCandidate> badCandidates = new ArrayList<>();

        for (TypedElementInfo candidate : matchingMethodsByName) {
            // now we need to find a method that matches
            // - return type
            // - parameters
            // - has an optional additional Throwable argument (must be last)

            TypeName candidateReturn = candidate.typeName();

            if (!expectedReturnType.equals(candidateReturn)) {
                badCandidates.add(new BadCandidate(candidate, "Same name, different return types"));
                continue;
            }

            List<TypedElementInfo> candidateArguments = candidate.parameterArguments();
            if (expectedArguments.size() != candidateArguments.size()
                    && expectedArguments.size() != candidateArguments.size() - 1) {
                badCandidates.add(new BadCandidate(candidate, "Same name, wrong number of parameters"));
                continue;
            }

            boolean goodCandidate = true;
            for (int i = 0; i < expectedArguments.size(); i++) {
                if (!expectedArguments.get(i).typeName().equals(candidateArguments.get(i).typeName())) {
                    badCandidates.add(new BadCandidate(candidate, "Same name, different parameter types at index " + i));
                    goodCandidate = false;
                    break;
                }
            }
            if (!goodCandidate) {
                continue;
            }
            if (expectedArguments.size() == candidateArguments.size()) {
                // this is a good candidate, let's use it (we may still find a better candidate with Throwable)
                fallback.fallbackStatic = candidate.modifiers().contains(TypeValues.MODIFIER_STATIC);
                fallback.fallbackAcceptsThrowable = false;
                found = true;
            } else {
                // check last parameter
                if (candidateArguments.get(candidateArguments.size() - 1).typeName().equals(THROWABLE_TYPE)) {
                    // best candidate
                    fallback.fallbackStatic = candidate.modifiers().contains(TypeValues.MODIFIER_STATIC);
                    fallback.fallbackAcceptsThrowable = true;
                    found = true;
                    break;
                }
                badCandidates.add(new BadCandidate(candidate, "Same name, last parameter is not java.lang.Throwable"));
            }
        }

        if (!found) {
            throw new ToolsException("Could not find matching fallback method for name " + fallback.fallbackName + ","
                                             + " following bad candidates found: " + badCandidates);
        }

        TypedElementInfo targetMethod = matchingMethodsByName.get(0);
        if (targetMethod.modifiers().contains(TypeValues.MODIFIER_STATIC)) {
            fallback.fallbackStatic = true;
        }
        if (!targetMethod.typeName().name().equals(fallback.returnType)) {
            throw new ToolsException("Fallback method " + fallback.fallbackName + " in"
                                             + enclosingType.typeName() + " has different return type. Expected: "
                                             + fallback.returnType + ", got: " + targetMethod.typeName().name());
        }
        // hardcode to true for now
        fallback.fallbackAcceptsThrowable = true;
        /*
        boolean found = false;
        // matches by name, but not by return type or parameters
        List<BadCandidate> badCandidates = new ArrayList<>();
        f
        */

        response.put("fallback", fallback);
        return response;
    }

    private record BadCandidate(TypedElementInfo element, String reason) {
    }

    /**
     * Needed for template processing.
     * Do not use.
     */
    @Deprecated(since = "1.0.0")
    public static class FallbackDef {
        // name of the method that is annotated
        private String methodName;
        // return type of the annotated method
        private String returnType;
        private boolean returnVoid;
        // type of the bean that hosts the annotated method
        private String beanType;

        private List<String> applyOn;
        private List<String> skipOn;
        private List<String> paramTypes;

        private String fallbackName;
        private boolean fallbackStatic;
        private boolean fallbackAcceptsThrowable;

        public String getMethodName() {
            return methodName;
        }

        public String getReturnType() {
            return returnType;
        }

        public boolean isReturnVoid() {
            return returnVoid;
        }

        public String getBeanType() {
            return beanType;
        }

        public List<String> getApplyOn() {
            return applyOn;
        }

        public List<String> getSkipOn() {
            return skipOn;
        }

        public List<String> getParamTypes() {
            return paramTypes;
        }

        public boolean isFallbackStatic() {
            return fallbackStatic;
        }

        public String isFallbackName() {
            return fallbackName;
        }

        public boolean isFallbackAcceptsThrowable() {
            return fallbackAcceptsThrowable;
        }
    }
}
