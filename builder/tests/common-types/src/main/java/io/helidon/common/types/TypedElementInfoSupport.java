/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.common.types;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.builder.api.Prototype;

final class TypedElementInfoSupport {
    private TypedElementInfoSupport() {
    }

    @Prototype.PrototypeMethod
    @Prototype.Annotated("java.lang.Override")
    static String toString(TypedElementInfo me) {
        StringBuilder builder = new StringBuilder();
        if (ElementKind.PARAMETER != me.kind()) {
            me.enclosingType()
                    .ifPresent(enclosingTypeName -> builder.append(enclosingTypeName).append("::"));
        }
        builder.append(me.toDeclaration());
        return builder.toString();
    }

    /**
     * Provides a description for this instance.
     *
     * @return provides the {typeName}{space}{elementName}
     */
    @Prototype.PrototypeMethod
    static String toDeclaration(TypedElementInfo me) {
        StringBuilder builder = new StringBuilder();
        builder.append(me.typeName()).append(" ").append(me.elementName());
        String params = me.parameterArguments().stream()
                .map(it -> it.typeName() + " " + it.elementName())
                .collect(Collectors.joining(", "));
        if (!params.isBlank()) {
            builder.append("(").append(params).append(")");
        }
        return builder.toString();
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<TypedElementInfo.BuilderBase<?, ?>> {
        BuilderDecorator() {
        }

        @Override
        public void decorate(TypedElementInfo.BuilderBase<?, ?> target) {
            defaultAccessModifier(target);
            constructorName(target);
            signature(target);
        }

        private void signature(TypedElementInfo.BuilderBase<?, ?> target) {
            if (target.kind().isEmpty()) {
                // this will fail when validating
                target.signature(ElementSignatures.createNone());
                return;
            } else {
                target.signature(signature(target, target.kind().get()));
            }
        }

        private ElementSignature signature(TypedElementInfo.BuilderBase<?, ?> target, ElementKind elementKind) {
            if (elementKind == ElementKind.CONSTRUCTOR) {
                return ElementSignatures.createConstructor(toTypes(target.parameterArguments()));
            }
            // for everything else we need the type (it is required)
            if (target.typeName().isEmpty() || target.elementName().isEmpty()) {
                return ElementSignatures.createNone();
            }

            TypeName typeName = target.typeName().get();
            String name = target.elementName().get();

            if (elementKind == ElementKind.FIELD
                    || elementKind == ElementKind.RECORD_COMPONENT
                    || elementKind == ElementKind.ENUM_CONSTANT) {
                return ElementSignatures.createField(typeName, name);
            }
            if (elementKind == ElementKind.METHOD) {
                return ElementSignatures.createMethod(typeName, name, toTypes(target.parameterArguments()));
            }
            if (elementKind == ElementKind.PARAMETER) {
                return ElementSignatures.createParameter(typeName, name);
            }
            return ElementSignatures.createNone();
        }

        private List<TypeName> toTypes(List<TypedElementInfo> typedElementInfos) {
            return typedElementInfos.stream()
                    .map(TypedElementInfo::typeName)
                    .collect(Collectors.toUnmodifiableList());
        }

        private void constructorName(TypedElementInfo.BuilderBase<?, ?> target) {
            Optional<ElementKind> elementKind = target.kind();
            if (elementKind.isPresent()) {
                if (elementKind.get() == ElementKind.CONSTRUCTOR) {
                    target.elementName("<init>");
                }
            }
        }

        private void defaultAccessModifier(TypedElementInfo.BuilderBase<?, ?> target) {
            if (target.accessModifier().isEmpty()) {
                target.accessModifier(AccessModifier.PACKAGE_PRIVATE);
            }
        }
    }
}
