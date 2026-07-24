/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json.binding;

import java.util.List;

import io.helidon.common.GenericType;

final class JsonBindingTypes {
    private static final ClassValue<GenericType<?>> LIST_TYPES = new ClassValue<>() {
        @Override
        protected GenericType<?> computeValue(Class<?> type) {
            return GenericType.builder()
                    .baseType(List.class)
                    .addGenericParameter(type)
                    .build();
        }
    };

    private JsonBindingTypes() {
    }

    @SuppressWarnings("unchecked")
    static <T> GenericType<List<T>> listType(Class<? super T> itemType) {
        return (GenericType<List<T>>) LIST_TYPES.get(itemType);
    }
}
