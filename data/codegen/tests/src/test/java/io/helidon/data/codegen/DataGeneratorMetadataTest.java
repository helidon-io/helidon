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
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.TypeHierarchyResolver;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.BaseRepositoryGenerator;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataGeneratorMetadataTest {

    /**
     * Verifies that public repository metadata entry points reject null inputs
     * before they inspect the repository hierarchy or code generation context.
     */
    @Test
    void rejectsNullMetadataInputs() {
        DataGenerator generator = new DataGenerator();
        CodegenContext context = mock(CodegenContext.class);
        TypeInfo repository = TypeInfo.builder()
                .typeName(TypeName.create("example.BookRepository"))
                .kind(ElementKind.INTERFACE)
                .build();

        NullPointerException missingInterface = assertThrows(
                NullPointerException.class,
                () -> generator.createRepositoryInfo(null, context));
        assertThat(missingInterface.getMessage(), is("The repository interface information must not be null."));

        NullPointerException missingContext = assertThrows(
                NullPointerException.class,
                () -> generator.createRepositoryInfo(repository, null));
        assertThat(missingContext.getMessage(), is("The code generation context must not be null."));

        NullPointerException missingHierarchy = assertThrows(
                NullPointerException.class,
                () -> BaseRepositoryGenerator.hasInterface(null, DataCodegenTypes.GENERIC_REPOSITORY));
        assertThat(missingHierarchy.getMessage(), is("The interface information must not be null."));

        NullPointerException missingName = assertThrows(
                NullPointerException.class,
                () -> BaseRepositoryGenerator.hasInterface(repository, null));
        assertThat(missingName.getMessage(), is("The interface name must not be null."));
    }

    /**
     * Verifies that an inconsistent hierarchy resolver produces a concise
     * diagnostic without including repository or type names.
     */
    @Test
    void reportsSanitizedHierarchyResolutionFailure() {
        TypeName entityType = TypeName.create("example.Book");
        TypeName repositoryType = TypeName.create("example.BookRepository");
        TypeInfo genericRepository = TypeInfo.builder()
                .typeName(TypeName.builder(DataCodegenTypes.GENERIC_REPOSITORY)
                                  .addTypeArgument(entityType)
                                  .addTypeArgument(TypeName.create(Long.class))
                                  .build())
                .kind(ElementKind.INTERFACE)
                .build();
        TypeInfo repository = TypeInfo.builder()
                .typeName(repositoryType)
                .kind(ElementKind.INTERFACE)
                .addInterfaceTypeInfo(genericRepository)
                .build();
        CodegenContext context = mock(CodegenContext.class);
        when(context.typeHierarchyResolver(any()))
                .thenReturn(new EmptyTypeHierarchyResolver());

        CodegenException failure = assertThrows(
                CodegenException.class,
                () -> new DataGenerator().createRepositoryInfo(repository, context));

        assertThat(failure.getMessage(),
                   is("Helidon could not resolve the repository's inherited type arguments."));
    }

    private static final class EmptyTypeHierarchyResolver extends TypeHierarchyResolver {

        private EmptyTypeHierarchyResolver() {
            super(_ -> Optional.empty());
        }
    }
}
