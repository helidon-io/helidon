/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.lra;

import java.lang.reflect.Method;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.inject.spi.DeploymentException;
import javax.inject.Inject;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.Forget;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

class InspectionService {

    private static final Logger LOGGER = Logger.getLogger(InspectionService.class.getName());

    private final IndexView index;
    static final DotName LRA = DotName.createSimple(LRA.class.getName());
    static final DotName LEAVE = DotName.createSimple(Leave.class.getName());
    static final DotName AFTER_LRA = DotName.createSimple(AfterLRA.class.getName());
    static final DotName COMPLETE = DotName.createSimple(Complete.class.getName());
    static final DotName COMPENSATE = DotName.createSimple(Compensate.class.getName());
    static final DotName FORGET = DotName.createSimple(Forget.class.getName());
    static final DotName STATUS = DotName.createSimple(Status.class.getName());
    static final Set<DotName> LRA_ANNOTATIONS = Set.of(LRA, LEAVE, AFTER_LRA, COMPLETE, COMPENSATE, FORGET, STATUS);

    @Inject
    InspectionService(LraCdiExtension lraCdiExtension) {
        this.index = lraCdiExtension.getIndex();
    }

    /**
     * Resolve all methods with LRA annotations directly on methods or inherited from interfaces, parents of enclosing classes.
     */
    ParticipantValidationModel lookUpLraMethods(ClassInfo classInfo) {
        ParticipantValidationModel validationModel = new ParticipantValidationModel(classInfo);
        List<ClassInfo> classHierarchy = getAllParents(classInfo);
        classHierarchy.add(0, classInfo);
        for (ClassInfo clazz : classHierarchy) {
            for (MethodInfo method : getAllDeclaredMethods(clazz)) {
                Set<AnnotationInstance> lraAnnotations = lookUpLraAnnotations(method);
                if (!lraAnnotations.isEmpty()) {
                    validationModel.method(method, lraAnnotations);
                }
            }
        }
        return validationModel;
    }

    List<ClassInfo> getAllParents(ClassInfo classInfo) {
        List<ClassInfo> superClasses = new ArrayList<>();

        // extends
        DotName superClassName = classInfo.superName();
        while (superClassName != null) {
            ClassInfo superClass = index.getClassByName(superClassName);
            if (superClass == null) break;
            superClasses.add(superClass);
            superClassName = superClass.superName();
        }

        // implements
        for (DotName implementedInterfaceName : classInfo.interfaceNames()) {
            ClassInfo interfaceClass = index.getClassByName(implementedInterfaceName);
            superClasses.addAll(getAllParents(interfaceClass));
        }

        return superClasses;
    }

