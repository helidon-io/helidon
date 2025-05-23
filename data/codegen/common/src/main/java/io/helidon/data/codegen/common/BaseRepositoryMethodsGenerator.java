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

import java.util.List;
import java.util.stream.Stream;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.Annotation;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.data.codegen.common.spi.PersistenceGenerator;

import static io.helidon.data.codegen.common.DataCommonCodegenTypes.PAGE;
import static io.helidon.data.codegen.common.DataCommonCodegenTypes.SLICE;

/**
 * Data repository interface code generator base class for methods generators.
 */
public abstract class BaseRepositoryMethodsGenerator extends BaseRepositoryInterfaceGenerator {

    /**
     * {@link java.util.stream.Stream} type.
     */
    private static final TypeName STREAM = TypeName.create(Stream.class);

    /**
     * Creates an instance of data repository interface code generator base class for methods generators.
     *
     * @param repositoryInfo       data repository interface info
     * @param classModel           target class builder
     * @param codegenContext       code processing and generation context
     * @param persistenceGenerator persistence provider specific generator
     */
    protected BaseRepositoryMethodsGenerator(RepositoryInfo repositoryInfo,
                                             ClassModel.Builder classModel,
                                             CodegenContext codegenContext,
                                             PersistenceGenerator persistenceGenerator) {
        super(repositoryInfo, classModel, codegenContext, persistenceGenerator);
    }

    // Static utility methods

    /**
     * Extract {@link TypeName} of generic argument from provided {@link TypedElementInfo} {@code methodInfo}
     * return type.
     * Retrieves generic argument from single generic argument types like {@link java.util.List}
     * or {@link java.util.stream.Stream}
     *
     * @param methodInfo method info
     * @return {@link TypeName} of generic argument
     */
    protected static TypeName genericReturnTypeArgument(TypedElementInfo methodInfo) {
        List<TypeName> genericArguments = methodInfo.typeName().typeArguments();
        if (genericArguments == null || genericArguments.isEmpty()) {
            throw new CodegenException("Missing generic argument of method "
                                               + methodInfo.elementName()
                                               + " with return type "
                                               + methodInfo.typeName());
        }
        return genericArguments.getFirst();
    }

    /**
     * Check whether provided {@link TypeName} is {@link java.util.List} or {@link java.util.Collection}.
     *
     * @param typeName type to check
     * @return value of {@code true} when provided {@link TypeName} is {@link java.util.List}
     *         or {@link java.util.Collection}, value of {@code false} otherwise
     */
    protected static boolean isListOrCollection(TypeName typeName) {
        return typeName.isList()
                || TypeNames.COLLECTION.equals(typeName);
    }

    /**
     * Check whether provided {@link TypeName} is {@link java.util.stream.Stream}.
     *
     * @param typeName type to check
     * @return value of {@code true} when provided {@link TypeName} is {@link java.util.stream.Stream},
     *         value of {@code false} otherwise
     */
    protected static boolean isStream(TypeName typeName) {
        return STREAM.equals(typeName);
    }

    /**
     * Check whether provided {@link TypeName} is {@code io.helidon.data.Slice}.
     *
     * @param typeName type to check
     * @return value of {@code true} when provided {@link TypeName} is {@code io.helidon.data.Slice},
     *         value of {@code false} otherwise
     */
    protected static boolean isSlice(TypeName typeName) {
        return SLICE.equals(typeName);
    }

    /**
     * Check whether provided {@link TypeName} is {@code io.helidon.data.Page}.
     *
     * @param typeName type to check
     * @return value of {@code true} when provided {@link TypeName} is {@code io.helidon.data.Page},
     *         value of {@code false} otherwise
     */
    protected static boolean isPage(TypeName typeName) {
        return PAGE.equals(typeName);
    }

    /**
     * Check whether provided {@link TypeName} is {@code io.helidon.data.Slice} or {@code io.helidon.data.Page}.
     *
     * @param typeName type to check
     * @return value of {@code true} when provided {@link TypeName} is {@code io.helidon.data.Slice}
     *         or {@code io.helidon.data.Page}, value of {@code false} otherwise
     */
    protected static boolean isSliceOrPage(TypeName typeName) {
        return SLICE.equals(typeName) || PAGE.equals(typeName);
    }

    /**
     * Retrieve name of the method parameter from provided {@link TypedElementInfo} {@code param}.
     *
     * @param param method parameter info
     * @return name of the method parameter
     */
    protected static String paramElementName(TypedElementInfo param) {
        return param.elementName();
    }

    // Class methods

    /**
     * Generate method header matching interface prototype and return method parameters.
     *
     * @param builder    method builder
     * @param methodInfo method info
     * @return method parameters
     */
    protected MethodParams generateHeader(Method.Builder builder,
                                          TypedElementInfo methodInfo) {
        MethodParams methodParams = buildMethodParams(methodInfo.parameterArguments());
        builder.name(methodInfo.elementName())
                .returnType(methodInfo.typeName())
                .addAnnotation(Annotation.create(Override.class));
        methodInfo.parameterArguments()
                .forEach(parameterInfo -> builder.addParameter(Parameter.builder()
                                                                       .name(parameterInfo.elementName())
                                                                       .type(parameterInfo.typeName())
                                                                       .build()));
        return methodParams;
    }

    /**
     * Process method parameter.
     * Extending class must implement this method to properly handle each parameter of the generated method.
     *
     * @param builder   method builder
     * @param paramInfo method parameter info
     */
    protected abstract void processParam(MethodParams.Builder builder, TypedElementInfo paramInfo);

    private static boolean filterParameters(TypedElementInfo info) {
        return info.kind() == ElementKind.PARAMETER;
    }

    private MethodParams buildMethodParams(List<TypedElementInfo> methodParams) {
        MethodParams.Builder builder = MethodParams.builder();
        methodParams.stream()
                .filter(BaseRepositoryMethodsGenerator::filterParameters)
                .forEach(typedElementInfo -> processParam(builder, typedElementInfo));
        return builder.build();
    }

}
