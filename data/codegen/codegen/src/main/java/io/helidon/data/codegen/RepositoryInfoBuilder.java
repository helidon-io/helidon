/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.RepositoryInfo;

/**
 * Builds metadata for repository interfaces.
 */
class RepositoryInfoBuilder extends RepositoryInfo.Builder {

    RepositoryInfoBuilder(CodegenContext codegenContext) {
        super(codegenContext);
    }

    /**
     * Builds repository metadata, including repositories that do not declare an entity type.
     *
     * @return repository metadata
     * @throws CodegenException if metadata for a declared entity type is unavailable
     */
    @Override
    public RepositoryInfo build() {
        TypeName entity = TypeName.create(Object.class);
        TypeName id = TypeName.create(Object.class);
        if (interfaces().containsKey(DataCodegenTypes.CRUD_REPOSITORY)) {
            entity = interfaces().get(DataCodegenTypes.CRUD_REPOSITORY).entityType();
            id = interfaces().get(DataCodegenTypes.CRUD_REPOSITORY).idType();
        } else if (interfaces().containsKey(DataCodegenTypes.BASIC_REPOSITORY)) {
            entity = interfaces().get(DataCodegenTypes.BASIC_REPOSITORY).entityType();
            id = interfaces().get(DataCodegenTypes.BASIC_REPOSITORY).idType();
        } else if (interfaces().containsKey(DataCodegenTypes.PAGEABLE_REPOSITORY)) {
            entity = interfaces().get(DataCodegenTypes.PAGEABLE_REPOSITORY).entityType();
            id = interfaces().get(DataCodegenTypes.PAGEABLE_REPOSITORY).idType();
        } else if (interfaces().containsKey(DataCodegenTypes.GENERIC_REPOSITORY)) {
            entity = interfaces().get(DataCodegenTypes.GENERIC_REPOSITORY).entityType();
            id = interfaces().get(DataCodegenTypes.GENERIC_REPOSITORY).idType();
        }
        Optional<TypeInfo> maybeEntityInfo = codegenContext().typeInfo(entity);
        if (maybeEntityInfo.isEmpty()) {
            if (interfaces().isEmpty() && entity.equals(TypeName.create(Object.class))) {
                // Repositories selected only by annotation have no entity metadata. An Object descriptor preserves the
                // existing RepositoryInfo contract without requiring an entity.
                TypeInfo placeholder = TypeInfo.builder()
                        .typeName(entity)
                        .kind(ElementKind.CLASS)
                        .accessModifier(AccessModifier.PUBLIC)
                        .build();
                return new RepositoryInfo(interfaceInfo(), interfaces(), placeholder, id);
            }
            throw new CodegenException("Could not find " + entity + " entity type information");
        }
        return new RepositoryInfo(interfaceInfo(), interfaces(), maybeEntityInfo.get(), id);
    }

}
