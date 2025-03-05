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

import java.util.Objects;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.common.spi.RepositoryGenerator;

/**
 * Specific persistence provider (e.g. Jakarta Persistence, EclipseLink native, ...) generator base class.
 */
public abstract class BasePersistenceGenerator
        extends BaseGenerator
        implements PersistenceGenerator {

    /**
     * Creates an instance of specific persistence provider generator base class.
     */
    protected BasePersistenceGenerator() {
        super();
    }

    @Override
    public void generate(CodegenContext codegenContext,
                         RoundContext roundContext,
                         TypeInfo interfaceInfo,
                         RepositoryGenerator repositoryGenerator) {
        Objects.requireNonNull(interfaceInfo, "Data repository interface info value is null");
        Objects.requireNonNull(codegenContext, "Codegen context value is null");
        Objects.requireNonNull(repositoryGenerator, "Data repository generator value is null");

        TypeName repositoryClassName = repositoryClassName(interfaceInfo.typeName());
        TypeName providerClassName = providerClassName(interfaceInfo.typeName());
        RepositoryInfo repositoryInfo = repositoryGenerator.createRepositoryInfo(interfaceInfo, codegenContext);

        generateRepositoryClass(codegenContext,
                                roundContext,
                                repositoryInfo,
                                repositoryClassName,
                                repositoryGenerator);
        generateRepositoryProvider(codegenContext,
                                   roundContext,
                                   repositoryInfo,
                                   providerClassName,
                                   repositoryClassName,
                                   repositoryGenerator);
    }

    /**
     * Data repository interface implementing class name for specific persistence provider.
     *
     * @param baseName repository interface name (target name base)
     * @return @return implementing class name
     */
    protected abstract TypeName repositoryClassName(TypeName baseName);

    /**
     * Data repository interface implementing class provider name for specific persistence provider.
     * This is the name of {@code RepositoryProvider} implementing class.
     *
     * @param baseName repository interface name (target name base)
     * @return implementing class provider name
     */
    protected abstract TypeName providerClassName(TypeName baseName);

    /**
     * Generate data repository interface implementing class for specific persistence provider.
     *
     * @param codegenContext      code processing and generation context.
     * @param roundContext        code processing and generation round contexts
     * @param repositoryInfo      data repository interface info
     * @param className           implementing class name (from {#link {@link #repositoryClassName(TypeName)}})
     * @param classModel          target implementing class model builder
     * @param repositoryGenerator specific data repository code generator
     */
    protected abstract void generateRepositoryClass(CodegenContext codegenContext,
                                                    RoundContext roundContext,
                                                    RepositoryGenerator repositoryGenerator,
                                                    RepositoryInfo repositoryInfo,
                                                    TypeName className,
                                                    ClassModel.Builder classModel);

    /**
     * Generate data repository interface implementing class provider for specific persistence provider.
     *
     * @param codegenContext      code processing and generation context
     * @param roundContext        code processing and generation round context
     * @param repositoryInfo      data repository interface info
     * @param className           implementing provider class name (from {#link {@link #providerClassName(TypeName)}})
     * @param repositoryClassName data repository implementation class name
     * @param classModel          target implementing provider class model builder
     * @param repositoryGenerator specific data repository code generator
     */
    protected abstract void generateRepositoryProvider(CodegenContext codegenContext,
                                                       RoundContext roundContext,
                                                       RepositoryGenerator repositoryGenerator,
                                                       RepositoryInfo repositoryInfo,
                                                       TypeName className,
                                                       TypeName repositoryClassName,
                                                       ClassModel.Builder classModel);

    private void generateRepositoryClass(CodegenContext codegenContext,
                                         RoundContext roundContext,
                                         RepositoryInfo repositoryInfo,
                                         TypeName className,
                                         RepositoryGenerator repositoryGenerator) {
        ClassModel.Builder classModel = ClassModel.builder();
        generateRepositoryClass(codegenContext,
                                roundContext,
                                repositoryGenerator,
                                repositoryInfo,
                                className,
                                classModel);

        roundContext.addGeneratedType(className,
                                      classModel,
                                      repositoryInfo.interfaceInfo().typeName(),
                                      repositoryInfo.interfaceInfo().originatingElementValue());
    }

    private void generateRepositoryProvider(CodegenContext codegenContext,
                                            RoundContext roundContext,
                                            RepositoryInfo repositoryInfo,
                                            TypeName className,
                                            TypeName repositoryClassName,
                                            RepositoryGenerator repositoryGenerator) {
        ClassModel.Builder classModel = ClassModel.builder();
        generateRepositoryProvider(codegenContext,
                                   roundContext,
                                   repositoryGenerator,
                                   repositoryInfo,
                                   className,
                                   repositoryClassName,
                                   classModel);

        roundContext.addGeneratedType(className,
                                      classModel,
                                      repositoryInfo.interfaceInfo().typeName(),
                                      repositoryInfo.interfaceInfo().originatingElementValue());
    }

}
