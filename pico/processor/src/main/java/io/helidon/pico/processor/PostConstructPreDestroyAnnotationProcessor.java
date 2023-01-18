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

package io.helidon.pico.processor;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.tools.JavaxTypeTools;
import io.helidon.pico.tools.ToolsException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import static io.helidon.pico.tools.TypeTools.createTypeNameFromElement;
import static io.helidon.pico.tools.TypeTools.oppositeOf;

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

    private static void addJavaxTypes(
            Set<Class<? extends Annotation>> supportedTargets) {
        if (javaxPreDestroyType != null && javaxPostConstructType != null) {
            return;
        }

        try {
            javaxPreDestroyType = JavaxTypeTools.INSTANCE.get()
                        .loadAnnotationClass(oppositeOf(PreDestroy.class.getName())).orElse(null);
            if (javaxPreDestroyType != null) {
                supportedTargets.add(javaxPreDestroyType);
                javaxPostConstructType = JavaxTypeTools.INSTANCE.get()
                        .loadAnnotationClass(oppositeOf(PostConstruct.class.getName())).orElseThrow();
                supportedTargets.add(javaxPostConstructType);
            }
        } catch (Throwable t) {
            // normal
        }
    }

    @Override
    Set<Class<? extends Annotation>> annoTypes() {
        return Set.copyOf(SUPPORTED_TARGETS);
    }

    @Override
    Set<String> contraAnnotations() {
        return Set.of(CONFIGURED_BY_TYPENAME);
    }

    @Override
    void doInner(
            ExecutableElement method,
            Void builder) {
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

        if (method.getAnnotation(PreDestroy.class) != null) {
            services.addPreDestroyMethod(createTypeNameFromElement(method.getEnclosingElement()).orElseThrow(),
                                         method.getSimpleName().toString());
        } else if (javaxPreDestroyType != null && Objects.nonNull(method.getAnnotation(javaxPreDestroyType))) {
            services.addPreDestroyMethod(createTypeNameFromElement(method.getEnclosingElement()).orElseThrow(),
                                         method.getSimpleName().toString());
        }

        if (method.getAnnotation(PostConstruct.class) != null) {
            services.addPostConstructMethod(createTypeNameFromElement(method.getEnclosingElement()).orElseThrow(),
                                            method.getSimpleName().toString());
        } else if (Objects.nonNull(javaxPostConstructType) && Objects.nonNull(method.getAnnotation(javaxPostConstructType))) {
            services.addPostConstructMethod(createTypeNameFromElement(method.getEnclosingElement()).orElseThrow(),
                                            method.getSimpleName().toString());
        }
    }

}
