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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypedElementInfo;

/**
 * Each described service has elements for the service itself (both if provider and if direct implementation),
 * and in case of provider it has elements for the provided types.
 */
class DescribedElements {
    private final Set<ElementSignature> interceptedMethods;
    private final Set<ElementSignature> plainMethods;
    // all elements that are accessible
    private final List<TypedElements.ElementMeta> allElements;
    // all intercepted elements (subset of allElements)
    private final List<TypedElements.ElementMeta> interceptedElements;
    // all plain (not intercepted) elements (subset of allElements)
    private final List<TypedElements.ElementMeta> plainElements;
    private final boolean intercepted;
    private final boolean methodIntercepted;
    private final boolean constructorIntercepted;

    private DescribedElements(Set<ElementSignature> interceptedMethods,
                              Set<ElementSignature> plainMethods,
                              List<TypedElements.ElementMeta> allElements,
                              List<TypedElements.ElementMeta> interceptedElements,
                              List<TypedElements.ElementMeta> plainElements,
                              boolean isIntercepted,
                              boolean methodsIntercepted,
                              boolean constructorIntercepted) {
        this.interceptedMethods = interceptedMethods;
        this.plainMethods = plainMethods;
        this.allElements = allElements;
        this.interceptedElements = interceptedElements;
        this.plainElements = plainElements;
        this.intercepted = isIntercepted;
        this.methodIntercepted = methodsIntercepted;
        this.constructorIntercepted = constructorIntercepted;
    }

    /**
     * Create for a service.
     *
     * @param ctx             to find {@link io.helidon.common.types.TypeInfo} for analysis
     * @param interception    interception support
     * @param serviceTypeInfo type info of the processed service
     * @param contracts       eligible contracts of the service
     * @return described elements for a service
     */
    static DescribedElements create(CodegenContext ctx,
                                    InterceptionSupport interception,
                                    Collection<ResolvedType> contracts,
                                    TypeInfo serviceTypeInfo) {
        var all = TypedElements.gatherElements(ctx, contracts, serviceTypeInfo)
                .stream()
                .collect(Collectors.toUnmodifiableList());
        var allMethods = all.stream()
                .map(TypedElements.ElementMeta::element)
                .filter(ElementInfoPredicates::isMethod)
                .map(TypedElementInfo::signature)
                .collect(Collectors.toUnmodifiableSet());

        var intercepted = interception.interception().maybeIntercepted(serviceTypeInfo, all);
        var interceptedMethods = intercepted.stream()
                .map(TypedElements.ElementMeta::element)
                .filter(ElementInfoPredicates::isMethod)
                .map(TypedElementInfo::signature)
                .collect(Collectors.toUnmodifiableSet());

        var plain = all.stream()
                .filter(it -> !interceptedMethods.contains(it.element().signature()))
                .collect(Collectors.toUnmodifiableList());
        var plainSignatures = allMethods.stream()
                .filter(it -> !interceptedMethods.contains(it))
                .collect(Collectors.toUnmodifiableSet());

        boolean methodsIntercepted = intercepted.stream()
                .map(TypedElements.ElementMeta::element)
                .anyMatch(ElementInfoPredicates::isMethod);
        boolean constructorIntercepted = intercepted.stream()
                .map(TypedElements.ElementMeta::element)
                .anyMatch(ElementInfoPredicates::isConstructor);
        boolean isIntercepted = !intercepted.isEmpty();

        return new DescribedElements(interceptedMethods,
                                     plainSignatures,
                                     all,
                                     intercepted,
                                     plain,
                                     isIntercepted,
                                     methodsIntercepted,
                                     constructorIntercepted);
    }

    @Override
    public String toString() {
        return "intercepted (" + interceptedElements.size() + "), plain (" + plainElements.size() + ")";
    }

    public Set<ElementSignature> interceptedMethods() {
        return interceptedMethods;
    }

    public Set<ElementSignature> plainMethods() {
        return plainMethods;
    }

    public List<TypedElements.ElementMeta> allElements() {
        return allElements;
    }

    public List<TypedElements.ElementMeta> interceptedElements() {
        return interceptedElements;
    }

    public List<TypedElements.ElementMeta> plainElements() {
        return plainElements;
    }

    public boolean intercepted() {
        return intercepted;
    }

    public boolean methodsIntercepted() {
        return methodIntercepted;
    }

    public boolean constructorIntercepted() {
        return constructorIntercepted;
    }

}
