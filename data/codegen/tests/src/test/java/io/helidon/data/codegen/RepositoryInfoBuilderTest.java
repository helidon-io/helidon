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
package io.helidon.data.codegen;

import java.util.Optional;

import io.helidon.codegen.CodegenContext;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.RepositoryInterfaceInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositoryInfoBuilderTest {

    @Test
    void representsAnnotationOnlyRepositoryWithoutApplicationEntityMetadata() {
        CodegenContext context = mock(CodegenContext.class);
        when(context.typeInfo(TypeName.create(Object.class))).thenReturn(Optional.empty());
        TypeInfo repository = TypeInfo.builder()
                .typeName(TypeName.create("example.AnnotationOnlyRepository"))
                .kind(ElementKind.INTERFACE)
                .build();

        RepositoryInfo result = new RepositoryInfoBuilder(context)
                .interfaceInfo(repository)
                .build();

        assertThat(result.interfacesInfo().isEmpty(), is(true));
        assertThat(result.entityInfo().typeName(), is(TypeName.create(Object.class)));
        assertThat(result.entityInfo().kind(), is(ElementKind.CLASS));
    }

    @Test
    void preservesDeclaredEntityMetadataWithoutUsingThePlaceholder() {
        TypeName entityType = TypeName.create("example.Contact");
        TypeName idType = TypeName.create(Long.class);
        TypeInfo entityInfo = TypeInfo.builder()
                .typeName(entityType)
                .kind(ElementKind.RECORD)
                .build();
        CodegenContext context = mock(CodegenContext.class);
        when(context.typeInfo(entityType)).thenReturn(Optional.of(entityInfo));
        TypeInfo repository = TypeInfo.builder()
                .typeName(TypeName.create("example.ContactRepository"))
                .kind(ElementKind.INTERFACE)
                .build();

        RepositoryInfo result = new RepositoryInfoBuilder(context)
                .interfaceInfo(repository)
                .addInterface(DataCodegenTypes.CRUD_REPOSITORY,
                              new RepositoryInterfaceInfo(DataCodegenTypes.CRUD_REPOSITORY, entityType, idType))
                .build();

        assertThat(result.entityInfo(), is(entityInfo));
        assertThat(result.id(), is(idType));
    }
}
