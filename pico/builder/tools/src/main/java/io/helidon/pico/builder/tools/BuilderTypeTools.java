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

package io.helidon.pico.builder.tools;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import io.helidon.pico.builder.spi.DefaultTypeInfo;
import io.helidon.pico.builder.spi.TypeInfo;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.DefaultTypedElementName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

/**
 * The default implementation for {@link io.helidon.pico.builder.tools.TypeInfoCreator}. This also contains an abundance of other
 * useful methods used for annotation processing.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 1)
public class BuilderTypeTools implements TypeInfoCreator {

    private final System.Logger logger = System.getLogger(getClass().getName());

    @Override
    public TypeInfo createTypeInfo(AnnotationAndValue annotation,
                                   TypeName typeName,
                                   TypeElement element,
                                   ProcessingEnvironment processingEnv) {
        if (Objects.isNull(annotation) || Objects.isNull(annotation.typeName()) || Objects.isNull(typeName)) {
            throw new IllegalArgumentException();
        }

        if (typeName.name().equals(Annotation.class.getName())) {
            return null;
        }

        if (element.getKind() != ElementKind.INTERFACE && element.getKind() != ElementKind.ANNOTATION_TYPE) {
            String msg = annotation.typeName() + " is intended to be used on interfaces only: " + element;
            logger.log(System.Logger.Level.ERROR, msg);
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            return null;
        }

        List<ExecutableElement> problems = element.getEnclosedElements().stream()
                .filter(it -> it.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(this::canAccept)
                .filter(it -> !it.getParameters().isEmpty())
                .collect(Collectors.toList());
        if (!problems.isEmpty()) {
            String msg = "only simple getters with 0 args are supported: " + element + ": " + problems;
            logger.log(System.Logger.Level.ERROR, msg);
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            return null;
        }

        Collection<TypedElementName> elementInfo = toElementInfo(element, processingEnv);
        return DefaultTypeInfo.builder()
                .typeName(typeName)
                .typeKind(String.valueOf(element.getKind()))
                .annotations(BuilderTypeTools.createAnnotationAndValueListFromElement(element, processingEnv.getElementUtils()))
                .elementInfo(elementInfo)
                .superTypeInfo(toTypeInfo(annotation, element, processingEnv))
                .build();
    }

    protected Collection<TypedElementName> toElementInfo(TypeElement element, ProcessingEnvironment processingEnv) {
        return element.getEnclosedElements().stream()
                .filter(it -> it.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(this::canAccept)
                .map(it -> createTypedElementNameFromElement(it, processingEnv.getElementUtils()))
                .collect(Collectors.toList());
    }

    protected boolean canAccept(ExecutableElement ee) {
        Set<Modifier> mods = ee.getModifiers();
        return !mods.contains(Modifier.DEFAULT) && !mods.contains(Modifier.STATIC);
    }

    private TypeInfo toTypeInfo(AnnotationAndValue annotation, TypeElement element, ProcessingEnvironment processingEnv) {
        List<? extends TypeMirror> ifaces = element.getInterfaces();
        if (ifaces.size() > 1) {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR, "currently only supports one parent interface: " + element);
        } else if (ifaces.isEmpty()) {
            return null;
        }

        TypeElement parent = toTypeElement(ifaces.get(0));
        if (Objects.isNull(parent)) {
            return null;
        }

        return createTypeInfo(annotation, createTypeNameFromElement(parent), parent, processingEnv);
    }

    /**
     * Translates a {@link javax.lang.model.type.TypeMirror} into a {@link javax.lang.model.element.TypeElement}.
     *
     * @param typeMirror the type mirror
     * @return the type element
     */
    public static TypeElement toTypeElement(TypeMirror typeMirror) {
        if (TypeKind.DECLARED == typeMirror.getKind()) {
            TypeElement te = (TypeElement) ((DeclaredType) typeMirror).asElement();
            return (te.toString().equals(Object.class.getName())) ? null : te;
        }
        return null;
    }

    /**
     * Creates a name from an element type during annotation processing.
     *
     * @param type the element type
     * @return the associated type name instance
     */
    public static DefaultTypeName createTypeNameFromElement(Element type) {
        if (Objects.isNull(type)) {
            return null;
        }

        if (type instanceof VariableElement) {
            return createTypeNameFromMirror(type.asType());
        }

        if (type instanceof ExecutableElement) {
            return createTypeNameFromMirror(((ExecutableElement) type).getReturnType());
        }

        String className = type.getSimpleName().toString();
        while (Objects.nonNull(type.getEnclosingElement()) && ElementKind.PACKAGE != type.getEnclosingElement().getKind()) {
            className = type.getEnclosingElement().getSimpleName() + "." + className;
            type = type.getEnclosingElement();
        }
        return Objects.isNull(type.getEnclosingElement())
                ? DefaultTypeName.create(type.toString(), className)
                : DefaultTypeName.create(type.getEnclosingElement().toString(), className);
    }

    /**
     * Converts a type mirror to a type name during annotation processing.
     *
     * @param typeMirror the type mirror
     * @return the type name associated with the type mirror, or null for generic type variables
     */
    public static DefaultTypeName createTypeNameFromMirror(TypeMirror typeMirror) {
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

            return DefaultTypeName.create(type);
        }

        if (TypeKind.VOID == kind) {
            return DefaultTypeName.create(void.class);
        } else if (TypeKind.TYPEVAR == kind) {
            return null;
        } else if (TypeKind.WILDCARD == kind) {
            return DefaultTypeName.createFromTypeName(typeMirror.toString());
        }

        if (typeMirror instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) typeMirror;
            return createTypeNameFromMirror(arrayType.getComponentType()).toBuilder()
                    .array(true)
                    .build();
        }

        if (typeMirror instanceof DeclaredType) {
            DeclaredType declaredType = (DeclaredType) typeMirror;
            DefaultTypeName result = createTypeNameFromElement(declaredType.asElement());
            List<TypeName> typeParams = declaredType.getTypeArguments().stream()
                    .map(BuilderTypeTools::createTypeNameFromMirror)
                    .collect(Collectors.toList());
            if (!typeParams.isEmpty()) {
                result = result.toBuilder().typeArguments(typeParams).build();
            }
            return result;
        }

        throw new AssertionError("unknown type mirror: " + typeMirror);
    }

    /**
     * Locate an annotation mirror by name.
     *
     * @param annotationType the annotation type to search for
     * @param ams the list to search through
     * @return the annotation mirror, or null if not found.
     */
    public static AnnotationMirror findAnnotationMirror(String annotationType, List<? extends AnnotationMirror> ams) {
        return ams.stream()
                .filter(it -> annotationType.equals(it.getAnnotationType().toString()))
                .findFirst().orElse(null);
    }

    /**
     * Creates an instance from an annotation mirror during annotation processing.
     *
     * @param am the annotation mirror
     * @param elements  the elements
     * @return the new instance
     */
    public static AnnotationAndValue createAnnotationAndValueFromMirror(AnnotationMirror am, Elements elements) {
        return DefaultAnnotationAndValue.create(createTypeNameFromMirror(am.getAnnotationType()), extractValues(am, elements));
    }

    /**
     * Creates an instance from a variable element during annotation processing.
     *
     * @param e the variable/type element
     * @param elements  the elements
     * @return the new instance
     */
    public static List<AnnotationAndValue> createAnnotationAndValueListFromElement(Element e, Elements elements) {
        return e.getAnnotationMirrors().stream().map(it -> createAnnotationAndValueFromMirror(it, elements))
                .collect(Collectors.toList());
    }

    /**
     * Extracts values from the annotation mirror value.
     *
     * @param am        the annotation mirror
     * @param elements  the elements
     * @return the extracted values
     */
    public static Map<String, String> extractValues(AnnotationMirror am, Elements elements) {
        if (Objects.nonNull(elements)) {
            return extractValues(elements.getElementValuesWithDefaults(am));
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
    public static TypedElementName createTypedElementNameFromElement(Element v, Elements elements) {
        final TypeName type = createTypeNameFromElement(v);
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

}
