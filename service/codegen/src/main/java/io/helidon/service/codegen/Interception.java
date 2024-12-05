/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.codegen;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeNames;

import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPTION_INTERCEPTED;

final class Interception {
    private static final Annotation RUNTIME_RETENTION = Annotation.create(Retention.class, RetentionPolicy.RUNTIME.name());
    private static final Annotation CLASS_RETENTION = Annotation.create(Retention.class, RetentionPolicy.CLASS.name());

    private final InterceptionStrategy interceptionStrategy;

    Interception(InterceptionStrategy interceptionStrategy) {
        this.interceptionStrategy = interceptionStrategy;
    }

    /**
     * Find all elements that may be intercepted.
     * This method also returns fields (as injection into fields can be intercepted).
     *
     * @param typeInfo type being processed
     * @return all elements that may be intercepted (constructors, fields, methods)
     */
    List<TypedElements.ElementMeta> maybeIntercepted(TypeInfo typeInfo) {
        if (interceptionStrategy == InterceptionStrategy.NONE) {
            return List.of();
        }

        List<TypedElements.ElementMeta> result = new ArrayList<>();

        // depending on strategy
        List<TypedElements.ElementMeta> allElements = TypedElements.gatherElements(typeInfo);

        if (hasInterceptTrigger(typeInfo)) {
            // we cannot intercept private stuff (never modify source code or bytecode!)
            allElements.stream()
                    .filter(it -> it.element().accessModifier() != AccessModifier.PRIVATE)
                    .forEach(result::add);
            result.add(TypedElements.DEFAULT_CONSTRUCTOR); // we must intercept construction as well
        } else {
            allElements.stream()
                    .filter(methodMetadata -> hasInterceptTrigger(typeInfo, methodMetadata))
                    .peek(it -> {
                        if (it.element().accessModifier() == AccessModifier.PRIVATE) {
                            throw new CodegenException(typeInfo.typeName()
                                                               .fqName() + "#" + it.element()
                                    .elementName() + " is declared "
                                                               + "as private, but has interceptor trigger "
                                                               + "annotation declared. "
                                                               + "This cannot be supported, as we do not modify "
                                                               + "sources or bytecode.",
                                                       it.element().originatingElementValue());
                        }
                    })
                    .forEach(result::add);
        }

        return result;
    }

    /**
     * Find all elements that may be intercepted.
     * This method also returns fields (as injection into fields can be intercepted).
     *
     * @param typeInfo the type
     * @param elements elements to process
     * @return all elements that may be intercepted (constructors, fields, methods)
     */
    List<TypedElements.ElementMeta> maybeIntercepted(TypeInfo typeInfo, List<TypedElements.ElementMeta> elements) {
        if (interceptionStrategy == InterceptionStrategy.NONE) {
            return List.of();
        }

        List<TypedElements.ElementMeta> result = new ArrayList<>();

        if (hasInterceptTrigger(typeInfo)) {
            // we cannot intercept private stuff (never modify source code or bytecode!)
            elements.stream()
                    .filter(it -> it.element().accessModifier() != AccessModifier.PRIVATE)
                    .forEach(result::add);
            result.add(TypedElements.DEFAULT_CONSTRUCTOR); // we must intercept construction as well
        } else {
            elements.stream()
                    .filter(methodMetadata -> hasInterceptTrigger(typeInfo, methodMetadata))
                    .peek(it -> {
                        if (it.element().accessModifier() == AccessModifier.PRIVATE) {
                            throw new CodegenException(typeInfo.typeName()
                                                               .fqName() + "#" + it.element()
                                    .elementName() + " is declared "
                                                               + "as private, but has interceptor trigger "
                                                               + "annotation declared. "
                                                               + "This cannot be supported, as we do not modify "
                                                               + "sources or bytecode.",
                                                       it.element().originatingElementValue());
                        }
                    })
                    .forEach(result::add);
        }

        return result;
    }

    // intercept trigger on the type (or on implemented interface)
    private boolean hasInterceptTrigger(TypeInfo typeInfo) {
        if (hasInterceptTrigger(typeInfo, typeInfo)) {
            return true;
        }
        // check all implemented interfaces
        for (TypeInfo ifaceType : typeInfo.interfaceTypeInfo()) {
            if (hasInterceptTrigger(ifaceType)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInterceptTrigger(TypeInfo typeInfo, TypedElements.ElementMeta methodMeta) {
        if (hasInterceptTrigger(typeInfo, methodMeta.element())) {
            // the method is declared with the annotation
            return true;
        }
        for (TypedElements.DeclaredElement interfaceMethod : methodMeta.abstractMethods()) {
            return hasInterceptTrigger(interfaceMethod.abstractType(), interfaceMethod.element());
        }

        return false;
    }

    private boolean hasInterceptTrigger(TypeInfo typeInfo, Annotated element) {
        for (Annotation annotation : element.annotations()) {
            if (interceptionStrategy.ordinal() >= InterceptionStrategy.EXPLICIT.ordinal()) {
                if (typeInfo.hasMetaAnnotation(annotation.typeName(), INTERCEPTION_INTERCEPTED)) {
                    return true;
                }
            }
            if (interceptionStrategy.ordinal() >= InterceptionStrategy.ALL_RUNTIME.ordinal()) {
                Optional<Annotation> retention = typeInfo.metaAnnotation(annotation.typeName(),
                                                                         TypeNames.RETENTION);
                boolean isRuntime = retention.map(RUNTIME_RETENTION::equals).orElse(false);
                if (isRuntime) {
                    return true;
                }
            }
            if (interceptionStrategy.ordinal() >= InterceptionStrategy.ALL_RETAINED.ordinal()) {
                Optional<Annotation> retention = typeInfo.metaAnnotation(annotation.typeName(),
                                                                         TypeNames.RETENTION);
                boolean isClass = retention.map(CLASS_RETENTION::equals).orElse(false);
                if (isClass) {
                    return true;
                }
            }
        }
        return false;
    }
}
