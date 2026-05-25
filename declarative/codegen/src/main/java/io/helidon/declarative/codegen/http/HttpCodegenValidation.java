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

package io.helidon.declarative.codegen.http;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.codegen.CodegenException;
import io.helidon.common.Api;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_COOKIE_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_ENTITY_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_FORM_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_PATH_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_QUERY_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_REQUEST_PARAMS_ANNOTATION;

/**
 * HTTP validation utilities used by code generation.
 */
@Api.Internal
public final class HttpCodegenValidation {
    private static final List<TypeName> METHOD_PARAMETER_ANNOTATIONS = List.of(HTTP_HEADER_PARAM_ANNOTATION,
                                                                               HTTP_COOKIE_PARAM_ANNOTATION,
                                                                               HTTP_QUERY_PARAM_ANNOTATION,
                                                                               HTTP_FORM_PARAM_ANNOTATION,
                                                                               HTTP_PATH_PARAM_ANNOTATION,
                                                                               HTTP_ENTITY_ANNOTATION,
                                                                               HTTP_REQUEST_PARAMS_ANNOTATION);
    private static final List<TypeName> REQUEST_PARAMS_COMPONENT_ANNOTATIONS = List.of(HTTP_HEADER_PARAM_ANNOTATION,
                                                                                      HTTP_COOKIE_PARAM_ANNOTATION,
                                                                                      HTTP_QUERY_PARAM_ANNOTATION,
                                                                                      HTTP_FORM_PARAM_ANNOTATION,
                                                                                      HTTP_PATH_PARAM_ANNOTATION,
                                                                                      HTTP_ENTITY_ANNOTATION);

    private HttpCodegenValidation() {
    }

    /**
     * Validate that endpoint method parameter annotations contain at most one supported request parameter annotation.
     *
     * @param annotations annotations to inspect
     * @param message validation failure message
     * @param originatingElement originating element
     */
    public static void validateMethodParameterAnnotationCount(Collection<Annotation> annotations,
                                                              String message,
                                                              Object originatingElement) {
        validateSupportedAnnotationCount(annotations, METHOD_PARAMETER_ANNOTATIONS, message, originatingElement);
    }

    /**
     * Validate that a request parameter record component contains at most one supported request parameter annotation.
     *
     * @param annotations annotations to inspect
     * @param message validation failure message
     * @param originatingElement originating element
     */
    public static void validateRequestParamsComponentAnnotationCount(Collection<Annotation> annotations,
                                                                     String message,
                                                                     Object originatingElement) {
        validateSupportedAnnotationCount(annotations, REQUEST_PARAMS_COMPONENT_ANNOTATIONS, message, originatingElement);
    }

    /**
     * Find the first supported request parameter record component annotation.
     *
     * @param annotations annotations to inspect
     * @return first supported request parameter record component annotation
     */
    public static Optional<Annotation> firstRequestParamsComponentAnnotation(Collection<Annotation> annotations) {
        return annotations.stream()
                .filter(it -> REQUEST_PARAMS_COMPONENT_ANNOTATIONS.contains(it.typeName()))
                .findFirst();
    }

    /**
     * Whether the annotation collection contains the annotation type.
     *
     * @param annotations annotations to inspect
     * @param annotation annotation type
     * @return whether the annotation collection contains the annotation type
     */
    public static boolean hasAnnotation(Collection<Annotation> annotations, TypeName annotation) {
        return annotations.stream()
                .anyMatch(it -> it.typeName().equals(annotation));
    }

    /**
     * Resolve and validate a request parameter record type.
     *
     * @param typeInfoResolver type info resolver
     * @param requestParamsType request parameter type
     * @param originatingElement originating element
     * @return resolved request parameter record type
     */
    public static TypeInfo requestParamsRecordType(Function<TypeName, Optional<TypeInfo>> typeInfoResolver,
                                                   TypeName requestParamsType,
                                                   Object originatingElement) {
        TypeInfo typeInfo = typeInfoResolver.apply(requestParamsType)
                .orElseThrow(() -> new CodegenException("Cannot find type information for @Http.RequestParams type "
                                                                + requestParamsType.fqName() + ".",
                                                        originatingElement));
        if (typeInfo.kind() != ElementKind.RECORD) {
            throw new CodegenException("@Http.RequestParams type must be a record. Type "
                                               + requestParamsType.fqName() + " is " + typeInfo.kind() + ".",
                                       typeInfo.originatingElementValue());
        }
        return typeInfo;
    }

