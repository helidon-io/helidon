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
import io.helidon.common.types.TypeNames;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.ScalarSchemaType;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;

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

    void process(RegistryRoundContext roundContext,
                 TypeInfo endpointTypeInfo,
                 TypeName featureType,
                 ScalarSchemaType scalarType) {
        TypeInfo scalarTypeInfo = roundContext.typeInfo(scalarType.javaType().boxed())
                .or(() -> roundContext.typeInfo(scalarType.javaType()))
                .orElseThrow(() -> new CodegenException("Unknown Java GraphQL scalar type "
                                                                + scalarType.javaType().fqName(),
                                                        scalarType.originatingElement()));
        TypeName generatedType = customScalarAdapterName(featureType, scalarType.javaType());
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
                    generatedType,
                    featureType);
        }
    }

    private void process(RegistryRoundContext roundContext,
                         TypeInfo endpointTypeInfo,
                         TypeName customScalar,
                         TypeInfo scalarTypeInfo,
                         Set<Annotation> scalarAnnotations,
                         TypeName generatedType,
                         TypeName featureType) {
        ClassModel.Builder classModel = ClassModel.builder()
                .type(generatedType)
                .copyright(CodegenUtil.copyright(GENERATOR, endpointTypeInfo.typeName(), generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR, endpointTypeInfo.typeName(), generatedType, "0", ""))
                .addAnnotation(DeclarativeTypes.SINGLETON_ANNOTATION)
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED, featureType.fqName()))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addInterface(GRAPHQL_SCALAR_SPI)
                .addField(delegate -> delegate
                        .accessModifier(AccessModifier.PRIVATE)
                        .isFinal(true)
                        .type(customScalar)
                        .name("delegate"));

        classModel.addConstructor(constructor -> constructor
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .addParameter(delegate -> delegate
                        .type(customScalar)
                        .name("delegate"))
                .addContentLine("this.delegate = delegate;"));

        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.STRING)
                .name("name")
                .addContent("return ")
                .addContentLiteral(scalarName(scalarTypeInfo, scalarAnnotations))
                .addContentLine(";"));
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeName.builder(TypeName.create(Class.class))
                                    .addTypeArgument(TypeNames.WILDCARD)
                                    .build())
                .name("type")
                .addContent("return ")
                .addContent(scalarTypeInfo.typeName())
                .addContentLine(".class;"));
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.STRING)
                .name("description")
                .addContent("return ")
                .addContentLiteral(description(scalarAnnotations).orElse(""))
                .addContentLine(";"));
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.OBJECT)
                .name("serialize")
                .addParameter(value -> value
                        .type(TypeNames.OBJECT)
                        .name("value"))
                .addContent("return java.util.Objects.requireNonNull(delegate.serialize((")
                .addContent(scalarTypeInfo.typeName())
                .addContentLine(") value), \"serialize result\");"));
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.OBJECT)
                .name("parseValue")
                .addParameter(value -> value
                        .type(TypeNames.OBJECT)
                        .name("value"))
                .addContentLine("return java.util.Objects.requireNonNull(delegate.parseValue(value), \"parseValue result\");"));
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(TypeNames.OBJECT)
                .name("parseLiteral")
                .addParameter(value -> value
                        .type(TypeNames.OBJECT)
                        .name("value"))
                .addContentLine("return java.util.Objects.requireNonNull(")
                .addContentLine("        delegate.parseValue(java.util.Objects.requireNonNull(value)),")
                .addContentLine("        \"parseLiteral result\");"));

        roundContext.addGeneratedType(generatedType,
                                      classModel,
                                      endpointTypeInfo.typeName(),
                                      endpointTypeInfo.originatingElementValue());
    }

    private static TypeName customScalarAdapterName(TypeName featureType, TypeName scalarType) {
        return TypeName.builder()
                .packageName(featureType.packageName())
                .className(featureType.className() + "__"
                                   + javaIdentifierSuffix(scalarType.fqName())
                                   + "Scalar__GraphQlScalar")
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
