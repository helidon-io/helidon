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

package io.helidon.common.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import javax.tools.JavaFileObject;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import static io.helidon.common.types.TypeName.createFromGenericDeclaration;
import static java.util.function.Predicate.not;

/**
 * Factory to analyze processed types and to provide {@link io.helidon.common.types.TypeInfo} for them.
 */
public final class TypeInfoFactory {
    private static final AllPredicate ALL_PREDICATE = new AllPredicate();

    private TypeInfoFactory() {
    }

    /**
     * Create type information from a type element, reading all child elements.
     *
     * @param env         annotation processor processing environment
     * @param typeElement type element of the type we want to analyze
     * @return type info for the type element
     * @throws java.lang.IllegalArgumentException when the element cannot be resolved into type info (such as if you ask for
     *                                            a primitive type)
     */
    public static Optional<TypeInfo> create(ProcessingEnvironment env,
                                            TypeElement typeElement) {
        return create(env, typeElement, ALL_PREDICATE);
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
     * annotation processing. If the passed in element is not a {@link io.helidon.common.types.TypeValues#KIND_FIELD},
     * {@link io.helidon.common.types.TypeValues#KIND_METHOD},
     * {@link io.helidon.common.types.TypeValues#KIND_CONSTRUCTOR}, or {@link io.helidon.common.types.TypeValues#KIND_PARAMETER}
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

        if (v instanceof ExecutableElement) {
            ExecutableElement ee = (ExecutableElement) v;
            typeMirror = Objects.requireNonNull(ee.getReturnType());
            params = ee.getParameters().stream()
                    .map(it -> createTypedElementInfoFromElement(env, it, elements).orElseThrow())
                    .collect(Collectors.toList());
            AnnotationValue annotationValue = ee.getDefaultValue();
            defaultValue = (annotationValue == null) ? null
                    : annotationValue.accept(new ToStringAnnotationValueVisitor()
                                                     .mapBooleanToNull(true)
                                                     .mapVoidToNull(true)
                                                     .mapBlankArrayToNull(true)
                                                     .mapEmptyStringToNull(true)
                                                     .mapToSourceDeclaration(true), null);
        } else if (v instanceof VariableElement) {
            VariableElement ve = (VariableElement) v;
            typeMirror = Objects.requireNonNull(ve.asType());
        }

        if (typeMirror != null) {
            if (type == null) {
                type = TypeFactory.createTypeName(typeMirror).orElse(createFromGenericDeclaration(typeMirror.toString()));
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
                .elementTypeKind(v.getKind().name())
                .annotations(createAnnotations(v, elements))
                .elementTypeAnnotations(elementTypeAnnotations)
                .modifiers(modifierNames)
                .parameterArguments(params);
        TypeFactory.createTypeName(v.getEnclosingElement()).ifPresent(builder::enclosingType);
        Optional.ofNullable(defaultValue).ifPresent(builder::defaultValue);

        return Optional.of(builder.build());
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

    private static Optional<TypeInfo> create(ProcessingEnvironment processingEnv,
                                             TypeElement typeElement,
                                             Predicate<TypedElementInfo> elementPredicate,
                                             TypeName typeName) {
        Objects.requireNonNull(processingEnv);
        Objects.requireNonNull(typeElement);
        Objects.requireNonNull(elementPredicate);
        Objects.requireNonNull(typeName);

        if (typeName.resolved().equals(Object.class.getName())) {
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
                    List.copyOf(createAnnotations(elementUtils.getTypeElement(genericTypeName.resolved()), elementUtils));
            Map<TypeName, List<Annotation>> referencedAnnotations =
                    new LinkedHashMap<>(toMetaAnnotations(annotations, processingEnv));
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
                        merge(referencedAnnotations, toMetaAnnotations(it.annotations(), processingEnv));
                        it.parameterArguments().forEach(arg -> merge(referencedAnnotations,
                                                                     toMetaAnnotations(arg.annotations(), processingEnv)));
                    });
            TypeInfo.Builder builder = TypeInfo.builder()
                    .typeName(typeName)
                    .typeKind(String.valueOf(typeElement.getKind()))
                    .annotations(annotations)
                    .referencedTypeNamesToAnnotations(referencedAnnotations)
                    .modifiers(toModifierNames(typeElement.getModifiers()))
                    .elementInfo(elementsWeCareAbout)
                    .otherElementInfo(otherElements);

            // add all of the element's and parameters to the references annotation set
            elementsWeCareAbout.forEach(it -> {
                if (!isBuiltInJavaType(it.typeName()) && !it.typeName().generic()) {
                    allInterestingTypeNames.add(it.typeName().genericTypeName());
                }
                List<Annotation> annos = it.annotations();
                Map<TypeName, List<Annotation>> resolved = toMetaAnnotations(annos, processingEnv);
                resolved.forEach(builder::putReferencedTypeNamesToAnnotation);
                resolved.keySet().stream()
                        .map(TypeName::genericTypeName)
                        .filter(t -> !isBuiltInJavaType(t))
                        .filter(t -> !t.generic())
                        .forEach(allInterestingTypeNames::add);
                it.parameterArguments().stream()
                        .map(TypedElementInfo::typeName)
                        .map(TypeName::genericTypeName)
                        .filter(t -> !isBuiltInJavaType(t))
                        .filter(t -> !t.generic())
                        .forEach(allInterestingTypeNames::add);
            });

            TypeMirror superTypeMirror = typeElement.getSuperclass();
            TypeName fqSuperTypeName = TypeFactory.createTypeName(superTypeMirror).orElse(null);
            if (fqSuperTypeName != null && !fqSuperTypeName.name().equals(Object.class.getName())) {
                TypeElement superTypeElement = elementUtils.getTypeElement(fqSuperTypeName.name());
                if (superTypeElement != null) {
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
                TypeName fqInterfaceTypeName = TypeFactory.createTypeName(interfaceTypeMirror).orElse(null);
                if (fqInterfaceTypeName != null) {
                    TypeName genericInterfaceTypeName = fqInterfaceTypeName.genericTypeName();
                    allInterestingTypeNames.add(genericInterfaceTypeName);
                    fqInterfaceTypeName.typeArguments().stream()
                            .map(TypeName::genericTypeName)
                            .filter(it -> !isBuiltInJavaType(it))
                            .filter(it -> !it.generic())
                            .forEach(allInterestingTypeNames::add);
                    TypeElement interfaceTypeElement = elementUtils.getTypeElement(fqInterfaceTypeName.genericTypeName()
                                                                                           .resolved());
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

            return Optional.of(builder.build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process: " + typeElement, e);
        }
    }

    private static void merge(Map<TypeName, List<Annotation>> result,
                              Map<TypeName, List<Annotation>> metaAnnotations) {
        metaAnnotations.forEach((key1, value) -> result.computeIfAbsent(key1, (key) -> new ArrayList<>()).addAll(value));
    }

    private static List<Annotation> createAnnotations(Element element, Elements elements) {
        return element.getAnnotationMirrors()
                .stream()
                .map(it -> AnnotationFactory.createAnnotation(it, elements))
                .collect(Collectors.toList());
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
    private static Map<TypeName, List<Annotation>> toMetaAnnotations(Collection<Annotation> annotations,
                                                                     ProcessingEnvironment processingEnv) {
        if (annotations.isEmpty()) {
            return Map.of();
        }

        Elements elements = processingEnv.getElementUtils();
        Map<TypeName, List<Annotation>> result = new LinkedHashMap<>();
        annotations.stream()
                .filter(it -> !result.containsKey(it.typeName()))
                .forEach(it -> {
                    TypeElement typeElement = elements.getTypeElement(it.typeName().name());
                    if (typeElement != null) {
                        result.put(it.typeName(), new ArrayList<>(createAnnotations(typeElement, elements)));
                    }
                });
        return result;
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