    /**
     * Validate body parameter rules for a request parameter record.
     *
     * @param requestParamsType request parameter record type
     */
    public static void validateRequestParamsBodyComponents(TypeInfo requestParamsType) {
        int entityCount = 0;
        int formCount = 0;
        TypedElementInfo firstBodyComponent = null;

        for (TypedElementInfo component : requestParamsComponents(requestParamsType)) {
            validateRequestParamsComponentAnnotationCount(component.annotations(),
                                                         "Record component '" + component.elementName()
                                                                 + "' of @Http.RequestParams type "
                                                                 + requestParamsType.typeName().fqName()
                                                                 + " must have at most one supported request "
                                                                 + "parameter annotation.",
                                                         component.originatingElementValue());
            if (hasAnnotation(component.annotations(), HTTP_ENTITY_ANNOTATION)) {
                entityCount++;
                if (firstBodyComponent == null) {
                    firstBodyComponent = component;
                }
            }
            if (hasAnnotation(component.annotations(), HTTP_FORM_PARAM_ANNOTATION)) {
                formCount++;
                if (firstBodyComponent == null) {
                    firstBodyComponent = component;
                }
            }
        }

        if (entityCount > 1) {
            throw new CodegenException("Only one @Http.Entity record component is supported in @Http.RequestParams type "
                                               + requestParamsType.typeName().fqName() + ".",
                                       firstBodyComponent.originatingElementValue());
        }
        if (entityCount > 0 && formCount > 0) {
            throw new CodegenException("@Http.Entity and @Http.FormParam record components cannot be combined in "
                                               + "@Http.RequestParams type " + requestParamsType.typeName().fqName() + ".",
                                       firstBodyComponent.originatingElementValue());
        }
    }

    /**
     * Components of a request parameter record.
     *
     * @param requestParamsType request parameter record type
     * @return record components
     */
    public static List<TypedElementInfo> requestParamsComponents(TypeInfo requestParamsType) {
        return requestParamsType.elementInfo()
                .stream()
                .filter(it -> it.kind() == ElementKind.RECORD_COMPONENT)
                .toList();
    }

    /**
     * Validate a cookie parameter name.
     *
     * @param name cookie parameter name
     * @param annotationName annotation name for diagnostics
     * @param originatingElement originating element
     */
    public static void validateCookieName(String name, String annotationName, Object originatingElement) {
        if (!isToken(name)) {
            throw invalidCookieName(annotationName, originatingElement);
        }
    }

    private static void validateSupportedAnnotationCount(Collection<Annotation> annotations,
                                                         List<TypeName> supportedAnnotationTypes,
                                                         String message,
                                                         Object originatingElement) {
        long supportedAnnotations = annotations.stream()
                .map(Annotation::typeName)
                .filter(supportedAnnotationTypes::contains)
                .count();
        if (supportedAnnotations > 1) {
            throw new CodegenException(message, originatingElement);
        }
    }

    private static boolean isToken(String token) {
        if (token.isEmpty()) {
            return false;
        }

        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c > 254 || Character.isISOControl(c) || Character.isWhitespace(c)) {
                return false;
            }
            switch (c) {
            case '(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']', '?', '=', '{', '}' -> {
                return false;
            }
            default -> {
                // valid token character
            }
            }
        }

        return true;
    }

    private static CodegenException invalidCookieName(String annotationName, Object originatingElement) {
        String message = annotationName + " value must be a valid cookie name.";
        if (originatingElement == null) {
            return new CodegenException(message);
        }
        return new CodegenException(message, originatingElement);
    }
}
