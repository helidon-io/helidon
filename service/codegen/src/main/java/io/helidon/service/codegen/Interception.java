/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

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
     * An executable element may be intercepted if one of its parameters or return type may be intercepted,
     * in addition to annotations directly on the element.
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
        if (hasDirectInterceptTriggerAnnotation(typeInfo, element)) {
            return true;
        }

        if (element instanceof TypedElementInfo tei) {
            if (tei.kind() == ElementKind.METHOD || tei.kind() == ElementKind.CONSTRUCTOR || tei.kind() == ElementKind.FIELD) {
                // got through parameters and return types, and through a type for field
                return hasInterceptTriggerOnParams(typeInfo, tei);
            }
        }

        return false;
    }

    private boolean hasInterceptTriggerOnParams(TypeInfo typeInfo, TypedElementInfo tei) {
        // we currently cannot retrieve annotations on generics, i.e. List<@NotBlank String>
        if (hasInterceptTriggerOnTypeArguments(typeInfo, tei.typeName())) {
            return true;
        }

        // so for now, just support direct annotations on parameters
        for (TypedElementInfo parameter : tei.parameterArguments()) {
            if (hasInterceptTrigger(typeInfo, parameter)) {
                return true;
            }
            if (hasInterceptTriggerOnTypeArguments(typeInfo, parameter.typeName())) {
                return true;
            }
        }

        return false;
    }

    private boolean hasInterceptTriggerOnTypeArguments(TypeInfo typeInfo, TypeName typeName) {
        if (hasDirectInterceptTriggerAnnotation(typeInfo, typeName)) {
            return true;
        }
        for (TypeName typeArgument : typeName.typeArguments()) {
            if (hasInterceptTriggerOnTypeArguments(typeInfo, typeArgument)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDirectInterceptTriggerAnnotation(TypeInfo typeInfo, Annotated element) {
        for (Annotation annotation : element.annotations()) {
            if (interceptionStrategy.ordinal() >= InterceptionStrategy.EXPLICIT.ordinal()) {
                if (annotation.hasMetaAnnotation(INTERCEPTION_INTERCEPTED)) {
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
