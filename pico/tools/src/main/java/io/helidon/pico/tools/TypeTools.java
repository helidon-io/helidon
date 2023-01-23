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

package io.helidon.pico.tools;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;

import io.helidon.builder.processor.tools.BuilderTypeTools;
import io.helidon.pico.DefaultElementInfo;
import io.helidon.pico.DefaultInjectionPointInfo;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.ElementInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.InjectionPointProvider;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.DefaultTypedElementName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.AnnotationInfoList;
import io.github.classgraph.AnnotationParameterValue;
import io.github.classgraph.AnnotationParameterValueList;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.ClassTypeSignature;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.TypeArgument;
import io.github.classgraph.TypeSignature;
import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;

import static io.helidon.pico.tools.CommonUtils.first;
import static io.helidon.pico.tools.CommonUtils.hasValue;

/**
 * Generically handles Pico generated artifact creation via APT.
 */
public class TypeTools extends BuilderTypeTools {

    private TypeTools() {
    }

    /**
     * Converts the provided name to a type name path.
     *
     * @param typeName the type name to evaluate
     * @return the file path expression where dots are translated to file separators
     */
    static String toFilePath(
            TypeName typeName) {
        return toFilePath(typeName, ".java");
    }

    /**
     * Converts the provided name to a type name path.
     *
     * @param typeName the type name to evaluate
     * @param fileType the file type, typically ".java"
     * @return the file path expression where dots are translated to file separators
     */
    public static String toFilePath(
            TypeName typeName,
            String fileType) {
        String className = typeName.className();
        String packageName = typeName.packageName();
        if (!hasValue(packageName)) {
            packageName = "";
        } else {
            packageName = packageName.replace('.', File.separatorChar);
        }

        if (!fileType.startsWith(".")) {
            fileType = "." + fileType;
        }

        return packageName + File.separatorChar + className + fileType;
    }

    /**
     * Creates a type name from a classInfo.
     *
     * @param classInfo the classInfo
     * @return the typeName for the class info
     */
    static TypeName createTypeNameFromClassInfo(
            ClassInfo classInfo) {
        if (Objects.isNull(classInfo)) {
            return null;
        }
        return DefaultTypeName.create(classInfo.getPackageName(), classInfo.getSimpleName());
    }

