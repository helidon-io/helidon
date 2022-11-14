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

package io.helidon.pico.processor;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.processor.JavaxTypeTools;
import io.helidon.pico.tools.processor.TypeTools;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Handling for {@link jakarta.annotation.PostConstruct} and {@link jakarta.annotation.PreDestroy}.
 */
public class PostConstructPreDestroyAnnotationProcessor extends BaseAnnotationProcessor<Void> {

    private static final Set<Class<? extends Annotation>> SUPPORTED_TARGETS;
    private static Class<? extends Annotation> javaxPreDestroyType;
    private static Class<? extends Annotation> javaxPostConstructType;
    static {
        SUPPORTED_TARGETS = new HashSet<>();
        SUPPORTED_TARGETS.add(PreDestroy.class);
        SUPPORTED_TARGETS.add(PostConstruct.class);
        addJavaxTypes(SUPPORTED_TARGETS);
    }

    private static void addJavaxTypes(Set<Class<? extends Annotation>> supportedTargets) {
        if (Objects.nonNull(javaxPreDestroyType) && Objects.nonNull(javaxPostConstructType)) {
            return;
        }

        try {
            javaxPreDestroyType = JavaxTypeTools.INSTANCE.get()
                        .loadAnnotationClass(TypeTools.oppositeOf(PreDestroy.class.getName()));
            if (Objects.nonNull(javaxPreDestroyType)) {
                supportedTargets.add(javaxPreDestroyType);
                javaxPostConstructType = Objects.requireNonNull(JavaxTypeTools.INSTANCE.get()
                        .loadAnnotationClass(TypeTools.oppositeOf(PostConstruct.class.getName())));
                supportedTargets.add(javaxPostConstructType);
            }
        } catch (Throwable t) {
            // expected
        }
    }

    @Override
    protected Set<Class<? extends Annotation>> getAnnoTypes() {
        return SUPPORTED_TARGETS;
    }

    @Override
    public Set<String> getContraAnnotations() {
        return Collections.singleton(CONFIGURED_BY_TYPENAME);
    }

    @Override
    public void doInner(ExecutableElement method, Void builder) {
        if (method.getKind() == ElementKind.CONSTRUCTOR) {
            throw new ToolsException("Invalid use of PreDestroy/PostConstruct on " + method.getEnclosingElement() + "." + method);
        }

        boolean isStatic = false;
        InjectionPointInfo.Access access = InjectionPointInfo.Access.PACKAGE_PRIVATE;
        Set<Modifier> modifiers = method.getModifiers();
        if (Objects.nonNull(modifiers)) {
            for (Modifier modifier : modifiers) {
                if (Modifier.PUBLIC == modifier) {
                    access = InjectionPointInfo.Access.PUBLIC;
                } else if (Modifier.PROTECTED == modifier) {
                    access = InjectionPointInfo.Access.PROTECTED;
                } else if (Modifier.PRIVATE == modifier) {
                    access = InjectionPointInfo.Access.PRIVATE;
                } else if (Modifier.STATIC == modifier) {
                    isStatic = true;
                }
            }
        }

        if (isStatic || InjectionPointInfo.Access.PRIVATE == access) {
            throw new ToolsException("Invalid use of a private and/or static PreDestroy/PostConstruct method on "
                                             + method.getEnclosingElement() + "." + method);
        }

        if (!method.getParameters().isEmpty()) {
            throw new ToolsException("Invalid use PreDestroy/PostConstruct method w/ parameters on "
                                             + method.getEnclosingElement() + "." + method);
        }

        if (Objects.nonNull(method.getAnnotation(PreDestroy.class))) {
            services.setPreDestroyMethod(TypeTools.createTypeNameFromElement(method.getEnclosingElement()),
                                         method.getSimpleName().toString());
        } else if (Objects.nonNull(javaxPreDestroyType) && Objects.nonNull(method.getAnnotation(javaxPreDestroyType))) {
            services.setPreDestroyMethod(TypeTools.createTypeNameFromElement(method.getEnclosingElement()),
                                         method.getSimpleName().toString());
        }

        if (Objects.nonNull(method.getAnnotation(PostConstruct.class))) {
            services.setPostConstructMethod(TypeTools.createTypeNameFromElement(method.getEnclosingElement()),
                                            method.getSimpleName().toString());
        } else if (Objects.nonNull(javaxPostConstructType) && Objects.nonNull(method.getAnnotation(javaxPostConstructType))) {
            services.setPostConstructMethod(TypeTools.createTypeNameFromElement(method.getEnclosingElement()),
                                            method.getSimpleName().toString());
        }
    }

}
