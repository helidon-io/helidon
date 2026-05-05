/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.Api;
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
     * Annotations on the {@code typeInfo}, its methods, method parameters, and the type-use positions nested in their
     * {@link TypeName}s.
     * <p>
     * Type-use positions include type arguments, wildcard bounds, and array component types of the type itself,
     * element return or field types, and parameter types.
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
        typeNameAnnotations(typeInfo.typeName())
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

        typeInfo.elementInfo()
                .stream()
                .map(TypedElementInfo::typeName)
                .map(TypeHierarchy::typeNameAnnotations)
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

        typeInfo.elementInfo()
                .stream()
                .map(TypedElementInfo::parameterArguments)
                .flatMap(List::stream)
                .map(TypedElementInfo::typeName)
                .map(TypeHierarchy::typeNameAnnotations)
                .flatMap(List::stream)
                .map(Annotation::typeName)
                .forEach(result::add);

        return result;
    }

    /**
     * Annotation instances nested inside a type name.
     * This includes annotations declared directly on the provided type name and annotations declared on nested
     * type arguments, wildcard bounds, and array component types.
     *
     * @param typeName type name to scan
     * @return nested annotation instances
     */
    @Api.Internal
    public static List<Annotation> typeNameAnnotations(TypeName typeName) {
        List<Annotation> result = new ArrayList<>();
        result.addAll(typeName.annotations());
        result.addAll(typeName.inheritedAnnotations());
        for (TypeName typeArgument : typeName.typeArguments()) {
            result.addAll(typeNameAnnotations(typeArgument));
        }
        for (TypeName lowerBound : typeName.lowerBounds()) {
            result.addAll(typeNameAnnotations(lowerBound));
        }
        for (TypeName upperBound : typeName.upperBounds()) {
            result.addAll(typeNameAnnotations(upperBound));
        }
        typeName.componentType()
                .map(TypeHierarchy::typeNameAnnotations)
                .ifPresent(result::addAll);
        return result;
    }

    /**
     * Merge type-use annotations from a source type name into a target type name.
     * If the type names do not have the same structure, the target type name is returned unchanged.
     * <p>
     * Matching type names merge direct and inherited annotations on the root type, type arguments, wildcard bounds,
     * and array component types. When both type names contain the same annotation type at a matching position, the
     * target annotation is preserved. Formal type parameter names do not block merging.
     *
     * @param typeName       target type name
     * @param sourceTypeName source type name
     * @return type name with annotations merged from the source
     */
    @Api.Internal
    public static TypeName mergeTypeNameAnnotations(TypeName typeName, TypeName sourceTypeName) {
        if (!sameStructure(typeName, sourceTypeName)) {
            return typeName;
        }

        List<Annotation> annotations = new ArrayList<>(typeName.annotations());
        sourceTypeName.annotations().forEach(it -> addAnnotationIfAbsent(annotations, it));
        List<Annotation> inheritedAnnotations = new ArrayList<>(typeName.inheritedAnnotations());
        sourceTypeName.inheritedAnnotations().forEach(it -> addAnnotationIfAbsent(inheritedAnnotations, it));

        TypeName.Builder builder = TypeName.builder(typeName)
                .annotations(annotations)
                .inheritedAnnotations(inheritedAnnotations)
                .typeArguments(mergeTypeNameLists(typeName.typeArguments(), sourceTypeName.typeArguments()))
                .lowerBounds(mergeTypeNameLists(typeName.lowerBounds(), sourceTypeName.lowerBounds()))
                .upperBounds(mergeTypeNameLists(typeName.upperBounds(), sourceTypeName.upperBounds()));

        if (typeName.componentType().isPresent() && sourceTypeName.componentType().isPresent()) {
            builder.componentType(mergeTypeNameAnnotations(typeName.componentType().get(),
                                                          sourceTypeName.componentType().get()));
        }

        return builder.build();
    }

    private static List<TypeName> mergeTypeNameLists(List<TypeName> typeNames, List<TypeName> sourceTypeNames) {
        if (sourceTypeNames.isEmpty()) {
            return typeNames;
        }
        if (typeNames.isEmpty()) {
            return typeNames;
        }
        if (typeNames.size() != sourceTypeNames.size()) {
            return typeNames;
        }

        List<TypeName> merged = new ArrayList<>(typeNames.size());
        for (int i = 0; i < typeNames.size(); i++) {
            merged.add(mergeTypeNameAnnotations(typeNames.get(i), sourceTypeNames.get(i)));
        }
        return merged;
    }

    private static boolean sameStructure(TypeName typeName, TypeName sourceTypeName) {
        if (typeName.primitive() != sourceTypeName.primitive()
                || typeName.array() != sourceTypeName.array()
                || typeName.generic() != sourceTypeName.generic()
                || typeName.wildcard() != sourceTypeName.wildcard()
                || typeName.typeArguments().size() != sourceTypeName.typeArguments().size()
                || typeName.lowerBounds().size() != sourceTypeName.lowerBounds().size()
                || typeName.upperBounds().size() != sourceTypeName.upperBounds().size()
                || typeName.componentType().isPresent() != sourceTypeName.componentType().isPresent()) {
            return false;
        }
        return typeName.packageName().equals(sourceTypeName.packageName())
                && typeName.className().equals(sourceTypeName.className())
                && typeName.enclosingNames().equals(sourceTypeName.enclosingNames())
                && sameStructure(typeName.typeArguments(), sourceTypeName.typeArguments())
                && sameStructure(typeName.lowerBounds(), sourceTypeName.lowerBounds())
                && sameStructure(typeName.upperBounds(), sourceTypeName.upperBounds())
                && sameStructure(typeName.componentType(), sourceTypeName.componentType());
    }

    private static boolean sameStructure(List<TypeName> typeNames, List<TypeName> sourceTypeNames) {
        for (int i = 0; i < typeNames.size(); i++) {
            if (!sameStructure(typeNames.get(i), sourceTypeNames.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameStructure(Optional<TypeName> typeName, Optional<TypeName> sourceTypeName) {
        if (typeName.isEmpty()) {
            return true;
        }
        return sameStructure(typeName.get(), sourceTypeName.get());
    }

    private static void addAnnotationIfAbsent(List<Annotation> annotations, Annotation annotation) {
        if (annotations.stream().noneMatch(it -> it.typeName().equals(annotation.typeName()))) {
            annotations.add(annotation);
        }
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
