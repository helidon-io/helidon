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

    // The generated source records its origin for traceability.
    static final TypeName GENERATOR = TypeName.create(JdbcPersistenceGenerator.class);

    @Override
    public QueryBuilder queryBuilder(RepositoryInfo repositoryInfo) {
        throw new UnsupportedOperationException("JDBC repositories do not use entity query generation");
    }

    @Override
    public StatementGenerator statementGenerator() {
        throw new UnsupportedOperationException("JDBC repositories do not use entity statement generation");
    }

    @Override
    protected String provider() {
        return JdbcCodegenConstants.PROVIDER;
    }

    @Override
    protected TypeName repositoryClassName(TypeName baseName) {
        return TypeName.builder()
                .packageName(baseName.packageName())
                .className(baseName.classNameWithEnclosingNames().replace('.', '_')
                                   + JdbcCodegenConstants.REPOSITORY_SUFFIX)
                .build();
    }

    @Override
    protected void generateRepositoryClass(CodegenContext codegenContext,
                                           RoundContext roundContext,
                                           RepositoryGenerator repositoryGenerator,
                                           RepositoryInfo repositoryInfo,
                                           TypeName className,
                                           ClassModel.Builder classModel) {
        // Entity repository parents request derived persistence operations.
        // JDBC repositories support explicit SQL only.
        if (!repositoryInfo.interfacesInfo().isEmpty()) {
            throw new CodegenException("JDBC repositories must use explicit annotated SQL methods and "
                                               + "must not extend entity-oriented repository interfaces",
                                       repositoryInfo.interfaceInfo().originatingElementValue());
        }
        JdbcRepositoryClassGenerator.generate(roundContext,
                                               repositoryInfo,
                                               className,
                                               classModel);
    }
}
