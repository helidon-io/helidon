/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.CHAR_ARRAY;
import static io.helidon.common.types.TypeNames.SUPPLIER;

class TypeHandlerSupplier extends TypeHandlerBase {

    TypeHandlerSupplier(PrototypeInfo prototypeInfo, OptionInfo option) {
        super(prototypeInfo, option, firstTypeArgument(option));
    }

    @Override
    public Field.Builder field(boolean isBuilder) {
        Field.Builder builder = Field.builder()
                .type(option().declaredType())
                .name(option().name())
                .isFinal(!isBuilder);

        if (isBuilder && option().defaultValue().isPresent()) {
            builder.addContent("() -> ");
            option().defaultValue().get().accept(builder);
        }

        return builder;
    }

    @Override
    public void generateFromConfig(Method.Builder method, OptionConfigured optionConfigured) {
        if (option().provider().isPresent()) {
            return;
        }
        Optional<FactoryMethod> factoryMethod = findFactory(prototype(), option().declaredType());
        TypedElementInfo setter = option().setter();
        if (factoryMethod.isPresent()) {
            method.addContent(configGet(optionConfigured));
            generateFromConfig(method, factoryMethod.get());
            method.addContentLine(".ifPresent(this::" + setter.elementName() + ");");
        } else if (type().isOptional()) {
            method.addContent(setter.elementName() + "(");
            method.addContent(configGet(optionConfigured));
            method.addContent(generateFromConfigOptional());
            method.addContentLine(".optionalSupplier());");
        } else {
            method.addContent(setter.elementName() + "(");
            method.addContent(configGet(optionConfigured));
            generateFromConfig(method);
            method.addContentLine(".supplier());");
        }
    }

    @Override
    public void setters(InnerClass.Builder classBuilder, TypeName returnType) {
        declaredSetter(classBuilder, returnType);

        String optionName = option().name();
        var setter = option().setter();

        // and add the setter with the actual type
        Method.Builder method = Method.builder()
                .name(setter.elementName())
                .accessModifier(setter.accessModifier())
                .javadoc(Javadoc.parse(setter.description().orElse("")))
                .returnType(returnType)
                .addParameter(param -> param.name(optionName)
                        .type(type()))
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + optionName + ");")
                .addContentLine("this." + optionName + " = () -> " + optionName + ";")
                .addContentLine("return self();");
        classBuilder.addMethod(method);

        if (type().equals(CHAR_ARRAY)) {
            classBuilder.addMethod(builder -> builder.name(setter.elementName())
                    .returnType(returnType, "updated builder instance")
                    .javadoc(Javadoc.parse(setter.description().orElse("")))
                    .addParameter(param -> param.name(optionName)
                            .type(TypeNames.STRING))
                    .accessModifier(setter.accessModifier())
                    .addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + optionName + ");")
                    .addContentLine("this." + optionName + " = () -> " + optionName + ".toCharArray();")
                    .addContentLine("return self();"));
        }

        if (option().builderInfo().isPresent()) {
            // if there is a factory method for the return type, we also have setters for the type (probably config object)
            OptionBuilder optionBuilder = option().builderInfo().get();

            String argumentName = "consumer";
            TypeName argumentType = TypeName.builder()
                    .type(Consumer.class)
                    .addTypeArgument(optionBuilder.builderType())
                    .build();

            classBuilder.addMethod(builder -> builder.name(setter.elementName())
                    .accessModifier(setter.accessModifier())
                    .returnType(returnType)
                    .addParameter(param -> param.name(argumentName)
                            .type(argumentType))
                    .javadoc(Javadoc.parse(setter.description().orElse("")))
                    .addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + argumentName + ");")
                    .addContent("var builder = ")
                    .addContent(optionBuilder.builderType())
                    .addContentLine("." + optionBuilder.builderMethodName() + "();")
                    .addContentLine("consumer.accept(builder);")
                    .addContent("this." + optionName + "(builder.")
                    .addContent(optionBuilder.buildMethodName())
                    .addContentLine("());")
                    .addContentLine("return self();"));
        }
    }

    @Override
    TypeName setterArgumentTypeName() {
        TypeName type = type();
        if (TypeNames.STRING.equals(type) || type.unboxed().primitive() || type.array()) {
            return option().declaredType();
        }

        return TypeName.builder(SUPPLIER)
                .addTypeArgument(toWildcard(type))
                .build();
    }

    String generateFromConfigOptional() {
        TypeName optionalType = type().typeArguments().get(0);
        if (optionalType.fqName().equals("char[]")) {
            return ".asString().as(String::toCharArray)";
        }

        TypeName boxed = optionalType.boxed();
        return ".as(" + boxed.fqName() + ".class)";

    }

    void declaredSetter(InnerClass.Builder classBuilder, TypeName returnType) {
        var setter = option().setter();

        classBuilder.addMethod(method -> method.name(setter.elementName())
                .returnType(returnType)
                .javadoc(Javadoc.parse(setter.description().orElse("")))
                .addParameter(param -> param.name(option().name())
                        .type(setterArgumentTypeName()))
                .accessModifier(setter.accessModifier())
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + option().name() + ");")
                .addContentLine("this." + option().name() + " = " + option().name() + "::get;")
                .addContentLine("return self();"));
    }
}
