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

import java.util.Optional;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.RepositoryInfo;

// Interfaces info builder.
class RepositoryInfoBuilder extends RepositoryInfo.Builder {

    RepositoryInfoBuilder(CodegenContext codegenContext) {
        super(codegenContext);
    }

    @Override
    public RepositoryInfo build() {
        // Search for entity class from top level interfaces
        // FIXME: there may be user's interfaces above known Jakarta data interfaces
        TypeName entity = TypeName.create(Object.class);
        TypeName id = TypeName.create(Object.class);
        if (interfaces().containsKey(HelidonDataTypes.CRUD_REPOSITORY)) {
            entity = interfaces().get(HelidonDataTypes.CRUD_REPOSITORY).entityType();
            id = interfaces().get(HelidonDataTypes.CRUD_REPOSITORY).idType();
        } else if (interfaces().containsKey(HelidonDataTypes.BASIC_REPOSITORY)) {
            entity = interfaces().get(HelidonDataTypes.BASIC_REPOSITORY).entityType();
            id = interfaces().get(HelidonDataTypes.BASIC_REPOSITORY).idType();
        } else if (interfaces().containsKey(HelidonDataTypes.PAGEABLE_REPOSITORY)) {
            entity = interfaces().get(HelidonDataTypes.PAGEABLE_REPOSITORY).entityType();
            id = interfaces().get(HelidonDataTypes.PAGEABLE_REPOSITORY).idType();
        } else if (interfaces().containsKey(HelidonDataTypes.GENERIC_REPOSITORY)) {
            entity = interfaces().get(HelidonDataTypes.GENERIC_REPOSITORY).entityType();
            id = interfaces().get(HelidonDataTypes.GENERIC_REPOSITORY).idType();
        }
        Optional<TypeInfo> maybeEntityInfo = codegenContext().typeInfo(entity);
        if (maybeEntityInfo.isEmpty()) {
            throw new CodegenException("Could not find " + entity + " entity type information");
        }
        return new RepositoryInfo(interfaceInfo(), interfaces(), maybeEntityInfo.get(), id);
    }

}
