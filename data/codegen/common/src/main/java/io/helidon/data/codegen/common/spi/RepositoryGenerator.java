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
package io.helidon.data.codegen.common.spi;

import java.util.Map;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.RepositoryInterfaceInfo;

/**
 * Specific data repository (e.g. Jakarta Data, Micronaut Data, ...) generator.
 * Defines generated data repository interfaces implementations.
 */
public interface RepositoryGenerator {

    /**
     * Supported data repository annotations for repository code processing and generation.
     *
     * @return supported annotations
     */
    Set<TypeName> annotations();

    /**
     * Supported data repository interfaces for repository code processing and generation.
     *
     * @return supported interfaces
     */
    Set<TypeName> interfaces();

    /**
     * Create data repository interface descriptor (info).
     * Contains list of provider specific interfaces to be implemented and entity and ID types.
     *
     * @param interfaceInfo data repository interface type information
     * @param codegenContext code processing and generation context
     * @return data repository interface descriptor
     */
    RepositoryInfo createRepositoryInfo(TypeInfo interfaceInfo, CodegenContext codegenContext);

    /**
     * Return the most generic provider specific interface from provided interfaces.
     *
     * @param interfacesInfo data repository implemented interfaces {@link Map}.
     * @return the most generic interface
     */
    RepositoryInterfaceInfo genericInterface(Map<TypeName, RepositoryInterfaceInfo> interfacesInfo);

    /**
     * Generate repository interfaces (e.g. CrudRepository) methods.
     *
     * @param repositoryInfo data repository interface info
     * @param classModel target class builder
     * @param codegenContext code processing and generation context
     * @param persistenceGenerator persistence provider specific generator
     */
    void generateInterfaces(RepositoryInfo repositoryInfo,
                            ClassModel.Builder classModel,
                            CodegenContext codegenContext,
                            PersistenceGenerator persistenceGenerator);

    /**
     * Generate query by method name methods.
     *
     * @param repositoryInfo data repository interface info
     * @param classModel target class builder
     * @param codegenContext code processing and generation context
     * @param persistenceGenerator persistence provider specific generator
     */
    void generateQueryMethods(RepositoryInfo repositoryInfo,
                              ClassModel.Builder classModel,
                              CodegenContext codegenContext,
                              PersistenceGenerator persistenceGenerator);

}
