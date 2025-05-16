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
package io.helidon.data.jakarta.persistence.codegen;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.BasePersistenceGenerator;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.common.spi.RepositoryGenerator;

/**
 * Jakarta Persistence generator.
 */
class JakartaPersistenceGenerator extends BasePersistenceGenerator {

    static final TypeName GENERATOR = TypeName.create(JakartaPersistenceGenerator.class);

    JakartaPersistenceGenerator() {
        super();
    }

    @Override
    public PersistenceGenerator.QueryBuilder queryBuilder(RepositoryInfo repositoryInfo) {
        return new JakartaQueryBuilder(repositoryInfo);
    }

    @Override
    public PersistenceGenerator.StatementGenerator statementGenerator() {
        return new JakartaStatementGenerator();
    }

    @Override
    protected void generateRepositoryClass(CodegenContext codegenContext,
                                           RoundContext roundContext,
                                           RepositoryGenerator repositoryGenerator,
                                           RepositoryInfo repositoryInfo,
                                           TypeName className,
                                           ClassModel.Builder classModel) {

        JakartaRepositoryClassGenerator.generate(codegenContext,
                                                 roundContext,
                                                 repositoryGenerator,
                                                 repositoryInfo,
                                                 className,
                                                 classModel,
                                                 this);

    }

    @Override
    protected TypeName repositoryClassName(TypeName baseName) {
        return TypeName.builder(baseName)
                .className(baseName.className() + "__Jpa")
                .build();
    }

    @Override
    protected TypeName providerClassName(TypeName baseName) {
        return TypeName.builder(baseName)
                .className(baseName.className() + "__JpaProvider")
                .build();
    }

    @Override
    protected void generateRepositoryProvider(CodegenContext codegenContext,
                                              RoundContext roundContext,
                                              RepositoryGenerator repositoryGenerator,
                                              RepositoryInfo repositoryInfo,
                                              TypeName className,
                                              TypeName repositoryClassName,
                                              ClassModel.Builder classModel) {
        JakartaRepositoryProviderGenerator.generate(codegenContext,
                                                    roundContext,
                                                    repositoryGenerator,
                                                    repositoryInfo,
                                                    className,
                                                    repositoryClassName,
                                                    classModel);
    }

}
