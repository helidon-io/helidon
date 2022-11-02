/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.processor.tools;

import java.lang.annotation.Annotation;
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

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.pico.builder.processor.spi.DefaultTypeInfo;
import io.helidon.pico.builder.processor.spi.TypeInfo;
import io.helidon.pico.builder.processor.spi.TypeInfoCreator;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.DefaultTypedElementName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

/**
 * The default implementation for {@link io.helidon.pico.builder.processor.spi.TypeInfoCreator}. This also contains an abundance of other
 * useful methods used for annotation processing.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 1)
public class BuilderTypeTools implements TypeInfoCreator {

    private final System.Logger logger = System.getLogger(getClass().getName());

    /**
     * Default constructor.
     */
    // note: this needs to remain public since it will be resolved via service loader ...
    @Deprecated
    public BuilderTypeTools() {
    }

    @Override
    public Optional<TypeInfo> createTypeInfo(AnnotationAndValue annotation,
                                             TypeName typeName,
                                             TypeElement element,
                                             ProcessingEnvironment processingEnv) {
        Objects.requireNonNull(annotation);
        Objects.requireNonNull(annotation.typeName());
        Objects.requireNonNull(typeName);

        if (typeName.name().equals(Annotation.class.getName())) {
            return Optional.empty();
        }

        if (element.getKind() != ElementKind.INTERFACE && element.getKind() != ElementKind.ANNOTATION_TYPE) {
            String msg = annotation.typeName() + " is intended to be used on interfaces only: " + element;
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            throw new IllegalStateException(msg);
        }

        List<ExecutableElement> problems = element.getEnclosedElements().stream()
                .filter(it -> it.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(this::canAccept)
                .filter(it -> !it.getParameters().isEmpty())
                .collect(Collectors.toList());
        if (!problems.isEmpty()) {
            String msg = "only simple getters with 0 args are supported: " + element + ": " + problems;
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            throw new IllegalStateException(msg);
        }

        Collection<TypedElementName> elementInfo = toElementInfo(element, processingEnv);
        return Optional.of(DefaultTypeInfo.builder()
                .typeName(typeName)
                .typeKind(String.valueOf(element.getKind()))
                .annotations(BuilderTypeTools.createAnnotationAndValueListFromElement(element, processingEnv.getElementUtils()))
                .elementInfo(elementInfo)
                .superTypeInfo(toTypeInfo(annotation, element, processingEnv).orElse(null))
                .build());
    }

    /**
     * Translation the arguments to a collection of {@link io.helidon.pico.types.TypedElementName}'s.
     *
     * @param element           the typed element (i.e., class)
     * @param processingEnv     the processing env
     * @return the collection of typed elements
     */
    protected Collection<TypedElementName> toElementInfo(TypeElement element, ProcessingEnvironment processingEnv) {
        return element.getEnclosedElements().stream()
                .filter(it -> it.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(this::canAccept)
                .map(it -> createTypedElementNameFromElement(it, processingEnv.getElementUtils()))
                .collect(Collectors.toList());
    }

    /**
     * Returns true if the executable element passed is acceptable for processing (i.e., not a static and not a default method).
     *
     * @param ee    the executable element
     * @return true if not default and not static
     */
    protected boolean canAccept(ExecutableElement ee) {
        Set<Modifier> mods = ee.getModifiers();
        return !mods.contains(Modifier.DEFAULT) && !mods.contains(Modifier.STATIC);
    }

    private Optional<TypeInfo> toTypeInfo(AnnotationAndValue annotation,
                                          TypeElement element,
                                          ProcessingEnvironment processingEnv) {
        List<? extends TypeMirror> ifaces = element.getInterfaces();
        if (ifaces.size() > 1) {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR, "currently only supports one parent interface: " + element);
        } else if (ifaces.isEmpty()) {
            return Optional.empty();
        }

        Optional<TypeElement> parent = toTypeElement(ifaces.get(0));
        if (parent.isEmpty()) {
            return Optional.empty();
        }

        return createTypeInfo(annotation,
                              createTypeNameFromElement(parent.orElseThrow()).orElseThrow(),
                              parent.orElseThrow(),
                              processingEnv);
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

        String className = type.getSimpleName().toString();
        while (Objects.nonNull(type.getEnclosingElement())
                && ElementKind.PACKAGE != type.getEnclosingElement().getKind()) {
            className = type.getEnclosingElement().getSimpleName() + "." + className;
            type = type.getEnclosingElement();
        }
        return Optional.of(Objects.isNull(type.getEnclosingElement())
                ? DefaultTypeName.create(type.toString(), className)
                : DefaultTypeName.create(type.getEnclosingElement().toString(), className));
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
                throw new AssertionError("unknown primitive type: " + kind);
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
            DefaultTypeName result = createTypeNameFromElement(declaredType.asElement()).orElse(null);
            List<TypeName> typeParams = declaredType.getTypeArguments().stream()
                    .map(BuilderTypeTools::createTypeNameFromMirror)
                    .filter(Optional::isPresent)
                    .map(Optional::orElseThrow)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!typeParams.isEmpty()) {
                result = result.toBuilder().typeArguments(typeParams).build();
            }
            return Optional.of(result);
        }

        throw new AssertionError("unknown type mirror: " + typeMirror);
    }

    /**
     * Locate an annotation mirror by name.
     *
     * @param annotationType    the annotation type to search for
     * @param ams               the collection to search through
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
     * @param am        the annotation mirror
     * @param elements  the elements
     * @return the new instance or empty if the annotation mirror passed is invalid
     */
    public static Optional<AnnotationAndValue> createAnnotationAndValueFromMirror(AnnotationMirror am,
                                                                                  Elements elements) {
        Optional<DefaultTypeName> val = createTypeNameFromMirror(am.getAnnotationType());
        if (val.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DefaultAnnotationAndValue.create(val.get(), extractValues(am, Optional.of(elements))));
    }

    /**
     * Creates an instance from a variable element during annotation processing.
     *
     * @param e the variable/type element
     * @param elements  the elements
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
     * @param am        the annotation mirror
     * @param elements  the optional elements
     * @return the extracted values
     */
    public static Map<String, String> extractValues(AnnotationMirror am,
                                                    Optional<Elements> elements) {
        if (elements.isPresent()) {
            return extractValues(elements.get().getElementValuesWithDefaults(am));
        }

        return extractValues(am.getElementValues());
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
     * Creates an instance of a {@link TypedElementName} given its type and variable element from
     * annotation processing.
     *
     * @param v         the element (from annotation processing)
     * @param elements  the elements
     * @return the created instance
     */
    public static TypedElementName createTypedElementNameFromElement(Element v,
                                                                     Elements elements) {
        final TypeName type = createTypeNameFromElement(v).orElse(null);
        List<TypeName> componentTypeNames = null;
        String defaultValue = null;
        List<AnnotationAndValue> elementTypeAnnotations = Collections.emptyList();
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
            defaultValue = Objects.isNull(annotationValue)
                    ? null : annotationValue.accept(new ToStringAnnotationValueVisitor()
                                                            .mapBooleanToNull(true)
                                                            .mapVoidToNull(true)
                                                            .mapBlankArrayToNull(true)
                                                            .mapEmptyStringToNull(true)
                                                            .mapToSourceDeclaration(true), null);
        }

        return DefaultTypedElementName.builder()
                .typeName(type)
                .componentTypeNames(componentTypeNames)
                .elementName(v.getSimpleName().toString())
                .defaultValue(defaultValue)
                .annotations(createAnnotationAndValueListFromElement(v, elements))
                .elementTypeAnnotations(elementTypeAnnotations)
                .build();
    }

    /**
     * Helper method to determine if the value is present (i.e., non-null and non-blank).
     *
     * @param val the value to check
     * @return true if the value provided is non-null and non-blank.
     */
    static boolean hasNonBlankValue(String val) {
        return Objects.nonNull(val) && !val.isBlank();
    }

}
