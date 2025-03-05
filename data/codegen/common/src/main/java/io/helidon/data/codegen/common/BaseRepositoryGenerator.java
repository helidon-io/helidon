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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;
import io.helidon.data.codegen.common.spi.RepositoryGenerator;

/**
 * Specific data repository (e.g. Jakarta Data, Micronaut Data, ...) generator base class.
 */
public abstract class BaseRepositoryGenerator implements RepositoryGenerator {

    /**
     * Creates an instance of specific data repository generator base class.
     */
    protected BaseRepositoryGenerator() {
    }

    /**
     * Data repository specific repository interface info builder ({@link RepositoryInfo.Builder}).
     *
     * @param codegenContext code processing and generation context
     * @return repository interface info builder
     */
    protected abstract RepositoryInfo.Builder repositoryInfoBuilder(CodegenContext codegenContext);

    /**
     * Create data repository interface info.
     *
     * @param interfaceInfo  data repository interface type
     * @param codegenContext code processing and generation context
     * @return data repository interface info
     */
    @Override
    public RepositoryInfo createRepositoryInfo(TypeInfo interfaceInfo, CodegenContext codegenContext) {
        RepositoryInfo.Builder builder = repositoryInfoBuilder(codegenContext);
        builder.interfaceInfo(interfaceInfo);
        StreamSupport.stream(new TypeInfoSpliterator(interfaceInfo), false)
                .forEach(info -> {
                    if (!interfaceInfo.typeName().equals(info.typeName())
                            // FIXME: This filter shall be better
                            && info.typeName().typeArguments().size() == 2) {
                        builder.addInterface(info.typeName(), RepositoryInterfaceInfo.create(info));
                    }
                });
        return builder.build();
    }

    /**
     * Search for the provided {@code interfaceName} in {@link TypeInfo} interfaces hierarchy.
     *
     * @param interfaceInfo interfaces hierarchy to walk through
     * @param interfaceName interface name to search for
     * @return value of {@code true} when interfaces hierarchy contains {@code interfaceName} or {@code false} otherwise
     */
    public static boolean hasInterface(TypeInfo interfaceInfo, TypeName interfaceName) {
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
     * Repository interface generator factory.
     */
    protected interface GeneratorFactory {
        /**
         * Create repository interface code generator.
         *
         * @param repositoryInfo data repository interface info
         * @param classModel target class builder
         * @param codegenContext code processing and generation context
         * @param persistenceGenerator persistence provider specific generator
         * @return new instance of interface code generator
         */
        RepositoryInterfaceGenerator create(RepositoryInfo repositoryInfo,
                                            ClassModel.Builder classModel,
                                            CodegenContext codegenContext,
                                            PersistenceGenerator persistenceGenerator);
    }

}
