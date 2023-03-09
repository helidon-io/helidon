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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import io.helidon.builder.processor.spi.TypeInfoCreatorProvider;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.DefaultAnnotationAndValue;
import io.helidon.common.types.DefaultTypeInfo;
import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.DefaultTypedElementName;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementName;

import static io.helidon.builder.processor.tools.BeanUtils.isBuiltInJavaType;

/**
 * The default implementation for {@link io.helidon.builder.processor.spi.TypeInfoCreatorProvider}. This also contains an abundance of
 * other useful methods used for annotation processing.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 1)
public class BuilderTypeTools implements TypeInfoCreatorProvider {
    private static final boolean ACCEPT_ABSTRACT_CLASS_TARGETS = true;

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
    public Optional<TypeInfo> createTypeInfo(TypeName annotationTypeName,
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
                .collect(Collectors.toList());
        if (!problems.isEmpty()) {
            String msg = "only simple getters with no arguments are supported: " + element + ": " + problems;
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            throw new IllegalStateException(msg);
        }

        Collection<TypedElementName> elementInfo = toElementInfo(element, processingEnv, true, wantDefaultMethods);
        Collection<TypedElementName> otherElementInfo = toElementInfo(element, processingEnv, false, wantDefaultMethods);
        Set<String> modifierNames = element.getModifiers().stream()
                .map(Modifier::toString)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        return Optional.of(DefaultTypeInfo.builder()
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
                                   .update(it -> toTypeInfo(annotationTypeName, element, processingEnv, wantDefaultMethods)
                                           .ifPresent(it::superTypeInfo))
                                   .build());
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

            result.compute(referenced, (k, v) -> {
                if (v == null) {
                    // first time processing, we only need to do this on pass #1
                    TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(k.name());
                    if (typeElement != null) {
                        v = createAnnotationAndValueListFromElement(typeElement, processingEnv.getElementUtils());
                    }
                }
                return v;
            });
        }
    }

    /**
     * Determines if the target element with the {@link io.helidon.builder.Builder} annotation is an acceptable element type.
     * If it is not acceptable then the caller is expected to throw an exception or log an error, etc.
     *
     * @param element the element
     * @return true if the element is acceptable
     */
    public static boolean isAcceptableBuilderTarget(Element element) {
        final ElementKind kind = element.getKind();
        final Set<Modifier> modifiers = element.getModifiers();
        boolean isAcceptable = (kind == ElementKind.INTERFACE
                                        || kind == ElementKind.ANNOTATION_TYPE
                                        || (ACCEPT_ABSTRACT_CLASS_TARGETS
                                                    && (kind == ElementKind.CLASS && modifiers.contains(Modifier.ABSTRACT))));
        return isAcceptable;
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
    protected Collection<TypedElementName> toElementInfo(TypeElement element,
                                                         ProcessingEnvironment processingEnv,
                                                         boolean wantWhatWeCanAccept,
                                                         boolean wantDefaultMethods) {
        return element.getEnclosedElements().stream()
                .filter(it -> it.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(it -> (wantWhatWeCanAccept == canAccept(it, wantDefaultMethods)))
                .map(it -> createTypedElementNameFromElement(it, processingEnv.getElementUtils()))
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
    protected boolean canAccept(ExecutableElement ee,
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

    private Optional<TypeInfo> toTypeInfo(TypeName annotationTypeName,
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

        return createTypeInfo(annotationTypeName,
                              createTypeNameFromElement(parent.orElseThrow()).orElseThrow(),
                              parent.orElseThrow(),
                              processingEnv,
                              wantDefaultMethods);
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
    public static Optional<DefaultTypeName> createTypeNameFromDeclaredType(DeclaredType type) {
        return createTypeNameFromElement(type.asElement());
    }

    /**
     * Creates a name from an element type during annotation processing.
     *
     * @param type the element type
     * @return the associated type name instance
     */
    public static Optional<DefaultTypeName> createTypeNameFromElement(Element type) {
        if (type instanceof VariableElement) {
            return createTypeNameFromMirror(type.asType());
        }

        if (type instanceof ExecutableElement) {
            return createTypeNameFromMirror(((ExecutableElement) type).getReturnType());
        }

        List<String> classNames = new ArrayList<>();
        classNames.add(type.getSimpleName().toString());
        while (Objects.nonNull(type.getEnclosingElement())
                && ElementKind.PACKAGE != type.getEnclosingElement().getKind()) {
            classNames.add(type.getEnclosingElement().getSimpleName().toString());
            type = type.getEnclosingElement();
        }
        Collections.reverse(classNames);
        String className = String.join(".", classNames);

        Element packageName = type.getEnclosingElement() == null ? type : type.getEnclosingElement();

        return Optional.of(DefaultTypeName.create(packageName.toString(), className));
    }

    /**
     * Converts a type mirror to a type name during annotation processing.
     *
     * @param typeMirror the type mirror
     * @return the type name associated with the type mirror, or empty for generic type variables
     */
    public static Optional<DefaultTypeName> createTypeNameFromMirror(TypeMirror typeMirror) {
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
                throw new IllegalStateException("unknown primitive type: " + kind);
            }

            return Optional.of(DefaultTypeName.create(type));
        }

        if (TypeKind.VOID == kind) {
            return Optional.of(DefaultTypeName.create(void.class));
        } else if (TypeKind.TYPEVAR == kind) {
            return Optional.empty();
        } else if (TypeKind.WILDCARD == kind) {
            return Optional.of(DefaultTypeName.createFromTypeName(typeMirror.toString()));
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

            DefaultTypeName result = createTypeNameFromElement(declaredType.asElement()).orElse(null);
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
        Optional<DefaultTypeName> val = createTypeNameFromMirror(am.getAnnotationType());

        return val.map(it -> DefaultAnnotationAndValue.create(it, extractValues(am, elements)));
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
            if (Objects.nonNull(value)) {
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
     * annotation processing.
     *
     * @param v        the element (from annotation processing)
     * @param elements the elements
     * @return the created instance
     */
    public static TypedElementName createTypedElementNameFromElement(Element v,
                                                                     Elements elements) {
        TypeName type = createTypeNameFromElement(v).orElse(null);
        List<TypeName> componentTypeNames = null;
        String defaultValue = null;
        List<AnnotationAndValue> elementTypeAnnotations = List.of();
        Set<String> modifierNames = Set.of();
        if (v instanceof ExecutableElement) {
            ExecutableElement ee = (ExecutableElement) v;
            TypeMirror returnType = ee.getReturnType();
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
        }
        componentTypeNames = (componentTypeNames == null) ? List.of() : componentTypeNames;

        DefaultTypedElementName.Builder builder = DefaultTypedElementName.builder()
                .typeName(type)
                .componentTypeNames(componentTypeNames)
                .elementName(v.getSimpleName().toString())
                .elementKind(v.getKind().name())
                .annotations(createAnnotationAndValueListFromElement(v, elements))
                .elementTypeAnnotations(elementTypeAnnotations)
                .modifierNames(modifierNames);

        Optional.ofNullable(defaultValue).ifPresent(builder::defaultValue);

        return builder.build();
    }

    /**
     * Helper method to determine if the value is present (i.e., non-null and non-blank).
     *
     * @param val the value to check
     * @return true if the value provided is non-null and non-blank.
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
     * @param ignoredGeneratorClassTypeName the generator class type name
     * @return the generated comments
     */
    public static String copyrightHeaderFor(String ignoredGeneratorClassTypeName) {
        return "// This is a generated file (powered by Helidon). "
                    + "Do not edit or extend from this artifact as it is subject to change at any time!";
    }

}
