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
package io.helidon.data.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Query methods sorted into Lists by type.
 */
class QueryMethods {

    private final Map<Type, List<TypedElementInfo>> methods;

    private QueryMethods(Map<Type, List<TypedElementInfo>> methods) {
        this.methods = methods;
    }

    static Builder builder() {
        return new Builder();
    }

    // Methods assigned to specific methods generator type
    List<TypedElementInfo> methods(Type type) {
        return methods.getOrDefault(type, Collections.emptyList());
    }

    // Method types (to assign specific code generator)
    enum Type {
        // Method with Query annotation
        QUERY,
        // Query by method name
        BY_NAME;

        private static final int LENGTH = Type.values().length;

        private static final Map<TypeName, Type> TYPES_MAP = new HashMap<>(LENGTH);

        static {
            TYPES_MAP.put(HelidonDataTypes.QUERY_ANNOTATION, Type.QUERY);
        }

    }

    static class Builder implements io.helidon.common.Builder<Builder, QueryMethods> {

        // Methods split into Lists by type
        private final Map<Type, List<TypedElementInfo>> methods;

        private Builder() {
            this.methods = new HashMap<>(Type.LENGTH);
        }

        static boolean filterMethods(TypedElementInfo info) {
            return info.kind() == ElementKind.METHOD;
        }

        @Override
        public QueryMethods build() {
            return new QueryMethods(
                    Map.copyOf(methods.entrySet()
                                       .stream()
                                       .map(Builder::unmodifiableEntry)
                                       .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
        }

        Builder addMethod(TypedElementInfo methodInfo) {
            listByType(methodType(methodInfo))
                    .add(methodInfo);
            return this;
        }

        private static Map.Entry<Type, List<TypedElementInfo>> unmodifiableEntry(Map.Entry<Type, List<TypedElementInfo>> entry) {
            entry.setValue(List.copyOf(entry.getValue()));
            return entry;
        }

        // Compute methods list for specific type if missing
        private static List<TypedElementInfo> createList(Type type) {
            return new ArrayList<>();
        }

        // Compute method type from TypedElementInfo
        private static Type methodType(TypedElementInfo methodInfo) {
            Set<Type> types = new HashSet<>(Type.LENGTH);
            for (Annotation annotation : methodInfo.annotations()) {
                Type type = Type.TYPES_MAP.get(annotation.typeName());
                if (type != null) {
                    types.add(type);
                }
            }
            if (types.size() > 1) {
                throw new CodegenException("Method " + methodInfo.elementName()
                                                   + " contains multiple repository method annotations.");
            }
            return types.isEmpty()
                    ? Type.BY_NAME
                    // Set contains exactly one element
                    : types.iterator().next();
        }

        // Get methods list by Type
        private List<TypedElementInfo> listByType(Type methodType) {
            return methods.computeIfAbsent(methodType, Builder::createList);
        }

    }

}
