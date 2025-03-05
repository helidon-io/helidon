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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.BaseRepositoryGenerator;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.RepositoryInterfaceGenerator;
import io.helidon.data.codegen.common.RepositoryInterfaceInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;

class HelidonDataGenerator extends BaseRepositoryGenerator {

    private static final Map<TypeName, GeneratorFactory> GENERATOR_FACTORIES;

    static {
        Map<TypeName, GeneratorFactory> generatorFactories = new HashMap<>();
        generatorFactories.put(HelidonDataTypes.BASIC_REPOSITORY, BasicRepositoryInterfaceGenerator::new);
        generatorFactories.put(HelidonDataTypes.CRUD_REPOSITORY, CrudRepositoryInterfaceGenerator::new);
        generatorFactories.put(HelidonDataTypes.PAGEABLE_REPOSITORY, PageableRepositoryInterfaceGenerator::new);
        GENERATOR_FACTORIES = Map.copyOf(generatorFactories);
    }

    HelidonDataGenerator() {
        super();
    }

    @Override
    public Set<TypeName> annotations() {
        return HelidonDataTypes.ANNOTATIONS;
    }

    @Override
    public Set<TypeName> interfaces() {
        return HelidonDataTypes.INTERFACES;
    }

    @Override
    public RepositoryInterfaceInfo genericInterface(Map<TypeName, RepositoryInterfaceInfo> interfacesInfo) {
        Objects.requireNonNull(interfacesInfo, "Implemented interfaces Map value is null");
        for (TypeName name : HelidonDataTypes.INTERFACES_PRIORITY) {
            if (interfacesInfo.containsKey(name)) {
                return interfacesInfo.get(name);
            }
        }
        throw new CodegenException("Provided implemented interfaces Map did not contain any known interface");

    }

    @Override
    public void generateInterfaces(RepositoryInfo repositoryInfo,
                                   ClassModel.Builder classModel,
                                   CodegenContext codegenContext,
                                   PersistenceGenerator persistenceGenerator) {
        for (TypeName interfaceName : repositoryInfo.interfacesInfo().keySet()) {
            if (GENERATOR_FACTORIES.containsKey(interfaceName)) {
                RepositoryInterfaceGenerator generator = GENERATOR_FACTORIES.get(interfaceName)
                        .create(repositoryInfo,
                                classModel,
                                codegenContext,
                                persistenceGenerator);
                generator.generate();
            }
        }
    }

    @Override
    public void generateQueryMethods(RepositoryInfo repositoryInfo,
                                     ClassModel.Builder classModel,
                                     CodegenContext codegenContext,
                                     PersistenceGenerator persistenceGenerator) {
        // Process all methods and split them by (code generator) type
        QueryMethods.Builder builder = QueryMethods.builder();
        repositoryInfo.interfaceInfo().elementInfo()
                .stream()
                .filter(QueryMethods.Builder::filterMethods)
                .forEach(builder::addMethod);
        // Methods lists by (code generator) type
        QueryMethods methods = builder.build();
        // Query by name methods
        QueryByNameMethodsGenerator.generate(repositoryInfo,
                                             methods.methods(QueryMethods.Type.BY_NAME),
                                             classModel,
                                             codegenContext,
                                             persistenceGenerator);
        // Query by @Data.Query
        QueryByJpqlMethodsGenerator.generate(repositoryInfo,
                                             methods.methods(QueryMethods.Type.QUERY),
                                             classModel,
                                             codegenContext,
                                             persistenceGenerator);
    }

    @Override
    protected RepositoryInfo.Builder repositoryInfoBuilder(CodegenContext codegenContext) {
        return new RepositoryInfoBuilder(codegenContext);
    }
}
