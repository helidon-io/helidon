/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.mapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.Default;
import io.helidon.common.GenericType;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;

@Service.Singleton
class DefaultResolverService implements DefaultsResolver {
    private static final TypeName VALUE_TYPE = TypeName.create(Default.Value.class);
    private static final TypeName INT_TYPE = TypeName.create(Default.Int.class);
    private static final TypeName DOUBLE_TYPE = TypeName.create(Default.Double.class);
    private static final TypeName BOOLEAN_TYPE = TypeName.create(Default.Boolean.class);
    private static final TypeName LONG_TYPE = TypeName.create(Default.Long.class);

    private final Supplier<Mappers> mappers;

    @Service.Inject
    DefaultResolverService(Supplier<Mappers> mappers) {
        this.mappers = mappers;
    }

    @Override
    public List<?> resolve(Set<Annotation> annotations, GenericType<?> expectedType, String name, Object context) {
        /*
         *     io.helidon.common.Default.Value
         *     io.helidon.common.Default.Int
         *     io.helidon.common.Default.Double
         *     io.helidon.common.Default.Boolean
         *     io.helidon.common.Default.Long
         *
         * Same order as in DefaultsCodegen class
         */

        Optional<Annotation> found = Annotations.findFirst(VALUE_TYPE, annotations);
        if (found.isPresent()) {
            return value(found.get(), expectedType);
        }
        found = Annotations.findFirst(INT_TYPE, annotations);
        if (found.isPresent()) {
            return found.get().intValues().orElseGet(List::of);
        }
        found = Annotations.findFirst(DOUBLE_TYPE, annotations);
        if (found.isPresent()) {
            return found.get().doubleValues().orElseGet(List::of);
        }
        found = Annotations.findFirst(BOOLEAN_TYPE, annotations);
        if (found.isPresent()) {
            return found.get().booleanValues().orElseGet(List::of);
        }
        found = Annotations.findFirst(LONG_TYPE, annotations);
        if (found.isPresent()) {
            return found.get().longValues().orElseGet(List::of);
        }
        return List.of();
    }

    private List<Object> value(Annotation annotation, GenericType<?> expectedType) {
        List<String> values = annotation.stringValues().orElseGet(List::of);

        return values.stream()
                .map(it -> mappers.get().map(it, GenericType.STRING, expectedType, "defaults"))
                .collect(Collectors.toUnmodifiableList());
    }
}
