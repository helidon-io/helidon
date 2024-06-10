/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen.scan;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeInfoFactoryBase;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassMemberInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodParameterInfo;

import static java.util.function.Predicate.not;

/**
 * Factory to analyze processed types and to provide {@link io.helidon.common.types.TypeInfo} for them.
 */
public final class ScanTypeInfoFactory extends TypeInfoFactoryBase {
    // we expect that annotations themselves are not code generated, and can be cached
    private static final Map<TypeName, List<Annotation>> META_ANNOTATION_CACHE = new ConcurrentHashMap<>();

    private ScanTypeInfoFactory() {
    }

    /**
     * Create type information for a type name, reading all child elements.
     *
     * @param ctx      annotation processor processing context
     * @param typeName type name to find
     * @return type info for the type element
     * @throws IllegalArgumentException when the element cannot be resolved into type info (such as if you ask for
     *                                  a primitive type)
     */
    public static Optional<TypeInfo> create(ScanContext ctx,
                                            TypeName typeName) {
        return create(ctx, typeName, ElementInfoPredicates.ALL_PREDICATE);
    }

    /**
     * Create type information for a type name.
     *
     * @param ctx              annotation processor processing environment
     * @param typeName         type name to find
     * @param elementPredicate predicate for child elements
     * @return type info for the type element, or empty if it cannot be resolved
     */
    public static Optional<TypeInfo> create(ScanContext ctx,
                                            TypeName typeName,
                                            Predicate<TypedElementInfo> elementPredicate) throws IllegalArgumentException {

        ClassInfo classInfo = ctx.scanResult().getClassInfo(typeName.fqName());
        if (classInfo == null) {
            // this class is not part of the scan
            return Optional.empty();
        }

        return create(ctx, typeName, elementPredicate, classInfo)
                .flatMap(it -> mapType(ctx, it));
    }

    /**
     * Create type information from a type element, reading all child elements.
     *
     * @param ctx       annotation processor processing context
     * @param classInfo type element of the type we want to analyze
     * @return type info for the type element
     * @throws IllegalArgumentException when the element cannot be resolved into type info (such as if you ask for
     *                                  a primitive type)
     */
    public static Optional<TypeInfo> create(ScanContext ctx,
                                            ClassInfo classInfo) {
        return create(ctx, classInfo, ElementInfoPredicates.ALL_PREDICATE);
    }

    /**
     * Create type information from a type element.
     *
     * @param ctx              annotation processor processing context
     * @param classInfo        type element of the type we want to analyze
     * @param elementPredicate predicate for child elements
     * @return type info for the type element, or empty if it cannot be resolved
     */
    public static Optional<TypeInfo> create(ScanContext ctx,
                                            ClassInfo classInfo,
                                            Predicate<TypedElementInfo> elementPredicate) throws IllegalArgumentException {

        TypeName typeName = ScanTypeFactory.create(classInfo);

        return create(ctx, typeName, elementPredicate, classInfo);
    }

