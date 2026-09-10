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
package io.helidon.data.codegen.common;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.TypeHierarchyResolver;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.common.spi.RepositoryGenerator;

/**
 * Base implementation for Data repository generators such as Jakarta Data.
 */
public abstract class BaseRepositoryGenerator implements RepositoryGenerator {

    /**
     * Creates an instance of specific data repository generator base class.
     */
    protected BaseRepositoryGenerator() {
    }

    /**
     * Searches for {@code interfaceName} in the interface hierarchy represented by {@link TypeInfo}.
     *
     * @param interfaceInfo interfaces hierarchy to walk through
     * @param interfaceName interface name to search for
     * @return value of {@code true} when interfaces hierarchy contains {@code interfaceName} or {@code false} otherwise
     * @throws NullPointerException if {@code interfaceInfo} or {@code interfaceName} is {@code null}
     */
    public static boolean hasInterface(TypeInfo interfaceInfo, TypeName interfaceName) {
        Objects.requireNonNull(interfaceInfo, "The interface information must not be null.");
        Objects.requireNonNull(interfaceName, "The interface name must not be null.");
        AtomicBoolean result = new AtomicBoolean();
        result.set(false);
        StreamSupport.stream(new TypeInfoSpliterator(interfaceInfo), false)
                .forEach(info -> {
                    if (info.typeName().equals(interfaceName)) {
                        result.set(true);
                    }
                });
        return result.get();
    }

    /**
     * Creates metadata for a Data repository interface.
     * Generic parent interfaces are resolved from the repository use site so
     * their concrete entity and identifier types are retained.
     *
     * @param interfaceInfo  data repository interface type
     * @param codegenContext code processing and generation context
     * @return data repository interface info
     * @throws NullPointerException if {@code interfaceInfo} or {@code codegenContext} is {@code null}
     * @throws CodegenException if the repository interface hierarchy cannot be resolved
     */
    @Override
    public RepositoryInfo createRepositoryInfo(TypeInfo interfaceInfo, CodegenContext codegenContext) {
        Objects.requireNonNull(interfaceInfo, "The repository interface information must not be null.");
        Objects.requireNonNull(codegenContext, "The code generation context must not be null.");
        RepositoryInfo.Builder builder = repositoryInfoBuilder(codegenContext);
        builder.interfaceInfo(interfaceInfo);
        Set<TypeName> repositoryInterfaces = interfaces();
        TypeHierarchyResolver typeHierarchyResolver = codegenContext.typeHierarchyResolver(typeName -> {
            if (interfaceInfo.typeName().genericTypeName().equals(typeName.genericTypeName())) {
                return Optional.of(interfaceInfo);
            }
            return codegenContext.typeInfo(typeName);
        });
        StreamSupport.stream(new TypeInfoSpliterator(interfaceInfo), false)
                .forEach(info -> {
                    TypeName rawType = info.typeName().genericTypeName();
                    if (!interfaceInfo.typeName().equals(info.typeName())
                            && repositoryInterfaces.contains(rawType)) {
                        // TypeInfo represents the parent declaration, which may still contain its type variables.
                        // Resolve from the repository use site so source and compiled parents retain concrete arguments.
                        TypeName resolvedType = typeHierarchyResolver.resolveSupertype(interfaceInfo.typeName(), rawType)
                                .orElseThrow(() -> new CodegenException(
                                        "Helidon could not resolve the repository's inherited type arguments.",
                                        interfaceInfo.originatingElement().orElseGet(interfaceInfo::typeName)));
                        TypeInfo resolvedInfo = TypeInfo.builder(info)
                                .typeName(resolvedType)
                                .build();
                        builder.addInterface(rawType, RepositoryInterfaceInfo.create(resolvedInfo));
                    }
                });
        return builder.build();
    }

    /**
     * Data repository specific repository interface info builder ({@link RepositoryInfo.Builder}).
     *
     * @param codegenContext code processing and generation context
     * @return repository interface info builder
     */
    protected abstract RepositoryInfo.Builder repositoryInfoBuilder(CodegenContext codegenContext);

    /**
     * Repository interface generator factory.
     */
    protected interface GeneratorFactory {
        /**
         * Create repository interface code generator.
         *
         * @param repositoryInfo       data repository interface info
         * @param classModel           target class builder
         * @param codegenContext       code processing and generation context
         * @param persistenceGenerator persistence provider specific generator
         * @return new instance of interface code generator
         */
        RepositoryInterfaceGenerator create(RepositoryInfo repositoryInfo,
                                            ClassModel.Builder classModel,
                                            CodegenContext codegenContext,
                                            PersistenceGenerator persistenceGenerator);
    }

}
