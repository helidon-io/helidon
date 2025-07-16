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

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.data.codegen.common.BaseRepositoryMethodsGenerator;
import io.helidon.data.codegen.common.MethodParams;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;

/**
 * Helidon Data methods generator base class.
 */
abstract class BaseQueryMethodsGenerator extends BaseRepositoryMethodsGenerator {

    BaseQueryMethodsGenerator(RepositoryInfo repositoryInfo,
                              ClassModel.Builder classModel,
                              CodegenContext codegenContext,
                              PersistenceGenerator persistenceGenerator) {
        super(repositoryInfo, classModel, codegenContext, persistenceGenerator);

    }

    /**
     * Check that pageRequest argument is present in method parameters and return it.
     *
     * @param methodParams method parameters
     * @param methodInfo   method descriptor
     * @return pageRequest argument
     * @throws CodegenException when pageRequest argument is missing
     */
    protected static TypedElementInfo pageRequestRequired(MethodParams methodParams,
                                                          TypedElementInfo methodInfo) throws CodegenException {
        if (methodParams.pageRequest().isEmpty()) {
            throw new CodegenException("Method " + methodInfo.elementName()
                                               + " returns " + methodInfo.typeName()
                                               + ", but PageRequest parameter is missing");
        }
        return methodParams.pageRequest().get();
    }

    @Override
    protected void processParam(MethodParams.Builder builder, TypedElementInfo paramInfo) {
        if (paramInfo.typeName().equals(DataCodegenTypes.SORT)) {
            builder.order(paramInfo);
        } else if (paramInfo.typeName().equals(DataCodegenTypes.PAGE_REQUEST)) {
            builder.pageRequest(paramInfo);
        } else {
            builder.addParameter(paramInfo);
        }
    }

}
