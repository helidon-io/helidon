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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.inject.spi.DeploymentException;
import javax.ws.rs.core.Response;

import io.helidon.common.LazyValue;

import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

class ParticipantValidationModel {

    private static final Set<DotName> PARTICIPANT_ANNOTATIONS = Set.of(
            InspectionService.AFTER_LRA,
            InspectionService.COMPENSATE,
            InspectionService.COMPLETE,
            InspectionService.FORGET,
            InspectionService.STATUS
    );

    private final ClassInfo classInfo;
    private final Map<String, ParticipantMethod> methods = new HashMap<>();

    ParticipantValidationModel(ClassInfo classInfo) {
        this.classInfo = classInfo;
    }

    ParticipantValidationModel method(MethodInfo method, Set<AnnotationInstance> annotations) {
        ParticipantMethod pm = new ParticipantMethod(method).annotations(annotations);
        methods.putIfAbsent(pm.nameWithParams(), pm);
        methods.computeIfPresent(pm.nameWithParams(), (k, v) -> v.annotations(annotations));
        return this;
    }

    boolean isParticipant() {
        return !methods.isEmpty();
    }

    private void validateMandatoryMethods() {
        // one of compensate or afterLra is mandatory
        Set<DotName> mandatoryAnnotations = Set.of(InspectionService.COMPENSATE, InspectionService.AFTER_LRA);
        if (methods.values().stream()
                .flatMap(ParticipantValidationModel.ParticipantMethod::annotationStream)
                .map(AnnotationInstance::name)
                .noneMatch(mandatoryAnnotations::contains)) {
            throw new DeploymentException("Missing @Compensate or @AfterLRA on class " + classInfo);
        }
    }

    private void validateAmbiguousCompensatorMethods() {
        Map<DotName, List<ParticipantMethod>> groupedParticipantMethods = methods.values().stream()
                .filter(ParticipantMethod::isCompensator)
                .collect(Collectors.groupingBy(pm -> pm.participantAnnotation().get()));
        for (Map.Entry<DotName, List<ParticipantMethod>> e : groupedParticipantMethods.entrySet()) {
            int size = e.getValue().size();
            if (size > 1) {
                throw new DeploymentException("Class " + classInfo.simpleName()
                        + " have " + size
                        + " methods with annotation @" + e.getKey().withoutPackagePrefix()
                        + ": " + e.getValue().stream().map(pm -> pm.method().toString()).collect(Collectors.joining(", ")));
            }
        }
    }

    private void validateReturnTypes() {
        //allowed return types for compensate and afterLra
        Set<DotName> returnTypes = Stream.of(
                Response.class,
                ParticipantStatus.class,
                CompletionStage.class,
                void.class
        )
                .map(Class::getName)
                .map(DotName::createSimple)
                .collect(Collectors.toSet());

        methods.forEach((m, a) -> {
            if (a.annotationStream().map(AnnotationInstance::name).anyMatch(InspectionService.COMPENSATE::equals)) {
                if (!returnTypes.contains(a.method().returnType().name())) {
                    throw new DeploymentException("Invalid return type " + a.method().returnType()
                            + " of compensating method " + m);
                }
            }
            if (a.annotationStream().map(AnnotationInstance::name).anyMatch(InspectionService.AFTER_LRA::equals)) {
                if (!returnTypes.contains(a.method().returnType().name())) {
                    throw new DeploymentException("Invalid return type "
                            + a.method().returnType() + " of after method "
                            + m);
                }
            }
        });
    }

    void validate() {
        validateMandatoryMethods();
        validateAmbiguousCompensatorMethods();
        validateReturnTypes();
    }

    private static class ParticipantMethod {
        private final MethodInfo methodInfo;
        private final Set<AnnotationInstance> annotations = new HashSet<>();
        private final Set<DotName> annotationTypes = new HashSet<>();
        private final LazyValue<Optional<DotName>> participantAnnotation = LazyValue.create(() -> {
            List<DotName> dotNames = annotationTypes.stream()
                    .filter(PARTICIPANT_ANNOTATIONS::contains)
                    .collect(Collectors.toList());
            if (dotNames.size() > 1) {
                throw new DeploymentException("Participant method "
                        + this.nameWithParams()
                        + " needs to have exactly one compensator annotation but has "
                        + dotNames.stream().map(DotName::withoutPackagePrefix).collect(Collectors.joining(", "))
                );
            }
            return dotNames.stream().findFirst();
        });

        ParticipantMethod(MethodInfo methodInfo) {
            this.methodInfo = methodInfo;
        }

        String nameWithParams() {
            return methodInfo.name() + methodInfo.parameters().stream()
                    .map(Type::name)
                    .map(DotName::toString)
                    .collect(Collectors.joining());
        }

        ParticipantMethod annotations(Collection<AnnotationInstance> a) {
            this.annotations.addAll(a);
            a.forEach(ai -> annotationTypes.add(ai.name()));
            return this;
        }

        Stream<AnnotationInstance> annotationStream() {
            return annotations.stream();
        }

        /**
         * Any LRA method except @LRA.
         *
         * @return true if so
         */
        boolean isCompensator() {
            return participantAnnotation().isPresent();
        }

        Optional<DotName> participantAnnotation() {
            return participantAnnotation.get();
        }

        MethodInfo method() {
            return methodInfo;
        }
    }
}
