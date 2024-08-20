/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.common.types.TypeNames.INHERITED;
import static java.util.function.Predicate.not;

/**
 * Utilities for type hierarchy.
 */
public final class TypeHierarchy {
    private TypeHierarchy() {
    }

    /**
     * Find all annotations on the whole type hierarchy.
     * Adds all annotations on the provided type, and all
     * {@link java.lang.annotation.Inherited} annotations on supertype(s) and/or interface(s).
     *
     * @param ctx  codegen context
     * @param type type info to process
     * @return all annotations on the type and in its hierarchy
     */
    public static List<Annotation> hierarchyAnnotations(CodegenContext ctx, TypeInfo type) {
        Map<TypeName, Annotation> annotations = new LinkedHashMap<>();

        // this type
        type.annotations().forEach(annot -> annotations.put(annot.typeName(), annot));
        // inherited from supertype
        type.superTypeInfo().ifPresent(it -> {
            it.inheritedAnnotations()
                    .forEach(annot -> annotations.putIfAbsent(annot.typeName(), annot));
        });
        // and from interfaces (in order of implementation)
        for (TypeInfo typeInfo : type.interfaceTypeInfo()) {
            typeInfo.annotations()
                    .stream()
                    .filter(annot -> typeInfo.hasMetaAnnotation(annot.typeName(), INHERITED))
                    .forEach(annot -> annotations.putIfAbsent(annot.typeName(), annot));
        }

        Set<TypeName> processedTypes = new HashSet<>(annotations.keySet());

        // now we have a full list of annotations that are explicitly written in sources, now collect meta-annotations
        // i.e. all annotations on the annotations we have that have @Inherited placed on them
        processMetaAnnotations(ctx, processedTypes, annotations);

        return List.copyOf(annotations.values());
    }

    /**
     * Find all annotations on the whole type hierarchy.
     * Adds all annotations on the provided element, and all
     * {@link java.lang.annotation.Inherited} annotations on the same element from supertype(s),
     * and/or interfaces, and/or annotations.
     * <p>
     * Based on element type:
     * <ul>
     *     <li>Constructor: only uses annotations from the current element</li>
     *     <li>Constructor parameter: ditto</li>
     *     <li>Method: uses annotations from the current element, and from the overridden method/interface method</li>
     *     <li>Method parameter: use
     *     {@link #hierarchyAnnotations(CodegenContext,
     *                                  io.helidon.common.types.TypeInfo,
     *                                  io.helidon.common.types.TypedElementInfo,
     *                                  io.helidon.common.types.TypedElementInfo,
     *                                  int)} instead</li>
     *     <li>Field: only uses annotations from the current element</li>
     * </ul>
     * If the same annotation is on multiple levels (i.e. method, super type method, and interface), it will always be used
     * ONLY from the "closest" type - order is: this element, super type element, interface element.
     *
     * @param ctx     codegen context
     * @param type    type info owning the executable
     * @param element executable (method or constructor) element info
     * @return all annotations on the type and in its hierarchy
     */
    public static List<Annotation> hierarchyAnnotations(CodegenContext ctx, TypeInfo type, TypedElementInfo element) {
        if (element.kind() != ElementKind.METHOD) {
            return element.annotations();
        }

        // find the same method on supertype/interfaces
        List<TypedElementInfo> prototypes = new ArrayList<>();
        Set<TypeName> processedTypes = new HashSet<>();
        String packageName = type.typeName().packageName();
        // extends
        type.superTypeInfo().ifPresent(it -> collectInheritedMethods(
                processedTypes,
                prototypes,
                it,
                element,
                packageName));
        // implements
        type.interfaceTypeInfo().forEach(it -> collectInheritedMethods(
                processedTypes,
                prototypes,
                it,
                element,
                packageName));

        // we have collected all methods in the hierarchy, let's collect their annotations
        Map<TypeName, Annotation> annotations = new LinkedHashMap<>();
        // this type
        element.annotations().forEach(annot -> annotations.put(annot.typeName(), annot));
        // inherited from supertype(s) and interface(s)
        for (TypedElementInfo prototype : prototypes) {
            prototype.annotations().forEach(annot -> annotations.putIfAbsent(annot.typeName(), annot));
        }

        // now we have a full list of annotations that are explicitly written in sources, now collect meta-annotations
        // i.e. all annotations on the annotations we have that have @Inherited placed on them
        processMetaAnnotations(ctx, processedTypes, annotations);

        return List.copyOf(annotations.values());
    }

