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
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.data.codegen.common.BaseRepositoryGenerator;
import io.helidon.data.codegen.common.RepositoryInfo;
import io.helidon.data.codegen.common.spi.RepositoryGenerator;

import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceGenerator.GENERATOR;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.BASE_REPOSITORY_EXECUTOR;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.GENERIC_R;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.INJECTION_SINGLETON;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.PU_NAME_ANNOTATION;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.SESSION_CONSUMER;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.SESSION_FUNCTION;
import static io.helidon.data.jakarta.persistence.codegen.JakartaPersistenceTypes.SESSION_REPOSITORY;

final class JakartaRepositoryClassGenerator {
    private JakartaRepositoryClassGenerator() {
    }

    static void generate(CodegenContext codegenContext,
                         RoundContext roundContext,
                         RepositoryGenerator repositoryGenerator,
                         RepositoryInfo repositoryInfo,
                         TypeName className,
                         ClassModel.Builder classModel,
                         JakartaPersistenceGenerator generator) {

        TypeName repositoryInterface = repositoryInfo.interfaceInfo().typeName();

        // Class header (part that is not data repository specific)
        classModel.type(className)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 repositoryInterface,
                                                 className))
                .addAnnotation(INJECTION_SINGLETON)
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               repositoryInterface,
                                                               className,
                                                               "1",
                                                               ""))
                .classType(ElementKind.CLASS)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addInterface(repositoryInterface);

        generateFields(classModel);
        generateConstructor(classModel, repositoryInfo);

        // Generate all interfaces supported by data repository generators
        repositoryGenerator.generateInterfaces(repositoryInfo, classModel, codegenContext, generator);
        // Generate all query methods
        repositoryGenerator.generateQueryMethods(repositoryInfo, classModel, codegenContext, generator);
        // Generate SessionRepository<EntityManager> methods
        if (BaseRepositoryGenerator.hasInterface(repositoryInfo.interfaceInfo(), SESSION_REPOSITORY)) {
            generateSessionRepository(classModel);
        }
        generateCloseMethod(classModel);
    }

    private static void generateSessionRepository(ClassModel.Builder classModel) {
        classModel.addMethod(JakartaRepositoryClassGenerator::generateRunMethod);
        classModel.addMethod(JakartaRepositoryClassGenerator::generateCallMethod);
    }

    private static void generateRunMethod(Method.Builder builder) {
        builder.name("run")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(Parameter.builder()
                                      .name("task")
                                      .type(SESSION_CONSUMER)
                                      .build());
        builder.addContentLine("executor.run(task::accept);");
    }

    private static void generateCallMethod(Method.Builder builder) {
        builder.name("call")
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(Parameter.builder()
                                      .name("task")
                                      .type(SESSION_FUNCTION)
                                      .build())
                .returnType(GENERIC_R)
                .addGenericArgument(GENERIC_R);
        builder.addContentLine("return executor.call(task::apply);");
    }

    private static void generateCloseMethod(ClassModel.Builder classModel) {
        classModel.addMethod(close -> close
                .name("close")
                .addAnnotation(Annotation.create(TypeName.create("io.helidon.service.registry.Service.PreDestroy")))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addContentLine("this.executor.close();")
        );
    }

    private static void generateFields(ClassModel.Builder classModel) {
        classModel.addField(builder -> builder.name("executor")
                .isFinal(true)
                .type(BASE_REPOSITORY_EXECUTOR));
    }

    private static void generateConstructor(ClassModel.Builder classModel, RepositoryInfo repositoryInfo) {
        var ctr = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        boolean hasNamed = false;
        String name = null;
        boolean nameRequired = false;

        if (repositoryInfo.interfaceInfo().hasAnnotation(PU_NAME_ANNOTATION)) {
            Annotation dataSourceAnnotation = repositoryInfo.interfaceInfo().annotation(PU_NAME_ANNOTATION);

            name = dataSourceAnnotation.value().orElse("@default");
            nameRequired = dataSourceAnnotation.booleanValue("required").orElse(false);
            hasNamed = !name.equals("@default");
        }

        if (hasNamed) {
            Annotation named = Annotation.builder()
                    .typeName(TypeName.create("io.helidon.service.registry.Service.Named"))
                    .putValue("value", name)
                    .build();

            if (nameRequired) {
                ctr.addParameter(Parameter.builder()
                                         .addAnnotation(named)
                                         .name("executor")
                                         .type(BASE_REPOSITORY_EXECUTOR)
                                         .build())
                        .addContent("this.executor = executor;");
            } else {
                ctr.addParameter(Parameter.builder()
                                         .addAnnotation(named)
                                         .name("namedExecutor")
                                         .type(TypeName.builder(TypeNames.OPTIONAL)
                                                       .addTypeArgument(BASE_REPOSITORY_EXECUTOR)
                                                       .build())
                                         .build())
                        .addParameter(Parameter.builder()
                                              .name("executor")
                                              .type(TypeName.builder(TypeNames.SUPPLIER)
                                                            .addTypeArgument(BASE_REPOSITORY_EXECUTOR)
                                                            .build())
                                              .build())
                        .addContent("this.executor = namedExecutor.orElseGet(executor);");
            }
        } else {
            ctr.addParameter(Parameter.builder()
                                     .name("executor")
                                     .type(BASE_REPOSITORY_EXECUTOR)
                                     .build())
                    .addContent("this.executor = executor;");
        }

        classModel.addConstructor(ctr);
    }
}
