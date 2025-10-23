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
package io.helidon.data.codegen.common;

import java.util.List;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

/**
 * Implemented interface info.
 *
 * @param typeName   type of the repository
 * @param entityType type of the entity
 * @param idType     type of the ID
 */
public record RepositoryInterfaceInfo(TypeName typeName, TypeName entityType, TypeName idType) {

    /**
     * Creates an instance of implemented interface info.
     *
     * @param interfaceInfo source type info
     * @return implemented interface info
     */
    public static RepositoryInterfaceInfo create(TypeInfo interfaceInfo) {
        List<TypeName> typeArguments = interfaceInfo.typeName()
                .typeArguments();
        if (typeArguments.size() == 2) {
            return new RepositoryInterfaceInfo(interfaceInfo.typeName(), typeArguments.get(0), typeArguments.get(1));
        } else {
            throw new CodegenException(
                    String.format("Incorrect number of %s type arguments, expected 2 but found %d",
                                  interfaceInfo.typeName().genericTypeName().name(),
                                  typeArguments.size()),
                    interfaceInfo.originatingElement().orElseGet(interfaceInfo::typeName));
        }

    }

}