    /**
     * Annotations of a parameter, taken from the full inheritance hierarchy (super type(s), interface(s).
     *
     * @param ctx            codegen context to obtain {@link io.helidon.common.types.TypeInfo} of types
     * @param type           type info of the processed type
     * @param executable     owner of the parameter (constructor or method)
     * @param parameter      parameter info itself
     * @param parameterIndex index of the parameter within the method (as names may be wrong at runtime)
     * @return list of annotations on this parameter on this type, super type(s), and interface methods it implements
     */
    public static List<Annotation> hierarchyAnnotations(CodegenContext ctx,
                                                        TypeInfo type,
                                                        TypedElementInfo executable,
                                                        TypedElementInfo parameter,
                                                        int parameterIndex) {
        if (parameter.kind() != ElementKind.PARAMETER) {
            throw new CodegenException("This method only supports processing of parameter, yet kind is: " + parameter.kind());
        }
        if (!(executable.kind() == ElementKind.CONSTRUCTOR || executable.kind() == ElementKind.METHOD)) {
            throw new CodegenException("This method only supports processing of parameters of methods or constructors, yet "
                                               + "executable kind is: " + executable.kind());
        }
        if (executable.kind() == ElementKind.CONSTRUCTOR) {
            // constructor parameters are not inherited
            return parameter.annotations();
        }

        // find the same method on supertype/interfaces
        List<TypedElementInfo> prototypes = new ArrayList<>();
        Set<TypeName> processedTypes = new HashSet<>();
        String packageName = type.typeName().packageName();
        // extends
        type.superTypeInfo().ifPresent(it -> collectInheritedMethods(
                processedTypes,
                prototypes,
                it,
                executable,
                packageName));
        // implements
        type.interfaceTypeInfo().forEach(it -> collectInheritedMethods(
                processedTypes,
                prototypes,
                it,
                executable,
                packageName));

        // we have collected all methods in the hierarchy, let's collect their annotations
        Map<TypeName, Annotation> annotations = new LinkedHashMap<>();
        // this type
        parameter.annotations().forEach(annot -> annotations.put(annot.typeName(), annot));
        // inherited from supertype(s) and interface(s)
        for (TypedElementInfo prototype : prototypes) {
            prototype.parameterArguments()
                    .get(parameterIndex)
                    .annotations()
                    .forEach(annot -> annotations.putIfAbsent(annot.typeName(), annot));
        }

        // now we have a full list of annotations that are explicitly written in sources, now collect meta-annotations
        // i.e. all annotations on the annotations we have that have @Inherited placed on them
        processMetaAnnotations(ctx, processedTypes, annotations);

        return List.copyOf(annotations.values());

    }

    /**
     * Annotations on the {@code typeInfo}, it's methods, and method parameters.
     *
     * @param ctx context
     * @param typeInfo type info to check
     * @return a set of all annotation types on any of the elements, including inherited annotations
     */
    public static Set<TypeName> nestedAnnotations(CodegenContext ctx, TypeInfo typeInfo) {
        Set<TypeName> result = new HashSet<>();

        // on type
        typeInfo.annotations()
                .stream()
                .map(Annotation::typeName)
                .forEach(result::add);
        typeInfo.inheritedAnnotations()
                .stream()
                .map(Annotation::typeName)
                .forEach(result::add);

        // on fields, methods etc.
        typeInfo.elementInfo()
                .stream()
                .map(TypedElementInfo::annotations)
                .flatMap(List::stream)
                .map(Annotation::typeName)
                .forEach(result::add);

        typeInfo.elementInfo()
                .stream()
                .map(TypedElementInfo::inheritedAnnotations)
                .flatMap(List::stream)
                .map(Annotation::typeName)
                .forEach(result::add);

        // on parameters
        typeInfo.elementInfo()
                .stream()
                .map(TypedElementInfo::parameterArguments)
                .flatMap(List::stream)
                .map(TypedElementInfo::annotations)
                .flatMap(List::stream)
                .map(Annotation::typeName)
                .forEach(result::add);
        typeInfo.elementInfo()
                .stream()
                .map(TypedElementInfo::parameterArguments)
                .flatMap(List::stream)
                .map(TypedElementInfo::inheritedAnnotations)
                .flatMap(List::stream)
                .map(Annotation::typeName)
                .forEach(result::add);

        return result;
        /*
        Set<TypeName> result = new HashSet<>();

        // on type
        hierarchyAnnotations(ctx, typeInfo)
                .stream()
                .map(Annotation::typeName)
                .forEach(result::add);

        // on fields, methods etc.
        typeInfo.elementInfo()
                .stream()
                .map(it -> hierarchyAnnotations(ctx, typeInfo, it))
                .flatMap(List::stream)
                .map(Annotation::typeName)
                .forEach(result::add);

        // on parameters
        typeInfo.elementInfo()
                .stream()
                .forEach(it -> {
                    int index = 0;
                    for (var param : it.parameterArguments()) {
                        hierarchyAnnotations(ctx, typeInfo, it, param, index++)
                                .stream()
                                .map(Annotation::typeName)
                                .forEach(result::add);
                    }
                });

        return result;
         */
    }

