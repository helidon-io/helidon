/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.codegen;

import java.util.List;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.Builder;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;

/**
 * Builder is an inner class of the prototype.
 * It extends the base builder, so we can support further extensibility.
 * Class name is always "Builder"
 * Super class name is always "BuilderBase"
 */
final class GenerateBuilder {

    private GenerateBuilder() {
    }

    static void generate(ClassModel.Builder classBuilder,
                         TypeName prototype,
                         TypeName runtimeType,
                         List<TypeArgument> typeArguments,
                         boolean isFactory,
                         TypeContext typeContext) {
        classBuilder.addInnerClass(builder -> {
            TypeName builderType = TypeName.builder()
                    .from(TypeName.create(prototype.fqName() + ".Builder"))
                    .addTypeArguments(typeArguments)
                    .build();
            typeArguments.forEach(builder::addGenericArgument);
            builder.name("Builder")
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .description("Fluent API builder for {@link " + runtimeType.className() + "}.")
                    .superType(TypeName.builder()
                                         .from(TypeName.create(prototype.fqName() + ".BuilderBase"))
                                         .addTypeArguments(typeArguments)
                                         .addTypeArgument(builderType)
                                         .addTypeArgument(prototype)
                                         .build())
                    .addInterface(TypeName.builder()
                                          .from(TypeName.create(Builder.class))
                                          .addTypeArgument(builderType)
                                          .addTypeArgument(runtimeType)
                                          .build())
                    .addConstructor(constructor -> {
                        if (typeContext.blueprintData().builderPublic()) {
                            constructor.accessModifier(AccessModifier.PRIVATE);
                        } else {
                            // package private to allow instantiation
                            constructor.accessModifier(AccessModifier.PACKAGE_PRIVATE);
                        }
                    })
                    .addMethod(method -> {
                        method.name("buildPrototype")
                                .returnType(prototype)
                                .addAnnotation(Annotations.OVERRIDE)
                                .addContentLine("preBuildPrototype();")
                                .addContentLine("validatePrototype();")
                                .addContent("return new ")
                                .addContent(prototype.genericTypeName())
                                .addContent("Impl");
                        if (!typeArguments.isEmpty()) {
                            method.addContent("<>");
                        }
                        method.addContentLine("(this);");
                    });
            if (isFactory) {
                GenerateAbstractBuilder.buildRuntimeObjectMethod(builder, typeContext, true);
            } else {
                // build method returns the same as buildPrototype method
                builder.addMethod(method -> method.name("build")
                        .addAnnotation(Annotations.OVERRIDE)
                        .returnType(runtimeType)
                        .addContentLine("return buildPrototype();"));
            }
        });
    }
}
