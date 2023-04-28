/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.processor.tools;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import io.helidon.builder.processor.spi.TypeInfoCreatorProvider;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.AnnotationAndValueDefault;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeInfoDefault;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNameDefault;
import io.helidon.common.types.TypedElementName;
import io.helidon.common.types.TypedElementNameDefault;

// this is really ok!
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import static io.helidon.builder.processor.tools.BeanUtils.isBuiltInJavaType;
import static io.helidon.common.types.TypeNameDefault.create;
import static io.helidon.common.types.TypeNameDefault.createFromGenericDeclaration;
import static io.helidon.common.types.TypeNameDefault.createFromTypeName;

/**
 * The default implementation for {@link io.helidon.builder.processor.spi.TypeInfoCreatorProvider}. This also contains an abundance of
 * other useful methods used for annotation processing.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 2)   // allow all other creators to take precedence over us...
public class BuilderTypeTools implements TypeInfoCreatorProvider {
    /**
     * Default constructor. Service loaded.
     *
     * @deprecated needed for service loader
     */
    @Deprecated
    public BuilderTypeTools() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<TypeInfo> createBuilderTypeInfo(TypeName annotationTypeName,
                                                    TypeName typeName,
                                                    TypeElement element,
                                                    ProcessingEnvironment processingEnv,
                                                    boolean wantDefaultMethods) {
        Objects.requireNonNull(annotationTypeName);
        if (typeName.name().equals(Annotation.class.getName())) {
            return Optional.empty();
        }

        List<ExecutableElement> problems = element.getEnclosedElements().stream()
                .filter(it -> it.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(it -> canAccept(it, wantDefaultMethods))
                .filter(it -> !it.getParameters().isEmpty() || it.getReturnType().getKind() == TypeKind.VOID)
                .toList();
        if (!problems.isEmpty()) {
            String msg = "Only simple getters with no arguments are supported: " + element + ": " + problems;
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            throw new IllegalStateException(msg);
        }

        Collection<TypedElementName> elementInfo = toElementInfo(element, processingEnv, true, wantDefaultMethods);
        Collection<TypedElementName> otherElementInfo = toElementInfo(element, processingEnv, false, wantDefaultMethods);
        Set<String> modifierNames = toModifierNames(element.getModifiers()).stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        return Optional.of(TypeInfoDefault.builder()
                                   .typeName(typeName)
                                   .typeKind(String.valueOf(element.getKind()))
                                   .annotations(
                                           createAnnotationAndValueListFromElement(element, processingEnv.getElementUtils()))
                                   .elementInfo(elementInfo)
                                   .otherElementInfo(otherElementInfo)
                                   .referencedTypeNamesToAnnotations(
                                           toReferencedTypeNamesAndAnnotations(
                                                   processingEnv, typeName, elementInfo, otherElementInfo))
                                   .modifierNames(modifierNames)
                                   .update(it -> toBuilderTypeInfo(annotationTypeName, element, processingEnv, wantDefaultMethods)
                                           .ifPresent(it::superTypeInfo))
                                   .build());
    }

    @Override
    public Optional<TypeInfo> createTypeInfo(TypeElement element,
                                             TypeMirror mirror,
                                             ProcessingEnvironment processingEnv,
                                             Predicate<TypedElementName> isOneWeCareAbout) {
        return toTypeInfo(element, mirror, processingEnv, isOneWeCareAbout);
    }

    /**
     * Converts the provided modifiers to the corresponding set of modifier names.
     *
     * @param modifiers the modifiers
     * @return the modifier names
     */
    static Set<String> toModifierNames(Set<javax.lang.model.element.Modifier> modifiers) {
        return modifiers.stream()
                .map(javax.lang.model.element.Modifier::name)
                .collect(Collectors.toSet());
    }

    /**
     * Returns the map of meta annotations for the provided collection of annotation values.
     *
     * @param annotations the annotations
     * @return the meta annotations for the provided set of annotations
     */
    static Map<TypeName, List<AnnotationAndValue>> toMetaAnnotations(Collection<AnnotationAndValue> annotations,
                                                                     ProcessingEnvironment processingEnv) {
        if (annotations.isEmpty()) {
            return Map.of();
        }

        Elements elements = processingEnv.getElementUtils();
        Map<TypeName, List<AnnotationAndValue>> result = new LinkedHashMap<>();
        annotations.stream()
                .filter(it -> !result.containsKey(it.typeName()))
                .forEach(it -> {
                    TypeElement typeElement = elements.getTypeElement(it.typeName().name());
                    if (typeElement != null) {
                        result.put(it.typeName(), new ArrayList<>(createAnnotationAndValueSet(typeElement)));
                    }
                });
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<TypeName, List<AnnotationAndValue>> toReferencedTypeNamesAndAnnotations(ProcessingEnvironment processingEnv,
                                                                                        TypeName typeName,
                                                                                        Collection<TypedElementName>... refs) {
        Map<TypeName, List<AnnotationAndValue>> result = new LinkedHashMap<>();
        for (Collection<TypedElementName> ref : refs) {
            for (TypedElementName typedElementName : ref) {
                collectReferencedTypeNames(result, processingEnv, typeName, List.of(typedElementName.typeName()));
                collectReferencedTypeNames(result, processingEnv, typeName, typedElementName.typeName().typeArguments());
            }
        }
        return result;
    }

    private void collectReferencedTypeNames(Map<TypeName, List<AnnotationAndValue>> result,
                                            ProcessingEnvironment processingEnv,
                                            TypeName typeName,
                                            Collection<TypeName> referencedColl) {
        for (TypeName referenced : referencedColl) {
            if (isBuiltInJavaType(referenced) || typeName.equals(referenced)) {
                continue;
            }

            // first time processing, we only need to do this on pass #1
            result.computeIfAbsent(referenced, (k) -> {
                TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(k.name());
                return (typeElement == null)
                        ? null :  createAnnotationAndValueListFromElement(typeElement, processingEnv.getElementUtils());
            });
        }
    }

    /**
     * Translation the arguments to a collection of {@link io.helidon.common.types.TypedElementName}'s.
     *
     * @param element               the typed element (i.e., class)
     * @param processingEnv         the processing env
     * @param wantWhatWeCanAccept   pass true to get the elements we can accept to process, false for the other ones
     * @param wantDefaultMethods    true to process {@code default} methods
     * @return the collection of typed elements
     */
    private Collection<TypedElementName> toElementInfo(TypeElement element,
                                                       ProcessingEnvironment processingEnv,
                                                       boolean wantWhatWeCanAccept,
                                                       boolean wantDefaultMethods) {
        return element.getEnclosedElements().stream()
                .filter(it -> it.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(it -> (wantWhatWeCanAccept == canAccept(it, wantDefaultMethods)))
                .map(it -> createTypedElementNameFromElement(it, processingEnv.getElementUtils()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Returns true if the executable element passed is acceptable for processing. By default that means methods that are not
     * static or default methods.
     *
     * @param ee the executable element
     * @param defineDefaultMethods true if we should also process default methods
     * @return true if not able to accept
     */
    private boolean canAccept(ExecutableElement ee,
                              boolean defineDefaultMethods) {
        Set<Modifier> mods = ee.getModifiers();
        if (mods.contains(Modifier.ABSTRACT)) {
            return true;
        }

        if (defineDefaultMethods
                && mods.contains(Modifier.DEFAULT)
                && ee.getParameters().isEmpty()) {
            return (ee.getReturnType().getKind() != TypeKind.VOID);
        }

        return false;
    }

    private Optional<TypeInfo> toBuilderTypeInfo(TypeName annotationTypeName,
                                                 TypeElement element,
                                                 ProcessingEnvironment processingEnv,
                                                 boolean wantDefaultMethods) {
        List<? extends TypeMirror> ifaces = element.getInterfaces();
        if (ifaces.size() > 1) {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR, "only supports one parent interface: " + element);
        } else if (ifaces.isEmpty()) {
            return Optional.empty();
        }

        Optional<TypeElement> parent = toTypeElement(ifaces.get(0));
        if (parent.isEmpty()) {
            return Optional.empty();
        }

        return createBuilderTypeInfo(annotationTypeName,
                                     createTypeNameFromElement(parent.orElseThrow()).orElseThrow(),
                                     parent.orElseThrow(),
                                     processingEnv,
                                     wantDefaultMethods);
    }

    /**
     * Generally callers should instead use {@link #createTypeInfo(TypeElement, TypeMirror, ProcessingEnvironment, Predicate)}
     * instead of this static method since that method will usage of the pluggable replacement to create a specialized
     * {@link TypeInfo} through the SPI service loader extensibility. This method can be used, however, if the built-in creator
     * is sufficient for the caller's needs.
     *
     * @param element           the element that is being processed
     * @param mirror            the type mirror for the element being processed
     * @param processingEnv     the processing environment
     * @param isOneWeCareAbout  the predicate filter to determine whether the element is of interest, and therefore should be
     *                          included in {@link TypeInfo#elementInfo()}. Otherwise, if the predicate indicates it is not of
     *                          interest then the method will be placed under {@link TypeInfo#otherElementInfo()} instead
     * @return the type info associated with the arguments being processed, or empty if not able to process the type
     */
    static Optional<TypeInfo> toTypeInfo(TypeElement element,
                                         TypeMirror mirror,
                                         ProcessingEnvironment processingEnv,
                                         Predicate<TypedElementName> isOneWeCareAbout) {
        TypeName fqTypeName = createTypeNameFromMirror(mirror).orElseThrow();
        if (fqTypeName.name().equals(Object.class.getName())) {
            return Optional.empty();
        }

        TypeName genericTypeName = fqTypeName.genericTypeName();
        Set<TypeName> allInterestingTypeNames = new LinkedHashSet<>();
        allInterestingTypeNames.add(genericTypeName);
        fqTypeName.typeArguments()
                .stream()
                .map(TypeName::genericTypeName)
                .filter(it -> !isBuiltInJavaType(it))
                .filter(it -> !it.generic())
                .forEach(allInterestingTypeNames::add);

        Elements elementUtils = processingEnv.getElementUtils();
        try {
            Set<AnnotationAndValue> annotations =
                    createAnnotationAndValueSet(elementUtils.getTypeElement(genericTypeName.name()));
            Map<TypeName, List<AnnotationAndValue>> referencedAnnotations = toMetaAnnotations(annotations, processingEnv);
            List<TypedElementName> elementsWeCareAbout = new ArrayList<>();
            List<TypedElementName> otherElements = new ArrayList<>();
            element.getEnclosedElements().stream()
                    .map(it -> createTypedElementNameFromElement(it, elementUtils))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(it -> {
                        if (isOneWeCareAbout.test(it)) {
                            elementsWeCareAbout.add(it);
                        } else {
                            otherElements.add(it);
                        }
                    });
            TypeInfoDefault.Builder builder = TypeInfoDefault.builder()
                    .typeName(fqTypeName)
                    .typeKind(String.valueOf(element.getKind()))
                    .annotations(annotations)
                    .referencedTypeNamesToAnnotations(referencedAnnotations)
                    .modifierNames(toModifierNames(element.getModifiers()))
                    .elementInfo(elementsWeCareAbout)
                    .otherElementInfo(otherElements);

            // add all of the element's and parameters to the references annotation set
            elementsWeCareAbout.forEach(it -> {
                if (!isBuiltInJavaType(it.typeName()) && !it.typeName().generic()) {
                    allInterestingTypeNames.add(it.typeName().genericTypeName());
                }
                List<AnnotationAndValue> annos = it.annotations();
                Map<TypeName, List<AnnotationAndValue>> resolved = toMetaAnnotations(annos, processingEnv);
                resolved.forEach(builder::addReferencedTypeNamesToAnnotations);
                resolved.keySet().stream()
                        .map(TypeName::genericTypeName)
                        .filter(t -> !isBuiltInJavaType(t))
                        .filter(t -> !t.generic())
                        .forEach(allInterestingTypeNames::add);
                it.parameterArguments().stream()
                        .map(TypedElementName::typeName)
                        .map(TypeName::genericTypeName)
                        .filter(t -> !isBuiltInJavaType(t))
                        .filter(t -> !t.generic())
                        .forEach(allInterestingTypeNames::add);
            });

            TypeMirror superTypeMirror = element.getSuperclass();
            TypeName fqSuperTypeName = createTypeNameFromMirror(superTypeMirror).orElse(null);
            if (fqSuperTypeName != null && !fqSuperTypeName.name().equals(Object.class.getName())) {
                TypeElement superTypeElement = elementUtils.getTypeElement(fqSuperTypeName.name());
                if (superTypeElement != null) {
                    TypeName genericSuperTypeName = fqSuperTypeName.genericTypeName();
                    Optional<TypeInfo> superTypeInfo =
                            toTypeInfo(superTypeElement, superTypeMirror, processingEnv, isOneWeCareAbout);
                    superTypeInfo.ifPresent(builder::superTypeInfo);
                    allInterestingTypeNames.add(genericSuperTypeName);
                    fqSuperTypeName.typeArguments().stream()
                            .map(TypeName::genericTypeName)
                            .filter(it -> !isBuiltInJavaType(it))
                            .filter(it -> !it.generic())
                            .forEach(allInterestingTypeNames::add);
                }
            }

            element.getInterfaces().forEach(interfaceTypeMirror -> {
                TypeName fqInterfaceTypeName = createTypeNameFromMirror(interfaceTypeMirror).orElse(null);
                if (fqInterfaceTypeName != null) {
                    TypeName genericInterfaceTypeName = fqInterfaceTypeName.genericTypeName();
                    allInterestingTypeNames.add(genericInterfaceTypeName);
                    fqInterfaceTypeName.typeArguments().stream()
                            .map(TypeName::genericTypeName)
                            .filter(it -> !isBuiltInJavaType(it))
                            .filter(it -> !it.generic())
                            .forEach(allInterestingTypeNames::add);
                    TypeElement interfaceTypeElement = elementUtils.getTypeElement(fqInterfaceTypeName.name());
                    if (interfaceTypeElement != null) {
                        Optional<TypeInfo> superTypeInfo =
                                toTypeInfo(interfaceTypeElement, interfaceTypeMirror, processingEnv, isOneWeCareAbout);
                        superTypeInfo.ifPresent(builder::addInterfaceTypeInfo);
                    }
                }
            });

            AtomicReference<String> moduleName = new AtomicReference<>();
            allInterestingTypeNames.forEach(it -> {
                TypeElement typeElement = elementUtils.getTypeElement(it.name());
                if (typeElement == null || !isTypeInThisModule(typeElement, moduleName, processingEnv)) {
                    if (hasValue(moduleName.get())) {
                        builder.addReferencedModuleName(it, moduleName.get());
                    }
                }
            });

            return Optional.of(builder.build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process: " + element, e);
        }
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
    public static boolean isTypeInThisModule(TypeElement type,
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
     * Translates a {@link javax.lang.model.type.TypeMirror} into a {@link javax.lang.model.element.TypeElement}.
     *
     * @param typeMirror the type mirror
     * @return the type element
     */
    public static Optional<TypeElement> toTypeElement(TypeMirror typeMirror) {
        if (TypeKind.DECLARED == typeMirror.getKind()) {
            TypeElement te = (TypeElement) ((DeclaredType) typeMirror).asElement();
            return (te.toString().equals(Object.class.getName())) ? Optional.empty() : Optional.of(te);
        }
        return Optional.empty();
    }

    /**
     * Creates a name from a declared type during annotation processing.
     *
     * @param type the element type
     * @return the associated type name instance
     */
    public static Optional<TypeNameDefault> createTypeNameFromDeclaredType(DeclaredType type) {
        return createTypeNameFromElement(type.asElement());
    }

    /**
     * Creates a name from an element type during annotation processing.
     *
     * @param type the element type
     * @return the associated type name instance
     */
    public static Optional<TypeNameDefault> createTypeNameFromElement(Element type) {
        if (type instanceof VariableElement) {
            return createTypeNameFromMirror(type.asType());
        }

        if (type instanceof ExecutableElement) {
            return createTypeNameFromMirror(((ExecutableElement) type).getReturnType());
        }

        List<String> classNames = new ArrayList<>();
        classNames.add(type.getSimpleName().toString());
        while (type.getEnclosingElement() != null
                && ElementKind.PACKAGE != type.getEnclosingElement().getKind()) {
            classNames.add(type.getEnclosingElement().getSimpleName().toString());
            type = type.getEnclosingElement();
        }
        Collections.reverse(classNames);
        String className = String.join(".", classNames);

        Element packageName = type.getEnclosingElement() == null ? type : type.getEnclosingElement();

        return Optional.of(create(packageName.toString(), className));
    }

    /**
     * Converts a type mirror to a type name during annotation processing.
     *
     * @param typeMirror the type mirror
     * @return the type name associated with the type mirror, or empty for generic type variables
     */
    public static Optional<TypeNameDefault> createTypeNameFromMirror(TypeMirror typeMirror) {
        TypeKind kind = typeMirror.getKind();
        if (kind.isPrimitive()) {
            Class<?> type;
            switch (kind) {
            case BOOLEAN:
                type = boolean.class;
                break;
            case BYTE:
                type = byte.class;
                break;
            case SHORT:
                type = short.class;
                break;
            case INT:
                type = int.class;
                break;
            case LONG:
                type = long.class;
                break;
            case CHAR:
                type = char.class;
                break;
            case FLOAT:
                type = float.class;
                break;
            case DOUBLE:
                type = double.class;
                break;
            default:
                throw new IllegalStateException("Unknown primitive type: " + kind);
            }

            return Optional.of(create(type));
        }

        if (TypeKind.VOID == kind) {
            return Optional.of(create(void.class));
        } else if (TypeKind.TYPEVAR == kind) {
            // note that we converge on renaming generics to wildcard (?) here intentionally
            return Optional.of(createFromGenericDeclaration(/*typeMirror.toString()*/ "?"));
        } else if (TypeKind.WILDCARD == kind) {
            return Optional.of(createFromTypeName(typeMirror.toString()));
        } else if (TypeKind.NONE == kind) {
            return Optional.empty();
        }

        if (typeMirror instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) typeMirror;
            return Optional.of(createTypeNameFromMirror(arrayType.getComponentType()).orElseThrow().toBuilder()
                                       .array(true)
                                       .build());
        }

        if (typeMirror instanceof DeclaredType) {
            DeclaredType declaredType = (DeclaredType) typeMirror;
            List<TypeName> typeParams = declaredType.getTypeArguments()
                    .stream()
                    .map(BuilderTypeTools::createTypeNameFromMirror)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toList());

            TypeNameDefault result = createTypeNameFromElement(declaredType.asElement()).orElse(null);
            if (typeParams.isEmpty() || result == null) {
                return Optional.ofNullable(result);
            }

            return Optional.of(result.toBuilder().typeArguments(typeParams).build());
        }

        throw new IllegalStateException("Unknown type mirror: " + typeMirror);
    }

    /**
     * Locate an annotation mirror by name.
     *
     * @param annotationType the annotation type to search for
     * @param ams            the collection to search through
     * @return the annotation mirror, or empty if not found
     */
    public static Optional<? extends AnnotationMirror> findAnnotationMirror(String annotationType,
                                                                            Collection<? extends AnnotationMirror> ams) {
        return ams.stream()
                .filter(it -> annotationType.equals(it.getAnnotationType().toString()))
                .findFirst();
    }

    /**
     * Creates an instance from an annotation mirror during annotation processing.
     *
     * @param am       the annotation mirror
     * @param elements the elements
     * @return the new instance or empty if the annotation mirror passed is invalid
     */
    public static Optional<AnnotationAndValue> createAnnotationAndValueFromMirror(AnnotationMirror am,
                                                                                  Elements elements) {
        Optional<TypeNameDefault> val = createTypeNameFromMirror(am.getAnnotationType());

        return val.map(it -> AnnotationAndValueDefault.create(it, extractValues(am, elements)));
    }

    /**
     * Creates an instance from a variable element during annotation processing.
     *
     * @param e        the variable/type element
     * @param elements the elements
     * @return the list of annotations extracted from the element
     */
    public static List<AnnotationAndValue> createAnnotationAndValueListFromElement(Element e,
                                                                                   Elements elements) {
        return e.getAnnotationMirrors().stream().map(it -> createAnnotationAndValueFromMirror(it, elements))
                .filter(Optional::isPresent)
                .map(Optional::orElseThrow)
                .collect(Collectors.toList());
    }

    /**
     * Creates a set of annotations based using annotation processor.
     *
     * @param type the enclosing/owing type element
     * @return the annotation value set
     */
    public static Set<AnnotationAndValue> createAnnotationAndValueSet(Element type) {
        return type.getAnnotationMirrors().stream()
                .map(BuilderTypeTools::createAnnotationAndValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates a set of annotations based using annotation processor.
     *
     * @param annoMirrors the annotation type mirrors
     * @return the annotation value set
     */
    public static Set<AnnotationAndValue> createAnnotationAndValueSet(List<? extends AnnotationMirror> annoMirrors) {
        return annoMirrors.stream()
                .map(BuilderTypeTools::createAnnotationAndValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates an annotation and value from introspection.
     *
     * @param annotationMirror the introspected annotation
     * @return the annotation and value
     */
    public static AnnotationAndValue createAnnotationAndValue(AnnotationMirror annotationMirror) {
        TypeName annoTypeName = createTypeNameFromMirror(annotationMirror.getAnnotationType()).orElseThrow();
        return AnnotationAndValueDefault.create(annoTypeName, extractValues(annotationMirror.getElementValues()));
    }

    /**
     * Extracts values from the annotation mirror value.
     *
     * @param am       the annotation mirror
     * @param elements the elements
     * @return the extracted values
     */
    public static Map<String, String> extractValues(AnnotationMirror am,
                                                    Elements elements) {
        return extractValues(elements.getElementValuesWithDefaults(am));
    }

    /**
     * Extracts values from the annotation element values.
     *
     * @param values the element values
     * @return the extracted values
     */
    public static Map<String, String> extractValues(Map<? extends ExecutableElement, ? extends AnnotationValue> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((el, val) -> {
            String name = el.getSimpleName().toString();
            String value = val.accept(new ToStringAnnotationValueVisitor(), null);
            if (value != null) {
                result.put(name, value);
            }
        });
        return result;
    }

    /**
     * Extracts the singular {@code value()} value. Return value will always be non-null.
     *
     * @param am       the annotation mirror
     * @param elements the elements
     * @return the extracted values
     */
    public static String extractValue(AnnotationMirror am,
                                      Elements elements) {
        return Objects.requireNonNull(extractValues(elements.getElementValuesWithDefaults(am)).get("value"));
    }

    /**
     * Creates an instance of a {@link io.helidon.common.types.TypedElementName} given its type and variable element from
     * annotation processing. If the passed in element is not a {@link TypeInfo#KIND_FIELD}, {@link TypeInfo#KIND_METHOD},
     * {@link TypeInfo#KIND_CONSTRUCTOR}, or {@link TypeInfo#KIND_PARAMETER} then this method may return empty.
     *
     * @param v        the element (from annotation processing)
     * @param elements the elements
     * @return the created instance
     */
    public static Optional<TypedElementName> createTypedElementNameFromElement(Element v,
                                                                               Elements elements) {
        TypeName type = createTypeNameFromElement(v).orElse(null);
        List<TypeName> componentTypeNames = null;
        String defaultValue = null;
        List<AnnotationAndValue> elementTypeAnnotations = List.of();
        Set<String> modifierNames = Set.of();
        List<TypedElementName> params = List.of();
        if (v instanceof ExecutableElement) {
            ExecutableElement ee = (ExecutableElement) v;
            TypeMirror returnType = ee.getReturnType();
            if (type == null) {
                type = createTypeNameFromMirror(returnType).orElse(createFromGenericDeclaration(returnType.toString()));
            }
            if (returnType instanceof DeclaredType) {
                List<? extends TypeMirror> args = ((DeclaredType) returnType).getTypeArguments();
                componentTypeNames = args.stream()
                        .map(BuilderTypeTools::createTypeNameFromMirror)
                        .filter(Optional::isPresent)
                        .map(Optional::orElseThrow)
                        .collect(Collectors.toList());
                elementTypeAnnotations =
                        createAnnotationAndValueListFromElement(((DeclaredType) returnType).asElement(), elements);
            }
            AnnotationValue annotationValue = ee.getDefaultValue();
            defaultValue = annotationValue == null
                    ? null
                    : annotationValue.accept(new ToStringAnnotationValueVisitor()
                                                     .mapBooleanToNull(true)
                                                     .mapVoidToNull(true)
                                                     .mapBlankArrayToNull(true)
                                                     .mapEmptyStringToNull(true)
                                                     .mapToSourceDeclaration(true), null);
            modifierNames = ee.getModifiers().stream()
                    .map(Modifier::toString)
                    .collect(Collectors.toSet());
            params = ee.getParameters().stream()
                    .map(it -> createTypedElementNameFromElement(it, elements).orElseThrow())
                    .collect(Collectors.toList());
        }
        componentTypeNames = (componentTypeNames == null) ? List.of() : componentTypeNames;

        TypedElementNameDefault.Builder builder = TypedElementNameDefault.builder()
                .typeName(type)
                .componentTypeNames(componentTypeNames)
                .elementName(v.getSimpleName().toString())
                .elementKind(v.getKind().name())
                .annotations(createAnnotationAndValueListFromElement(v, elements))
                .elementTypeAnnotations(elementTypeAnnotations)
                .modifierNames(modifierNames)
                .parameterArgumentss(params);
        createTypeNameFromElement(v.getEnclosingElement()).ifPresent(builder::enclosingTypeName);
        Optional.ofNullable(defaultValue).ifPresent(builder::defaultValue);

        return Optional.of(builder.build());
    }

    /**
     * Helper method to determine if the value is present (i.e., non-null and non-blank).
     *
     * @param val the value to check
     * @return true if the value provided is non-null and non-blank
     */
    static boolean hasNonBlankValue(String val) {
        return (val != null) && !val.isBlank();
    }

    /**
     * Produces the generated sticker annotation attribute contents.
     *
     * @param generatorClassTypeName the generator class type name
     * @param versionId              the generator version identifier
     * @return the generated sticker
     */
    public static String generatedStickerFor(String generatorClassTypeName,
                                             String versionId) {
        return "value = \"" + Objects.requireNonNull(generatorClassTypeName)
                + "\", comments = \"version=" + versionId + "\"";
    }

    /**
     * Produces the generated copy right header on code generated artifacts.
     *
     * @param generatorClassTypeName the generator class type name
     * @return the generated comments
     */
    public static String copyrightHeaderFor(String generatorClassTypeName) {
        return "// This is a generated file (powered by Helidon). "
                    + "Do not edit or extend from this artifact as it is subject to change at any time!";
    }

    /**
     * Simple check to see the passed String value is non-null and non-blank.
     *
     * @param val the value to check
     * @return true if non-null and non-blank
     */
    static boolean hasValue(String val) {
        return (val != null && !val.isBlank());
    }

}
