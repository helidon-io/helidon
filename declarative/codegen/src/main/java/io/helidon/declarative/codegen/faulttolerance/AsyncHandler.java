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

package io.helidon.declarative.codegen.faulttolerance;

import java.util.function.Predicate;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.declarative.codegen.DeclarativeTypes.EXECUTOR_SERVICE;
import static io.helidon.declarative.codegen.DeclarativeTypes.WEIGHT;
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.ASYNC;
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.ASYNC_ANNOTATION;

final class AsyncHandler extends FtHandler {
    private static final TypeName OPTIONAL_EXECUTOR_SERVICE = TypeName.builder()
            .from(TypeNames.OPTIONAL)
            .addTypeArgument(EXECUTOR_SERVICE)
            .build();

    AsyncHandler(RegistryCodegenContext ctx) {
        super(ctx, ASYNC_ANNOTATION);
    }

    @Override
    void process(RegistryRoundContext roundContext,
                 TypeInfo enclosingType,
                 TypedElementInfo element,
                 Annotation annotation,
                 TypeName generatedType,
                 ClassModel.Builder classModel) {
        TypeName enclosingTypeName = enclosingType.typeName();

        // class definition
        classModel.superType(FtTypes.ASYNC_GENERATED_METHOD)
                .addAnnotation(Annotation.builder()
                                       .typeName(WEIGHT)
                                       .putValue("value", InterceptorWeights.WEIGHT_ASYNC)
                                       .build());

        // generate the class body
        asyncBody(classModel,
                  enclosingTypeName,
                  element,
                  generatedType,
                  annotation);

        // add type to context
        addType(roundContext,
                generatedType,
                classModel,
                enclosingTypeName,
                element);
    }

    private void asyncBody(ClassModel.Builder classModel,
                           TypeName enclosingTypeName,
                           TypedElementInfo element,
                           TypeName generatedType,
                           Annotation annotation) {

        classModel.addField(async -> async
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(ASYNC)
                .name("async"));

        String name = annotation.stringValue("name")
                .filter(Predicate.not(String::isBlank))
                .orElse(null);
        String executorName = annotation.stringValue("executorName")
                .filter(Predicate.not(String::isBlank))
                .orElse(null);

        /*
        Constructor (may inject named Async)
         */
        var ctr = Constructor.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        boolean hasName = name != null;
        boolean hasExecutorName = executorName != null;

        /*
        First parameters
         */
        if (hasName) {
            // named, inject
            ctr.addParameter(namedAsync -> namedAsync
                    .name("namedAsync")
                    .type(TypeName.builder()
                                  .from(TypeNames.OPTIONAL)
                                  .addTypeArgument(ASYNC)
                                  .build())
                    .addAnnotation(namedAnnotation(name)));
        }

        if (hasExecutorName) {
            // named, inject
            ctr.addParameter(namedExecutor -> namedExecutor
                    .name("namedExecutor")
                    .type(OPTIONAL_EXECUTOR_SERVICE)
                    .addAnnotation(namedAnnotation(executorName)));
        }

        // then creating the instance
        if (hasName) {
            ctr.addContent("this.async = namedAsync.orElseGet(");
            if (hasExecutorName) {
                ctr.addContent("() -> produceAsync(namedExecutor));");
            } else {
                ctr.addContent(generatedType)
                        .addContentLine("::produceAsync);");
            }
        } else {
            if (hasExecutorName) {
                ctr.addContent("this.async = produceAsync(namedExecutor);");
            } else {
                ctr.addContent("this.async = produceAsync();");
            }
        }

        classModel.addConstructor(ctr);

        /*
        Async method (implementing abstract method)
         */
        classModel.addMethod(retry -> retry
                .name("async")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(ASYNC)
                .accessModifier(AccessModifier.PROTECTED)
                .addContentLine("return async;")
        );

        /*
        Produce async method (from annotation values)
         */
        String customName;
        if (name == null) {
            customName = enclosingTypeName.fqName()
                    + "." + element.signature().text();
        } else {
            // as the named instance was not found, use the name and our unique signature
            customName = name + "-" + enclosingTypeName.fqName()
                    + "." + element.signature().text();
        }

        classModel.addMethod(produceAsync -> produceAsync
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(ASYNC)
                .name("produceAsync")
                .update(builder -> {
                    if (hasExecutorName) {
                        builder.addParameter(namedExecutor -> namedExecutor
                                .type(OPTIONAL_EXECUTOR_SERVICE)
                                .name("namedExecutor"));
                    }
                })
                .update(builder -> produceAsyncMethodBody(builder,
                                                          customName,
                                                          hasExecutorName))
        );
    }

    private void produceAsyncMethodBody(Method.Builder builder,
                                        String customName,
                                        boolean hasExecutorName) {

        builder.addContent("return ")
                .addContent(ASYNC)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".name(\"")
                .addContent(customName)
                .addContentLine("\")")
                .update(methodBuilder -> {
                    if (hasExecutorName) {
                        builder.addContentLine(".update(builder -> namedExecutor.ifPresent(builder::executor))");
                    }
                })
                .addContentLine(".build();")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }
}