    /**
     * Will convert any primitive type name to its Object type counterpart. If not primitive, will return the value passed.
     *
     * @param type the type name
     * @return the Object type name for the given type (e.g., "int.class" -> "Integer.class")
     */
    static TypeName toObjectTypeName(
            String type) {
        if (boolean.class.getName().equals(type)) {
            return DefaultTypeName.create(Boolean.class);
        } else if (byte.class.getName().equals(type)) {
            return DefaultTypeName.create(Byte.class);
        } else if (short.class.getName().equals(type)) {
            return DefaultTypeName.create(Short.class);
        } else if (int.class.getName().equals(type)) {
            return DefaultTypeName.create(Integer.class);
        } else if (long.class.getName().equals(type)) {
            return DefaultTypeName.create(Long.class);
        } else if (char.class.getName().equals(type)) {
            return DefaultTypeName.create(Character.class);
        } else if (float.class.getName().equals(type)) {
            return DefaultTypeName.create(Float.class);
        } else if (double.class.getName().equals(type)) {
            return DefaultTypeName.create(Double.class);
        } else {
            return DefaultTypeName.createFromTypeName(type);
        }
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annotation the annotation
     * @return the new instance
     * @deprecated switch to using pure annotation processing wherever possible
     */
    @Deprecated
    static AnnotationAndValue createAnnotationAndValueFromAnnotation(
            Annotation annotation) {
        return DefaultAnnotationAndValue.create(DefaultTypeName.create(annotation.annotationType()), extractValues(annotation));
    }

    /**
     * Creates a collection of {@link io.helidon.pico.types.TypedElementName} instances given a method definition.
     *
     * @param m the method definition (from introspection)
     * @return the created instance
     */
    static List<TypedElementName> createTypedElementNameListFromMethodArgs(
            Method m) {
        List<TypedElementName> result = new ArrayList<>(m.getParameterCount());
        for (int i = 0; i < m.getParameterCount(); i++) {
            Class<?> pType = m.getParameterTypes()[i];
            TypeName pTypeName = DefaultTypeName.create(pType);
            TypeName componentType = DefaultTypeName.create(pType.getComponentType());
            List<AnnotationAndValue> annotations = createAnnotationAndValueListFromAnnotations(m.getAnnotations());
            DefaultTypedElementName param = DefaultTypedElementName.builder()
                    .typeName(pTypeName)
                    .componentTypeNames(List.of(componentType))
                    .elementName("p" + i)
                    .annotations(annotations)
                    .build();
            result.add(param);
        }
        return result;
    }

    /**
     * Creates an instance from reflective access. Note that this approach will only have visibility to the
     * {@link java.lang.annotation.RetentionPolicy#RUNTIME} type annotations.
     *
     * @param annotations the annotations on the type, method, or parameters
     * @return the new instance
     * @deprecated Switch to use pure annotation processing instead of reflection
     */
    @Deprecated
    public static List<AnnotationAndValue> createAnnotationAndValueListFromAnnotations(
            Annotation[] annotations) {
        if (annotations == null || annotations.length <= 0) {
            return Collections.emptyList();
        }

        return Arrays.stream(annotations).map(TypeTools::createAnnotationAndValueFromAnnotation).collect(Collectors.toList());
    }

    /**
     * Extracts values from the annotation.
     *
     * @param annotation the annotation
     * @return the extracted value
     * @deprecated Switch to use pure annotation processing instead of reflection
     */
    @Deprecated
    static Map<String, String> extractValues(
            Annotation annotation) {
        Map<String, String> result = new HashMap<>();

        Class<? extends Annotation> aClass = annotation.annotationType();
        Method[] declaredMethods = aClass.getDeclaredMethods();
        for (Method declaredMethod : declaredMethods) {
            String propertyName = declaredMethod.getName();
            try {
                Object value = declaredMethod.invoke(annotation);
                if (value instanceof Annotation) {
                    // note to self: ignored for now
                } else {
                    String stringValue;
                    // check if array
                    if (value.getClass().isArray()) {
                        if (value.getClass().getComponentType().equals(Annotation.class)) {
                            stringValue = "array of annotations";
                        } else {
                            String[] stringArray;
                            if (value.getClass().getComponentType().equals(String.class)) {
                                stringArray = (String[]) value;
                            } else {
                                stringArray = new String[Array.getLength(value)];
                                for (int i = 0; i < stringArray.length; i++) {
                                    stringArray[i] = String.valueOf(Array.get(value, i));
                                }
                            }

                            stringValue = String.join(", ", stringArray);
                        }
                    } else {
                        // just convert to String
                        stringValue = String.valueOf(value);
                    }
                    result.put(propertyName, stringValue);
                }
            } catch (Throwable ignored) {
                boolean debugMe = true;
            }
        }
        return result;
    }

    /**
     * Extracts values from the annotation info list.
     *
     * @param values the annotation values
     * @return the extracted values
     */
    static Map<String, String> extractValues(
            AnnotationParameterValueList values) {
        return values.asMap().entrySet().stream()
                .map((e) -> new AbstractMap
                        .SimpleEntry<>(e.getKey(), toString(e.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Converts "Optional<Whatever>" or "Provider<Whatever>" -> "Whatever".
     *
     * @param typeName the type name, that might use generics
     * @return the generics component type name
     */
    static String componentTypeNameOf(
            String typeName) {
        int pos = typeName.indexOf('<');
        if (pos < 0) {
            return typeName;
        }

        int lastPos = typeName.indexOf('>', pos);
        assert (lastPos > pos) : typeName;
        return typeName.substring(pos + 1, lastPos);
    }

    private static String toString(
            AnnotationParameterValue val) {
        if (val == null) {
            return null;
        }

        Object v = val.getValue();
        if (v == null) {
            return null;
        }

        Class<?> clazz = v.getClass();
        if (!clazz.isArray()) {
            return v.toString();
        }

        Object[] arr = (Object[]) v;
        return "{" + CommonUtils.toString(Arrays.asList(arr)) + "}";
    }

    /**
     * Creates a set of qualifiers based upon class info introspection.
     *
     * @param classInfo the class info
     * @return the qualifiers
     */
    static Set<QualifierAndValue> createQualifierAndValueSet(
            ClassInfo classInfo) {
        return createQualifierAndValueSet(classInfo.getAnnotationInfo());
    }

    /**
     * Creates a set of qualifiers based upon method info introspection.
     *
     * @param methodInfo the method info
     * @return the qualifiers
     */
    static Set<QualifierAndValue> createQualifierAndValueSet(
            MethodInfo methodInfo) {
        return createQualifierAndValueSet(methodInfo.getAnnotationInfo());
    }

    /**
     * Creates a set of qualifiers based upon field info introspection.
     *
     * @param fieldInfo the field info
     * @return the qualifiers
     */
    static Set<QualifierAndValue> createQualifierAndValueSet(
            FieldInfo fieldInfo) {
        return createQualifierAndValueSet(fieldInfo.getAnnotationInfo());
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annotation the annotation
     * @return the new instance
     */
    static QualifierAndValue createQualifierAndValue(
            Annotation annotation) {
        return DefaultQualifierAndValue.create(DefaultTypeName.create(annotation.annotationType()), extractValues(annotation));
    }

    /**
     * Creates a set of qualifiers given the owning element.
     *
     * @param annotationInfoList the list of annotations
     * @return the qualifiers
     */
    static Set<QualifierAndValue> createQualifierAndValueSet(
            AnnotationInfoList annotationInfoList) {
        Set<AnnotationAndValue> set = createAnnotationAndValueSetFromMetaAnnotation(annotationInfoList, Qualifier.class);
        if (set.isEmpty()) {
            return Set.of();
        }

        return set.stream().map(DefaultQualifierAndValue::convert)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates a set of qualifiers given the owning element.

     * @param type the element type (from anno processing)
     * @return the set of qualifiers that the owning element has
     */
    public static Set<QualifierAndValue> createQualifierAndValueSet(
            Element type) {
        return createQualifierAndValueSet(type.getAnnotationMirrors());
    }

    /**
     * Creates a set of qualifiers given the owning element's annotation type mirror.

     * @param annoMirrors the annotation type mirrors (from anno processing)
     * @return the set of qualifiers that the owning element has
     */
    public static Set<QualifierAndValue> createQualifierAndValueSet(
            List<? extends AnnotationMirror> annoMirrors) {
        Set<QualifierAndValue> result = new LinkedHashSet<>();

        for (AnnotationMirror annoMirror : annoMirrors) {
            if (annoMirror.getAnnotationType().asElement().getAnnotation(Qualifier.class) != null) {
                String val = null;
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : annoMirror.getElementValues()
                        .entrySet()) {
                    if (e.getKey().toString().equals("value()")) {
                        val = String.valueOf(e.getValue().getValue());
                        break;
                    }
                }
                DefaultQualifierAndValue.Builder qualifier = DefaultQualifierAndValue.builder();
                qualifier.typeName(TypeTools.createTypeNameFromDeclaredType(annoMirror.getAnnotationType()).orElseThrow());
                if (val != null) {
                    qualifier.value(val);
                }
                result.add(qualifier.build());
            }
        }

        if (result.isEmpty()) {
            try {
                Class<? extends Annotation> javaxQualifier = JavaxTypeTools.INSTANCE.get()
                        .loadAnnotationClass("javax.inject.Qualifier").orElse(null);
                if (javaxQualifier != null) {
                    for (AnnotationMirror annoMirror : annoMirrors) {
                        if (annoMirror.getAnnotationType().asElement().getAnnotation(javaxQualifier) != null) {
                            String val = null;
                            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e
                                    : annoMirror.getElementValues().entrySet()) {
                                if (e.getKey().toString().equals("value()")) {
                                    val = String.valueOf(e.getValue().getValue());
                                    break;
                                }
                            }
                            result.add(DefaultQualifierAndValue.create(annoMirror.getAnnotationType().toString(), val));
                        }
                    }
                }
            } catch (Throwable t) {
                // normal
            }
        }

        return result;
    }

    /**
     * Creates a set of annotations based upon class info introspection.
     *
     * @param classInfo the class info
     * @return the annotation value set
     */
    static Set<AnnotationAndValue> createAnnotationAndValueSet(
            ClassInfo classInfo) {
        return classInfo.getAnnotationInfo().stream()
                .map(TypeTools::createAnnotationAndValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates a set of annotations based using annotation processor.
     *
     * @param type the enclosing/owing type element
     * @return the annotation value set
     */
    public static Set<AnnotationAndValue> createAnnotationAndValueSet(
            Element type) {
        return type.getAnnotationMirrors().stream()
                .map(TypeTools::createAnnotationAndValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates a set of annotations based using annotation processor.
     *
     * @param annoMirrors the annotation type mirrors
     * @return the annotation value set
     */
    static Set<AnnotationAndValue> createAnnotationAndValueSet(
            List<? extends AnnotationMirror> annoMirrors) {
        return annoMirrors.stream()
                .map(TypeTools::createAnnotationAndValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates a set of annotations given the owning element.
     *
     * @param annotationInfoList the list of annotations
     * @return the annotation and value set
     */
    public static Set<AnnotationAndValue> createAnnotationAndValueSet(
            AnnotationInfoList annotationInfoList) {
        return annotationInfoList.stream()
                .map(TypeTools::createAnnotationAndValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates an annotation and value from introspection.
     *
     * @param annotationInfo the introspected annotation
     * @return the annotation and value
     */
    static AnnotationAndValue createAnnotationAndValue(
            AnnotationInfo annotationInfo) {
        TypeName annoTypeName = createTypeNameFromClassInfo(annotationInfo.getClassInfo());
        return DefaultAnnotationAndValue.create(annoTypeName, extractValues(annotationInfo.getParameterValues()));
    }

    /**
     * Creates an annotation and value from introspection.
     *
     * @param annotationMirror the introspected annotation
     * @return the annotation and value
     */
    static AnnotationAndValue createAnnotationAndValue(
            AnnotationMirror annotationMirror) {
        TypeName annoTypeName = createTypeNameFromMirror(annotationMirror.getAnnotationType()).orElseThrow();
        return DefaultAnnotationAndValue.create(annoTypeName, extractValues(annotationMirror.getElementValues()));
    }

    /**
     * All annotations on every public method and the given type, including all of its methods.
     *
     * @param classInfo the classInfo of the enclosing class type
     * @return the complete set of annotations
     */
    static Set<AnnotationAndValue> gatherAllAnnotationsUsedOnPublicNonStaticMethods(
            ClassInfo classInfo) {
        Set<AnnotationAndValue> result = new LinkedHashSet<>(createAnnotationAndValueSet(classInfo));
        classInfo.getMethodAndConstructorInfo()
                .filter((m) -> !m.isPrivate() && !m.isStatic())
                .forEach((mi) -> result.addAll(createAnnotationAndValueSet(mi.getAnnotationInfo())));
        return result;
    }

    /**
     * All annotations on every public method and the given type, including all of its methods.
     *
     * @param serviceTypeElement the service type element of the enclosing class type
     * @param processEnv the processing environment
     * @return the complete set of annotations
     */
    static Set<AnnotationAndValue> gatherAllAnnotationsUsedOnPublicNonStaticMethods(
            TypeElement serviceTypeElement,
            ProcessingEnvironment processEnv) {
        Elements elementUtils = processEnv.getElementUtils();
        Set<AnnotationAndValue> result = new LinkedHashSet<>();
        createAnnotationAndValueSet(serviceTypeElement).forEach((anno) -> {
            TypeElement typeElement = elementUtils.getTypeElement(anno.typeName().name());
            if (typeElement != null) {
                typeElement.getAnnotationMirrors()
                        .forEach((am) -> result.add(createAnnotationAndValue(am)));
            }
        });
        serviceTypeElement.getEnclosedElements().stream()
                .filter((e) -> e.getKind() == ElementKind.METHOD)
                .filter((e) -> !e.getModifiers().contains(javax.lang.model.element.Modifier.PRIVATE))
                .filter((e) -> !e.getModifiers().contains(javax.lang.model.element.Modifier.STATIC))
                .map(ExecutableElement.class::cast)
                .forEach((exec) -> {
                    exec.getAnnotationMirrors().forEach((am) -> {
                        AnnotationAndValue anno = createAnnotationAndValue(am);
                        result.add(anno);
                        TypeElement typeElement = elementUtils.getTypeElement(anno.typeName().name());
                        if (typeElement != null) {
                            typeElement.getAnnotationMirrors()
                                    .forEach((am2) -> result.add(createAnnotationAndValue(am2)));
                        }
                    });
                });
        return result;
    }

    /**
     * Creates a set of annotation that are meta-annotated given the owning element.
     *
     * @param annotationInfoList the list of annotations
     * @param metaAnnoType the meta-annotation type
     * @return the qualifiers
     */
    static Set<AnnotationAndValue> createAnnotationAndValueSetFromMetaAnnotation(
            AnnotationInfoList annotationInfoList,
            Class<? extends Annotation> metaAnnoType) {
        AnnotationInfoList list = resolveMetaAnnotations(annotationInfoList, metaAnnoType);
        if (list == null) {
            if (metaAnnoType.getName().startsWith("jakarta.")) {
                try {
                    Class<? extends Annotation> javaxType = JavaxTypeTools.INSTANCE.get()
                            .loadAnnotationClass(oppositeOf(metaAnnoType.getName())).orElse(null);
                    if (javaxType != null) {
                        list = resolveMetaAnnotations(annotationInfoList, javaxType);
                    }
                } catch (Throwable t) {
                    // expected
                }

                if (list == null) {
                    return Set.of();
                }
            } else {
                return Set.of();
            }
        }

        Set<AnnotationAndValue> result = new LinkedHashSet<>();
        for (AnnotationInfo ai : list) {
            TypeName annotationType = DefaultTypeName.createFromTypeName(translate(ai.getName()));
            AnnotationParameterValueList values = ai.getParameterValues();
            if (values == null || values.isEmpty()) {
                result.add(DefaultAnnotationAndValue.create(annotationType, (String) null));
            } else if (values.size() > 1) {
                Map<String, String> strVals = extractValues(values);
                result.add(DefaultAnnotationAndValue.create(annotationType, strVals));
            } else {
                Object value = values.get(0).getValue();
                String strValue = Objects.nonNull(value) ? String.valueOf(value) : null;
                result.add(DefaultAnnotationAndValue.create(annotationType, strValue));
            }
        }
        return result;
    }

    /**
     * Extract the scope name from the given introspected class.
     *
     * @param classInfo the introspected class
     * @return the scope name, or null if no scope found
     */
    static String extractScopeTypeName(
            ClassInfo classInfo) {
        AnnotationInfoList list = resolveMetaAnnotations(classInfo.getAnnotationInfo(), Scope.class);
        if (list == null) {
            return null;
        }

        return translate(first(list, false).getName());
    }

    /**
     * Returns the methods that have an annotation.
     *
     * @param classInfo the class info
     * @param annoType  the annotation
     * @return the methods with the annotation
     */
    static MethodInfoList methodsAnnotatedWith(
            ClassInfo classInfo,
            Class<? extends Annotation> annoType) {
        MethodInfoList result = new MethodInfoList();
        classInfo.getMethodInfo().stream()
                .filter((methodInfo) -> hasAnnotation(methodInfo, annoType))
                .forEach(result::add);
        return result;
    }

    /**
     * Returns true if the method has an annotation.
     *
     * @param methodInfo the method info
     * @param annoType the annotation to check
     * @return true if the method has the annotation
     */
    static boolean hasAnnotation(
            MethodInfo methodInfo,
            Class<? extends Annotation> annoType) {
        String annoTypeName = annoType.getName();
        return methodInfo.hasAnnotation(annoTypeName) || methodInfo.hasAnnotation(oppositeOf(annoTypeName));
    }

    /**
     * Returns true if the method has an annotation.
     *
     * @param fieldInfo the field info
     * @param annoType the annotation to check
     * @return true if the method has the annotation
     */
    static boolean hasAnnotation(
            FieldInfo fieldInfo,
            Class<? extends Annotation> annoType) {
        String annoTypeName = annoType.getName();
        return fieldInfo.hasAnnotation(annoTypeName) || fieldInfo.hasAnnotation(oppositeOf(annoTypeName));
    }

    /**
     * Resolves meta annotations.
     *
     * @param annoList the complete set of annotations
     * @param metaAnnoType the meta-annotation to filter on
     * @return the filtered set having the meta-annotation.
     */
    static AnnotationInfoList resolveMetaAnnotations(
            AnnotationInfoList annoList,
            Class<? extends Annotation> metaAnnoType) {
        if (annoList == null) {
            return null;
        }

        AnnotationInfoList result = null;
        String metaName1 = metaAnnoType.getName();
        String metaName2 = oppositeOf(metaName1);
        for (AnnotationInfo ai : annoList) {
            ClassInfo aiClassInfo = ai.getClassInfo();
            if (aiClassInfo.hasAnnotation(metaName1) || aiClassInfo.hasAnnotation(metaName2)) {
                if (result == null) {
                    result = new AnnotationInfoList();
                }
                result.add(ai);
            }
        }
        return result;
    }

    /**
     * Determines the {@link jakarta.inject.Provider} or {@link io.helidon.pico.InjectionPointProvider} contract type.
     *
     * @param classInfo class info
     * @return the provided type
     */
    static String providesContractType(
            ClassInfo classInfo) {
        Set<String> candidates = new LinkedHashSet<>();

        ClassInfo nxt = classInfo;
        ToolsException firstTe = null;
        while (nxt != null) {
            ClassTypeSignature sig = nxt.getTypeSignature();
            List<ClassRefTypeSignature> superInterfaces = Objects.nonNull(sig)
                    ? sig.getSuperinterfaceSignatures()
                    : null;
            if (superInterfaces != null) {
                for (ClassRefTypeSignature superInterface : superInterfaces) {
                    if (!isProviderType(superInterface)) {
                        continue;
                    }

                    try {
                        candidates.add(Objects.requireNonNull(
                                providerTypeOf(superInterface.getTypeArguments().get(0), classInfo)));
                    } catch (ToolsException te) {
                        if (firstTe == null) {
                            firstTe = te;
                        }
                    }
                }
            }

            nxt = nxt.getSuperclass();
        }

        if (candidates.size() > 1) {
            throw new ToolsException("unsupported case where provider provides more than one type: " + classInfo);
        }

        if (!candidates.isEmpty()) {
            return first(candidates, false);
        }

        if (firstTe != null) {
            throw firstTe;
        }

        return null;
    }

    /**
     * Should only be called if the encloser of the typeArgument is known to be Provider type.
     */
    private static String providerTypeOf(
            TypeArgument typeArgument,
            Object enclosingElem) {
        if (!(typeArgument.getTypeSignature() instanceof ClassRefTypeSignature)) {
            throw new ToolsException("unsupported provider<> type of " + typeArgument + " in " + enclosingElem);
        }
        return typeArgument.toString();
    }

    /**
     * Determines the {@link jakarta.inject.Provider} contract type.
     *
     * @param sig class type signature
     * @return the provided type
     */
    static String providesContractType(
            TypeSignature sig) {
        if (sig instanceof ClassRefTypeSignature) {
            ClassRefTypeSignature csig = (ClassRefTypeSignature) sig;
            if (isProviderType(csig.getFullyQualifiedClassName())) {
                TypeArgument typeArg = csig.getTypeArguments().get(0);
                if (!(typeArg.getTypeSignature() instanceof ClassRefTypeSignature)) {
                    throw new ToolsException("unsupported: " + sig);
                }
                return typeArg.toString();
            }
        }
        return null;
    }

    /**
     * Returns the injection point info given a method element.
     *
     * @param elemInfo          the method element info
     * @return the injection point info
     */
    static InjectionPointInfo createInjectionPointInfo(
            MethodInfo elemInfo) {
        return createInjectionPointInfo(createTypeNameFromClassInfo(elemInfo.getClassInfo()), elemInfo, null);
    }

    /**
     * Returns the injection point info given a method element.
     *
     * @param serviceTypeName   the enclosing service type name
     * @param elemInfo          the method element info
     * @param elemOffset        optionally, the argument position (or null for the method level) - starts at 1 not 0
     * @return the injection point info
     */
    static InjectionPointInfo createInjectionPointInfo(
            TypeName serviceTypeName,
            MethodInfo elemInfo,
            Integer elemOffset) {
        String elemType;
        Set<QualifierAndValue> qualifiers;
        Set<AnnotationAndValue> annotations;
        AtomicReference<Boolean> isProviderWrapped = new AtomicReference<>();
        AtomicReference<Boolean> isListWrapped = new AtomicReference<>();
        AtomicReference<Boolean> isOptionalWrapped = new AtomicReference<>();
        if (Objects.nonNull(elemOffset)) {
            MethodParameterInfo paramInfo = elemInfo.getParameterInfo()[elemOffset - 1];
            elemType = extractInjectionPointTypeInfo(paramInfo, isProviderWrapped, isListWrapped, isOptionalWrapped);
            qualifiers = createQualifierAndValueSet(paramInfo.getAnnotationInfo());
            annotations = createAnnotationAndValueSet(paramInfo.getAnnotationInfo());
        } else {
            elemType = elemInfo.getTypeDescriptor().getResultType().toString();
            qualifiers = createQualifierAndValueSet(elemInfo);
            annotations = createAnnotationAndValueSet(elemInfo.getAnnotationInfo());
        }
        return DefaultInjectionPointInfo.builder()
                .serviceTypeName(serviceTypeName.name())
                .elementName(elemInfo.isConstructor()
                                     ? InjectionPointInfo.CONSTRUCTOR : elemInfo.getName())
                .elementKind(elemInfo.isConstructor()
                                     ? InjectionPointInfo.ElementKind.CONSTRUCTOR : InjectionPointInfo.ElementKind.METHOD)
                .elementTypeName(elemType)
                .elementArgs(elemInfo.getParameterInfo().length)
                .elementOffset(elemOffset)
                .access(toAccess(elemInfo.getModifiers()))
                .staticDeclaration(isStatic(elemInfo.getModifiers()))
                .qualifiers(qualifiers)
                .annotations(annotations)
                .optionalWrapped(isOptionalWrapped.get())
                .providerWrapped(isProviderWrapped.get())
                .listWrapped(isListWrapped.get())
                .build();
    }

    /**
     * Returns the method info given a method from introspection.
     *
     * @param methodInfo        the method element info
     * @param serviceLevelAnnos the annotation at the class level that should be inherited at the method level
     * @return the method info
     */
    static MethodElementInfo createMethodElementInfo(
            MethodInfo methodInfo,
            Set<AnnotationAndValue> serviceLevelAnnos) {
        TypeName serviceTypeName = createTypeNameFromClassInfo(methodInfo.getClassInfo());
        String elemType = methodInfo.getTypeDescriptor().getResultType().toString();
        Set<QualifierAndValue> qualifiers = createQualifierAndValueSet(methodInfo);
        Set<AnnotationAndValue> annotations = createAnnotationAndValueSet(methodInfo.getAnnotationInfo());
        if (Objects.nonNull(serviceLevelAnnos)) {
            annotations.addAll(serviceLevelAnnos);
        }
        List<String> throwables = extractThrowableTypeNames(methodInfo);
        List<ElementInfo> parameters = createParameterInfo(serviceTypeName, methodInfo);
        return DefaultMethodElementInfo.builder()
                .serviceTypeName(serviceTypeName.name())
                .elementName(methodInfo.isConstructor()
                                     ? InjectionPointInfo.CONSTRUCTOR : methodInfo.getName())
                .elementKind(methodInfo.isConstructor()
                                     ? InjectionPointInfo.ElementKind.CONSTRUCTOR : InjectionPointInfo.ElementKind.METHOD)
                .elementTypeName(elemType)
                .elementArgs(methodInfo.getParameterInfo().length)
                .elementOffset(Optional.empty())
                .access(toAccess(methodInfo.getModifiers()))
                .staticDeclaration(isStatic(methodInfo.getModifiers()))
                .annotations(annotations)
                .qualifiers(qualifiers)
                .throwableTypeNames(throwables)
                .parameterInfo(parameters)
                .build();
    }

    /**
     * Returns the method info given a method from annotation processing.
     *
     * @param serviceTypeElement the enclosing service type for the provided element
     * @param ee                the method element info
     * @param serviceLevelAnnos the annotation at the class level that should be inherited at the method level
     * @return the method info
     */
    static MethodElementInfo createMethodElementInfo(
            TypeElement serviceTypeElement,
            ExecutableElement ee,
            Set<AnnotationAndValue> serviceLevelAnnos) {
        TypeName serviceTypeName = createTypeNameFromElement(serviceTypeElement).orElseThrow();
        String elemType = ee.getReturnType().toString();
        Set<QualifierAndValue> qualifiers = createQualifierAndValueSet(ee);
        Set<AnnotationAndValue> annotations = createAnnotationAndValueSet(ee);
        if (serviceLevelAnnos != null) {
            annotations.addAll(serviceLevelAnnos);
        }
        List<String> throwables = extractThrowableTypeNames(ee);
        List<ElementInfo> parameters = createParameterInfo(serviceTypeName, ee);
        return DefaultMethodElementInfo.builder()
                .serviceTypeName(serviceTypeName.name())
                .elementName(ee.getKind() == ElementKind.CONSTRUCTOR
                                     ? InjectionPointInfo.CONSTRUCTOR : ee.getSimpleName().toString())
                .elementKind(ee.getKind() == ElementKind.CONSTRUCTOR
                                     ? InjectionPointInfo.ElementKind.CONSTRUCTOR : InjectionPointInfo.ElementKind.METHOD)
                .elementTypeName(elemType)
                .elementArgs(ee.getParameters().size())
                .elementOffset(Optional.empty())
                .access(toAccess(ee))
                .staticDeclaration(isStatic(ee))
                .qualifiers(qualifiers)
                .annotations(annotations)
                .throwableTypeNames(throwables)
                .parameterInfo(parameters)
                .build();
    }

    /**
     * Returns the throwable types on a method.
     *
     * @param methodInfo the method info
     * @return the list of throwable type names
     */
    private static List<String> extractThrowableTypeNames(
            MethodInfo methodInfo) {
        String[] thrownExceptionNames = methodInfo.getThrownExceptionNames();
        if (thrownExceptionNames == null || thrownExceptionNames.length == 0) {
            return List.of();
        }

        return Arrays.asList(thrownExceptionNames);
    }

    /**
     * Returns the throwable types on a method.
     *
     * @param methodInfo the method info
     * @return the list of throwable type names
     */
    private static List<String> extractThrowableTypeNames(
            ExecutableElement methodInfo) {
        List<? extends TypeMirror> thrownExceptionTypes = methodInfo.getThrownTypes();
        if (thrownExceptionTypes == null) {
            return List.of();
        }
        return thrownExceptionTypes.stream().map(TypeMirror::toString).collect(Collectors.toList());
    }

    /**
     * Returns the list of parameter info through introspection.
     *
     * @param serviceTypeName the enclosing service type name
     * @param methodInfo the method info
     * @return the list of info elements/parameters
     */
    private static List<ElementInfo> createParameterInfo(
            TypeName serviceTypeName,
            MethodInfo methodInfo) {
        List<ElementInfo> result = new LinkedList<>();
        int count = 0;
        for (MethodParameterInfo ignore : methodInfo.getParameterInfo()) {
            count++;
            result.add(createParameterInfo(serviceTypeName, methodInfo, count));
        }
        return result;
    }

    /**
     * Returns the list of parameter info through annotation processing.
     *
     * @param serviceTypeName the enclosing service type name
     * @param methodInfo the method info
     * @return the list of info elements/parameters
     */
    private static List<ElementInfo> createParameterInfo(
            TypeName serviceTypeName,
            ExecutableElement methodInfo) {
        List<ElementInfo> result = new LinkedList<>();
        int count = 0;
        for (VariableElement ignore : methodInfo.getParameters()) {
            count++;
            result.add(createParameterInfo(serviceTypeName, methodInfo, count));
        }
        return result;
    }

    /**
     * Returns the element info given a method element parameter.
     *
     * @param serviceTypeName   the enclosing service type name
     * @param elemInfo          the method element info
     * @param elemOffset        the argument position - starts at 1 not 0
     * @return the element info
     */
    static DefaultElementInfo createParameterInfo(
            TypeName serviceTypeName,
            MethodInfo elemInfo,
            int elemOffset) {
        MethodParameterInfo paramInfo = elemInfo.getParameterInfo()[elemOffset - 1];
        String elemType = paramInfo.getTypeDescriptor().toString();
//        Set<QualifierAndValue> qualifiers = createQualifierAndValueSet(paramInfo.getAnnotationInfo());
        Set<AnnotationAndValue> annotations = createAnnotationAndValueSet(paramInfo.getAnnotationInfo());
        return DefaultElementInfo.builder()
                .serviceTypeName(serviceTypeName.name())
                .elementName("p" + elemOffset)
                .elementKind(elemInfo.isConstructor()
                                     ? InjectionPointInfo.ElementKind.CONSTRUCTOR : InjectionPointInfo.ElementKind.METHOD)
                .elementTypeName(elemType)
                .elementArgs(elemInfo.getParameterInfo().length)
                .elementOffset(elemOffset)
                .access(toAccess(elemInfo.getModifiers()))
                .staticDeclaration(isStatic(elemInfo.getModifiers()))
//                .qualifiers(qualifiers)
                .annotations(annotations)
                .build();
    }

    /**
     * Returns the element info given a method element parameter.
     *
     * @param serviceTypeName   the enclosing service type name
     * @param elemInfo          the method element info
     * @param elemOffset        the argument position - starts at 1 not 0
     * @return the element info
     */
    static DefaultElementInfo createParameterInfo(
            TypeName serviceTypeName,
            ExecutableElement elemInfo,
            int elemOffset) {
        VariableElement paramInfo = elemInfo.getParameters().get(elemOffset - 1);
        String elemType = paramInfo.asType().toString();
        //        Set<QualifierAndValue> qualifiers = createQualifierAndValueSet(paramInfo.getAnnotationInfo());
        Set<AnnotationAndValue> annotations = createAnnotationAndValueSet(paramInfo.getAnnotationMirrors());
        return DefaultElementInfo.builder()
                .serviceTypeName(serviceTypeName.name())
                .elementName("p" + elemOffset)
                .elementKind(elemInfo.getKind() == ElementKind.CONSTRUCTOR
                                     ? InjectionPointInfo.ElementKind.CONSTRUCTOR : InjectionPointInfo.ElementKind.METHOD)
                .elementTypeName(elemType)
                .elementArgs(elemInfo.getParameters().size())
                .elementOffset(elemOffset)
                .access(toAccess(elemInfo))
                .staticDeclaration(isStatic(elemInfo))
                //                .qualifiers(qualifiers)
                .annotations(annotations)
                .build();
    }

    /**
     * Returns the injection point info given a field element.
     *
     * @param serviceTypeName   the enclosing service type name
     * @param elemInfo          the field element info
     * @return the injection point info
     */
    static InjectionPointInfo createInjectionPointInfo(
            TypeName serviceTypeName,
            FieldInfo elemInfo) {
        AtomicReference<Boolean> isProviderWrapped = new AtomicReference<>();
        AtomicReference<Boolean> isListWrapped = new AtomicReference<>();
        AtomicReference<Boolean> isOptionalWrapped = new AtomicReference<>();
        String elemType = extractInjectionPointTypeInfo(elemInfo, isProviderWrapped, isListWrapped, isOptionalWrapped);
        Set<QualifierAndValue> qualifiers = createQualifierAndValueSet(elemInfo);
        return DefaultInjectionPointInfo.builder()
                .serviceTypeName(serviceTypeName.name())
                .elementName(elemInfo.getName())
                .elementKind(InjectionPointInfo.ElementKind.FIELD)
                .elementTypeName(elemType)
                .access(toAccess(elemInfo.getModifiers()))
                .staticDeclaration(isStatic(elemInfo.getModifiers()))
                .qualifiers(qualifiers)
                .optionalWrapped(isOptionalWrapped.get())
                .providerWrapped(isProviderWrapped.get())
                .listWrapped(isListWrapped.get())
                .build();
    }

    /**
     * Determines the meta parts making up {@link InjectionPointInfo}.
     *
     * @param paramInfo         the method info
     * @param isProviderWrapped set to indicate that the ip is a provided type
     * @param isListWrapped     set to indicate that the ip is a list type
     * @param isOptionalWrapped set to indicate that the ip is a optional type
     * @return the return type of the parameter type
     */
    static String extractInjectionPointTypeInfo(
            MethodParameterInfo paramInfo,
            AtomicReference<Boolean> isProviderWrapped,
            AtomicReference<Boolean> isListWrapped,
            AtomicReference<Boolean> isOptionalWrapped) {
        TypeSignature sig = Objects.requireNonNull(paramInfo).getTypeSignature();
        if (sig == null) {
            sig = Objects.requireNonNull(paramInfo.getTypeDescriptor());
        }
        return extractInjectionPointTypeInfo(sig, paramInfo.getMethodInfo(),
                                             isProviderWrapped, isListWrapped, isOptionalWrapped);
    }

    /**
     * Determines the meta parts making up {@link InjectionPointInfo}.
     *
     * @param elemInfo          the field info
     * @param isProviderWrapped set to indicate that the ip is a provided type
     * @param isListWrapped     set to indicate that the ip is a list type
     * @param isOptionalWrapped set to indicate that the ip is a optional type
     * @return the return type of the injection point
     */
    static String extractInjectionPointTypeInfo(
            FieldInfo elemInfo,
            AtomicReference<Boolean> isProviderWrapped,
            AtomicReference<Boolean> isListWrapped,
            AtomicReference<Boolean> isOptionalWrapped) {
        TypeSignature sig = Objects.requireNonNull(elemInfo).getTypeSignature();
        if (sig == null) {
            sig = Objects.requireNonNull(elemInfo.getTypeDescriptor());
        }
        return extractInjectionPointTypeInfo(sig, elemInfo.getClassInfo(),
                                             isProviderWrapped, isListWrapped, isOptionalWrapped);
    }

    private static ClassRefTypeSignature toClassRefSignature(
            TypeSignature sig,
            Object enclosingElem) {
        if (!(Objects.requireNonNull(sig) instanceof ClassRefTypeSignature)) {
            throw new ToolsException("unsupported type for " + sig + " in " + enclosingElem);
        }
        return (ClassRefTypeSignature) sig;
    }

    private static ClassRefTypeSignature toClassRefSignature(
            TypeArgument arg,
            Object enclosingElem) {
        return toClassRefSignature(arg.getTypeSignature(), enclosingElem);
    }

    /**
     * Determines the meta parts making up {@link InjectionPointInfo} for reflective processing.
     *
     * @param sig               the variable / element type
     * @param isProviderWrapped set to indicate that the ip is a provided type
     * @param isListWrapped     set to indicate that the ip is a list type
     * @param isOptionalWrapped set to indicate that the ip is an optional type
     * @return the return type of the injection point
     */
    static String extractInjectionPointTypeInfo(
            TypeSignature sig,
            Object enclosingElem,
            AtomicReference<Boolean> isProviderWrapped,
            AtomicReference<Boolean> isListWrapped,
            AtomicReference<Boolean> isOptionalWrapped) {
        ClassRefTypeSignature csig = toClassRefSignature(sig, enclosingElem);
        boolean isProvider = false;
        boolean isOptional = false;
        boolean isList = false;
        String varTypeName = csig.toString();
        boolean handled = csig.getTypeArguments().isEmpty();
        if (1 == csig.getTypeArguments().size()) {
            isProvider = isProviderType(csig);
            isOptional = isOptionalType(csig);
            if (isProvider || isOptional) {
                ClassRefTypeSignature typeArgSig = toClassRefSignature(csig.getTypeArguments().get(0), enclosingElem);
                varTypeName = typeArgSig.toString();
                handled = typeArgSig.getTypeArguments().isEmpty();
                if (!handled) {
                    String typeArgClassName = typeArgSig.getClassInfo().getName();
                    if (isOptionalType(typeArgClassName)
                            || isListType(typeArgClassName)
                            || typeArgClassName.equals(Collections.class.getName())) {
                        // not handled
                    } else if (isProviderType(typeArgClassName)) {
                        isProvider = true;
                        varTypeName = toClassRefSignature(typeArgSig.getTypeArguments().get(0), enclosingElem).toString();
                        handled = true;
                    } else {
                        // let's treat it as a supported type ... this is a bit of a gamble though.
                        handled = true;
                    }
                }
            } else if (isListType(csig.getClassInfo().getName())) {
                isList = true;
                ClassRefTypeSignature typeArgSig = toClassRefSignature(csig.getTypeArguments().get(0), enclosingElem);
                varTypeName = typeArgSig.toString();
                handled = typeArgSig.getTypeArguments().isEmpty();
                if (!handled && isProviderType(typeArgSig.getClassInfo().getName())) {
                    isProvider = true;
                    varTypeName = toClassRefSignature(typeArgSig.getTypeArguments().get(0), enclosingElem).toString();
                    handled = true;
                }
            }
        }

        isProviderWrapped.set(isProvider);
        isListWrapped.set(isList);
        isOptionalWrapped.set(isOptional);

        if (!handled && !isOptional) {
            throw new ToolsException("unsupported type for " + csig + " in " + enclosingElem);
        }

        return Objects.requireNonNull(componentTypeNameOf(varTypeName));
    }

    /**
     * Determines the meta parts making up {@link InjectionPointInfo} for annotation processing.
     *
     * @param typeElement       the variable / element type
     * @param isProviderWrapped set to indicate that the ip is a provided type
     * @param isListWrapped     set to indicate that the ip is a list type
     * @param isOptionalWrapped set to indicate that the ip is an optional type
     * @return the return type of the injection point
     */
    public static String extractInjectionPointTypeInfo(
            Element typeElement,
            AtomicReference<Boolean> isProviderWrapped,
            AtomicReference<Boolean> isListWrapped,
            AtomicReference<Boolean> isOptionalWrapped) {
        TypeMirror typeMirror = typeElement.asType();
        if (!(typeMirror instanceof DeclaredType)) {
            throw new ToolsException("unsupported type for " + typeElement.getEnclosingElement() + "."
                                             + typeElement + " with " + typeMirror.getKind());
        }
        DeclaredType declaredTypeMirror = (DeclaredType) typeMirror;
        TypeElement declaredClassType = ((TypeElement) declaredTypeMirror.asElement());

        boolean isProvider = false;
        boolean isOptional = false;
        boolean isList = false;
        String varTypeName = declaredTypeMirror.toString();
        boolean handled = false;
        if (Objects.nonNull(declaredClassType)) {
            handled = declaredTypeMirror.getTypeArguments().isEmpty();
            if (1 == declaredTypeMirror.getTypeArguments().size()) {
                isProvider = isProviderType(declaredClassType);
                isOptional = isOptionalType(declaredClassType);
                if (isProvider || isOptional) {
                    typeMirror = declaredTypeMirror.getTypeArguments().get(0);
                    if (typeMirror.getKind() == TypeKind.TYPEVAR) {
                        typeMirror = ((TypeVariable) typeMirror).getUpperBound();
                    }
                    declaredTypeMirror = (DeclaredType) typeMirror;
                    declaredClassType = ((TypeElement) declaredTypeMirror.asElement());
                    varTypeName = declaredClassType.toString();
                    handled = declaredTypeMirror.getTypeArguments().isEmpty();
                    if (!handled) {
                        if (isOptionalType(varTypeName)
                                || isListType(varTypeName)
                                || varTypeName.equals(Collections.class.getName())) {
                            // not handled
                        } else if (isProviderType(varTypeName)) {
                            isProvider = true;
                            varTypeName = declaredTypeMirror.getTypeArguments().get(0).toString();
                            handled = true;
                        } else {
                            // let's treat it as a supported type ... this is a bit of a gamble though.
                            handled = true;
                        }
                    }
                } else if (isListType(declaredClassType)) {
                    isList = true;
                    typeMirror = declaredTypeMirror.getTypeArguments().get(0);
                    if (typeMirror.getKind() == TypeKind.TYPEVAR) {
                        typeMirror = ((TypeVariable) typeMirror).getUpperBound();
                    }
                    declaredTypeMirror = (DeclaredType) typeMirror;
                    declaredClassType = ((TypeElement) declaredTypeMirror.asElement());
                    varTypeName = declaredClassType.toString();
                    handled = declaredTypeMirror.getTypeArguments().isEmpty();
                    if (!handled) {
                        if (isProviderType(declaredClassType)) {
                            isProvider = true;
                            typeMirror = declaredTypeMirror.getTypeArguments().get(0);
                            declaredTypeMirror = (DeclaredType) typeMirror;
                            declaredClassType = ((TypeElement) declaredTypeMirror.asElement());
                            varTypeName = declaredClassType.toString();
                            if (!declaredTypeMirror.getTypeArguments().isEmpty()) {
                                throw new ToolsException("unsupported generics usage for " + typeMirror + " in "
                                                                 + typeElement.getEnclosingElement());
                            }
                            handled = true;
                        }
                    }
                }
            }
        }

        isProviderWrapped.set(isProvider);
        isListWrapped.set(isList);
        isOptionalWrapped.set(isOptional);

        if (!handled && !isOptional) {
            throw new ToolsException("unsupported type for " + typeElement.getEnclosingElement()
                                             + "." + typeElement + " with " + typeMirror.getKind());
        }

        return Objects.requireNonNull(componentTypeNameOf(varTypeName));
    }

    /**
     * Determines whether the type is a {@link jakarta.inject.Provider} (or javax equiv) type.
     *
     * @param typeElement the type element to check
     * @return true if {@link jakarta.inject.Provider} or {@link InjectionPointProvider}
     */
    static boolean isProviderType(
            TypeElement typeElement) {
        return isProviderType(typeElement.getQualifiedName().toString());
    }

    private static boolean isProviderType(
            ClassRefTypeSignature sig) {
        return isProviderType(sig.getFullyQualifiedClassName());
    }

    /**
     * Determines whether the type is a {@link jakarta.inject.Provider} (or javax equiv) type.
     *
     * @param typeName the type name to check
     * @return true if {@link jakarta.inject.Provider} or {@link InjectionPointProvider}
     */
    public static boolean isProviderType(
            String typeName) {
        String type = translate(componentTypeNameOf(typeName));
        return (Provider.class.getName().equals(type)
                        || "javax.inject.Provider".equals(type)
                        || InjectionPointProvider.class.getName().equals(type));
    }

    /**
     * Determines whether the type is an {@link java.util.Optional} type.
     *
     * @param typeElement the type element to check
     * @return true if {@link java.util.Optional}
     */
    static boolean isOptionalType(
            TypeElement typeElement) {
        return isOptionalType(typeElement.getQualifiedName().toString());
    }

    private static boolean isOptionalType(
            ClassRefTypeSignature sig) {
        return isOptionalType(sig.getFullyQualifiedClassName());
    }

    /**
     * Determines whether the type is an {@link java.util.Optional} type.
     *
     * @param typeName the type name to check
     * @return true if {@link java.util.Optional}
     */
    static boolean isOptionalType(
            String typeName) {
        return Optional.class.getName().equals(componentTypeNameOf(typeName));
    }

    /**
     * Determines whether the type is an {@link java.util.List} type.
     *
     * @param typeElement the type element to check
     * @return true if {@link java.util.List}
     */
    static boolean isListType(
            TypeElement typeElement) {
        return isListType(typeElement.getQualifiedName().toString());
    }

    /**
     * Determines whether the type is an {@link java.util.List} type.
     *
     * @param typeName the type name to check
     * @return true if {@link java.util.List}
     */
    static boolean isListType(
            String typeName) {
        return List.class.getName().equals(componentTypeNameOf(typeName));
    }

    /**
     * Transposes "jakarta.*" from and/or to "javax.*".
     *
     * @param typeName the type name to transpose
     * @return the transposed value, or the same if not able to be transposed
     */
    public static String oppositeOf(
            String typeName) {
        boolean startsWithJakarta = typeName.startsWith("jakarta.");
        boolean startsWithJavax = !startsWithJakarta && typeName.startsWith("javax.");

        assert (startsWithJakarta || startsWithJavax);

        if (startsWithJakarta) {
            return typeName.replace("jakarta.", "javax.");
        } else {
            return typeName.replace("javax.", "jakarta.");
        }
    }

    /**
     * Transpose the type name to "jakarta" (if javax is used).
     *
     * @param typeName the type name
     * @return the normalized, transposed value or the original if doesn't contain javax.
     */
    static String translate(
            String typeName) {
        if (typeName == null || typeName.startsWith("jakarta.")) {
            return typeName;
        }

        return typeName.replace("javax.", "jakarta.");
    }

    /**
     * Returns true if the modifiers indicate this is a package private element.
     *
     * @param modifiers the modifiers
     * @return true if package private
     */
    static boolean isPackagePrivate(
            int modifiers) {
        return !Modifier.isPrivate(modifiers) && !Modifier.isProtected(modifiers) && !Modifier.isPublic(modifiers);
    }

    /**
     * Returns true if the modifiers indicate this is a private element.
     *
     * @param modifiers the modifiers
     * @return true if private
     */
    static boolean isPrivate(
            int modifiers) {
        return Modifier.isPrivate(modifiers);
    }

    /**
     * Returns true if the modifiers indicate this is a static element.
     *
     * @param modifiers the modifiers
     * @return true if static
     */
    static boolean isStatic(
            int modifiers) {
        return Modifier.isStatic(modifiers);
    }

    /**
     * Returns true if the element is static.
     *
     * @param element the element
     * @return true if static
     */
    public static boolean isStatic(
            Element element) {
        Set<javax.lang.model.element.Modifier> modifiers = element.getModifiers();
        return (modifiers != null) && modifiers.contains(javax.lang.model.element.Modifier.STATIC);
    }

    /**
     * Returns true if the modifiers indicate this is an abstract element.
     *
     * @param modifiers the modifiers
     * @return true if abstract
     */
    static boolean isAbstract(
            int modifiers) {
        return Modifier.isInterface(modifiers) || Modifier.isAbstract(modifiers);
    }

    /**
     * Returns true if the element is abstract.
     *
     * @param element the element
     * @return true if abstract
     */
    public static boolean isAbstract(
            Element element) {
        Set<javax.lang.model.element.Modifier> modifiers = element.getModifiers();
        return (modifiers != null) && modifiers.contains(javax.lang.model.element.Modifier.ABSTRACT);
    }

    /**
     * Converts the modifiers to an {@link io.helidon.pico.ElementInfo.Access} type.
     *
     * @param modifiers the moifiers
     * @return the access
     */
    static InjectionPointInfo.Access toAccess(
            int modifiers) {
        if (Modifier.isPublic(modifiers)) {
            return InjectionPointInfo.Access.PUBLIC;
        } else if (Modifier.isProtected(modifiers)) {
            return InjectionPointInfo.Access.PROTECTED;
        } else if (Modifier.isPrivate(modifiers)) {
            return InjectionPointInfo.Access.PRIVATE;
        } else {
            return InjectionPointInfo.Access.PACKAGE_PRIVATE;
        }
    }

    /**
     * Determines the access from an {@link javax.lang.model.element.Element} (from anno processing).
     *
     * @param element the element
     * @return the access
     */
    public static InjectionPointInfo.Access toAccess(
            Element element) {
        InjectionPointInfo.Access access = InjectionPointInfo.Access.PACKAGE_PRIVATE;
        Set<javax.lang.model.element.Modifier> modifiers = element.getModifiers();
        if (modifiers != null) {
            for (javax.lang.model.element.Modifier modifier : modifiers) {
                if (javax.lang.model.element.Modifier.PUBLIC == modifier) {
                    access = InjectionPointInfo.Access.PUBLIC;
                    break;
                } else if (javax.lang.model.element.Modifier.PROTECTED == modifier) {
                    access = InjectionPointInfo.Access.PROTECTED;
                    break;
                } else if (javax.lang.model.element.Modifier.PRIVATE == modifier) {
                    access = InjectionPointInfo.Access.PRIVATE;
                    break;
                }
            }
        }
        return access;
    }

    /**
     * Returns the kind of the method.
     *
     * @param methodInfo the method info
     * @return the kind
     */
    static ElementInfo.ElementKind toKind(
            MethodInfo methodInfo) {
        return (methodInfo.isConstructor())
                ? ElementInfo.ElementKind.CONSTRUCTOR : ElementInfo.ElementKind.METHOD;
    }

    /**
     * Returns the kind of the method.
     *
     * @param methodInfo the method info
     * @return the kind
     */
    static ElementInfo.ElementKind toKind(
            ExecutableElement methodInfo) {
        return (methodInfo.getKind() == ElementKind.CONSTRUCTOR)
                ? ElementInfo.ElementKind.CONSTRUCTOR : ElementInfo.ElementKind.METHOD;
    }

    /**
     * Checks whether the package name need to be declared.
     *
     * @param packageName the package name
     * @return true if the package name needs to be declared
     */
    public static boolean needToDeclarePackageUsage(
            String packageName) {
        return !(packageName.startsWith("java.")
                         || packageName.startsWith("sun.")
                         || packageName.toLowerCase().endsWith(".impl"));
    }

    /**
     * Checks whether the module name needs to be declared.
     *
     * @param moduleName the module name
     * @return true if the module name needs to be declared
     */
    public static boolean needToDeclareModuleUsage(
            String moduleName) {
        return (moduleName != null) && !moduleName.equals(ModuleInfoDescriptor.DEFAULT_MODULE_NAME)
                && !(moduleName.startsWith("java.")
                             || moduleName.startsWith("sun.")
                             || moduleName.startsWith("jakarta.inject")
                             || moduleName.startsWith(PicoServicesConfig.FQN));
    }

}
