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

package io.helidon.builder.processor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.common.processor.ElementInfoPredicates;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

final class BuilderInfoPredicates {
    private BuilderInfoPredicates() {
    }

    /**
     * Predicate for methods that should be ignored.
     *
     * @param ignoredMethods ignored method signatures (such as methods that are defined as default)
     * @param ignoredNames ignored method names (equals, hashCode etc.)
     * @return a new predicate
     */
    static Predicate<? super TypedElementInfo> ignoredMethod(Set<MethodSignature> ignoredMethods,
                                                                Set<String> ignoredNames) {
        return it -> {
            // name is enough, signature is not important
            if (ignoredNames.contains(it.elementName())) {
                return true;
            }

            return ignoredMethods.contains(MethodSignature.create(it));
        };
    }

    /**
     * Find a method matching the filters from {@link TypeInfo#elementInfo()}.
     *
     * @param signatureFilter   expected signature
     * @param expectedModifiers expected modifier(s), found method may have more modifiers than defined here
     * @param typeInfo          type info to search
     * @return found method, ord empty if method does not exist, if more than one exist, the first one is returned
     */
    static Optional<TypedElementInfo> findMethod(MethodSignature signatureFilter,
                                                 Set<Modifier> expectedModifiers,
                                                 TypeInfo typeInfo) {
        return typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(it -> {
                    Set<Modifier> modifiers = it.elementModifiers();
                    if (expectedModifiers != null) {
                        for (Modifier expectedModifier : expectedModifiers) {
                            if (!modifiers.contains(expectedModifier)) {
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .filter(it -> {
                    if (signatureFilter.returnType() != null) {
                        if (!it.typeName().equals(signatureFilter.returnType())) {
                            return false;
                        }
                    }
                    if (signatureFilter.name() != null) {
                        if (!it.elementName().equals(signatureFilter.name())) {
                            return false;
                        }
                    }
                    List<TypeName> expectedArguments = signatureFilter.arguments();
                    if (expectedArguments != null) {
                        List<TypedElementInfo> actualArguments = it.parameterArguments();
                        if (actualArguments.size() != expectedArguments.size()) {
                            return false;
                        }
                        for (int i = 0; i < expectedArguments.size(); i++) {
                            TypeName expected = expectedArguments.get(i);
                            TypeName actualArgument = actualArguments.get(i).typeName();
                            if (!expected.equals(actualArgument)) {
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .findFirst();

    }
}
