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

import java.util.List;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
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
import io.helidon.common.types.TypeNames;
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
     * @param codegenContext current code generation context
     * @param roundContext current generation round
     * @param repositoryInfo repository metadata
     * @param className generated class name
     * @param classModel generated class
     */
    static void generate(CodegenContext codegenContext,
                         RoundContext roundContext,
                         RepositoryInfo repositoryInfo,
                         TypeName className,
                         ClassModel.Builder classModel) {
        TypeName repositoryType = repositoryInfo.interfaceInfo().typeName();
        classModel.type(className)
                .copyright(CodegenUtil.copyright(JdbcRepositoryGenerator.GENERATOR,
                                                 repositoryType,
                                                 className))
                .addAnnotation(Annotation.create(JdbcCodegenTypes.SERVICE_SINGLETON))
                .addAnnotation(CodegenUtil.generatedAnnotation(JdbcRepositoryGenerator.GENERATOR,
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
                        .isFinal(true)
                        .type(JdbcCodegenTypes.JDBC_CLIENT));

        JdbcMethodGenerator.generate(codegenContext,
                                     roundContext,
                                     repositoryInfo,
                                     classModel);
    }

    /**
     * Generates the repository constructor with the selected JDBC client and row mapper services.
     * Repositories with configuration references also receive root configuration and initialize
     * a reusable statement supplier for each reference.
     *
     * @param classModel The model describes the generated repository class.
     * @param repositoryInfo The metadata describes the repository contract.
     * @param mapperDependencies The dependencies identify the statically typed mapper services.
     * @param plans The plans describe the validated repository methods.
     */
    static void generateConstructor(ClassModel.Builder classModel,
                                    RepositoryInfo repositoryInfo,
                                    Iterable<JdbcMethodGenerator.MapperDependency> mapperDependencies,
                                    List<JdbcMethodPlan> plans) {
        Constructor.Builder constructor = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);
        Annotation provider = Annotation.builder()
                .typeName(JdbcCodegenTypes.DATA_PROVIDER_TYPE)
                .property(JdbcCodegenConstants.ANNOTATION_VALUE_PROPERTY, JdbcCodegenConstants.PROVIDER)
                .build();
        if (repositoryInfo.interfaceInfo().hasAnnotation(JdbcCodegenTypes.DATA_PERSISTENCE_UNIT)) {
            throw new CodegenException("JDBC repositories do not use @Data.PersistenceUnit. Use @Jdbc.Client to "
                                               + "select a named JDBC client from data.clients.jdbc.",
                                       repositoryInfo.interfaceInfo().originatingElementValue());
        }

        String name = JdbcCodegenConstants.DEFAULT_CLIENT_NAME;
        Annotation client = repositoryInfo.interfaceInfo()
                .findAnnotation(JdbcCodegenTypes.JDBC_CLIENT_ANNOTATION)
                .orElse(null);
        if (client != null) {
            name = client.stringValue().orElse("");
            if (name.isBlank()) {
                throw new CodegenException("A JDBC repository @Jdbc.Client value must not be blank.",
                                           repositoryInfo.interfaceInfo().originatingElementValue());
            }
        }
        Annotation named = Annotation.builder()
                .typeName(JdbcCodegenTypes.SERVICE_NAMED)
                .property(JdbcCodegenConstants.ANNOTATION_VALUE_PROPERTY, name)
                .build();

        constructor.addParameter(Parameter.builder()
                                         .name(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                                         .type(JdbcCodegenTypes.JDBC_CLIENT)
                                         .addAnnotation(named)
                                         .addAnnotation(provider)
                                         .build())
                .addContent("this.")
                .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                .addContent(" = ")
                .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME)
                .addContentLine(";");
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
        if (plans.stream().anyMatch(plan -> plan.reference() != null)) {
            constructor.addParameter(Parameter.builder().name("config").type(JdbcCodegenTypes.CONFIG).build());
            // Mapper parameter names include a mapper suffix, so these lookup locals cannot collide with them.
            String sqlName = JdbcCodegenConstants.GENERATED_VARIABLE_PREFIX + "sql";
            String nodeName = JdbcCodegenConstants.GENERATED_VARIABLE_PREFIX + "sqlNode";
            // Resolve each reference once and retain its statement supplier for later invocations.
            // Separate blocks let all methods use the same lookup variable names.
            for (JdbcMethodPlan plan : plans) {
                JdbcSqlReference reference = plan.reference();
                if (reference == null) {
                    continue;
                }
                constructor.addContentLine("{")
                        .addContent(TypeNames.STRING).addContent(" ").addContent(sqlName).addContentLine(";")
                        .addContentLine("try {")
                        .addContent(JdbcCodegenTypes.CONFIG).addContent(" ").addContent(nodeName)
                        .addContent(" = config.get(").addContentLiteral(reference.key()).addContentLine(");")
                        .addContent("if (").addContent(nodeName).addContent(".exists() && ")
                        .addContent(nodeName).addContent(".type() != ").addContent(JdbcCodegenTypes.CONFIG)
                        .addContentLine(".Type.VALUE) {")
                        .addContent("throw new ").addContent(JdbcCodegenTypes.DATA_EXCEPTION)
                        .addContentLine("(\"The configured SQL must be a scalar value.\");")
                        .addContentLine("}")
                        .addContent(sqlName).addContent(" = ").addContent(nodeName).addContent(".asString()");
                if (reference.defaultSql() == null) {
                    constructor.addContentLine(".get();");
                } else {
                    constructor.addContent(".orElse(").addContentLiteral(reference.defaultSql()).addContentLine(");");
                }
                // Discard configuration exception graphs because they may contain SQL or configuration values.
                constructor.addContentLine("}")
                        .addContent("catch (").addContent(JdbcCodegenTypes.CONFIG_EXCEPTION).addContentLine(" _) {")
                        .addContent("throw new ").addContent(JdbcCodegenTypes.DATA_EXCEPTION)
                        .addContentLine("(\"The configured SQL could not be resolved.\");")
                        .addContentLine("}")
                        .addContent("this.").addContent(plan.sqlFieldName()).addContent(" = ");
                // Any parameter alias requires named SQL for the whole method input contract.
                if (plan.callable() != null) {
                    constructor.addContent(JdbcCodegenTypes.GENERATED_JDBC_DATA).addContent(".prepareCall(")
                            .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME).addContent(", ");
                } else if (plan.method().parameterArguments().stream()
                                .anyMatch(parameter -> parameter.hasAnnotation(JdbcCodegenTypes.DATA_PARAM))) {
                    constructor.addContent(JdbcCodegenTypes.GENERATED_JDBC_DATA).addContent(".prepareNamed(")
                            .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME).addContent(", ");
                } else {
                    constructor.addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME).addContent(".prepare(");
                }
                constructor.addContent(sqlName).addContent(", ").addContent(List.class).addContent(".of(");
                List<String> parameterNames = plan.callable() == null
                        ? plan.parameterPlan().binds().stream()
                                .map(bind -> JdbcSqlParameterPlan.effectiveName(bind.parameter())).toList()
                        : plan.callable().names();
                boolean first = true;
                for (String parameterName : parameterNames) {
                    if (!first) {
                        constructor.addContent(", ");
                    }
                    constructor.addContentLiteral(parameterName);
                    first = false;
                }
                constructor.addContent(")");
                if (plan.callable() != null) {
                    constructor.addContent(", ").addContent(String.valueOf(plan.callable().namedOnly()))
                            .addContent(", ").addContent(String.valueOf(plan.callable().outputs().stream()
                                    .anyMatch(output -> output.marker().isEmpty())));
                }
                constructor.addContentLine(");").addContentLine("}");
            }
        }
        classModel.addConstructor(constructor);
    }
}
