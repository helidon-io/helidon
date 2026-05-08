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

import io.helidon.builder.api.Prototype;

final class TypeInfoSupport {
    private TypeInfoSupport() {
    }

    static final class TypeInfoDecorator implements Prototype.BuilderDecorator<TypeInfo.BuilderBase<?, ?>> {
        @Override
        public void decorate(TypeInfo.BuilderBase<?, ?> target) {
            if (target.accessModifier().isEmpty()) {
                target.accessModifier(AccessModifier.PACKAGE_PRIVATE);
            }

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
