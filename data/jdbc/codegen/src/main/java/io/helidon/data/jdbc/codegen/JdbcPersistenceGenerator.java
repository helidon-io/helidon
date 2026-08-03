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
package io.helidon.data.jdbc.codegen;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.BasePersistenceGenerator;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator.QueryBuilder;
import io.helidon.data.codegen.common.spi.PersistenceGenerator.StatementGenerator;
import io.helidon.data.codegen.common.spi.RepositoryGenerator;

/**
 * Persistence generator for explicit SQL repositories using the JDBC client.
 */
final class JdbcPersistenceGenerator extends BasePersistenceGenerator {

    // Recorded in generated source so its origin remains traceable.
    static final TypeName GENERATOR = TypeName.create(JdbcPersistenceGenerator.class);

    /** {@inheritDoc} */
    @Override
    protected String provider() {
        return JdbcCodegenConstants.PROVIDER;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean generateByDefault() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected TypeName repositoryClassName(TypeName baseName) {
        return TypeName.builder()
                .packageName(baseName.packageName())
                .className(baseName.classNameWithEnclosingNames().replace('.', '_')
                                   + JdbcCodegenConstants.REPOSITORY_SUFFIX)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public QueryBuilder queryBuilder(RepositoryInfo repositoryInfo) {
        throw new UnsupportedOperationException("JDBC repositories do not use entity query generation");
    }

    /** {@inheritDoc} */
    @Override
    public StatementGenerator statementGenerator() {
        throw new UnsupportedOperationException("JDBC repositories do not use entity statement generation");
    }

    /** {@inheritDoc} */
    @Override
    protected void generateRepositoryClass(CodegenContext codegenContext,
                                           RoundContext roundContext,
                                           RepositoryGenerator repositoryGenerator,
                                           RepositoryInfo repositoryInfo,
                                           TypeName className,
                                           ClassModel.Builder classModel) {
        if (!repositoryInfo.interfacesInfo().isEmpty()) {
            throw new CodegenException("JDBC repositories must use explicit annotated SQL methods and "
                                               + "must not extend entity-oriented repository interfaces",
                                       repositoryInfo.interfaceInfo().originatingElementValue());
        }
        JdbcRepositoryClassGenerator.generate(codegenContext,
                                               repositoryInfo,
                                               className,
                                               classModel);
    }
}
