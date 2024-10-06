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

import java.util.Objects;
import java.util.Set;

import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

/**
 * A described type (class, interface).
 * User service can have up to two types - one is the service itself, another one is a provided contract,
 * if the service is a provider.
 */
class DescribedType {
    private final TypeInfo typeInfo;
    private final boolean isAbstract;
    private final TypeName typeName;
    private final Set<TypeName> contracts;
    private final DescribedElements elements;

    DescribedType(TypeInfo typeInfo, TypeName typeName, Set<TypeName> contracts, DescribedElements elements) {
        Objects.requireNonNull(typeInfo);
        Objects.requireNonNull(typeName);
        Objects.requireNonNull(contracts);
        Objects.requireNonNull(elements);

        this.typeInfo = typeInfo;
        this.isAbstract = isAbstract(typeInfo);
        this.typeName = typeName;
        this.contracts = contracts;
        this.elements = elements;
    }

    boolean isAbstract() {
        return isAbstract;
    }

    TypeInfo typeInfo() {
        return typeInfo;
    }

    TypeName typeName() {
        return typeName;
    }

    Set<TypeName> contracts() {
        return contracts;
    }

    DescribedElements elements() {
        return elements;
    }

    private static boolean isAbstract(TypeInfo typeInfo) {
        if (typeInfo == null) {
            return false;
        }
        if (typeInfo.kind() == ElementKind.CLASS) {
            return typeInfo.elementModifiers().contains(Modifier.ABSTRACT);
        }
        return typeInfo.kind() == ElementKind.INTERFACE;
    }
}
