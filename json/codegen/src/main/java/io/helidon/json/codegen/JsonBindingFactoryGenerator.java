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

package io.helidon.json.codegen;

import java.lang.reflect.Type;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.classmodel.Annotation;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.Weighted;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

class JsonBindingFactoryGenerator {

    private JsonBindingFactoryGenerator() {
    }

    static void generateBindingFactory(ClassBase.Builder<?, ?> classBuilder, TypeInfo annotatedType, CodegenContext ctx) {
        ConvertedTypeInfo convertedTypeInfo = ConvertedTypeInfo.create(annotatedType, ctx);
        classBuilder.addAnnotation(b -> b.type(JsonTypes.SERVICE_REGISTRY_PER_LOOKUP))
                .addAnnotation(Annotation.builder()
                                       .type(TypeNames.WEIGHT)
                                       .addParameter("value", Weighted.DEFAULT_WEIGHT - 5)
                                       .build())
                .javadoc(Javadoc.builder()
                                 .add("Json binding factory for {@link " + annotatedType.typeName().fqName() + "}.")
                                 .build())
                .addInterface(TypeName.builder()
                                      .from(JsonTypes.JSON_BINDING_FACTORY)
                                      .addTypeArgument(convertedTypeInfo.wildcardsGenerics())
                                      .build());

        InnerClass.Builder converterClassBuilder = InnerClass.builder()
                .name(convertedTypeInfo.converterType().className())
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .isStatic(true)
                .addField(builder -> builder.isFinal(true).type(Type.class).name("type"))
                .addConstructor(builder -> builder.accessModifier(AccessModifier.PACKAGE_PRIVATE)
                        .addParameter(param -> param.type(Type.class).name("type"))
                        .addContent("this.type = type;"));
        JsonConverterGenerator.generateConverter(converterClassBuilder, convertedTypeInfo, true);
        classBuilder.addInnerClass(converterClassBuilder)
                .addMethod(method -> addCreateDeserializerMethodClass(method, convertedTypeInfo))
                .addMethod(method -> addCreateDeserializerMethodGenerics(method, convertedTypeInfo))
                .addMethod(method -> addCreateSerializerMethodClass(method, convertedTypeInfo))
                .addMethod(method -> addCreateSerializerMethodGenerics(method, convertedTypeInfo))
                .addMethod(method -> addTypeMethod(method, convertedTypeInfo));
    }

    private static void addCreateDeserializerMethodClass(Method.Builder method, ConvertedTypeInfo convertedTypeInfo) {
        TypeName classType = TypeName.builder()
                .type(Class.class)
                .addTypeArgument(it -> it.from(TypeArgument.create("?")).addUpperBound(convertedTypeInfo.wildcardsGenerics()))
                .build();
        addCreateDeserializerMethod(method, convertedTypeInfo, classType, false);
    }

    private static void addCreateDeserializerMethodGenerics(Method.Builder method, ConvertedTypeInfo convertedTypeInfo) {
        TypeName classType = TypeName.builder()
                .from(TypeNames.GENERIC_TYPE)
                .addTypeArgument(it -> it.from(TypeArgument.create("?")).addUpperBound(convertedTypeInfo.wildcardsGenerics()))
                .build();
        addCreateDeserializerMethod(method, convertedTypeInfo, classType, true);
    }

    private static void addCreateDeserializerMethod(Method.Builder method,
                                                    ConvertedTypeInfo convertedTypeInfo,
                                                    TypeName parameter,
                                                    boolean genericType) {
        method.name("createDeserializer")
                .addAnnotation(Annotation.create(Override.class))
                .returnType(builder -> builder.type(TypeName.builder()
                                                            .from(JsonTypes.JSON_DESERIALIZER_TYPE)
                                                            .addTypeArgument(convertedTypeInfo.wildcardsGenerics())
                                                            .build()))
                .addParameter(builder -> builder.type(parameter).name("type"))
                .addContent("return new ")
                .addContent(convertedTypeInfo.converterType())
                .addContentLine(genericType ? "(type.type());" : "(type);");
    }

    private static void addCreateSerializerMethodClass(Method.Builder method, ConvertedTypeInfo convertedTypeInfo) {
        TypeName classType = TypeName.builder()
                .type(Class.class)
                .addTypeArgument(it -> it.from(TypeArgument.create("?")).addUpperBound(convertedTypeInfo.wildcardsGenerics()))
                .build();
        addCreateSerializerMethod(method, convertedTypeInfo, classType, false);
    }

    private static void addCreateSerializerMethodGenerics(Method.Builder method, ConvertedTypeInfo convertedTypeInfo) {
        TypeName classType = TypeName.builder()
                .from(TypeNames.GENERIC_TYPE)
                .addTypeArgument(it -> it.from(TypeArgument.create("?")).addUpperBound(convertedTypeInfo.wildcardsGenerics()))
                .build();
        addCreateSerializerMethod(method, convertedTypeInfo, classType, true);
    }

    private static void addCreateSerializerMethod(Method.Builder method,
                                                  ConvertedTypeInfo convertedTypeInfo,
                                                  TypeName parameter,
                                                  boolean genericType) {
        method.name("createSerializer")
                .addAnnotation(Annotation.create(Override.class))
                .returnType(builder -> builder.type(TypeName.builder()
                                                            .from(JsonTypes.JSON_SERIALIZER_TYPE)
                                                            .addTypeArgument(convertedTypeInfo.wildcardsGenerics())
                                                            .build()))
                .addParameter(builder -> builder.type(parameter).name("type"))
                .addContent("return new ")
                .addContent(convertedTypeInfo.converterType())
                .addContentLine(genericType ? "(type.type());" : "(type);");
    }

    private static void addTypeMethod(Method.Builder method, ConvertedTypeInfo convertedTypeInfo) {
        method.name("supportedTypes")
                .addAnnotation(Annotation.create(Override.class))
                .returnType(builder -> builder.type(TypeName.builder()
                                                            .type(Set.class)
                                                            .addTypeArgument(b -> b.type(Class.class)
                                                                    .addTypeArgument(TypeArgument.create("?")))
                                                            .build()))
                .addContent("return ")
                .addContent(Set.class)
                .addContent(".of(")
                .addContent(convertedTypeInfo.originalType().genericTypeName())
                .addContentLine(".class);");
    }

}