    private static void processMetaAnnotations(CodegenContext ctx,
                                               Set<TypeName> processedTypes,
                                               Map<TypeName, Annotation> annotations) {

        List<Annotation> newAnnotations = new ArrayList<>();

        for (Annotation value : annotations.values()) {
            Optional<TypeInfo> typeInfo = ctx.typeInfo(value.typeName());
            if (typeInfo.isPresent()) {
                // we can handle only annotations on classpath, all others are just ignored
                TypeInfo annotationInfo = typeInfo.get();

                annotationInfo
                        .annotations()
                        .forEach(metaAnnotation -> {
                            collectMetaAnnotations(ctx, processedTypes, newAnnotations, metaAnnotation);
                        });
            }
        }

        newAnnotations.forEach(it -> annotations.putIfAbsent(it.typeName(), it));
    }

    private static void collectMetaAnnotations(CodegenContext ctx,
                                               Set<TypeName> processedTypes,
                                               List<Annotation> metaAnnotations,
                                               Annotation annotation) {
        if (!processedTypes.add(annotation.typeName())) {
            // this annotation was already processed
            return;
        }
        Optional<TypeInfo> typeInfo = ctx.typeInfo(annotation.typeName());
        if (typeInfo.isEmpty()) {
            return;
        }
        TypeInfo annotationInfo = typeInfo.get();
        if (annotationInfo.hasAnnotation(INHERITED)) {
            metaAnnotations.add(annotation);
        }
        // and check all annotations of this annotation
        annotationInfo.annotations()
                .forEach(metaAnnotation -> collectMetaAnnotations(ctx, processedTypes, metaAnnotations, metaAnnotation));
    }

    private static void collectInheritedMethods(Set<TypeName> processed,
                                                List<TypedElementInfo> collected,
                                                TypeInfo type,
                                                TypedElementInfo method,
                                                String currentPackage) {
        if (!processed.add(type.typeName())) {
            // already handled this type
            return;
        }

        inherited(
                type,
                method,
                method.parameterArguments()
                        .stream()
                        .map(TypedElementInfo::typeName)
                        .collect(Collectors.toUnmodifiableList()),
                currentPackage)
                .ifPresent(collected::add);

        type.superTypeInfo().ifPresent(it -> collectInheritedMethods(processed, collected, it, method, currentPackage));
        for (TypeInfo typeInfo : type.interfaceTypeInfo()) {
            collectInheritedMethods(processed, collected, typeInfo, method, currentPackage);
        }
    }

    /**
     * Check if the provided type declares a method that is overridden.
     *
     * @param type           first immediate supertype we will be checking
     * @param method         method we are investigating
     * @param arguments      method signature
     * @param currentPackage package of the current type declaring the method
     * @return overridden method element
     */
    private static Optional<TypedElementInfo> inherited(TypeInfo type,
                                                        TypedElementInfo method,
                                                        List<TypeName> arguments,
                                                        String currentPackage) {

        String methodName = method.elementName();
        // we look only for exact match (including types)
        Optional<TypedElementInfo> found = type.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(ElementInfoPredicates.elementName(methodName))
                .filter(ElementInfoPredicates.hasParams(arguments))
                .findFirst();

        if (found.isPresent()) {
            TypedElementInfo superMethod = found.get();

            // method has same signature, but is package local and is in a different package
            boolean realOverride = superMethod.accessModifier() != AccessModifier.PACKAGE_PRIVATE
                    || currentPackage.equals(type.typeName().packageName());

            if (realOverride) {
                // this is a valid method that the type overrides
                return Optional.of(superMethod);
            }
        }

        return Optional.empty();
    }

}
