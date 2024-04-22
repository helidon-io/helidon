/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tools;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
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

import io.helidon.common.processor.AnnotationFactory;
import io.helidon.common.processor.TypeFactory;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeValues;
import io.helidon.inject.api.ElementInfo;
import io.helidon.inject.api.InjectionPointInfo;
import io.helidon.inject.api.InjectionPointProvider;
import io.helidon.inject.api.Qualifier;
import io.helidon.inject.api.ServiceInfoCriteria;
import io.helidon.inject.runtime.Dependencies;

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
import jakarta.inject.Singleton;

import static io.helidon.common.types.TypeNames.COLLECTION;
import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.OPTIONAL;

/**
 * Generically handles generated artifact creation via APT.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public final class TypeTools {
    private TypeTools() {
    }

    /**
     * Converts the provided name to a type name path.
     *
     * @param typeName the type name to evaluate
     * @return the file path expression where dots are translated to file separators
     */
    public static String toFilePath(TypeName typeName) {
        return toFilePath(typeName, ".java");
    }

    /**
     * Converts the provided name to a type name path.
     *
     * @param typeName the type name to evaluate
     * @param fileType the file type, typically ".java"
     * @return the file path expression where dots are translated to file separators
     */
    public static String toFilePath(TypeName typeName,
                                    String fileType) {
        String className = typeName.className();
        String packageName = typeName.packageName();
        if (!CommonUtils.hasValue(packageName)) {
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
    static TypeName createTypeNameFromClassInfo(ClassInfo classInfo) {
        if (classInfo == null) {
            return null;
        }
        return TypeName.create(classInfo.getName());
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annotation the annotation
     * @return the new instance
     * @deprecated switch to using pure annotation processing wherever possible
     */
    @Deprecated
    static Annotation createAnnotationFromAnnotation(java.lang.annotation.Annotation annotation) {
        return Annotation.create(TypeName.create(annotation.annotationType()), extractValues(annotation));
    }

    /**
     * Creates an instance from reflective access. Note that this approach will only have visibility to the
     * {@link java.lang.annotation.RetentionPolicy#RUNTIME} type annotations.
     *
     * @param annotations the annotations on the type, method, or parameters
     * @return the new instance
     * @deprecated switch to use pure annotation processing instead of reflection
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public static List<Annotation> createAnnotationListFromAnnotations(java.lang.annotation.Annotation[] annotations) {
        if (annotations == null || annotations.length <= 0) {
            return List.of();
        }

        return Arrays.stream(annotations).map(TypeTools::createAnnotationFromAnnotation).collect(Collectors.toList());
    }

    /**
     * Extracts values from the annotation.
     *
     * @param annotation the annotation
     * @return the extracted value
     * @deprecated switch to use pure annotation processing instead of reflection
     */
    @Deprecated
    static Map<String, String> extractValues(java.lang.annotation.Annotation annotation) {
        Map<String, String> result = new HashMap<>();

        Class<? extends java.lang.annotation.Annotation> aClass = annotation.annotationType();
        Method[] declaredMethods = aClass.getDeclaredMethods();
        for (Method declaredMethod : declaredMethods) {
            String propertyName = declaredMethod.getName();
            try {
                Object value = declaredMethod.invoke(annotation);
                if (!(value instanceof Annotation)) {
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
    static Map<String, String> extractValues(AnnotationParameterValueList values) {
        return values.asMap().entrySet().stream()
                .map(e -> new AbstractMap
                        .SimpleEntry<>(e.getKey(), toString(e.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Converts "Optional<Whatever>" or "Provider<Whatever>" -> "Whatever".
     *
     * @param typeName the type name, that might use generics
     * @return the generics component type name
     */
    static TypeName componentTypeNameOf(String typeName) {
        int pos = typeName.indexOf('<');
        if (pos < 0) {
            return TypeName.create(typeName);
        }

        int lastPos = typeName.indexOf('>', pos);
        assert (lastPos > pos) : typeName;
        return TypeName.create(typeName.substring(pos + 1, lastPos));
    }

    /**
     * Converts "Optional<Whatever>" or "Provider<Whatever>" -> "Whatever".
     *
     * @param typeName the type name, that might use generics
     * @return the generics component type name
     */
    static TypeName componentTypeNameOf(TypeName typeName) {
        if (typeName.typeArguments().size() == 1) {
            return typeName.typeArguments().get(0);
        }
        return typeName;
    }

    /**
     * Creates a set of qualifiers based upon class info introspection.
     *
     * @param classInfo the class info
     * @return the qualifiers
     */
    static Set<Qualifier> createQualifierSet(ClassInfo classInfo) {
        return createQualifierSet(classInfo.getAnnotationInfo());
    }

    /**
     * Creates a set of qualifiers based upon method info introspection.
     *
     * @param methodInfo the method info
     * @return the qualifiers
     */
    static Set<Qualifier> createQualifierSet(MethodInfo methodInfo) {
        return createQualifierSet(methodInfo.getAnnotationInfo());
    }

    /**
     * Creates a set of qualifiers based upon field info introspection.
     *
     * @param fieldInfo the field info
     * @return the qualifiers
     */
    static Set<Qualifier> createQualifierSet(FieldInfo fieldInfo) {
        return createQualifierSet(fieldInfo.getAnnotationInfo());
    }

    /**
     * Creates a set of qualifiers given the owning element.
     *
     * @param annotationInfoList the list of annotations
     * @return the qualifiers
     */
    static Set<Qualifier> createQualifierSet(AnnotationInfoList annotationInfoList) {
        Set<Annotation> set = createAnnotationSetFromMetaAnnotation(annotationInfoList,
                                                                    TypeNames.JAKARTA_QUALIFIER);
        if (set.isEmpty()) {
            return Set.of();
        }

        return set.stream()
                .map(Qualifier::create)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates a set of qualifiers given the owning element.
     * @param type the element type (from anno processing)
     * @return the set of qualifiers that the owning element has
     */
    public static Set<Qualifier> createQualifierSet(Element type) {
        return createQualifierSet(type.getAnnotationMirrors());
    }

    /**
     * Creates a set of qualifiers given the owning element's annotation type mirror.
     * @param annoMirrors the annotation type mirrors (from anno processing)
     * @return the set of qualifiers that the owning element has
     */
    public static Set<Qualifier> createQualifierSet(List<? extends AnnotationMirror> annoMirrors) {
        Set<Qualifier> result = new LinkedHashSet<>();

        for (AnnotationMirror annoMirror : annoMirrors) {
            if (findAnnotationMirror(TypeNames.JAKARTA_QUALIFIER, annoMirror.getAnnotationType()
                    .asElement()
                    .getAnnotationMirrors())
                    .isPresent()) {

                String val = null;
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : annoMirror.getElementValues()
                        .entrySet()) {
                    if (e.getKey().toString().equals("value()")) {
                        val = String.valueOf(e.getValue().getValue());
                        break;
                    }
                }
                Qualifier.Builder qualifier = Qualifier.builder();
                qualifier.typeName(TypeFactory.createTypeName(annoMirror.getAnnotationType()).orElseThrow());
                if (val != null) {
                    qualifier.value(val);
                }
                result.add(qualifier.build());
            }
        }

        if (result.isEmpty()) {
            for (AnnotationMirror annoMirror : annoMirrors) {
                Optional<? extends AnnotationMirror> mirror = findAnnotationMirror(TypeNames.JAVAX_QUALIFIER,
                                                                                   annoMirror.getAnnotationType().asElement()
                                                                                           .getAnnotationMirrors());

                if (mirror.isPresent()) {
                    // there is an annotation meta-annotated with @javax.inject.Qualifier, let's add it to the list
                    Map<? extends ExecutableElement, ? extends AnnotationValue> annoValues = annoMirror.getElementValues();

                    Map<String, String> values = new HashMap<>();
                    annoValues.forEach((method, value) -> {
                        values.put(method.getSimpleName().toString(), String.valueOf(value.getValue()));
                    });

                    TypeName annot = TypeName.create(annoMirror.getAnnotationType().toString());
                    result.add(Qualifier.builder()
                                       .typeName(annot)
                                       .values(values)
                                       .build());
                }
            }
        }

        return result;
    }

    /**
     * Returns the annotations on the type having the meta annotation provided.
     *
     * @param processingEnv annotation processing environment
     * @param type          the type to inspect
     * @param metaAnnoType  the meta annotation type name
     * @return the annotations on the type having the meta annotation
     */
    public static List<Annotation> annotationsWithAnnotationOf(ProcessingEnvironment processingEnv,
                                                               TypeElement type,
                                                               String metaAnnoType) {
        Set<Annotation> annotations = AnnotationFactory.createAnnotations(type, processingEnv.getElementUtils());
        if (annotations.isEmpty()) {
            return List.of();
        }

        List<String> list = annotationsWithAnnotationsOfNoOpposite(type, metaAnnoType);
        if (list.isEmpty()) {
            list.addAll(annotationsWithAnnotationsOfNoOpposite(type, oppositeOf(metaAnnoType)));
        }

        return annotations.stream()
                .filter(it -> list.contains(it.typeName().name()))
                .collect(Collectors.toList());
    }

    /**
     * Creates a set of annotations based upon class info introspection.
     *
     * @param classInfo the class info
     * @return the annotation value set
     */
    static Set<Annotation> createAnnotationSet(ClassInfo classInfo) {
        return classInfo.getAnnotationInfo()
                .stream()
                .map(TypeTools::createAnnotation)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates a set of annotations given the owning element.
     *
     * @param annotationInfoList the list of annotations
     * @return the annotation and value set
     */
    public static Set<Annotation> createAnnotationSet(AnnotationInfoList annotationInfoList) {
        return annotationInfoList.stream()
                .map(TypeTools::createAnnotation)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Creates an annotation and value from introspection.
     *
     * @param annotationInfo the introspected annotation
     * @return the annotation and value
     */
    static Annotation createAnnotation(AnnotationInfo annotationInfo) {
        TypeName annoTypeName = createTypeNameFromClassInfo(annotationInfo.getClassInfo());
        return Annotation.create(annoTypeName, extractValues(annotationInfo.getParameterValues()));
    }

    /**
     * All annotations on every public method and the given type, including all of its methods.
     *
     * @param classInfo the classInfo of the enclosing class type
     * @return the complete set of annotations
     */
    static Set<Annotation> gatherAllAnnotationsUsedOnPublicNonStaticMethods(ClassInfo classInfo) {
        Set<Annotation> result = new LinkedHashSet<>(createAnnotationSet(classInfo));
        classInfo.getMethodAndConstructorInfo()
                .filter(m -> !m.isPrivate() && !m.isStatic())
                .forEach(mi -> result.addAll(createAnnotationSet(mi.getAnnotationInfo())));
        return result;
    }

    /**
     * All annotations on every public method and the given type, including all of its methods.
     *
     * @param serviceTypeElement the service type element of the enclosing class type
     * @param processEnv the processing environment
     * @return the complete set of annotations
     */
    static Set<Annotation> gatherAllAnnotationsUsedOnPublicNonStaticMethods(TypeElement serviceTypeElement,
                                                                            ProcessingEnvironment processEnv) {
        Elements elementUtils = processEnv.getElementUtils();
        Set<Annotation> result = new LinkedHashSet<>();
        AnnotationFactory.createAnnotations(serviceTypeElement, processEnv.getElementUtils()).forEach(anno -> {
            result.add(anno);
            TypeElement typeElement = elementUtils.getTypeElement(anno.typeName().name());
            if (typeElement != null) {
                typeElement.getAnnotationMirrors()
                        .forEach(am -> result.add(AnnotationFactory.createAnnotation(am, processEnv.getElementUtils())));
            }
        });
        serviceTypeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .filter(e -> !e.getModifiers().contains(javax.lang.model.element.Modifier.PRIVATE))
                .filter(e -> !e.getModifiers().contains(javax.lang.model.element.Modifier.STATIC))
                .map(ExecutableElement.class::cast)
                .forEach(exec -> {
                    exec.getAnnotationMirrors().forEach(am -> {
                        Annotation anno = AnnotationFactory.createAnnotation(am, processEnv.getElementUtils());
                        result.add(anno);
                        TypeElement typeElement = elementUtils.getTypeElement(anno.typeName().name());
                        if (typeElement != null) {
                            typeElement.getAnnotationMirrors()
                                    .forEach(am2 -> result.add(AnnotationFactory.createAnnotation(am2,
                                                                                                  processEnv.getElementUtils())));
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
    static Set<Annotation> createAnnotationSetFromMetaAnnotation(AnnotationInfoList annotationInfoList,
                                                                 String metaAnnoType) {
        // resolve meta annotations uses the opposite of already
        AnnotationInfoList list = resolveMetaAnnotations(annotationInfoList, metaAnnoType);
        if (list == null) {
            return Set.of();
        }

        Set<Annotation> result = new LinkedHashSet<>();
        for (AnnotationInfo ai : list) {
            TypeName annotationType = translate(TypeName.create(ai.getName()));
            AnnotationParameterValueList values = ai.getParameterValues();
            if (values == null || values.isEmpty()) {
                result.add(Annotation.create(annotationType));
            } else if (values.size() > 1) {
                Map<String, String> strVals = extractValues(values);
                result.add(Annotation.create(annotationType, strVals));
            } else {
                Object value = values.get(0).getValue();
                String strValue = (value != null) ? String.valueOf(value) : null;
                Annotation annotationAndValue = (strValue == null)
                        ? Annotation.create(annotationType)
                        : Annotation.create(annotationType, strValue);
                result.add(annotationAndValue);
            }
        }
        return result;
    }

    /**
     * Extracts all the scope type names from the provided type.
     *
     * @param processingEnv annotation processing environment
     * @param type the type to analyze
     * @return the list of all scope type annotation and values
     */
    public static List<Annotation> toScopeAnnotations(ProcessingEnvironment processingEnv, TypeElement type) {
        List<Annotation> scopeAnnotations = annotationsWithAnnotationOf(processingEnv, type, TypeNames.JAKARTA_SCOPE);
        if (scopeAnnotations.isEmpty()) {
            scopeAnnotations = annotationsWithAnnotationOf(processingEnv, type, TypeNames.JAKARTA_CDI_NORMAL_SCOPE);
        }

        if (Options.isOptionEnabled(Options.TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE)) {
            boolean hasApplicationScope = scopeAnnotations.stream()
                    .map(it -> it.typeName().name())
                    .anyMatch(it -> it.equals(TypeNames.JAKARTA_APPLICATION_SCOPED)
                            || it.equals(TypeNames.JAVAX_APPLICATION_SCOPED));
            if (hasApplicationScope) {
                scopeAnnotations.add(Annotation.create(Singleton.class));
            }
        }

        return scopeAnnotations;
    }

    /**
     * Extract the scope name from the given introspected class.
     *
     * @param classInfo the introspected class
     * @return the scope name, or null if no scope found
     */
    static TypeName extractScopeTypeName(ClassInfo classInfo) {
        AnnotationInfoList list = resolveMetaAnnotations(classInfo.getAnnotationInfo(), TypeNames.JAKARTA_SCOPE);
        if (list == null) {
            return null;
        }

        return translate(TypeName.create(CommonUtils.first(list, false).getName()));
    }

    /**
     * Returns the methods that have an annotation.
     *
     * @param classInfo the class info
     * @param annoType  the annotation
     * @return the methods with the annotation
     */
    static MethodInfoList methodsAnnotatedWith(ClassInfo classInfo,
                                               String annoType) {
        MethodInfoList result = new MethodInfoList();
        classInfo.getMethodInfo()
                .stream()
                .filter(methodInfo -> hasAnnotation(methodInfo, annoType))
                .forEach(result::add);
        return result;
    }

    /**
     * Returns true if the method has an annotation.
     *
     * @param methodInfo the method info
     * @param annoTypeName the annotation to check
     * @return true if the method has the annotation
     */
    static boolean hasAnnotation(MethodInfo methodInfo,
                                 String annoTypeName) {
        return methodInfo.hasAnnotation(annoTypeName) || methodInfo.hasAnnotation(oppositeOf(annoTypeName));
    }

    /**
     * Returns true if the method has an annotation.
     *
     * @param fieldInfo the field info
     * @param annoTypeName the annotation to check
     * @return true if the method has the annotation
     */
    static boolean hasAnnotation(FieldInfo fieldInfo,
                                 String annoTypeName) {
        return fieldInfo.hasAnnotation(annoTypeName) || fieldInfo.hasAnnotation(oppositeOf(annoTypeName));
    }

    /**
     * Resolves meta annotations.
     *
     * @param annoList the complete set of annotations
     * @param metaAnnoType the meta-annotation to filter on
     * @return the filtered set having the meta-annotation
     */
    static AnnotationInfoList resolveMetaAnnotations(AnnotationInfoList annoList,
                                                     String metaAnnoType) {
        if (annoList == null) {
            return null;
        }

        AnnotationInfoList result = null;
        String metaName2 = oppositeOf(metaAnnoType);
        for (AnnotationInfo ai : annoList) {
            ClassInfo aiClassInfo = ai.getClassInfo();
            if (aiClassInfo.hasAnnotation(metaAnnoType) || aiClassInfo.hasAnnotation(metaName2)) {
                if (result == null) {
                    result = new AnnotationInfoList();
                }
                result.add(ai);
            }
        }
        return result;
    }

    /**
     * Determines the {@link jakarta.inject.Provider} or {@link InjectionPointProvider} contract type.
     *
     * @param classInfo class info
     * @return the provided type
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    static String providesContractType(ClassInfo classInfo) {
        Set<String> candidates = new LinkedHashSet<>();

        ClassInfo nxt = classInfo;
        ToolsException firstTe = null;
        while (nxt != null) {
            ClassTypeSignature sig = nxt.getTypeSignature();
            List<ClassRefTypeSignature> superInterfaces = (sig != null) ? sig.getSuperinterfaceSignatures() : null;
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
            throw new IllegalStateException("Unsupported case where provider provides more than one type: " + classInfo);
        }

        if (!candidates.isEmpty()) {
            return CommonUtils.first(candidates, false);
        }

        if (firstTe != null) {
            throw firstTe;
        }

        return null;
    }

    /**
     * Determines the {@link jakarta.inject.Provider} contract type.
     *
     * @param sig class type signature
     * @return the provided type
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    static TypeName providesContractType(TypeSignature sig) {
        if (sig instanceof ClassRefTypeSignature csig) {
            if (isProviderType(TypeName.create(csig.getFullyQualifiedClassName()))) {
                TypeArgument typeArg = csig.getTypeArguments().get(0);
                if (!(typeArg.getTypeSignature() instanceof ClassRefTypeSignature)) {
                    throw new IllegalStateException("Unsupported signature: " + sig);
                }
                return TypeName.create(typeArg.toString());
            }
        }
        return null;
    }

    /**
     * Returns the injection point info given a method element.
     *
     * @param elemInfo the method element info
     * @return the injection point info
     */
    static InjectionPointInfo createInjectionPointInfo(MethodInfo elemInfo) {
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
    static InjectionPointInfo createInjectionPointInfo(TypeName serviceTypeName,
                                                       MethodInfo elemInfo,
                                                       Integer elemOffset) {
        TypeName elemType;
        Set<Qualifier> qualifiers;
        Set<Annotation> annotations;
        AtomicReference<Boolean> isProviderWrapped = new AtomicReference<>();
        AtomicReference<Boolean> isListWrapped = new AtomicReference<>();
        AtomicReference<Boolean> isOptionalWrapped = new AtomicReference<>();
        String ipName;
        TypeName ipType;
        if (elemOffset != null) {
            MethodParameterInfo paramInfo = elemInfo.getParameterInfo()[elemOffset - 1];
            elemType = extractInjectionPointTypeInfo(paramInfo, isProviderWrapped, isListWrapped, isOptionalWrapped);
            TypeSignature typeSignature = paramInfo.getTypeSignature();
            typeSignature = typeSignature == null ? paramInfo.getTypeDescriptor() : typeSignature;
            ipType = TypeName.create(typeSignature.toString());
            qualifiers = createQualifierSet(paramInfo.getAnnotationInfo());
            annotations = createAnnotationSet(paramInfo.getAnnotationInfo());
            ipName = paramInfo.getName();
            if (ipName == null) {
                ipName = "arg" + elemOffset;
            }
        } else {
            elemType = TypeName.create(elemInfo.getTypeDescriptor().getResultType().toString());
            ipType = elemType;
            qualifiers = createQualifierSet(elemInfo);
            annotations = createAnnotationSet(elemInfo.getAnnotationInfo());
            ipName = elemInfo.getName();
            if (ipName == null) {
                ipName = "arg";
            }
        }

        String elemName = elemInfo.isConstructor()
                ? InjectionPointInfo.CONSTRUCTOR : elemInfo.getName();
        int elemArgs = elemInfo.getParameterInfo().length;
        AccessModifier access = toAccess(elemInfo.getModifiers());
        String packageName = serviceTypeName.packageName();
        ServiceInfoCriteria serviceInfo = ServiceInfoCriteria.builder()
                .serviceTypeName(elemType)
                .build();
        return InjectionPointInfo.builder()
                .baseIdentity(Dependencies.toMethodBaseIdentity(elemName, elemArgs, access, packageName))
                .id(Dependencies.toMethodIdentity(elemName, elemArgs, elemOffset, access, packageName))
                .dependencyToServiceInfo(serviceInfo)
                .serviceTypeName(serviceTypeName)
                .elementName(elemName)
                .elementKind(elemInfo.isConstructor()
                                     ? io.helidon.inject.api.ElementKind.CONSTRUCTOR : io.helidon.inject.api.ElementKind.METHOD)
                .elementTypeName(elemType)
                .elementArgs(elemArgs)
                .elementOffset(elemOffset)
                .access(access)
                .staticDeclaration(isStatic(elemInfo.getModifiers()))
                .qualifiers(qualifiers)
                .annotations(annotations)
                .optionalWrapped(isOptionalWrapped.get())
                .providerWrapped(isProviderWrapped.get())
                .listWrapped(isListWrapped.get())
                .ipName(ipName)
                .ipType(ipType)
                .build();
    }

    /**
     * Returns the method info given a method from introspection.
     *
     * @param methodInfo        the method element info
     * @param serviceLevelAnnos the annotation at the class level that should be inherited at the method level
     * @return the method info
     */
    static MethodElementInfo createMethodElementInfo(MethodInfo methodInfo,
                                                     Set<Annotation> serviceLevelAnnos) {
        TypeName serviceTypeName = createTypeNameFromClassInfo(methodInfo.getClassInfo());
        TypeName elemType = TypeName.create(methodInfo.getTypeDescriptor().getResultType().toString());
        Set<Qualifier> qualifiers = createQualifierSet(methodInfo);
        Set<Annotation> annotations = createAnnotationSet(methodInfo.getAnnotationInfo());
        if (serviceLevelAnnos != null) {
            annotations.addAll(serviceLevelAnnos);
        }
        List<String> throwables = extractThrowableTypeNames(methodInfo);
        List<ElementInfo> parameters = createParameterInfo(serviceTypeName, methodInfo);
        return MethodElementInfo.builder()
                .serviceTypeName(serviceTypeName)
                .elementName(methodInfo.isConstructor()
                                     ? InjectionPointInfo.CONSTRUCTOR : methodInfo.getName())
                .elementKind(methodInfo.isConstructor()
                                     ? io.helidon.inject.api.ElementKind.CONSTRUCTOR : io.helidon.inject.api.ElementKind.METHOD)
                .elementTypeName(elemType)
                .elementArgs(methodInfo.getParameterInfo().length)
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
    static MethodElementInfo createMethodElementInfo(ProcessingEnvironment processingEnv,
                                                     TypeElement serviceTypeElement,
                                                     ExecutableElement ee,
                                                     Set<Annotation> serviceLevelAnnos) {
        TypeName serviceTypeName = TypeFactory.createTypeName(serviceTypeElement).orElseThrow();
        TypeName elemType = TypeName.create(ee.getReturnType().toString());
        Set<Qualifier> qualifiers = createQualifierSet(ee);
        Set<Annotation> annotations = AnnotationFactory.createAnnotations(ee, processingEnv.getElementUtils());
        if (serviceLevelAnnos != null) {
            annotations.addAll(serviceLevelAnnos);
        }
        List<String> throwables = extractThrowableTypeNames(ee);
        List<ElementInfo> parameters = createParameterInfo(processingEnv, serviceTypeName, ee);
        return MethodElementInfo.builder()
                .serviceTypeName(serviceTypeName)
                .elementName((ee.getKind() == ElementKind.CONSTRUCTOR)
                                     ? InjectionPointInfo.CONSTRUCTOR : ee.getSimpleName().toString())
                .elementKind((ee.getKind() == ElementKind.CONSTRUCTOR)
                                     ? io.helidon.inject.api.ElementKind.CONSTRUCTOR : io.helidon.inject.api.ElementKind.METHOD)
                .elementTypeName(elemType)
                .elementArgs(ee.getParameters().size())
                .access(toAccess(ee))
                .staticDeclaration(isStatic(ee))
                .qualifiers(qualifiers)
                .annotations(annotations)
                .throwableTypeNames(throwables)
                .parameterInfo(parameters)
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
    static ElementInfo createParameterInfo(TypeName serviceTypeName,
                                           MethodInfo elemInfo,
                                           int elemOffset) {
        MethodParameterInfo paramInfo = elemInfo.getParameterInfo()[elemOffset - 1];
        TypeName elemType = TypeName.create(paramInfo.getTypeDescriptor().toString());
        Set<Annotation> annotations = createAnnotationSet(paramInfo.getAnnotationInfo());
        return ElementInfo.builder()
                .serviceTypeName(serviceTypeName)
                .elementName("p" + elemOffset)
                .elementKind(elemInfo.isConstructor()
                                     ? io.helidon.inject.api.ElementKind.CONSTRUCTOR : io.helidon.inject.api.ElementKind.METHOD)
                .elementTypeName(elemType)
                .elementArgs(elemInfo.getParameterInfo().length)
                .elementOffset(elemOffset)
                .access(toAccess(elemInfo.getModifiers()))
                .staticDeclaration(isStatic(elemInfo.getModifiers()))
                .annotations(annotations)
                .build();
    }

    /**
     * Returns the element info given a method element parameter.
     *
     * @param processingEnv     the processing environment
     * @param serviceTypeName   the enclosing service type name
     * @param elemInfo          the method element info
     * @param elemOffset        the argument position - starts at 1 not 0
     * @return the element info
     */
    static ElementInfo createParameterInfo(ProcessingEnvironment processingEnv,
                                           TypeName serviceTypeName,
                                           ExecutableElement elemInfo,
                                           int elemOffset) {
        VariableElement paramInfo = elemInfo.getParameters().get(elemOffset - 1);
        TypeName elemTypeName = TypeFactory.createTypeName(paramInfo).orElseThrow();
        Set<Annotation> annotations = AnnotationFactory.createAnnotations(paramInfo.getAnnotationMirrors(),
                                                                          processingEnv.getElementUtils());
        return ElementInfo.builder()
                .serviceTypeName(serviceTypeName)
                .elementName("p" + elemOffset)
                .elementKind(elemInfo.getKind() == ElementKind.CONSTRUCTOR
                                     ? io.helidon.inject.api.ElementKind.CONSTRUCTOR : io.helidon.inject.api.ElementKind.METHOD)
                .elementTypeName(elemTypeName)
                .elementArgs(elemInfo.getParameters().size())
                .elementOffset(elemOffset)
                .access(toAccess(elemInfo))
                .staticDeclaration(isStatic(elemInfo))
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
    static InjectionPointInfo createInjectionPointInfo(TypeName serviceTypeName,
                                                       FieldInfo elemInfo) {
        AtomicReference<Boolean> isProviderWrapped = new AtomicReference<>();
        AtomicReference<Boolean> isListWrapped = new AtomicReference<>();
        AtomicReference<Boolean> isOptionalWrapped = new AtomicReference<>();
        TypeName elemType = extractInjectionPointTypeInfo(elemInfo, isProviderWrapped, isListWrapped, isOptionalWrapped);
        Set<Qualifier> qualifiers = createQualifierSet(elemInfo);
        String elemName = elemInfo.getName();
        String id = Dependencies.toFieldIdentity(elemName, serviceTypeName.packageName());
        ServiceInfoCriteria serviceInfo = ServiceInfoCriteria.builder()
                .serviceTypeName(elemType)
                .build();
        return InjectionPointInfo.builder()
                .baseIdentity(id)
                .id(id)
                .dependencyToServiceInfo(serviceInfo)
                .serviceTypeName(serviceTypeName)
                .elementName(elemInfo.getName())
                .elementKind(io.helidon.inject.api.ElementKind.FIELD)
                .elementTypeName(elemType)
                .access(toAccess(elemInfo.getModifiers()))
                .staticDeclaration(isStatic(elemInfo.getModifiers()))
                .qualifiers(qualifiers)
                .optionalWrapped(isOptionalWrapped.get())
                .providerWrapped(isProviderWrapped.get())
                .listWrapped(isListWrapped.get())
                .ipName(elemInfo.getName())
                .ipType(elemType)
                .build();
    }

    /**
     * Determines the meta parts making up {@link InjectionPointInfo}.
     *
     * @param paramInfo         the method info
     * @param isProviderWrapped set to indicate that the ip is a provided type
     * @param isListWrapped     set to indicate that the ip is a list type
     * @param isOptionalWrapped set to indicate that the ip is am optional type
     * @return the return type of the parameter type
     */
    static TypeName extractInjectionPointTypeInfo(MethodParameterInfo paramInfo,
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
     * @param isOptionalWrapped set to indicate that the ip is an optional type
     * @return the return type of the injection point
     */
    static TypeName extractInjectionPointTypeInfo(FieldInfo elemInfo,
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

    /**
     * Determines the meta parts making up {@link InjectionPointInfo} for reflective processing.
     *
     * @param sig               the variable / element type
     * @param isProviderWrapped set to indicate that the ip is a provided type
     * @param isListWrapped     set to indicate that the ip is a list type
     * @param isOptionalWrapped set to indicate that the ip is an optional type
     * @return the return type of the injection point
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    static TypeName extractInjectionPointTypeInfo(TypeSignature sig,
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
                    TypeName typeArgType = TypeName.create(typeArgSig.getClassInfo().getName());
                    if (typeArgType.isOptional()
                            || typeArgType.isList()
                            || typeArgType.equals(COLLECTION)) {
                        // not handled
                    } else if (isProviderType(typeArgType)) {
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
                if (!handled && isProviderType(TypeName.create(typeArgSig.getClassInfo().getName()))) {
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
            throw new IllegalStateException("Unsupported type for " + csig + " in " + enclosingElem);
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
     * @throws IllegalStateException thrown if internal state inconsistencies are found
     */
    public static TypeName extractInjectionPointTypeInfo(Element typeElement,
                                                         AtomicReference<Boolean> isProviderWrapped,
                                                         AtomicReference<Boolean> isListWrapped,
                                                         AtomicReference<Boolean> isOptionalWrapped) {
        TypeMirror typeMirror = typeElement.asType();
        if (!(typeMirror instanceof DeclaredType declaredTypeMirror)) {
            throw new IllegalStateException("Unsupported type for " + typeElement.getEnclosingElement() + "."
                                                    + typeElement + " with " + typeMirror.getKind());
        }
        TypeElement declaredClassType = ((TypeElement) declaredTypeMirror.asElement());

        boolean isProvider = false;
        boolean isOptional = false;
        boolean isList = false;
        String varTypeName = declaredTypeMirror.toString();
        boolean handled = false;
        if (declaredClassType != null) {
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
                        } else if (isProviderType(TypeName.create(varTypeName))) {
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
                                throw new IllegalStateException("Unsupported generics usage for " + typeMirror + " in "
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
            throw new IllegalStateException("Unsupported type for " + typeElement.getEnclosingElement()
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
    static boolean isProviderType(TypeElement typeElement) {
        return isProviderType(TypeName.create(typeElement.getQualifiedName().toString()));
    }

    /**
     * Determines whether the type is a {@link jakarta.inject.Provider} (or javax equiv) type.
     *
     * @param typeName the type name to check
     * @return true if {@link jakarta.inject.Provider} or {@link InjectionPointProvider}
     */
    public static boolean isProviderType(TypeName typeName) {
        TypeName type = translate(componentTypeNameOf(typeName));
        return TypeNames.JAKARTA_PROVIDER_TYPE.equals(type)
                || TypeNames.JAVAX_PROVIDER_TYPE.equals(type)
                || TypeNames.INJECTION_POINT_PROVIDER_TYPE.equals(type);
    }

    /**
     * Determines whether the type is an {@link java.util.Optional} type.
     *
     * @param typeElement the type element to check
     * @return true if {@link java.util.Optional}
     */
    static boolean isOptionalType(TypeElement typeElement) {
        return isOptionalType(typeElement.getQualifiedName().toString());
    }

    /**
     * Determines whether the type is an {@link java.util.Optional} type.
     *
     * @param typeName the type name to check
     * @return true if {@link java.util.Optional}
     */
    static boolean isOptionalType(String typeName) {
        return OPTIONAL.equals(componentTypeNameOf(typeName));
    }

    /**
     * Determines whether the type is an {@link java.util.List} type.
     *
     * @param typeElement the type element to check
     * @return true if {@link java.util.List}
     */
    static boolean isListType(TypeElement typeElement) {
        return isListType(typeElement.getQualifiedName().toString());
    }

    /**
     * Determines whether the type is an {@link java.util.List} type.
     *
     * @param typeName the type name to check
     * @return true if {@link java.util.List}
     */
    static boolean isListType(String typeName) {
        return LIST.equals(componentTypeNameOf(typeName));
    }

    /**
     * Transposes {@value TypeNames#PREFIX_JAKARTA} from and/or to {@value TypeNames#PREFIX_JAVAX}.
     *
     * @param typeName the type name to transpose
     * @return the transposed value, or the same if not able to be transposed
     */
    public static String oppositeOf(String typeName) {
        boolean startsWithJakarta = typeName.startsWith(TypeNames.PREFIX_JAKARTA);
        boolean startsWithJavax = !startsWithJakarta && typeName.startsWith(TypeNames.PREFIX_JAVAX);

        assert (startsWithJakarta || startsWithJavax) : typeName;

        if (startsWithJakarta) {
            return typeName.replace(TypeNames.PREFIX_JAKARTA, TypeNames.PREFIX_JAVAX);
        } else {
            return typeName.replace(TypeNames.PREFIX_JAVAX, TypeNames.PREFIX_JAKARTA);
        }
    }

    /**
     * Transpose the type name to "jakarta" (if javax is used).
     *
     * @param typeName the type name
     * @return the normalized, transposed value or the original if it doesn't contain javax
     */
    static TypeName translate(TypeName typeName) {
        if (typeName.packageName().startsWith(TypeNames.PREFIX_JAKARTA)) {
            return typeName;
        }

        return TypeName.builder(typeName)
                .packageName(typeName.packageName().replace(TypeNames.PREFIX_JAVAX, TypeNames.PREFIX_JAKARTA))
                .build();
    }

    /**
     * Returns true if the modifiers indicate this is a package private element.
     *
     * @param modifiers the modifiers
     * @return true if package private
     */
    static boolean isPackagePrivate(int modifiers) {
        return !Modifier.isPrivate(modifiers) && !Modifier.isProtected(modifiers) && !Modifier.isPublic(modifiers);
    }

    /**
     * Returns true if the modifiers indicate this is a private element.
     *
     * @param modifiers the modifiers
     * @return true if private
     */
    static boolean isPrivate(int modifiers) {
        return Modifier.isPrivate(modifiers);
    }

    /**
     * Returns true if the modifiers indicate this is a static element.
     *
     * @param modifiers the modifiers
     * @return true if static
     */
    static boolean isStatic(int modifiers) {
        return Modifier.isStatic(modifiers);
    }

    /**
     * Returns true if the element is static.
     *
     * @param element the element
     * @return true if static
     */
    public static boolean isStatic(Element element) {
        Set<javax.lang.model.element.Modifier> modifiers = element.getModifiers();
        return (modifiers != null) && modifiers.contains(javax.lang.model.element.Modifier.STATIC);
    }

    /**
     * Returns true if the modifiers indicate this is an abstract element.
     *
     * @param modifiers the modifiers
     * @return true if abstract
     */
    static boolean isAbstract(int modifiers) {
        return Modifier.isInterface(modifiers) || Modifier.isAbstract(modifiers);
    }

    /**
     * Returns true if the element is abstract.
     *
     * @param element the element
     * @return true if abstract
     */
    public static boolean isAbstract(Element element) {
        Set<javax.lang.model.element.Modifier> modifiers = element.getModifiers();
        return (modifiers != null) && modifiers.contains(javax.lang.model.element.Modifier.ABSTRACT);
    }

    /**
     * Converts the modifiers to an {@link AccessModifier} type.
     *
     * @param modifiers the modifiers
     * @return the access
     */
    static AccessModifier toAccess(int modifiers) {
        if (Modifier.isPublic(modifiers)) {
            return AccessModifier.PUBLIC;
        } else if (Modifier.isProtected(modifiers)) {
            return AccessModifier.PROTECTED;
        } else if (Modifier.isPrivate(modifiers)) {
            return AccessModifier.PRIVATE;
        } else {
            return AccessModifier.PACKAGE_PRIVATE;
        }
    }

    /**
     * Converts the modifiers to an {@link AccessModifier} type.
     *
     * @param modifiers the modifiers
     * @return the access
     */
    public static AccessModifier toAccess(Set<String> modifiers) {
        if (modifiers.contains(TypeValues.MODIFIER_PROTECTED)) {
            return AccessModifier.PROTECTED;
        } else if (modifiers.contains(TypeValues.MODIFIER_PRIVATE)) {
            return AccessModifier.PRIVATE;
        } else if (modifiers.contains(TypeValues.MODIFIER_PUBLIC)) {
            return AccessModifier.PUBLIC;
        }
        return AccessModifier.PACKAGE_PRIVATE;
    }

    /**
     * Determines the access from an {@link javax.lang.model.element.Element} (from anno processing).
     *
     * @param element the element
     * @return the access
     */
    public static AccessModifier toAccess(Element element) {
        AccessModifier access = AccessModifier.PACKAGE_PRIVATE;
        Set<javax.lang.model.element.Modifier> modifiers = element.getModifiers();
        if (modifiers != null) {
            for (javax.lang.model.element.Modifier modifier : modifiers) {
                if (javax.lang.model.element.Modifier.PUBLIC == modifier) {
                    access = AccessModifier.PUBLIC;
                    break;
                } else if (javax.lang.model.element.Modifier.PROTECTED == modifier) {
                    access = AccessModifier.PROTECTED;
                    break;
                } else if (javax.lang.model.element.Modifier.PRIVATE == modifier) {
                    access = AccessModifier.PRIVATE;
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
    static ElementKind toKind(MethodInfo methodInfo) {
        return (methodInfo.isConstructor())
                ? ElementKind.CONSTRUCTOR : ElementKind.METHOD;
    }

    /**
     * Returns the kind of the method.
     *
     * @param methodInfo the method info
     * @return the kind
     */
    static ElementKind toKind(ExecutableElement methodInfo) {
        return (methodInfo.getKind() == ElementKind.CONSTRUCTOR)
                ? ElementKind.CONSTRUCTOR : ElementKind.METHOD;
    }

    /**
     * Checks whether the package name need to be declared.
     *
     * @param packageName the package name
     * @return true if the package name needs to be declared
     */
    public static boolean needToDeclarePackageUsage(String packageName) {
        return !(
                packageName.startsWith("java.")
                        || packageName.startsWith("sun.")
                        || packageName.toLowerCase().endsWith(".impl")
                        || packageName.equals("io.helidon.inject.api"));
    }

    /**
     * Checks whether the module name needs to be declared.
     *
     * @param moduleName the module name
     * @return true if the module name needs to be declared
     */
    public static boolean needToDeclareModuleUsage(String moduleName) {
        return (moduleName != null) && !moduleName.equals(ModuleInfoDescriptorBlueprint.DEFAULT_MODULE_NAME)
                && !(
                moduleName.startsWith("java.")
                        || moduleName.startsWith("sun.")
                        || moduleName.startsWith("jakarta.inject")
                        || moduleName.startsWith("io.helidon.inject"));
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

    private static String toString(AnnotationParameterValue val) {
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

    private static List<String> annotationsWithAnnotationsOfNoOpposite(TypeElement type,
                                                                       String annotation) {
        List<String> list = new ArrayList<>();
        type.getAnnotationMirrors()
                .forEach(am -> findAnnotationMirror(annotation,
                                                    am.getAnnotationType().asElement()
                                                            .getAnnotationMirrors())
                        .ifPresent(it -> list.add(am.getAnnotationType().asElement().toString())));
        return list;
    }

    /**
     * Should only be called if the encloser of the typeArgument is known to be Provider type.
     */
    private static String providerTypeOf(TypeArgument typeArgument,
                                         Object enclosingElem) {
        if (!(typeArgument.getTypeSignature() instanceof ClassRefTypeSignature)) {
            throw new IllegalStateException("Unsupported provider<> type of " + typeArgument + " in " + enclosingElem);
        }
        return typeArgument.toString();
    }

    /**
     * Returns the throwable types on a method.
     *
     * @param methodInfo the method info
     * @return the list of throwable type names
     */
    private static List<String> extractThrowableTypeNames(MethodInfo methodInfo) {
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
    private static List<String> extractThrowableTypeNames(ExecutableElement methodInfo) {
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
    private static List<ElementInfo> createParameterInfo(TypeName serviceTypeName,
                                                         MethodInfo methodInfo) {
        List<ElementInfo> result = new ArrayList<>();
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
     * @param processingEnv the processing environment
     * @param serviceTypeName the enclosing service type name
     * @param methodInfo the method info
     * @return the list of info elements/parameters
     */
    private static List<ElementInfo> createParameterInfo(ProcessingEnvironment processingEnv,
                                                         TypeName serviceTypeName,
                                                         ExecutableElement methodInfo) {
        List<ElementInfo> result = new ArrayList<>();
        int count = 0;
        for (VariableElement ignore : methodInfo.getParameters()) {
            count++;
            result.add(createParameterInfo(processingEnv, serviceTypeName, methodInfo, count));
        }
        return result;
    }

    private static ClassRefTypeSignature toClassRefSignature(TypeSignature sig,
                                                             Object enclosingElem) {
        if (!(Objects.requireNonNull(sig) instanceof ClassRefTypeSignature)) {
            throw new IllegalStateException("Unsupported type for " + sig + " in " + enclosingElem);
        }
        return (ClassRefTypeSignature) sig;
    }

    private static ClassRefTypeSignature toClassRefSignature(
            TypeArgument arg,
            Object enclosingElem) {
        return toClassRefSignature(arg.getTypeSignature(), enclosingElem);
    }

    private static boolean isProviderType(ClassRefTypeSignature sig) {
        return isProviderType(TypeName.create(sig.getFullyQualifiedClassName()));
    }

    private static boolean isOptionalType(ClassRefTypeSignature sig) {
        return isOptionalType(sig.getFullyQualifiedClassName());
    }

    /**
     * Locate an annotation mirror by name.
     *
     * @param annotationType the annotation type to search for
     * @param ams            the collection to search through
     * @return the annotation mirror, or empty if not found
     */
    private static Optional<? extends AnnotationMirror> findAnnotationMirror(String annotationType,
                                                                             Collection<? extends AnnotationMirror> ams) {
        return ams.stream()
                .filter(it -> annotationType.equals(it.getAnnotationType().toString()))
                .findFirst();
    }

}
