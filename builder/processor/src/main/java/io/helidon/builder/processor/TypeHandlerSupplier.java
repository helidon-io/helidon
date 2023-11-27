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

import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.common.processor.classmodel.Field;
import io.helidon.common.processor.classmodel.InnerClass;
import io.helidon.common.processor.classmodel.Javadoc;
import io.helidon.common.processor.classmodel.Method;
import io.helidon.common.types.TypeName;

import static io.helidon.builder.processor.Types.CHAR_ARRAY_TYPE;
import static io.helidon.builder.processor.Types.STRING_TYPE;
import static io.helidon.common.types.TypeNames.SUPPLIER;

class TypeHandlerSupplier extends TypeHandler.OneTypeHandler {

    TypeHandlerSupplier(String name, String getterName, String setterName, TypeName declaredType) {
        super(name, getterName, setterName, declaredType);
    }

    @Override
    Field.Builder fieldDeclaration(AnnotationDataOption configured, boolean isBuilder, boolean alwaysFinal) {
        Field.Builder builder = Field.builder()
                .type(declaredType())
                .name(name())
                .isFinal(alwaysFinal || !isBuilder);

        if (isBuilder && configured.hasDefault()) {
            builder.defaultValue("() -> " + configured.defaultValue());
        }

        return builder;
    }

    @Override
    TypeName argumentTypeName() {
        return TypeName.builder(SUPPLIER)
                .addTypeArgument(toWildcard(actualType()))
                .build();
    }

    @Override
    void generateFromConfig(Method.Builder method, AnnotationDataOption configured, FactoryMethods factoryMethods) {
        if (configured.provider()) {
            return;
        }
        if (factoryMethods.createFromConfig().isPresent()) {
            method.addLine(configGet(configured)
                                   + generateFromConfig(factoryMethods)
                                   + ".ifPresent(this::" + setterName() + ");");
        } else if (actualType().isOptional()) {
            method.add(setterName() + "(");
            method.add(configGet(configured));
            method.add(generateFromConfigOptional(factoryMethods));
            method.addLine(".optionalSupplier());");
        } else {
            method.add(setterName() + "(");
            method.add(configGet(configured));
            method.add(generateFromConfig(factoryMethods));
            method.addLine(".supplier());");
        }
    }

    String generateFromConfigOptional(FactoryMethods factoryMethods) {
        TypeName optionalType = actualType().typeArguments().get(0);
        if (optionalType.fqName().equals("char[]")) {
            return ".asString().as(String::toCharArray)";
        }

        TypeName boxed = optionalType.boxed();
        return factoryMethods.createFromConfig()
                .map(it -> ".map(" + it.typeWithFactoryMethod().genericTypeName().fqName() + "::" + it.createMethodName() + ")")
                .orElseGet(() -> ".as(" + boxed.fqName() + ".class)");

    }

    @Override
    void setters(InnerClass.Builder classBuilder,
                 AnnotationDataOption configured,
                 FactoryMethods factoryMethod,
                 TypeName returnType,
                 Javadoc blueprintJavadoc) {

        declaredSetter(classBuilder, configured, returnType, blueprintJavadoc);

        // and add the setter with the actual type
        Method.Builder method = Method.builder()
                .name(setterName())
                .description(blueprintJavadoc.content())
                .returnType(returnType, "updated builder instance")
                .addParameter(param -> param.name(name())
                        .type(actualType())
                        .description(blueprintJavadoc.returnDescription()))
                .addJavadocTag("see", "#" + getterName() + "()")
                .typeName(Objects.class)
                .addLine(".requireNonNull(" + name() + ");")
                .addLine("this." + name() + " = () -> " + name() + ";")
                .addLine("return self();");
        classBuilder.addMethod(method);

        if (actualType().equals(CHAR_ARRAY_TYPE)) {
            classBuilder.addMethod(builder -> builder.name(setterName())
                    .returnType(returnType, "updated builder instance")
                    .description(blueprintJavadoc.content())
                    .addJavadocTag("see", "#" + getterName() + "()")
                    .addParameter(param -> param.name(name())
                            .type(STRING_TYPE)
                            .description(blueprintJavadoc.returnDescription()))
                    .accessModifier(setterAccessModifier(configured))
                    .typeName(Objects.class).addLine(".requireNonNull(" + name() + ");")
                    .addLine("this." + name() + " = () -> " + name() + ".toCharArray();")
                    .addLine("return self();"));
        }

        if (factoryMethod.createTargetType().isPresent()) {
            // if there is a factory method for the return type, we also have setters for the type (probably config object)
            FactoryMethods.FactoryMethod fm = factoryMethod.createTargetType().get();
            String argumentName = name() + "Config";

            classBuilder.addMethod(builder -> builder.name(setterName())
                    .accessModifier(setterAccessModifier(configured))
                    .description(blueprintJavadoc.content())
                    .returnType(returnType, "updated builder instance")
                    .addParameter(param -> param.name(argumentName)
                            .type(fm.argumentType())
                            .description(blueprintJavadoc.returnDescription()))
                    .addJavadocTag("see", "#" + getterName() + "()")
                    .typeName(Objects.class)
                    .addLine(".requireNonNull(" + argumentName + ");")
                    .add("this." + name() + " = ")
                    .typeName(fm.typeWithFactoryMethod().genericTypeName())
                    .addLine("." + fm.createMethodName() + "(" + argumentName + ");")
                    .addLine("return self();"));
        }

        if (factoryMethod.builder().isPresent()) {
            // if there is a factory method for the return type, we also have setters for the type (probably config object)
            FactoryMethods.FactoryMethod fm = factoryMethod.builder().get();

            TypeName builderType;
            String className = fm.factoryMethodReturnType().className();
            if (className.equals("Builder") || className.endsWith(".Builder")) {
                builderType = fm.factoryMethodReturnType();
            } else {
                builderType = TypeName.create(fm.factoryMethodReturnType().fqName() + ".Builder");
            }
            String argumentName = "consumer";
            TypeName argumentType = TypeName.builder()
                    .type(Consumer.class)
                    .addTypeArgument(builderType)
                    .build();

            Javadoc javadoc = setterJavadoc(blueprintJavadoc)
                    .addParameter(argumentName, blueprintJavadoc.returnDescription())
                    .build();

            classBuilder.addMethod(builder -> builder.name(setterName())
                    .accessModifier(setterAccessModifier(configured))
                    .returnType(returnType)
                    .addParameter(param -> param.name(argumentName)
                            .type(argumentType))
                    .javadoc(javadoc)
                    .typeName(Objects.class)
                    .addLine(".requireNonNull(" + argumentName + ");")
                    .add("var builder = ")
                    .typeName(fm.typeWithFactoryMethod().genericTypeName())
                    .addLine("." + fm.createMethodName() + "();")
                    .addLine("consumer.accept(builder);")
                    .addLine("this." + name() + "(builder.build());")
                    .addLine("return self();"));
        }
    }

    protected void declaredSetter(InnerClass.Builder classBuilder,
                                  AnnotationDataOption configured,
                                  TypeName returnType,
                                  Javadoc blueprintJavadoc) {
        classBuilder.addMethod(method -> method.name(setterName())
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(name())
                        .type(argumentTypeName())
                        .description(blueprintJavadoc.returnDescription()))
                .accessModifier(setterAccessModifier(configured))
                .typeName(Objects.class).addLine(".requireNonNull(" + name() + ");")
                .addLine("this." + name() + " = " + name() + "::get;")
                .addLine("return self();"));
    }
}
