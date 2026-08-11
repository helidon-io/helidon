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

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.common.Api;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.data.codegen.common.RepositoryInfo;

/**
 * Builds the service shell around JDBC-generated repository methods.
 */
final class JdbcRepositoryClassGenerator {

    /**
     * Prevents construction of the generator utility.
     */
    private JdbcRepositoryClassGenerator() {
    }

    /**
     * Defines the generated repository class and delegates its method body
     * generation to the JDBC provider.
     *
     * @param roundContext current generation round
     * @param repositoryInfo repository metadata
     * @param className generated class name
     * @param classModel generated class
     */
    static void generate(RoundContext roundContext,
                         RepositoryInfo repositoryInfo,
                         TypeName className,
                         ClassModel.Builder classModel) {
        TypeName repositoryType = repositoryInfo.interfaceInfo().typeName();
        classModel.type(className)
                .description("Generated JDBC implementation of {@link " + repositoryType.fqName() + "}.")
                .copyright(CodegenUtil.copyright(JdbcPersistenceGenerator.GENERATOR,
                                                 repositoryType,
                                                 className))
                .addAnnotation(Annotation.create(JdbcPersistenceTypes.SERVICE_SINGLETON))
                .addAnnotation(CodegenUtil.generatedAnnotation(JdbcPersistenceGenerator.GENERATOR,
                                                               repositoryType,
                                                               className,
                                                               JdbcCodegenConstants.GENERATED_VERSION,
                                                               ""))
                // Helidon owns generated implementation details. Application-authored API usage remains unsuppressed.
                .addAnnotation(Annotation.create(SuppressWarnings.class, Api.SUPPRESS_ALL))
                .classType(ElementKind.CLASS)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addInterface(repositoryType)
                .addField(field -> field.name(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                        .description("JDBC client for the selected persistence unit.")
                        .isFinal(true)
                        .type(JdbcPersistenceTypes.JDBC_CLIENT));

        JdbcMethodGenerator.generate(repositoryInfo,
                                     classModel,
                                     roundContext);
    }

    /**
     * Generates the repository constructor with the selected JDBC persistence unit and row-mapper services.
     *
     * @param classModel generated repository class
     * @param repositoryInfo repository metadata
     * @param mapperDependencies statically typed mapper-service dependencies
     */
    static void generateConstructor(ClassModel.Builder classModel,
                                    RepositoryInfo repositoryInfo,
                                    Iterable<JdbcMethodGenerator.MapperDependency> mapperDependencies) {
        Constructor.Builder constructor = Constructor.builder()
                .description("Creates the generated repository with its selected JDBC client and row mappers.")
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);
        Annotation provider = Annotation.builder()
                .typeName(JdbcPersistenceTypes.DATA_PROVIDER_TYPE)
                .property(JdbcCodegenConstants.ANNOTATION_VALUE_PROPERTY, JdbcCodegenConstants.PROVIDER)
                .build();
        Annotation defaultNamed = Annotation.builder()
                .typeName(JdbcPersistenceTypes.SERVICE_NAMED)
                .property(JdbcCodegenConstants.ANNOTATION_VALUE_PROPERTY,
                          JdbcCodegenConstants.DEFAULT_PERSISTENCE_UNIT)
                .build();

        Annotation persistenceUnit = repositoryInfo.interfaceInfo()
                .findAnnotation(JdbcPersistenceTypes.DATA_PERSISTENCE_UNIT)
                .orElse(null);
        String name = persistenceUnit == null
                ? JdbcCodegenConstants.DEFAULT_PERSISTENCE_UNIT
                : persistenceUnit.stringValue().orElse(JdbcCodegenConstants.DEFAULT_PERSISTENCE_UNIT);
        boolean required = persistenceUnit == null
                || persistenceUnit.booleanValue(JdbcCodegenConstants.PERSISTENCE_UNIT_REQUIRED_PROPERTY).orElse(true);

        if (!JdbcCodegenConstants.DEFAULT_PERSISTENCE_UNIT.equals(name)) {
            Annotation named = Annotation.builder()
                    .typeName(JdbcPersistenceTypes.SERVICE_NAMED)
                    .property(JdbcCodegenConstants.ANNOTATION_VALUE_PROPERTY, name)
                    .build();
            if (required) {
                constructor.addParameter(Parameter.builder()
                                                 .name(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                                                 .type(JdbcPersistenceTypes.JDBC_CLIENT)
                                                 .addAnnotation(named)
                                                 .addAnnotation(provider)
                                                 .build())
                        .addContent("this.")
                        .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                        .addContent(" = ")
                        .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                        .addContentLine(";");
            } else {
                // Resolve the default lazily so it is activated only when the optional named client is absent.
                TypeName optionalClient = TypeName.builder(JdbcPersistenceTypes.OPTIONAL)
                        .addTypeArgument(JdbcPersistenceTypes.JDBC_CLIENT)
                        .build();
                TypeName clientSupplier = TypeName.builder(JdbcPersistenceTypes.SUPPLIER)
                        .addTypeArgument(JdbcPersistenceTypes.JDBC_CLIENT)
                        .build();
                constructor.addParameter(Parameter.builder()
                                                 .name(JdbcCodegenConstants.NAMED_JDBC_CLIENT_NAME)
                                                 .type(optionalClient)
                                                 .addAnnotation(named)
                                                 .addAnnotation(provider)
                                                 .build())
                        .addParameter(Parameter.builder()
                                              .name(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                                              .type(clientSupplier)
                                              .addAnnotation(defaultNamed)
                                              .addAnnotation(provider)
                                              .build())
                        .addContent("this.")
                        .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                        .addContent(" = ")
                        .addContent(JdbcCodegenConstants.NAMED_JDBC_CLIENT_NAME)
                        .addContent(".orElseGet(")
                        .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                        .addContentLine(");");
            }
        } else {
            constructor.addParameter(Parameter.builder()
                                             .name(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                                             .type(JdbcPersistenceTypes.JDBC_CLIENT)
                                             .addAnnotation(defaultNamed)
                                             .addAnnotation(provider)
                                             .build())
                    .addContent("this.")
                    .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                    .addContent(" = ")
                    .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                    .addContentLine(";");
        }
        for (JdbcMethodGenerator.MapperDependency dependency : mapperDependencies) {
            constructor.addParameter(Parameter.builder()
                                             .name(dependency.parameterName())
                                             .type(dependency.parameterType())
                                             .build())
                    .addContent("this.")
                    .addContent(dependency.fieldName())
                    .addContent(" = ")
                    .addContent(dependency.parameterName())
                    .addContentLine(";");
        }
        classModel.addConstructor(constructor);
    }
}
