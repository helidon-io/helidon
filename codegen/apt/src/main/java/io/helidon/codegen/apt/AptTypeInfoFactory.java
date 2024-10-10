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

package io.helidon.codegen.apt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeInfoFactoryBase;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import static io.helidon.common.types.TypeName.createFromGenericDeclaration;
import static java.util.function.Predicate.not;

/**
 * Factory to analyze processed types and to provide {@link io.helidon.common.types.TypeInfo} for them.
 *
 * @deprecated this is an internal API, all usage should be done through {@code helidon-codegen} APIs,
 *         such as {@link io.helidon.codegen.CodegenContext#typeInfo(io.helidon.common.types.TypeName)};
 *         this type will be package local in the future
 */
@SuppressWarnings("removal")
@Deprecated(forRemoval = true)
public final class AptTypeInfoFactory extends TypeInfoFactoryBase {

    // we expect that annotations themselves are not code generated, and can be cached
    private static final Map<TypeName, List<Annotation>> META_ANNOTATION_CACHE = new ConcurrentHashMap<>();

    private AptTypeInfoFactory() {
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
    public static Optional<TypeInfo> create(AptContext ctx,
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
    public static Optional<TypeInfo> create(AptContext ctx,
                                            TypeName typeName,
                                            Predicate<TypedElementInfo> elementPredicate) throws IllegalArgumentException {

        TypeElement typeElement = ctx.aptEnv().getElementUtils().getTypeElement(typeName.fqName());
        if (typeElement == null) {
            return Optional.empty();
        }
        return AptTypeFactory.createTypeName(typeElement.asType())
                .flatMap(it -> create(ctx, typeElement, elementPredicate, it))
                .flatMap(it -> mapType(ctx, it));
    }

    /**
     * Create type information from a type element, reading all child elements.
     *
     * @param ctx         annotation processor processing context
     * @param typeElement type element of the type we want to analyze
     * @return type info for the type element
     * @throws IllegalArgumentException when the element cannot be resolved into type info (such as if you ask for
     *                                  a primitive type)
     * @deprecated this is an internal API, all usage should be done through {@code helidon-codegen} APIs,
     *         such as {@link io.helidon.codegen.CodegenContext#typeInfo(io.helidon.common.types.TypeName)}
     */
    @Deprecated(forRemoval = true)
    public static Optional<TypeInfo> create(AptContext ctx,
                                            TypeElement typeElement) {

        TypeName typeName = AptTypeFactory.createTypeName(typeElement.asType()).orElse(null);

        if (typeName == null) {
            return Optional.empty();
        }

        return create(ctx, typeName);
    }

    /**
     * Create type information from a type element.
     *
     * @param ctx              annotation processor processing context
     * @param typeElement      type element of the type we want to analyze
     * @param elementPredicate predicate for child elements
     * @return type info for the type element, or empty if it cannot be resolved
     */
    public static Optional<TypeInfo> create(AptContext ctx,
                                            TypeElement typeElement,
                                            Predicate<TypedElementInfo> elementPredicate) throws IllegalArgumentException {

        return AptTypeFactory.createTypeName(typeElement.asType())
                .flatMap(it -> create(ctx, typeElement, elementPredicate, it));
    }

    /**
     * Creates an instance of a {@link io.helidon.common.types.TypedElementInfo} given its type and variable element from
     * annotation processing. If the passed in element is not a {@link io.helidon.common.types.ElementKind#FIELD},
     * {@link io.helidon.common.types.ElementKind#METHOD},
     * {@link io.helidon.common.types.ElementKind#CONSTRUCTOR}, or {@link io.helidon.common.types.ElementKind#PARAMETER}
     * then this method may return empty.
     *
     * @param ctx           annotation processing context
     * @param processedType the type that is being processed, to avoid infinite loop when looking for inherited annotations
     * @param v             the element (from annotation processing)
     * @param elements      the elements
     * @return the created instance
     */
    public static Optional<TypedElementInfo> createTypedElementInfoFromElement(AptContext ctx,
                                                                               TypeName processedType,
                                                                               Element v,
                                                                               Elements elements) {
        TypeName type = AptTypeFactory.createTypeName(v).orElse(null);
        TypeMirror typeMirror = null;
        String defaultValue = null;
        List<TypedElementInfo> params = List.of();
        List<TypeName> componentTypeNames = List.of();
        List<Annotation> elementTypeAnnotations = List.of();

        Set<String> modifierNames = v.getModifiers()
                .stream()
                .map(Modifier::toString)
                .collect(Collectors.toSet());
        Set<TypeName> thrownChecked = Set.of();

        if (v instanceof ExecutableElement ee) {
            typeMirror = Objects.requireNonNull(ee.getReturnType());
            params = ee.getParameters()
                    .stream()
                    .map(it -> createTypedElementInfoFromElement(ctx, processedType, it, elements).orElseThrow(() -> {
                        return new CodegenException("Failed to create element info for parameter: " + it + ", either it uses "
                                                            + "invalid type, or it was removed by an element mapper. This would"
                                                            + " result in an invalid TypeInfo model.",
                                                    it);
                    }))
                    .toList();
            AnnotationValue annotationValue = ee.getDefaultValue();
            defaultValue = (annotationValue == null) ? null
                    : String.valueOf(annotationValue.accept(new ToAnnotationValueVisitor(elements)
                                                                    .mapBooleanToNull(true)
                                                                    .mapVoidToNull(true)
                                                                    .mapBlankArrayToNull(true)
                                                                    .mapEmptyStringToNull(true)
                                                                    .mapToSourceDeclaration(true), null));

            thrownChecked = ee.getThrownTypes()
                    .stream()
                    .filter(it -> isCheckedException(ctx, it))
                    .flatMap(it -> AptTypeFactory.createTypeName(it).stream())
                    .collect(Collectors.toSet());
        } else if (v instanceof VariableElement ve) {
            typeMirror = Objects.requireNonNull(ve.asType());
        }

        if (typeMirror != null) {
            if (type == null) {
                Element element = ctx.aptEnv().getTypeUtils().asElement(typeMirror);
                if (element instanceof TypeElement typeElement) {
                    type = AptTypeFactory.createTypeName(typeElement, typeMirror)
                            .orElse(createFromGenericDeclaration(typeMirror.toString()));
                } else {
                    type = AptTypeFactory.createTypeName(typeMirror)
                            .orElse(createFromGenericDeclaration(typeMirror.toString()));
                }
            }
            if (typeMirror instanceof DeclaredType) {
                List<? extends TypeMirror> args = ((DeclaredType) typeMirror).getTypeArguments();
                componentTypeNames = args.stream()
                        .map(AptTypeFactory::createTypeName)
                        .filter(Optional::isPresent)
                        .map(Optional::orElseThrow)
                        .collect(Collectors.toList());
                elementTypeAnnotations =
                        createAnnotations(ctx, ((DeclaredType) typeMirror).asElement(), elements);
            }
        }
        String javadoc = ctx.aptEnv().getElementUtils().getDocComment(v);
        javadoc = javadoc == null || javadoc.isBlank() ? "" : javadoc;

        List<Annotation> annotations = createAnnotations(ctx, v, elements);
        List<Annotation> inheritedAnnotations = createInheritedAnnotations(ctx, processedType.genericTypeName(), annotations);

        TypedElementInfo.Builder builder = TypedElementInfo.builder()
                .description(javadoc)
                .typeName(type)
                .componentTypes(componentTypeNames)
                .elementName(v.getSimpleName().toString())
                .kind(kind(v.getKind()))
                .annotations(annotations)
                .elementTypeAnnotations(elementTypeAnnotations)
                .inheritedAnnotations(inheritedAnnotations)
                .elementModifiers(modifiers(ctx, modifierNames))
                .accessModifier(accessModifier(modifierNames))
                .throwsChecked(thrownChecked)
                .parameterArguments(params)
                .originatingElement(v);
        AptTypeFactory.createTypeName(v.getEnclosingElement()).ifPresent(builder::enclosingType);
        Optional.ofNullable(defaultValue).ifPresent(builder::defaultValue);

        return mapElement(ctx, builder.build());
    }

    /**
     * Creates an instance of a {@link io.helidon.common.types.TypedElementInfo} given its type and variable element from
     * annotation processing. If the passed in element is not a {@link io.helidon.common.types.ElementKind#FIELD},
     * {@link io.helidon.common.types.ElementKind#METHOD},
     * {@link io.helidon.common.types.ElementKind#CONSTRUCTOR}, or {@link io.helidon.common.types.ElementKind#PARAMETER}
     * then this method may return empty.
     * <p>
     * This method does not include inherited annotations.
     *
     * @param ctx      annotation processing context
     * @param v        the element (from annotation processing)
     * @param elements the elements
     * @return the created instance
     * @deprecated use
     *         {@link #createTypedElementInfoFromElement(AptContext, io.helidon.common.types.TypeName,
     *         javax.lang.model.element.Element, javax.lang.model.util.Elements)}
     *         instead
     */
    @Deprecated(since = "4.0.10", forRemoval = true)
    public static Optional<TypedElementInfo> createTypedElementInfoFromElement(AptContext ctx,
                                                                               Element v,
                                                                               Elements elements) {
        TypeName type = AptTypeFactory.createTypeName(v).orElse(null);
        TypeMirror typeMirror = null;
        String defaultValue = null;
        List<TypedElementInfo> params = List.of();
        List<TypeName> componentTypeNames = List.of();
        List<Annotation> elementTypeAnnotations = List.of();
        Set<String> modifierNames = v.getModifiers()
                .stream()
                .map(Modifier::toString)
                .collect(Collectors.toSet());
        Set<TypeName> thrownChecked = Set.of();

        if (v instanceof ExecutableElement ee) {
            typeMirror = Objects.requireNonNull(ee.getReturnType());
            params = ee.getParameters().stream()
                    .map(it -> createTypedElementInfoFromElement(ctx, it, elements).orElseThrow(() -> {
                        return new CodegenException("Failed to create element info for parameter: " + it + ", either it uses "
                                                            + "invalid type, or it was removed by an element mapper. This would"
                                                            + " result in an invalid TypeInfo model.",
                                                    it);
                    }))
                    .toList();
            AnnotationValue annotationValue = ee.getDefaultValue();
            defaultValue = (annotationValue == null) ? null
                    : String.valueOf(annotationValue.accept(new ToAnnotationValueVisitor(elements)
                                                                    .mapBooleanToNull(true)
                                                                    .mapVoidToNull(true)
                                                                    .mapBlankArrayToNull(true)
                                                                    .mapEmptyStringToNull(true)
                                                                    .mapToSourceDeclaration(true), null));

            thrownChecked = ee.getThrownTypes()
                    .stream()
                    .filter(it -> isCheckedException(ctx, it))
                    .flatMap(it -> AptTypeFactory.createTypeName(it).stream())
                    .collect(Collectors.toSet());
        } else if (v instanceof VariableElement ve) {
            typeMirror = Objects.requireNonNull(ve.asType());
        }

        if (typeMirror != null) {
            if (type == null) {
                Element element = ctx.aptEnv().getTypeUtils().asElement(typeMirror);
                if (element instanceof TypeElement typeElement) {
                    type = AptTypeFactory.createTypeName(typeElement, typeMirror)
                            .orElse(createFromGenericDeclaration(typeMirror.toString()));
                } else {
                    type = AptTypeFactory.createTypeName(typeMirror)
                            .orElse(createFromGenericDeclaration(typeMirror.toString()));
                }
            }
            if (typeMirror instanceof DeclaredType) {
                List<? extends TypeMirror> args = ((DeclaredType) typeMirror).getTypeArguments();
                componentTypeNames = args.stream()
                        .map(AptTypeFactory::createTypeName)
                        .filter(Optional::isPresent)
                        .map(Optional::orElseThrow)
                        .collect(Collectors.toList());
                elementTypeAnnotations =
                        createAnnotations(ctx, ((DeclaredType) typeMirror).asElement(), elements);
            }
        }
        String javadoc = ctx.aptEnv().getElementUtils().getDocComment(v);
        javadoc = javadoc == null || javadoc.isBlank() ? "" : javadoc;

        TypedElementInfo.Builder builder = TypedElementInfo.builder()
                .description(javadoc)
                .typeName(type)
                .componentTypes(componentTypeNames)
                .elementName(v.getSimpleName().toString())
                .kind(kind(v.getKind()))
                .annotations(createAnnotations(ctx, v, elements))
                .elementTypeAnnotations(elementTypeAnnotations)
                .elementModifiers(modifiers(ctx, modifierNames))
                .accessModifier(accessModifier(modifierNames))
                .throwsChecked(thrownChecked)
                .parameterArguments(params)
                .originatingElement(v);
        AptTypeFactory.createTypeName(v.getEnclosingElement()).ifPresent(builder::enclosingType);
        Optional.ofNullable(defaultValue).ifPresent(builder::defaultValue);

        return mapElement(ctx, builder.build());
    }

    private static boolean isCheckedException(AptContext ctx, TypeMirror it) {
        ProcessingEnvironment aptEnv = ctx.aptEnv();
        Elements elements = aptEnv.getElementUtils();
        Types types = aptEnv.getTypeUtils();
        TypeMirror exception = elements.getTypeElement(Exception.class.getName()).asType();
        TypeMirror runtimeException = elements.getTypeElement(RuntimeException.class.getName()).asType();

        return types.isAssignable(it, exception) && !types.isAssignable(it, runtimeException);
    }

    private static ElementKind kind(javax.lang.model.element.ElementKind kind) {
        try {
            return ElementKind.valueOf(String.valueOf(kind).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // not supported, consider other type
            return ElementKind.OTHER;
        }
    }

    private static Optional<TypeInfo> create(AptContext ctx,
                                             TypeElement typeElement,
                                             Predicate<TypedElementInfo> elementPredicate,
                                             TypeName typeName) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(typeElement);
        Objects.requireNonNull(elementPredicate);
        Objects.requireNonNull(typeName);

        if (typeName.resolvedName().equals(Object.class.getName())) {
            // Object is not to be analyzed
            return Optional.empty();
        }

        if (elementPredicate == ElementInfoPredicates.ALL_PREDICATE) {
            // we can safely cache
            return ctx.cache(typeName, () -> createUncached(ctx, typeElement, elementPredicate, typeName));
        }

        return createUncached(ctx, typeElement, elementPredicate, typeName);
    }

    private static Optional<TypeInfo> createUncached(AptContext ctx,
                                                     TypeElement typeElement,
                                                     Predicate<TypedElementInfo> elementPredicate,
                                                     TypeName typeName) {
        TypeName genericTypeName = typeName.genericTypeName();
        Set<TypeName> allInterestingTypeNames = new LinkedHashSet<>();
        allInterestingTypeNames.add(genericTypeName);
        typeName.typeArguments()
                .stream()
                .map(TypeName::genericTypeName)
                .filter(not(AptTypeInfoFactory::isBuiltInJavaType))
                .filter(not(TypeName::generic))
                .forEach(allInterestingTypeNames::add);

        Elements elementUtils = ctx.aptEnv().getElementUtils();
        try {
            TypeElement foundType = elementUtils.getTypeElement(genericTypeName.resolvedName());
            if (foundType == null) {
                // this is probably forward referencing a generated type, ignore
                return Optional.empty();
            }
            TypeName declaredTypeName = declaredTypeName(ctx, genericTypeName);
            List<Annotation> annotations = createAnnotations(ctx,
                                                             foundType,
                                                             elementUtils);
            List<Annotation> inheritedAnnotations = createInheritedAnnotations(ctx, genericTypeName, annotations);

            Set<TypeName> annotationsOnTypeOrElements = new HashSet<>();
            annotations.stream()
                    .map(Annotation::typeName)
                    .forEach(annotationsOnTypeOrElements::add);

            List<TypedElementInfo> elementsWeCareAbout = new ArrayList<>();
            List<TypedElementInfo> otherElements = new ArrayList<>();
            typeElement.getEnclosedElements()
                    .stream()
                    .flatMap(it -> createTypedElementInfoFromElement(ctx, genericTypeName, it, elementUtils).stream())
                    .forEach(it -> collectEnclosedElements(elementPredicate,
                                                           elementsWeCareAbout,
                                                           otherElements,
                                                           annotationsOnTypeOrElements,
                                                           it));


            Set<String> modifiers = toModifierNames(typeElement.getModifiers());
            TypeInfo.Builder builder = TypeInfo.builder()
                    .originatingElement(typeElement)
                    .typeName(typeName)
                    .rawType(genericTypeName)
                    .declaredType(declaredTypeName)
                    .kind(kind(typeElement.getKind()))
                    .annotations(annotations)
                    .inheritedAnnotations(inheritedAnnotations)
                    .elementModifiers(modifiers(ctx, modifiers))
                    .accessModifier(accessModifier(modifiers))
                    .elementInfo(elementsWeCareAbout)
                    .otherElementInfo(otherElements);

            String javadoc = elementUtils.getDocComment(typeElement);
            if (javadoc != null) {
                builder.description(javadoc);
            }

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

            TypeMirror superTypeMirror = typeElement.getSuperclass();
            TypeElement superTypeElement = (TypeElement) ctx.aptEnv().getTypeUtils().asElement(superTypeMirror);

            TypeName fqSuperTypeName;
            if (superTypeElement != null) {
                fqSuperTypeName = AptTypeFactory.createTypeName(superTypeElement, superTypeMirror)
                        .orElse(null);

                if (fqSuperTypeName != null && !TypeNames.OBJECT.equals(fqSuperTypeName)) {

                    TypeName genericSuperTypeName = fqSuperTypeName.genericTypeName();
                    Optional<TypeInfo> superTypeInfo =
                            create(ctx, superTypeElement, elementPredicate, fqSuperTypeName);
                    superTypeInfo.ifPresent(builder::superTypeInfo);
                    allInterestingTypeNames.add(genericSuperTypeName);
                    fqSuperTypeName.typeArguments().stream()
                            .map(TypeName::genericTypeName)
                            .filter(it -> !isBuiltInJavaType(it))
                            .filter(it -> !it.generic())
                            .forEach(allInterestingTypeNames::add);
                }
            }

            typeElement.getInterfaces().forEach(interfaceTypeMirror -> {
                TypeName fqInterfaceTypeName = AptTypeFactory.createTypeName(interfaceTypeMirror).orElse(null);

                if (fqInterfaceTypeName != null) {
                    TypeName genericInterfaceTypeName = fqInterfaceTypeName.genericTypeName();
                    allInterestingTypeNames.add(genericInterfaceTypeName);
                    fqInterfaceTypeName.typeArguments().stream()
                            .map(TypeName::genericTypeName)
                            .filter(it -> !isBuiltInJavaType(it))
                            .filter(it -> !it.generic())
                            .forEach(allInterestingTypeNames::add);
                    TypeElement interfaceTypeElement = elementUtils.getTypeElement(fqInterfaceTypeName.genericTypeName()
                                                                                           .resolvedName());
                    if (interfaceTypeElement != null) {
                        Optional<TypeInfo> superTypeInfo =
                                create(ctx, interfaceTypeElement, elementPredicate, fqInterfaceTypeName);
                        superTypeInfo.ifPresent(builder::addInterfaceTypeInfo);
                    }
                }
            });

            AtomicReference<String> moduleName = new AtomicReference<>();
            allInterestingTypeNames.forEach(it -> {
                TypeElement theTypeElement = elementUtils.getTypeElement(it.name());
                if (theTypeElement == null || !isTypeInThisModule(ctx, theTypeElement, moduleName)) {
                    if (hasValue(moduleName.get())) {
                        builder.putReferencedModuleName(it, moduleName.get());
                    }
                }
            });
            ModuleElement module = ctx.aptEnv().getElementUtils().getModuleOf(typeElement);
            if (module != null) {
                builder.module(module.toString());
            }

            builder.referencedTypeNamesToAnnotations(toMetaAnnotations(ctx, annotationsOnTypeOrElements));

            return Optional.of(builder.build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process: " + typeElement, e);
        }
    }

    private static TypeName declaredTypeName(AptContext ctx, TypeName typeName) {
        TypeElement typeElement = ctx.aptEnv().getElementUtils().getTypeElement(typeName.fqName());
        // we know this type exists, we do not have to check for null
        return AptTypeFactory.createTypeName(typeElement.asType()).orElseThrow();
    }

    private static void collectEnclosedElements(Predicate<TypedElementInfo> elementPredicate,
                                                List<TypedElementInfo> elementsWeCareAbout,
                                                List<TypedElementInfo> otherElements,
                                                Set<TypeName> annotationsOnTypeOrElements,
                                                TypedElementInfo enclosedElement) {
        if (elementPredicate.test(enclosedElement)) {
            elementsWeCareAbout.add(enclosedElement);
        } else {
            otherElements.add(enclosedElement);
        }
        annotationsOnTypeOrElements.addAll(enclosedElement.annotations()
                                                   .stream()
                                                   .map(Annotation::typeName)
                                                   .collect(Collectors.toSet()));
        enclosedElement.parameterArguments()
                .forEach(arg -> annotationsOnTypeOrElements.addAll(arg.annotations()
                                                                           .stream()
                                                                           .map(Annotation::typeName)
                                                                           .collect(Collectors.toSet())));
    }

    private static AccessModifier accessModifier(Set<String> stringModifiers) {
        for (String stringModifier : stringModifiers) {
            try {
                return AccessModifier.valueOf(stringModifier.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                // we do not care about modifiers we do not understand - either non-access modifier, or something new
            }
        }
        return AccessModifier.PACKAGE_PRIVATE;
    }

    private static List<Annotation> createAnnotations(AptContext ctx, Element element, Elements elements) {
        ElementKind elementKind = kind(element.getKind());
        return element.getAnnotationMirrors()
                .stream()
                .map(it -> AptAnnotationFactory.createAnnotation(it, elements))
                .flatMap(it -> mapAnnotation(ctx, it, elementKind).stream())
                .filter(TypeInfoFactoryBase::annotationFilter)
                .toList();
    }

    private static List<Annotation> createInheritedAnnotations(AptContext ctx,
                                                               TypeName processedType,
                                                               List<Annotation> elementAnnotations) {
        List<Annotation> result = new ArrayList<>();
        Set<TypeName> processedTypes = new HashSet<>();

        // for each annotation on this type, find its type info, and collect all meta annotations marked as @Inherited
        for (Annotation elementAnnotation : elementAnnotations) {
            if (processedType.equals(elementAnnotation.typeName())) {
                // self reference
                continue;
            }
            addInherited(ctx, result, processedTypes, elementAnnotation, false);
        }
        return result;
    }

    private static void addInherited(AptContext ctx,
                                     List<Annotation> result,
                                     Set<TypeName> processedTypes,
                                     Annotation annotation,
                                     boolean shouldAdd) {
        TypeName annotationType = annotation.typeName();
        if (!processedTypes.add(annotationType)) {
            return;
        }

        if (!annotationFilter(annotation)) {
            return;
        }

        if (annotationType.equals(TypeNames.INHERITED)) {
            return;
        }

        Optional<TypeInfo> found = create(ctx, annotationType, it -> false);

        if (found.isEmpty()) {
            ctx.logger().log(System.Logger.Level.DEBUG, "Annotation " + annotationType
                    + " not available, cannot obtain inherited annotations");
            return;
        }

        TypeInfo annoTypeInfo = found.get();

        if (annoTypeInfo.hasAnnotation(TypeNames.INHERITED)) {
            // first level annotations should not be added as inherited
            if (shouldAdd) {
                // add this annotation
                result.add(annotation);
            }

            // check my meta annotations
            for (Annotation metaAnnotation : annoTypeInfo.annotations()) {
                // if self annotated, ignore
                if (annotationType.equals(metaAnnotation.typeName())) {
                    continue;
                }
                addInherited(ctx, result, processedTypes, metaAnnotation, true);
            }
        }
    }

    /**
     * Converts the provided modifiers to the corresponding set of modifier names.
     *
     * @param modifiers the modifiers
     * @return the modifier names
     */
    private static Set<String> toModifierNames(Set<Modifier> modifiers) {
        return modifiers.stream()
                .map(Modifier::name)
                .collect(Collectors.toSet());
    }

    /**
     * Determines if the given type element is defined in the module being processed. If so then the return value is set to
     * {@code true} and the moduleName is cleared out. If not then the return value is set to {@code false} and the
     * {@code moduleName} is set to the module name if it has a qualified module name, and not from an internal java module system
     * type. Note that this method will only return {@code true} when the module info paths are being used in the project.
     *
     * @param ctx        processing context
     * @param type       the type element to analyze
     * @param moduleName the module name to populate if it is determinable
     * @return true if the type is definitely defined in this module, false otherwise
     */
    private static boolean isTypeInThisModule(AptContext ctx,
                                              TypeElement type,
                                              AtomicReference<String> moduleName) {
        moduleName.set(null);

        ModuleElement module = ctx.aptEnv().getElementUtils().getModuleOf(type);
        if (!module.isUnnamed()) {
            String name = module.getQualifiedName().toString();
            if (hasValue(name)) {
                moduleName.set(name);
            }
        }

        // if there is no module-info in use we need to try to find the type is in our source path and if
        // not found then just assume it is external
        try {
            Trees trees = Trees.instance(ctx.aptEnv());
            TreePath path = trees.getPath(type);
            if (path == null) {
                return false;
            }
            JavaFileObject sourceFile = path.getCompilationUnit().getSourceFile();
            return (sourceFile != null);
        } catch (Throwable t) {
            // assumed external
            return false;
        }
    }

    /**
     * Returns the map of meta annotations for the provided collection of annotation values.
     *
     * @param annotations the annotations
     * @return the meta annotations for the provided set of annotations
     */
    private static Map<TypeName, List<Annotation>> toMetaAnnotations(AptContext ctx,
                                                                     Set<TypeName> annotations) {
        if (annotations.isEmpty()) {
            return Map.of();
        }

        Map<TypeName, List<Annotation>> result = new HashMap<>();

        gatherMetaAnnotations(ctx, annotations, result);

        return result;
    }

    // gather a single level map of types to their meta annotation
    private static void gatherMetaAnnotations(AptContext ctx,
                                              Set<TypeName> annotationTypes,
                                              Map<TypeName, List<Annotation>> result) {
        if (annotationTypes.isEmpty()) {
            return;
        }

        Elements elements = ctx.aptEnv().getElementUtils();

        annotationTypes.stream()
                .filter(not(result::containsKey)) // already in the result, no need to add it
                .forEach(it -> {
                    List<Annotation> meta = META_ANNOTATION_CACHE.get(it);
                    boolean fromCache = true;
                    if (meta == null) {
                        fromCache = false;
                        TypeElement typeElement = elements.getTypeElement(it.fqName());
                        if (typeElement != null) {
                            List<Annotation> metaAnnotations = createAnnotations(ctx, typeElement, elements);
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

    /**
     * Simple check to see the passed String value is non-null and non-blank.
     *
     * @param val the value to check
     * @return true if non-null and non-blank
     */
    private static boolean hasValue(String val) {
        return (val != null && !val.isBlank());
    }
}
