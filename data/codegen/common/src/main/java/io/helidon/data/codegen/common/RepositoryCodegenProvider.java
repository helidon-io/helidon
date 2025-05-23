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

import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.common.spi.PersistenceGeneratorProvider;
import io.helidon.data.codegen.common.spi.RepositoryGenerator;
import io.helidon.data.codegen.common.spi.RepositoryGeneratorProvider;

/**
 * Data repository {@link CodegenExtension} provider.
 */
public class RepositoryCodegenProvider implements CodegenExtensionProvider {

    // All available data repository generators.
    static final List<RepositoryGenerator> DATA_REPOSITORY_GENERATORS;
    // All available persistence generators.
    static final List<PersistenceGenerator> PERSISTENCE_GENERATORS;
    // All supported annotations.
    static final Set<TypeName> SUPPORTED_ANNOTATIONS;

    static {
        List<RepositoryGeneratorProvider> repositoryGeneratorProviders = HelidonServiceLoader.create(
                        ServiceLoader.load(RepositoryGeneratorProvider.class,
                                           RepositoryCodegen.class.getClassLoader()))
                .asList();
        List<PersistenceGeneratorProvider> persistenceGeneratorProviders = HelidonServiceLoader.create(
                        ServiceLoader.load(PersistenceGeneratorProvider.class,
                                           RepositoryCodegen.class.getClassLoader()))
                .asList();
        DATA_REPOSITORY_GENERATORS = repositoryGeneratorProviders.stream()
                .map(RepositoryGeneratorProvider::create)
                .collect(Collectors.toUnmodifiableList());
        PERSISTENCE_GENERATORS = persistenceGeneratorProviders.stream()
                .map(PersistenceGeneratorProvider::create)
                .collect(Collectors.toUnmodifiableList());
        Set<TypeName> supportedAnnotations = new HashSet<>();
        DATA_REPOSITORY_GENERATORS.forEach(generator -> supportedAnnotations.addAll(generator.annotations()));
        SUPPORTED_ANNOTATIONS = Set.copyOf(supportedAnnotations);
    }

    /**
     * Creates an instance of Data repository {@link CodegenExtension} provider.
     */
    public RepositoryCodegenProvider() {
    }

    @Override
    public CodegenExtension create(CodegenContext ctx, TypeName typeName) {
        return new RepositoryCodegen(ctx, DATA_REPOSITORY_GENERATORS, PERSISTENCE_GENERATORS);
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return SUPPORTED_ANNOTATIONS;
    }

}
