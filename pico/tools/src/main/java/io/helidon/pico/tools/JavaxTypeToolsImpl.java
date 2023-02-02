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

package io.helidon.pico.tools;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Priority;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import io.helidon.pico.types.TypeName;

/**
 * This class will load only if supporting (javax) types are on the classpath.
 *
 * @deprecated
 */
public class JavaxTypeToolsImpl implements JavaxTypeTools {
    private static final String JAVAX_SCOPE_TYPE = "javax.inject.Scope";
    private static final String JAVAX_EE_NORMAL_SCOPE_TYPE = "jakarta.enterprise.context.NormalScope";

    private boolean getPriorityEnabled = true;
    private boolean getAnnotationsWithAnnotationEnabled = true;

    /**
     * Service loader based constructor.
     *
     * @deprecated
     */
    public JavaxTypeToolsImpl() {
    }

    @Override
    public Optional<Integer> priorityOf(
            TypeName serviceTypeName,
            TypeElement type) {
        if (!getPriorityEnabled) {
            return Optional.empty();
        }

        try {
            Priority priority = type.getAnnotation(Priority.class);
            if (priority != null) {
                return Optional.of(priority.value());
            }
        } catch (NoClassDefFoundError ncdfe) {
            // expected in most circumstances
            getPriorityEnabled = false;
        }

        return Optional.empty();
    }

    @Override
    public List<String> annotationsWithAnnotationOf(
            TypeElement type,
            String annotationTypeName) {
        if (!getAnnotationsWithAnnotationEnabled) {
            return List.of();
        }

        try {
            List<String> result = new ArrayList<>();
            type.getAnnotationMirrors().forEach(am -> {
                DeclaredType annoType = am.getAnnotationType();
                for (AnnotationMirror metaAm : annoType.asElement().getAnnotationMirrors()) {
                    if (metaAm.getAnnotationType().toString().equals(annotationTypeName)) {
                        result.add(annoType.toString());
                        break;
                    }
                }
            });

            // Scope ==> NormalScope in EE...
            if (result.isEmpty() && annotationTypeName.equals(JAVAX_SCOPE_TYPE)) {
                return annotationsWithAnnotationOf(type, JAVAX_EE_NORMAL_SCOPE_TYPE);
            }

            return result;
        } catch (Throwable t) {
            // expected in most circumstances
            getAnnotationsWithAnnotationEnabled = false;
        }

        return List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Class<? extends Annotation>> loadAnnotationClass(
            String annotationTypeName) {
        try {
            return Optional.of((Class<? extends Annotation>) Class.forName(annotationTypeName));
        } catch (Throwable t) {
            // expected in most circumstances
            Throwable debugMe = t;
        }
        return Optional.empty();
    }

}