    Set<MethodInfo> getAllDeclaredMethods(ClassInfo classInfo) {
        return Stream.of(
                Optional.ofNullable(classInfo.name()).map(List::of).orElseGet(List::of),
                classInfo.interfaceNames(),
                Optional.ofNullable(classInfo.superName()).map(List::of).orElseGet(List::of)
        )
                .flatMap(List::stream)
                .map(className -> {
                    ClassInfo ci = index.getClassByName(className);
                    if (ci == null) {
                        LOGGER.log(Level.SEVERE, "Class {0} not found in index", className);
                    }
                    return ci;
                })
                .filter(Objects::nonNull)
                .map(ClassInfo::methods)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    Set<AnnotationInstance> lookUpLraAnnotations(Method method) {
        ClassInfo declaringClazz = index.getClassByName(DotName.createSimple(method.getDeclaringClass().getName()));
        if (declaringClazz == null) {
            throw new DeploymentException("Can't find indexed declaring class of method " + method);
        }

        Type[] params = Arrays.stream(method.getParameterTypes())
                .map(c -> Type.create(DotName.createSimple(c.getName()), Type.Kind.CLASS))
                .toArray(Type[]::new);

        MethodInfo methodInfo = declaringClazz.method(method.getName(), params);

        if (methodInfo == null) {
            throw new DeploymentException("LRA method " + method + " not found indexed in class " + declaringClazz.name());
        }

        return lookUpLraAnnotations(methodInfo);
    }

    Set<AnnotationInstance> lookUpLraAnnotations(MethodInfo methodInfo) {
        Map<String, AnnotationInstance> annotations = new HashMap<>();
        deepScanLraMethod(
                methodInfo.declaringClass(),
                annotations,
                methodInfo.name(),
                methodInfo.parameters().toArray(new Type[0])
        );
        HashSet<AnnotationInstance> result = new HashSet<>(annotations.values());

        // Only LRA annotations concern us
        if (result.stream()
                .map(AnnotationInstance::name)
                .noneMatch(LRA_ANNOTATIONS::contains)) {
            return Set.of();
        }

        // Compensate can't be accompanied by class level LRA
        if (annotations.containsKey(COMPENSATE.toString())) {
            return result;
        }

        AnnotationInstance classLevelLraAnnotation = deepScanClassLevelLraAnnotation(methodInfo.declaringClass());
        if (classLevelLraAnnotation != null) {
            // add class level @LRA only if not already declared by method
            if (result.stream().noneMatch(a -> a.name().equals(classLevelLraAnnotation.name()))) {
                result.add(classLevelLraAnnotation);
            }
        }
        return result;
    }

    IndexView index() {
        return index;
    }


    AnnotationInstance deepScanClassLevelLraAnnotation(ClassInfo classInfo) {
        if (classInfo == null) return null;
        AnnotationInstance lraClassAnnotation = classInfo.classAnnotation(DotName.createSimple(LRA.class.getName()));
        if (lraClassAnnotation != null) {
            return lraClassAnnotation;
        }
        // extends
        lraClassAnnotation = deepScanClassLevelLraAnnotation(index.getClassByName(classInfo.superName()));
        if (lraClassAnnotation != null) {
            return lraClassAnnotation;
        }
        // implements
        for (DotName interfaceName : classInfo.interfaceNames()) {
            lraClassAnnotation = deepScanClassLevelLraAnnotation(index.getClassByName(interfaceName));
            if (lraClassAnnotation != null) {
                return lraClassAnnotation;
            }
        }
        return null;
    }

    void deepScanLraMethod(ClassInfo classInfo,
                           Map<String, AnnotationInstance> annotations,
                           String methodName,
                           Type... parameters) {
        if (classInfo == null) return;
        // add only those not already present(overriding)
        MethodInfo method = classInfo.method(methodName, parameters);
        if (method == null) return;
        method.asMethod()
                .annotations()
                .forEach(a -> annotations.putIfAbsent(a.name().toString(), a));
        // extends
        deepScanLraMethod(index.getClassByName(classInfo.superName()), annotations, methodName, parameters);
        // implements
        for (DotName interfaceName : classInfo.interfaceNames()) {
            deepScanLraMethod(index.getClassByName(interfaceName), annotations, methodName, parameters);
        }
    }

    Lra lraAnnotation(AnnotationInstance annotationInstance) {
        return new Lra(annotationInstance, index);
    }

    static class Lra {
        private final Map<String, AnnotationValue> values;

        private Lra(AnnotationInstance annotationInstance, IndexView indexView) {
            values = annotationInstance.valuesWithDefaults(indexView).stream()
                    .collect(Collectors.toMap(AnnotationValue::name, Function.identity()));
        }

        LRA.Type value() {
            return org.eclipse.microprofile.lra.annotation.ws.rs.LRA.Type.valueOf(values.get("value").asEnum());
        }

        long timeLimit() {
            return values.get("timeLimit").asLong();
        }

        ChronoUnit timeUnit() {
            return ChronoUnit.valueOf(values.get("timeUnit").asEnum());
        }

        boolean end() {
            return values.get("end").asBoolean();
        }

        Set<Response.Status.Family> cancelOnFamily() {
            return Arrays.stream(values.get("cancelOnFamily").asEnumArray())
                    .map(Response.Status.Family::valueOf)
                    .collect(Collectors.toSet());
        }

        Set<Response.Status> cancelOn() {
            return Arrays.stream(values.get("cancelOn").asEnumArray())
                    .map(Response.Status::valueOf)
                    .collect(Collectors.toSet());
        }
    }

}
