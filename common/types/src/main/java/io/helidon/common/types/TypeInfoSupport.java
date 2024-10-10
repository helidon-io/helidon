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

package io.helidon.common.types;

import java.util.Locale;

import io.helidon.builder.api.Prototype;

final class TypeInfoSupport {
    private TypeInfoSupport() {
    }

    static final class TypeInfoDecorator implements Prototype.BuilderDecorator<TypeInfo.BuilderBase<?, ?>> {
        @SuppressWarnings("removal") // this method makes sure we are backward compatible
        @Override
        public void decorate(TypeInfo.BuilderBase<?, ?> target) {
            /*
            Backward compatibility for deprecated methods.
             */
            if (target.kind().isEmpty() && target.typeKind().isPresent()) {
                target.kind(ElementKind.valueOf(target.typeKind().get().toUpperCase(Locale.ROOT)));
            }
            target.typeKind(target.kind().get().toString());

            if (target.accessModifier().isEmpty()) {
                AccessModifier accessModifier = null;
                for (String modifier : target.modifiers()) {
                    if (TypeValues.MODIFIER_PUBLIC.equals(modifier)) {
                        accessModifier = AccessModifier.PUBLIC;
                        break;
                    }
                    if (TypeValues.MODIFIER_PROTECTED.equals(modifier)) {
                        accessModifier = AccessModifier.PROTECTED;
                        break;
                    }
                    if (TypeValues.MODIFIER_PRIVATE.equals(modifier)) {
                        accessModifier = AccessModifier.PRIVATE;
                        break;
                    }
                }
                if (accessModifier == null) {
                    accessModifier = AccessModifier.PACKAGE_PRIVATE;
                }
                target.accessModifier(accessModifier);
            }
            for (String modifier : target.modifiers()) {
                try {
                    target.addElementModifier(Modifier.valueOf(modifier.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // best effort - we need to skip access modifiers and unknown modifiers
                }
            }
            for (Modifier typeModifier : target.elementModifiers()) {
                target.addModifier(typeModifier.modifierName());
            }
            target.addModifier(target.accessModifier().get().modifierName());

            // new methods, simplify for tests
            if (target.rawType().isEmpty()) {
                target.typeName()
                        .map(TypeName::genericTypeName)
                        .ifPresent(target::rawType);
            }
            if (target.declaredType().isEmpty()) {
                // this may not be correct, but is correct for all types that do not have any declaration of generics
                // so it simplifies a lot of use cases
                target.rawType().ifPresent(target::declaredType);
            }
        }
    }
}
