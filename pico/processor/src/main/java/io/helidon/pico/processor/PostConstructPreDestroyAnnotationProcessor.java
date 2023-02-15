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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.tools.ToolsException;

import static io.helidon.builder.processor.tools.BuilderTypeTools.findAnnotationMirror;
import static io.helidon.pico.tools.TypeTools.createTypeNameFromElement;

/**
 * Handling for {@link jakarta.annotation.PostConstruct} and {@link jakarta.annotation.PreDestroy}.
 *
 * @deprecated
 */
public class PostConstructPreDestroyAnnotationProcessor extends BaseAnnotationProcessor<Void> {
    private static final String PRE_DESTROY = "jakarta.annotation.PreDestroy";
    private static final String POST_CONSTRUCT = "jakarta.annotation.PostConstruct";
    private static final String PRE_DESTROY_JAVAX = "javax.annotation.PreDestroy";
    private static final String POST_CONSTRUCT_JAVAX = "javax.annotation.PostConstruct";

    private static final Set<String> SUPPORTED_TARGETS = Set.of(PRE_DESTROY,
                                   POST_CONSTRUCT,
                                   PRE_DESTROY_JAVAX,
                                   POST_CONSTRUCT_JAVAX);

    /**
     * Service loader based constructor.
     *
     * @deprecated
     */
    public PostConstructPreDestroyAnnotationProcessor() {
    }

    @Override
    protected Set<String> annoTypes() {
        return SUPPORTED_TARGETS;
    }

    @Override
    protected Set<String> contraAnnotations() {
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
        if (modifiers != null) {
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

        List<? extends AnnotationMirror> annotations = method.getAnnotationMirrors();

        /*
         * Either Jakarta or javax pre-destroy
         */
        Optional<? extends AnnotationMirror> mirror = findAnnotationMirror(PRE_DESTROY, annotations);
        if (mirror.isPresent()) {
            servicesToProcess().addPreDestroyMethod(createTypeNameFromElement(method.getEnclosingElement()).orElseThrow(),
                                                    method.getSimpleName().toString());
        } else {
            mirror = findAnnotationMirror(PRE_DESTROY_JAVAX, annotations);
            if (mirror.isPresent()) {
                servicesToProcess().addPreDestroyMethod(createTypeNameFromElement(method.getEnclosingElement()).orElseThrow(),
                                                        method.getSimpleName().toString());
            }
        }

        /*
         * Either Jakarta or javax post-construct
         */
        mirror = findAnnotationMirror(POST_CONSTRUCT, annotations);
        if (mirror.isPresent()) {
            servicesToProcess().addPostConstructMethod(createTypeNameFromElement(method.getEnclosingElement()).orElseThrow(),
                                                       method.getSimpleName().toString());
        } else {
            mirror = findAnnotationMirror(POST_CONSTRUCT_JAVAX, annotations);
            if (mirror.isPresent()) {
                servicesToProcess().addPostConstructMethod(createTypeNameFromElement(method.getEnclosingElement()).orElseThrow(),
                                                           method.getSimpleName().toString());
            }
        }
    }

}
