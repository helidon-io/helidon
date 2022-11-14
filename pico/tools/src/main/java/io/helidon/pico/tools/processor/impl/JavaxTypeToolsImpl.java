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

package io.helidon.pico.tools.processor.impl;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Priority;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import io.helidon.pico.tools.processor.JavaxTypeTools;
import io.helidon.pico.types.TypeName;

import jakarta.inject.Singleton;

/**
 * Will load only if supporting types are on the classpath.
 */
@SuppressWarnings("unchecked")
@Singleton
public class JavaxTypeToolsImpl implements JavaxTypeTools {
    private static final String JAVAX_SCOPE_TYPE = "javax.inject.Scope";
    private static final String JAVAX_EE_NORMAL_SCOPE_TYPE = "jakarta.enterprise.context.NormalScope";

    boolean getPriorityEnabled = true;
    boolean getAnnotationsWithAnnotationEnabled = true;

    @Override
    public Integer getPriority(TypeName serviceTypeName, TypeElement type) {
        if (!getPriorityEnabled) {
            return null;
        }

        try {
            Priority priority = type.getAnnotation(Priority.class);
            if (Objects.nonNull(priority)) {
                return priority.value();
            }
        } catch (NoClassDefFoundError ncdfe) {
            // expected in most circumstances
            getPriorityEnabled = false;
        }

        return null;
    }

    @Override
    public List<String> getAnnotationsWithAnnotation(TypeElement type, String annotationTypeName) {
        if (!getAnnotationsWithAnnotationEnabled) {
            return Collections.emptyList();
        }

        try {
            List<String> list = new LinkedList<>();
            type.getAnnotationMirrors().forEach(am -> {
                DeclaredType annoType = am.getAnnotationType();
                for (AnnotationMirror metaAm : annoType.asElement().getAnnotationMirrors()) {
                    if (metaAm.getAnnotationType().toString().equals(annotationTypeName)) {
                        list.add(annoType.toString());
                        break;
                    }
                }
            });

            // Scope ==> NormalScope in EE...
            if (list.isEmpty() && annotationTypeName.equals(JAVAX_SCOPE_TYPE)) {
                return getAnnotationsWithAnnotation(type, JAVAX_EE_NORMAL_SCOPE_TYPE);
            }

            return list;
        } catch (Throwable t) {
            // expected in most circumstances
            getAnnotationsWithAnnotationEnabled = false;
        }

        return Collections.emptyList();
    }

    @Override
    public Class<? extends Annotation> loadAnnotationClass(String annotationTypeName) {
        try {
            return (Class<? extends Annotation>) Class.forName(annotationTypeName);
        } catch (Throwable t) {
            // expected in most circumstances
        }

        return null;
    }

}
