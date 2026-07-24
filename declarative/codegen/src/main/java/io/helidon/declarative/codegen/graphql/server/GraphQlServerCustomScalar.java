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

package io.helidon.declarative.codegen.graphql.server;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.ScalarSchemaType;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;

import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_CUSTOM_SCALAR_SPI;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_DESCRIPTION;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_NAME;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SCALAR;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SCALAR_SPI;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerInputValues.javaIdentifierSuffix;
import static java.util.function.Predicate.not;

class GraphQlServerCustomScalar {
    private static final TypeName GENERATOR = TypeName.create(GraphQlServerExtension.class);

    private final RegistryCodegenContext ctx;
    private final Set<TypeName> generatedScalarTypes = new HashSet<>();

    GraphQlServerCustomScalar(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    TypeName process(RegistryRoundContext roundContext, TypeInfo endpointTypeInfo, ScalarSchemaType scalarType) {
        TypeInfo scalarTypeInfo = roundContext.typeInfo(scalarType.javaType().boxed())
                .or(() -> roundContext.typeInfo(scalarType.javaType()))
                .orElseThrow(() -> new CodegenException("Unknown Java GraphQL scalar type "
                                                                + scalarType.javaType().fqName(),
                                                        scalarType.originatingElement()));
        TypeName generatedType = customScalarAdapterName(endpointTypeInfo.typeName(), scalarType.javaType());
        if (generatedScalarTypes.add(generatedType)) {
            Set<Annotation> scalarAnnotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, scalarTypeInfo));
            TypeName customScalar = TypeName.builder(GRAPHQL_CUSTOM_SCALAR_SPI)
                    .addTypeArgument(scalarType.javaType())
                    .build();
            process(roundContext,
                    endpointTypeInfo,
                    customScalar,
                    scalarTypeInfo,
                    scalarAnnotations,
                    generatedType);
        }
        return generatedType;
    }

    private void process(RegistryRoundContext roundContext,
                         TypeInfo endpointTypeInfo,
                         TypeName customScalar,
                         TypeInfo scalarTypeInfo,
                         Set<Annotation> scalarAnnotations,
                         TypeName generatedType) {
        ClassModel.Builder classModel = ClassModel.builder()
                .type(generatedType)
                .copyright(CodegenUtil.copyright(GENERATOR, endpointTypeInfo.typeName(), generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR, endpointTypeInfo.typeName(), generatedType, "0", ""))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        classModel.addConstructor(constructor -> constructor
                .accessModifier(AccessModifier.PRIVATE));

        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .isStatic(true)
                .returnType(GRAPHQL_SCALAR_SPI)
                .name("create")
                .addParameter(delegate -> delegate
                        .type(customScalar)
                        .name("delegate"))
                .addContent("return new ")
                .addContent(GRAPHQL_SCALAR_SPI)
                .addContentLine("() {")
                .increaseContentPadding()
                .addContentLine("@Override")
                .addContentLine("public String name() {")
                .increaseContentPadding()
                .addContent("return ")
                .addContentLiteral(scalarName(scalarTypeInfo, scalarAnnotations))
                .addContentLine(";")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine()
                .addContentLine("@Override")
                .addContentLine("public Class<?> type() {")
                .increaseContentPadding()
                .addContent("return ")
                .addContent(scalarTypeInfo.typeName())
                .addContentLine(".class;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine()
                .addContentLine("@Override")
                .addContentLine("public String description() {")
                .increaseContentPadding()
                .addContent("return ")
                .addContentLiteral(description(scalarAnnotations).orElse(""))
                .addContentLine(";")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine()
                .addContentLine("@Override")
                .addContentLine("public Object serialize(Object value) {")
                .increaseContentPadding()
                .addContent("return java.util.Objects.requireNonNull(delegate.serialize((")
                .addContent(scalarTypeInfo.typeName())
                .addContentLine(") value), \"serialize result\");")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine()
                .addContentLine("@Override")
                .addContentLine("public Object parseValue(Object value) {")
                .increaseContentPadding()
                .addContentLine("return java.util.Objects.requireNonNull(delegate.parseValue(value), \"parseValue result\");")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine()
                .addContentLine("@Override")
                .addContentLine("public Object parseLiteral(Object value) {")
                .increaseContentPadding()
                .addContentLine("return java.util.Objects.requireNonNull(")
                .addContentLine("        delegate.parseValue(java.util.Objects.requireNonNull(value)),")
                .addContentLine("        \"parseLiteral result\");")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("};"));

        roundContext.addGeneratedType(generatedType,
                                      classModel,
                                      endpointTypeInfo.typeName(),
                                      endpointTypeInfo.originatingElementValue());
    }

    private static TypeName customScalarAdapterName(TypeName endpointType, TypeName scalarType) {
        return TypeName.builder()
                .packageName(endpointType.packageName())
                .className(javaIdentifierSuffix(scalarType.fqName()) + "Scalar__GraphQlScalar")
                .build();
    }

    private static String scalarName(TypeInfo typeInfo, Set<Annotation> annotations) {
        Optional<String> scalarName = Annotations.findFirst(GRAPHQL_SCALAR, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank));
        Optional<String> graphQlName = Annotations.findFirst(GRAPHQL_NAME, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank));
        if (scalarName.isPresent() && graphQlName.isPresent() && !scalarName.orElseThrow().equals(graphQlName.orElseThrow())) {
            throw new CodegenException("@GraphQl.Scalar value and @GraphQl.Name cannot declare different GraphQL type names "
                                               + "on " + typeInfo.typeName().fqName(),
                                       typeInfo.originatingElementValue());
        }
        String defaultName = typeInfo.typeName().className();
        String name = scalarName.or(() -> graphQlName).orElse(defaultName);
        return GraphQlServerExtension.validateGraphQlName(name, typeInfo.originatingElementValue());
    }

    private static Optional<String> description(Set<Annotation> annotations) {
        return Annotations.findFirst(GRAPHQL_DESCRIPTION, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank));
    }
}
