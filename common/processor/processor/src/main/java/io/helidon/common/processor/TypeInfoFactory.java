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

package io.helidon.common.processor;

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
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

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
 * @deprecated use {@code helidon-codegen} instead.
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public final class TypeInfoFactory {
    private static final AllPredicate ALL_PREDICATE = new AllPredicate();

    private static final Set<TypeName> IGNORED_ANNOTATIONS = Set.of(TypeName.create(SuppressWarnings.class),
                                                                    TypeName.create(Override.class));

    // we expect that annotations themselves are not code generated, and can be cached
    private static final Map<TypeName, List<Annotation>> META_ANNOTATION_CACHE = new ConcurrentHashMap<>();

    private TypeInfoFactory() {
    }

    /**
     * Create type information for a type name, reading all child elements.
     *
     * @param processingEnv annotation processor processing environment
     * @param typeName      type name to find
     * @return type info for the type element
     * @throws java.lang.IllegalArgumentException when the element cannot be resolved into type info (such as if you ask for
     *                                            a primitive type)
     */
    public static Optional<TypeInfo> create(ProcessingEnvironment processingEnv,
                                            TypeName typeName) {
        return create(processingEnv, typeName, ALL_PREDICATE);
    }

    /**
     * Create type information for a type name.
     *
     * @param processingEnv    annotation processor processing environment
     * @param typeName         type name to find
     * @param elementPredicate predicate for child elements
     * @return type info for the type element, or empty if it cannot be resolved
     */
    public static Optional<TypeInfo> create(ProcessingEnvironment processingEnv,
                                            TypeName typeName,
                                            Predicate<TypedElementInfo> elementPredicate) throws IllegalArgumentException {

        TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(typeName.fqName());
        if (typeElement == null) {
            return Optional.empty();
        }
        return TypeFactory.createTypeName(typeElement.asType())
                .flatMap(it -> create(processingEnv, typeElement, elementPredicate, it));
    }

    /**
     * Create type information from a type element, reading all child elements.
     *
     * @param processingEnv         annotation processor processing environment
     * @param typeElement type element of the type we want to analyze
     * @return type info for the type element
     * @throws java.lang.IllegalArgumentException when the element cannot be resolved into type info (such as if you ask for
     *                                            a primitive type)
     */
    public static Optional<TypeInfo> create(ProcessingEnvironment processingEnv,
                                            TypeElement typeElement) {
        return create(processingEnv, typeElement, ALL_PREDICATE);
    }

    /**
     * Create type information from a type element.
     *
     * @param processingEnv    annotation processor processing environment
     * @param typeElement      type element of the type we want to analyze
     * @param elementPredicate predicate for child elements
     * @return type info for the type element, or empty if it cannot be resolved
     */
    public static Optional<TypeInfo> create(ProcessingEnvironment processingEnv,
                                            TypeElement typeElement,
                                            Predicate<TypedElementInfo> elementPredicate) throws IllegalArgumentException {

        return TypeFactory.createTypeName(typeElement.asType())
                .flatMap(it -> create(processingEnv, typeElement, elementPredicate, it));
    }

    /**
     * Creates an instance of a {@link io.helidon.common.types.TypedElementInfo} given its type and variable element from
     * annotation processing. If the passed in element is not a {@link io.helidon.common.types.ElementKind#FIELD},
     * {@link io.helidon.common.types.ElementKind#METHOD},
     * {@link io.helidon.common.types.ElementKind#CONSTRUCTOR}, or {@link io.helidon.common.types.ElementKind#FIELD}
     * then this method may return empty.
     *
     * @param env      annotation processing environment
     * @param v        the element (from annotation processing)
     * @param elements the elements
     * @return the created instance
     */
    public static Optional<TypedElementInfo> createTypedElementInfoFromElement(ProcessingEnvironment env,
                                                                               Element v,
                                                                               Elements elements) {
        TypeName type = TypeFactory.createTypeName(v).orElse(null);
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
                    .map(it -> createTypedElementInfoFromElement(env, it, elements).orElseThrow())
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
                    .filter(it -> isCheckedException(env, it))
                    .map(TypeFactory::createTypeName)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toSet());
        } else if (v instanceof VariableElement ve) {
            typeMirror = Objects.requireNonNull(ve.asType());
        }

        if (typeMirror != null) {
            if (type == null) {
                Element element = env.getTypeUtils().asElement(typeMirror);
                if (element instanceof TypeElement typeElement) {
                    type = TypeFactory.createTypeName(typeElement, typeMirror)
                            .orElse(createFromGenericDeclaration(typeMirror.toString()));
                } else {
                    type = TypeFactory.createTypeName(typeMirror)
                            .orElse(createFromGenericDeclaration(typeMirror.toString()));
                }
            }
            if (typeMirror instanceof DeclaredType) {
                List<? extends TypeMirror> args = ((DeclaredType) typeMirror).getTypeArguments();
                componentTypeNames = args.stream()
                        .map(TypeFactory::createTypeName)
                        .filter(Optional::isPresent)
                        .map(Optional::orElseThrow)
                        .collect(Collectors.toList());
                elementTypeAnnotations =
                        createAnnotations(((DeclaredType) typeMirror).asElement(), elements);
            }
        }
        String javadoc = env.getElementUtils().getDocComment(v);
        javadoc = javadoc == null || javadoc.isBlank() ? "" : javadoc;

        TypedElementInfo.Builder builder = TypedElementInfo.builder()
                .description(javadoc)
                .typeName(type)
                .componentTypes(componentTypeNames)
                .elementName(v.getSimpleName().toString())
                .kind(kind(v.getKind()))
                .annotations(createAnnotations(v, elements))
                .elementTypeAnnotations(elementTypeAnnotations)
                .elementModifiers(modifiers(modifierNames))
                .accessModifier(accessModifier(modifierNames))
                .throwsChecked(thrownChecked)
                .parameterArguments(params);
        TypeFactory.createTypeName(v.getEnclosingElement()).ifPresent(builder::enclosingType);
        Optional.ofNullable(defaultValue).ifPresent(builder::defaultValue);

        return Optional.of(builder.build());
    }

    private static boolean isCheckedException(ProcessingEnvironment env, TypeMirror it) {
        Elements elements = env.getElementUtils();
        Types types = env.getTypeUtils();
        TypeMirror exception = elements.getTypeElement(Exception.class.getName()).asType();
        TypeMirror runtimeException = elements.getTypeElement(RuntimeException.class.getName()).asType();

        return types.isAssignable(it, exception) && !types.isAssignable(it, runtimeException);
    }

    /**
     * Check if the provided type is either a primitive type, or is from the {@code java} package namespace.
     *
     * @param type type to check
     * @return {@code true} if the type is a primitive type, or its package starts with {@code java.}
     */
    public static boolean isBuiltInJavaType(TypeName type) {
        return type.primitive() || type.packageName().startsWith("java.");
    }

    static ElementKind kind(javax.lang.model.element.ElementKind kind) {
        try {
            return ElementKind.valueOf(String.valueOf(kind).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // not supported, consider other type
            return ElementKind.OTHER;
        }
    }

    private static Optional<TypeInfo> create(ProcessingEnvironment processingEnv,
                                             TypeElement typeElement,
                                             Predicate<TypedElementInfo> elementPredicate,
                                             TypeName typeName) {
        Objects.requireNonNull(processingEnv);
        Objects.requireNonNull(typeElement);
        Objects.requireNonNull(elementPredicate);
        Objects.requireNonNull(typeName);

        if (typeName.resolvedName().equals(Object.class.getName())) {
            // Object is not to be analyzed
            return Optional.empty();
        }
        TypeName genericTypeName = typeName.genericTypeName();
        Set<TypeName> allInterestingTypeNames = new LinkedHashSet<>();
        allInterestingTypeNames.add(genericTypeName);
        typeName.typeArguments()
                .stream()
                .map(TypeName::genericTypeName)
                .filter(not(TypeInfoFactory::isBuiltInJavaType))
                .filter(not(TypeName::generic))
                .forEach(allInterestingTypeNames::add);

        Elements elementUtils = processingEnv.getElementUtils();
        try {
            List<Annotation> annotations =
                    List.copyOf(createAnnotations(elementUtils.getTypeElement(genericTypeName.resolvedName()),
                                                  elementUtils));
            Set<TypeName> annotationsOnTypeOrElements = new HashSet<>();
            annotations.stream()
                    .map(Annotation::typeName)
                    .forEach(annotationsOnTypeOrElements::add);

            List<TypedElementInfo> elementsWeCareAbout = new ArrayList<>();
            List<TypedElementInfo> otherElements = new ArrayList<>();
            typeElement.getEnclosedElements()
                    .stream()
                    .map(it -> createTypedElementInfoFromElement(processingEnv, it, elementUtils))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(it -> {
                        if (elementPredicate.test(it)) {
                            elementsWeCareAbout.add(it);
                        } else {
                            otherElements.add(it);
                        }
                        annotationsOnTypeOrElements.addAll(it.annotations()
                                                                   .stream()
                                                                   .map(Annotation::typeName)
                                                                   .collect(Collectors.toSet()));
                        it.parameterArguments()
                                .forEach(arg -> annotationsOnTypeOrElements.addAll(arg.annotations()
                                                                                           .stream()
                                                                                           .map(Annotation::typeName)
                                                                                           .collect(Collectors.toSet())));
                    });

            Set<String> modifiers = toModifierNames(typeElement.getModifiers());
            TypeInfo.Builder builder = TypeInfo.builder()
                    .typeName(typeName)
                    .kind(kind(typeElement.getKind()))
                    .annotations(annotations)
                    .elementModifiers(modifiers(modifiers))
                    .accessModifier(accessModifier(modifiers))
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

            TypeMirror superTypeMirror = typeElement.getSuperclass();
            TypeElement superTypeElement = (TypeElement) processingEnv.getTypeUtils().asElement(superTypeMirror);

            TypeName fqSuperTypeName;
            if (superTypeElement != null) {
                fqSuperTypeName = TypeFactory.createTypeName(superTypeElement, superTypeMirror)
                        .orElse(null);

                if (fqSuperTypeName != null && !TypeNames.OBJECT.equals(fqSuperTypeName)) {

                    TypeName genericSuperTypeName = fqSuperTypeName.genericTypeName();
                    Optional<TypeInfo> superTypeInfo =
                            create(processingEnv, superTypeElement, elementPredicate, fqSuperTypeName);
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
                TypeElement element = (TypeElement) processingEnv.getTypeUtils().asElement(interfaceTypeMirror);
                TypeName fqInterfaceTypeName = TypeFactory.createTypeName(interfaceTypeMirror).orElse(null);
                List<? extends TypeParameterElement> typeParameters = element.getTypeParameters();

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
                                create(processingEnv, interfaceTypeElement, elementPredicate, fqInterfaceTypeName);
                        superTypeInfo.ifPresent(builder::addInterfaceTypeInfo);
                    }
                }
            });

            AtomicReference<String> moduleName = new AtomicReference<>();
            allInterestingTypeNames.forEach(it -> {
                TypeElement theTypeElement = elementUtils.getTypeElement(it.name());
                if (theTypeElement == null || !isTypeInThisModule(theTypeElement, moduleName, processingEnv)) {
                    if (hasValue(moduleName.get())) {
                        builder.putReferencedModuleName(it, moduleName.get());
                    }
                }
            });
            ModuleElement module = processingEnv.getElementUtils().getModuleOf(typeElement);
            if (module != null) {
                builder.module(module.toString());
            }

            builder.referencedTypeNamesToAnnotations(toMetaAnnotations(processingEnv, annotationsOnTypeOrElements));

            return Optional.of(builder.build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process: " + typeElement, e);
        }
    }

    private static Set<io.helidon.common.types.Modifier> modifiers(Set<String> stringModifiers) {
        Set<io.helidon.common.types.Modifier> result = new HashSet<>();

        for (String stringModifier : stringModifiers) {
            try {
                result.add(io.helidon.common.types.Modifier.valueOf(stringModifier.toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
                // we do not care about modifiers we do not understand - either access modifier, or something new
            }
        }

        return result;
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

    private static void merge(Map<TypeName, List<Annotation>> result,
                              Map<TypeName, List<Annotation>> metaAnnotations) {
        metaAnnotations.forEach((key1, value) -> result.computeIfAbsent(key1, (key) -> new ArrayList<>()).addAll(value));
    }

    private static List<Annotation> createAnnotations(Element element, Elements elements) {
        return element.getAnnotationMirrors()
                .stream()
                .map(it -> AnnotationFactory.createAnnotation(it, elements))
                .filter(TypeInfoFactory::filterAnnotations)
                .toList();
    }

    private static boolean filterAnnotations(Annotation annotation) {
        return !IGNORED_ANNOTATIONS.contains(annotation.typeName());
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
     * @param type          the type element to analyze
     * @param moduleName    the module name to populate if it is determinable
     * @param processingEnv the processing env
     * @return true if the type is definitely defined in this module, false otherwise
     */
    private static boolean isTypeInThisModule(TypeElement type,
                                              AtomicReference<String> moduleName,
                                              ProcessingEnvironment processingEnv) {
        moduleName.set(null);

        ModuleElement module = processingEnv.getElementUtils().getModuleOf(type);
        if (!module.isUnnamed()) {
            String name = module.getQualifiedName().toString();
            if (hasValue(name)) {
                moduleName.set(name);
            }
        }

        // if there is no module-info in use we need to try to find the type is in our source path and if
        // not found then just assume it is external
        try {
            Trees trees = Trees.instance(processingEnv);
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
    private static Map<TypeName, List<Annotation>> toMetaAnnotations(ProcessingEnvironment env,
                                                                     Set<TypeName> annotations) {
        if (annotations.isEmpty()) {
            return Map.of();
        }

        Map<TypeName, List<Annotation>> result = new HashMap<>();

        gatherMetaAnnotations(env, annotations, result);

        return result;
    }

    // gather a single level map of types to their meta annotation
    private static void gatherMetaAnnotations(ProcessingEnvironment env,
                                              Set<TypeName> annotationTypes,
                                              Map<TypeName, List<Annotation>> result) {
        if (annotationTypes.isEmpty()) {
            return;
        }

        Elements elements = env.getElementUtils();

        annotationTypes.stream()
                .filter(not(result::containsKey)) // already in the result, no need to add it
                .forEach(it -> {
                    List<Annotation> meta = META_ANNOTATION_CACHE.get(it);
                    boolean fromCache = true;
                    if (meta == null) {
                        fromCache = false;
                        TypeElement typeElement = elements.getTypeElement(it.name());
                        if (typeElement != null) {
                            List<Annotation> metaAnnotations = createAnnotations(typeElement, elements);
                            result.put(it, new ArrayList<>(metaAnnotations));
                            // now rinse and repeat for the referenced annotations
                            gatherMetaAnnotations(env,
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

    private static final class AllPredicate implements Predicate<TypedElementInfo> {
        @Override
        public boolean test(TypedElementInfo typedElementName) {
            return true;
        }
    }
}
