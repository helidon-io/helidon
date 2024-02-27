/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;

import static io.helidon.codegen.CodegenUtil.capitalize;
import static io.helidon.common.types.TypeNames.LIST;

class TypeHandlerList extends TypeHandlerCollection {

    TypeHandlerList(String name, String getterName, String setterName, TypeName declaredType) {
        super(name, getterName, setterName, declaredType, LIST, "toList()", Optional.empty());
    }

    @Override
    void setters(InnerClass.Builder classBuilder,
                 AnnotationDataOption configured,
                 FactoryMethods factoryMethods,
                 TypeName returnType,
                 Javadoc blueprintJavadoc) {
        super.setters(classBuilder, configured, factoryMethods, returnType, blueprintJavadoc);
        declaredDistinctSetter(classBuilder, returnType, blueprintJavadoc);
    }

    private void declaredDistinctSetter(InnerClass.Builder classBuilder, TypeName returnType, Javadoc blueprintJavadoc) {

        // Primarily for use in preventing default values from being included in a list twice using Builder.from.
        classBuilder.addImport(Predicate.class);
        Method.Builder builder = Method.builder()
                .name("addDistinct" + capitalize(name()))
                .accessModifier(AccessModifier.PROTECTED)
                .returnType(returnType, "updated builder instance")
                .description("Adds distinct values")
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(name())
                        .type(argumentTypeName())
                        .description(blueprintJavadoc.returnDescription()))

                .addContentLine("Objects.requireNonNull(" + name() + ");")
                .addContentLine(name() + ".stream()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine(".filter(Predicate.not(this." + name() + "::contains))")
                .addContentLine(".forEach(this." + name() + "::add);")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("return self();");
        classBuilder.addMethod(builder);
    }
}
