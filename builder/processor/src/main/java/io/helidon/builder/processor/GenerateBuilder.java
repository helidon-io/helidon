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

package io.helidon.builder.processor;

import java.util.List;

import io.helidon.common.Builder;
import io.helidon.common.processor.model.AccessModifier;
import io.helidon.common.processor.model.Annotation;
import io.helidon.common.processor.model.ClassModel;
import io.helidon.common.processor.model.TypeArgument;
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
                    .addConstructor(constructor -> constructor.accessModifier(AccessModifier.PRIVATE))
                    .addMethod(method -> {
                        method.name("buildPrototype")
                                .returnType(prototype)
                                .addAnnotation(Annotation.create(Override.class))
                                .addLine("preBuildPrototype();")
                                .addLine("validatePrototype();")
                                .add("return new ")
                                .typeName(prototype)
                                .add("Impl");
                        if (!typeArguments.isEmpty()) {
                            method.add("<>");
                        }
                        method.addLine("(this);");
                    });
            if (isFactory) {
                GenerateAbstractBuilder.buildRuntimeObjectMethod(builder, typeContext, true);
            } else {
                // build method returns the same as buildPrototype method
                builder.addMethod(method -> method.name("build")
                        .addAnnotation(Annotation.create(Override.class))
                        .returnType(runtimeType)
                        .addLine("return buildPrototype();"));
            }
        });
    }
}