    private static Optional<TypeInfo> create(ScanContext ctx,
                                             TypeName typeName,
                                             Predicate<TypedElementInfo> elementPredicate,
                                             ClassInfo classInfo) {

        if (typeName.fqName().equals(Object.class.getName())) {
            // Object or object array is not to be analyzed
            return Optional.empty();
        }
        TypeName genericTypeName = typeName.genericTypeName();
        Set<TypeName> allInterestingTypeNames = new LinkedHashSet<>();
        allInterestingTypeNames.add(genericTypeName);
        typeName.typeArguments()
                .stream()
                .map(TypeName::genericTypeName)
                .filter(not(ScanTypeInfoFactory::isBuiltInJavaType))
                .filter(not(TypeName::generic))
                .forEach(allInterestingTypeNames::add);

        try {
            List<Annotation> annotations = createAnnotations(ctx, classInfo.getAnnotationInfo(), kind(classInfo));
            Set<TypeName> annotationsOnTypeOrElements = new HashSet<>();
            annotations.stream()
                    .map(Annotation::typeName)
                    .forEach(annotationsOnTypeOrElements::add);

            List<TypedElementInfo> elementsWeCareAbout = new ArrayList<>();
            List<TypedElementInfo> otherElements = new ArrayList<>();

            classInfo.getDeclaredFieldInfo()
                    .forEach(it -> processField(ctx,
                                                elementPredicate,
                                                elementsWeCareAbout,
                                                otherElements,
                                                annotationsOnTypeOrElements,
                                                it));
            classInfo.getDeclaredConstructorInfo()
                    .forEach(it -> processMethod(ctx,
                                                 elementPredicate,
                                                 elementsWeCareAbout,
                                                 otherElements,
                                                 annotationsOnTypeOrElements,
                                                 it,
                                                 true));
            classInfo.getDeclaredMethodInfo()
                    .forEach(it -> processMethod(ctx,
                                                 elementPredicate,
                                                 elementsWeCareAbout,
                                                 otherElements,
                                                 annotationsOnTypeOrElements,
                                                 it,
                                                 false));

            classInfo.getInnerClasses()
                    .forEach(it -> processInnerClass(ctx,
                                                     elementPredicate,
                                                     elementsWeCareAbout,
                                                     otherElements,
                                                     annotationsOnTypeOrElements,
                                                     it));

            Set<Modifier> modifiers = toModifiers(classInfo);
            TypeInfo.Builder builder = TypeInfo.builder()
                    .originatingElement(classInfo)
                    .typeName(typeName)
                    .kind(kind(classInfo))
                    .annotations(annotations)
                    .elementModifiers(modifiers)
                    .accessModifier(toAccessModifier(classInfo))
                    .elementInfo(elementsWeCareAbout)
                    .otherElementInfo(otherElements);

            // add all of the element's and parameters to the references annotation set
            elementsWeCareAbout.forEach(it -> {
                if (!isBuiltInJavaType(it.typeName()) && !it.typeName().generic()) {
                    allInterestingTypeNames.add(it.typeName().genericTypeName());
                }
                it.parameterArguments().stream()
                        .map(TypedElementInfo::typeName)
                        .map(TypeName::genericTypeName)
                        .filter(t -> !isBuiltInJavaType(t))
                        .filter(t -> !t.generic())
                        .forEach(allInterestingTypeNames::add);
            });

            ClassInfo superclass = classInfo.getSuperclass();

            TypeName fqSuperTypeName;
            if (superclass != null) {
                fqSuperTypeName = ScanTypeFactory.create(superclass);

                if (fqSuperTypeName != null && !TypeNames.OBJECT.equals(fqSuperTypeName)) {

                    TypeName genericSuperTypeName = fqSuperTypeName.genericTypeName();
                    Optional<TypeInfo> superTypeInfo =
                            create(ctx, fqSuperTypeName, elementPredicate, superclass);
                    superTypeInfo.ifPresent(builder::superTypeInfo);
                    allInterestingTypeNames.add(genericSuperTypeName);
                    fqSuperTypeName.typeArguments().stream()
                            .map(TypeName::genericTypeName)
                            .filter(it -> !isBuiltInJavaType(it))
                            .filter(it -> !it.generic())
                            .forEach(allInterestingTypeNames::add);
                }
            }

            classInfo.getInterfaces().forEach(ifaceClassInfo -> {
                TypeName fqInterfaceTypeName = ScanTypeFactory.create(ifaceClassInfo);

                TypeName genericInterfaceTypeName = fqInterfaceTypeName.genericTypeName();
                allInterestingTypeNames.add(genericInterfaceTypeName);
                fqInterfaceTypeName.typeArguments().stream()
                        .map(TypeName::genericTypeName)
                        .filter(it -> !isBuiltInJavaType(it))
                        .filter(it -> !it.generic())
                        .forEach(allInterestingTypeNames::add);

                create(ctx, fqInterfaceTypeName, elementPredicate, ifaceClassInfo)
                        .ifPresent(builder::addInterfaceTypeInfo);
            });

            var moduleInfo = classInfo.getModuleInfo();
            String moduleName;
            if (moduleInfo == null) {
                moduleName = null;
            } else {
                moduleName = moduleInfo.getName();
                builder.module(moduleInfo.getName());
            }

            allInterestingTypeNames.forEach(it -> {
                ClassInfo referencedType = ctx.scanResult().getClassInfo(it.fqName());
                if (referencedType != null
                        && referencedType.getModuleInfo() != null) {
                    if (moduleName == null || !referencedType.getModuleInfo().getName().equals(moduleName)) {
                        builder.putReferencedModuleName(it, referencedType.getModuleInfo().getName());
                    }
                }
            });

            builder.referencedTypeNamesToAnnotations(toMetaAnnotations(ctx, annotationsOnTypeOrElements));

            return Optional.of(builder.build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process: " + classInfo, e);
        }
    }

    private static void processMethod(ScanContext ctx,
                                      Predicate<TypedElementInfo> elementPredicate,
                                      List<TypedElementInfo> elementsWeCareAbout,
                                      List<TypedElementInfo> otherElements,
                                      Set<TypeName> annotationsOnTypeOrElements,
                                      MethodInfo methodInfo,
                                      boolean isConstructor) {

        ElementKind kind = isConstructor ? ElementKind.CONSTRUCTOR : ElementKind.METHOD;

        TypedElementInfo.Builder builder = TypedElementInfo.builder()
                .typeName(ScanTypeFactory.create(methodInfo.getTypeSignatureOrTypeDescriptor()))
                .elementModifiers(toModifiers(methodInfo))
                .accessModifier(toAccessModifier(methodInfo))
                .elementName(methodInfo.getName())
                .kind(kind)
                .annotations(createAnnotations(ctx, methodInfo.getAnnotationInfo(), kind))
                .originatingElement(methodInfo);

        int index = 0;
        for (MethodParameterInfo methodParameterInfo : methodInfo.getParameterInfo()) {
            String paramName = methodParameterInfo.getName();
            if (paramName == null) {
                paramName = "param_" + index;
                index++;
            }
            processMethodParameter(ctx,
                                   annotationsOnTypeOrElements,
                                   methodParameterInfo,
                                   builder,
                                   paramName);
        }

        Set<TypeName> checkedExceptions = methodInfo.getThrownExceptions()
                .stream()
                .filter(ScanTypeInfoFactory::isCheckedException)
                .map(ScanTypeFactory::create)
                .collect(Collectors.toSet());

        builder.addThrowsChecked(checkedExceptions);

        processElement(ctx,
                       builder.build(),
                       elementPredicate,
                       elementsWeCareAbout,
                       otherElements,
                       annotationsOnTypeOrElements);
    }

    private static void processMethodParameter(ScanContext ctx,
                                               Set<TypeName> annotationsOnTypeOrElements,
                                               MethodParameterInfo methodParameterInfo,
                                               TypedElementInfo.Builder methodBuilder,
                                               String paramName) {

        TypedElementInfo paramInfo = TypedElementInfo.builder()
                .typeName(ScanTypeFactory.create(methodParameterInfo.getTypeSignatureOrTypeDescriptor()))
                .elementName(paramName)
                .kind(ElementKind.PARAMETER)
                .annotations(createAnnotations(ctx, methodParameterInfo.getAnnotationInfo(), ElementKind.PARAMETER))
                .build();
        paramInfo = processElement(ctx,
                                   paramInfo,
                                   ElementInfoPredicates.ALL_PREDICATE,
                                   new ArrayList<>(),
                                   new ArrayList<>(),
                                   annotationsOnTypeOrElements)
                .orElseThrow(() -> new CodegenException("Failed to process a parameter element, as a mapper removed it. "
                                                                + "Mappers must not remove parameters, as this would result "
                                                                + "in a broken type info model",
                                                        methodParameterInfo));

        methodBuilder.addParameterArgument(paramInfo);
    }

    private static void processInnerClass(ScanContext ctx,
                                          Predicate<TypedElementInfo> elementPredicate,
                                          List<TypedElementInfo> elementsWeCareAbout,
                                          List<TypedElementInfo> otherElements,
                                          Set<TypeName> annotationsOnTypeOrElements,
                                          ClassInfo classInfo) {

        ElementKind kind = kind(classInfo);

        TypedElementInfo elementInfo = TypedElementInfo.builder()
                .typeName(ScanTypeFactory.create(classInfo))
                .elementModifiers(toModifiers(classInfo))
                .accessModifier(toAccessModifier(classInfo))
                .elementName(classInfo.getName())
                .kind(kind)
                .annotations(createAnnotations(ctx, classInfo.getAnnotationInfo(), kind))
                .originatingElement(classInfo)
                .build();

        processElement(ctx,
                       elementInfo,
                       elementPredicate,
                       elementsWeCareAbout,
                       otherElements,
                       annotationsOnTypeOrElements);
    }

    private static void processField(ScanContext ctx,
                                     Predicate<TypedElementInfo> elementPredicate,
                                     List<TypedElementInfo> elementsWeCareAbout,
                                     List<TypedElementInfo> otherElements,
                                     Set<TypeName> annotationsOnTypeOrElements,
                                     FieldInfo fieldInfo) {

        TypedElementInfo elementInfo = TypedElementInfo.builder()
                .typeName(ScanTypeFactory.create(fieldInfo.getTypeSignatureOrTypeDescriptor()))
                .elementModifiers(toModifiers(fieldInfo))
                .accessModifier(toAccessModifier(fieldInfo))
                .elementName(fieldInfo.getName())
                .kind(ElementKind.FIELD)
                .annotations(createAnnotations(ctx, fieldInfo.getAnnotationInfo(), ElementKind.FIELD))
                .originatingElement(fieldInfo)
                .build();

        processElement(ctx,
                       elementInfo,
                       elementPredicate,
                       elementsWeCareAbout,
                       otherElements,
                       annotationsOnTypeOrElements);
    }

    private static Optional<TypedElementInfo> processElement(ScanContext ctx, TypedElementInfo element,
                                                   Predicate<TypedElementInfo> elementPredicate,
                                                   List<TypedElementInfo> elementsWeCareAbout,
                                                   List<TypedElementInfo> otherElements,
                                                   Set<TypeName> annotationsOnTypeOrElements) {
        Optional<TypedElementInfo> mapped = mapElement(ctx, element);
        if (mapped.isEmpty()) {
            return mapped;
        }

        TypedElementInfo elementInfo = mapped.get();
        if (elementPredicate.test(elementInfo)) {
            elementsWeCareAbout.add(elementInfo);
        } else {
            otherElements.add(elementInfo);
        }
        annotationsOnTypeOrElements.addAll(elementInfo.annotations()
                                                   .stream()
                                                   .map(Annotation::typeName)
                                                   .toList());
        return Optional.of(elementInfo);
    }

    private static Set<Modifier> toModifiers(ClassInfo classInfo) {
        Set<Modifier> result = EnumSet.noneOf(Modifier.class);

        if (classInfo.isFinal()) {
            result.add(Modifier.FINAL);
        }
        if (classInfo.isStatic()) {
            result.add(Modifier.STATIC);
        }
        if (classInfo.isAbstract()) {
            result.add(Modifier.ABSTRACT);
        }

        return result;
    }

    private static Set<Modifier> toModifiers(ClassMemberInfo memberInfo) {
        Set<Modifier> result = EnumSet.noneOf(Modifier.class);

        if (memberInfo.isFinal()) {
            result.add(Modifier.FINAL);
        }
        if (memberInfo.isStatic()) {
            result.add(Modifier.STATIC);
        }

        if (memberInfo instanceof MethodInfo mi) {
            if (mi.isDefault()) {
                result.add(Modifier.DEFAULT);
            }
            if (mi.isAbstract()) {
                result.add(Modifier.ABSTRACT);
            }
        }

        return result;
    }

    private static AccessModifier toAccessModifier(ClassInfo classInfo) {
        if (classInfo.isPrivate()) {
            return AccessModifier.PRIVATE;
        }
        if (classInfo.isProtected()) {
            return AccessModifier.PROTECTED;
        }
        if (classInfo.isPublic()) {
            return AccessModifier.PUBLIC;
        }
        return AccessModifier.PACKAGE_PRIVATE;
    }

    private static AccessModifier toAccessModifier(ClassMemberInfo memberInfo) {
        if (memberInfo.isPrivate()) {
            return AccessModifier.PRIVATE;
        }
        if (memberInfo.isProtected()) {
            return AccessModifier.PROTECTED;
        }
        if (memberInfo.isPublic()) {
            return AccessModifier.PUBLIC;
        }
        return AccessModifier.PACKAGE_PRIVATE;
    }

    private static boolean isCheckedException(ClassInfo exception) {
        return exception.extendsSuperclass(Exception.class) && !exception.extendsSuperclass(RuntimeException.class);
    }

    private static ElementKind kind(ClassInfo info) {
        if (info.isInterface()) {
            return ElementKind.INTERFACE;
        }
        if (info.isEnum()) {
            return ElementKind.ENUM;
        }
        if (info.isRecord()) {
            return ElementKind.RECORD;
        }
        if (info.isAnnotation()) {
            return ElementKind.ANNOTATION_TYPE;
        }

        if (info.isStandardClass()) {
            return ElementKind.CLASS;
        }

        return ElementKind.OTHER;
    }

    private static List<Annotation> createAnnotations(ScanContext ctx, List<AnnotationInfo> annotations, ElementKind kind) {
        return annotations
                .stream()
                .map(it -> ScanAnnotationFactory.createAnnotation(ctx, it))
                .flatMap(it -> mapAnnotation(ctx, it, kind).stream())
                .filter(TypeInfoFactoryBase::annotationFilter)
                .toList();
    }

    /**
     * Returns the map of meta annotations for the provided collection of annotation values.
     *
     * @param annotations the annotations
     * @return the meta annotations for the provided set of annotations
     */
    private static Map<TypeName, List<Annotation>> toMetaAnnotations(ScanContext ctx,
                                                                     Set<TypeName> annotations) {
        if (annotations.isEmpty()) {
            return Map.of();
        }

        Map<TypeName, List<Annotation>> result = new HashMap<>();

        gatherMetaAnnotations(ctx, annotations, result);

        return result;
    }

    // gather a single level map of types to their meta annotation
    private static void gatherMetaAnnotations(ScanContext ctx,
                                              Set<TypeName> annotationTypes,
                                              Map<TypeName, List<Annotation>> result) {
        if (annotationTypes.isEmpty()) {
            return;
        }

        annotationTypes.stream()
                .filter(not(result::containsKey)) // already in the result, no need to add it
                .forEach(it -> {
                    List<Annotation> meta = META_ANNOTATION_CACHE.get(it);
                    boolean fromCache = true;
                    if (meta == null) {
                        fromCache = false;
                        ClassInfo classInfo = ctx.scanResult().getClassInfo(it.name());
                        if (classInfo != null) {
                            List<Annotation> metaAnnotations = createAnnotations(ctx,
                                                                                 classInfo.getAnnotationInfo(),
                                                                                 ElementKind.ANNOTATION_TYPE);
                            result.put(it, new ArrayList<>(metaAnnotations));
                            // now rinse and repeat for the referenced annotations
                            gatherMetaAnnotations(ctx,
                                                  metaAnnotations.stream()
                                                          .map(Annotation::typeName)
                                                          .collect(Collectors.toSet()),
                                                  result);
                            meta = metaAnnotations;
                        } else {
                            meta = List.of();
                        }
                    }
                    if (!fromCache) {
                        // we cannot use computeIfAbsent, as that would do a recursive update if nested more than once
                        META_ANNOTATION_CACHE.putIfAbsent(it, meta);
                    }
                    if (!meta.isEmpty()) {
                        result.put(it, meta);
                    }
                });
    }
}
