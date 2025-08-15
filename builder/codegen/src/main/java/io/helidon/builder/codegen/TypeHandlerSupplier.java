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

class TypeHandlerSupplier extends TypeHandler.OneTypeHandler {

    TypeHandlerSupplier(TypeName blueprintType,
                        TypedElementInfo annotatedMethod,
                        String name, String getterName, String setterName, TypeName declaredType) {
        super(blueprintType, annotatedMethod, name, getterName, setterName, declaredType);
    }

    @Override
    Field.Builder fieldDeclaration(AnnotationDataOption configured, boolean isBuilder, boolean alwaysFinal) {
        Field.Builder builder = Field.builder()
                .type(declaredType())
                .name(name())
                .isFinal(alwaysFinal || !isBuilder);

        if (isBuilder && configured.hasDefault()) {
            builder.addContent("() -> ");
            configured.defaultValue().accept(builder);
        }

        return builder;
    }

    @Override
    TypeName argumentTypeName() {
        TypeName type = actualType();
        if (TypeNames.STRING.equals(type) || toPrimitive(type).primitive() || type.array()) {
            return declaredType();
        }

        return TypeName.builder(SUPPLIER)
                .addTypeArgument(toWildcard(type))
                .build();
    }

    @Override
    void generateFromConfig(Method.Builder method, AnnotationDataOption configured, FactoryMethods factoryMethods) {
        if (configured.provider()) {
            return;
        }
        if (factoryMethods.createFromConfig().isPresent()) {
            method.addContent(configGet(configured));
            generateFromConfig(method, factoryMethods);
            method.addContentLine(".ifPresent(this::" + setterName() + ");");
        } else if (actualType().isOptional()) {
            method.addContent(setterName() + "(");
            method.addContent(configGet(configured));
            method.addContent(generateFromConfigOptional(factoryMethods));
            method.addContentLine(".optionalSupplier());");
        } else {
            method.addContent(setterName() + "(");
            method.addContent(configGet(configured));
            generateFromConfig(method, factoryMethods);
            method.addContentLine(".supplier());");
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
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + name() + ");")
                .addContentLine("this." + name() + " = () -> " + name() + ";")
                .addContentLine("return self();");
        classBuilder.addMethod(method);

        if (actualType().equals(CHAR_ARRAY)) {
            classBuilder.addMethod(builder -> builder.name(setterName())
                    .returnType(returnType, "updated builder instance")
                    .description(blueprintJavadoc.content())
                    .addJavadocTag("see", "#" + getterName() + "()")
                    .addParameter(param -> param.name(name())
                            .type(TypeNames.STRING)
                            .description(blueprintJavadoc.returnDescription()))
                    .accessModifier(setterAccessModifier(configured))
                    .addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + name() + ");")
                    .addContentLine("this." + name() + " = () -> " + name() + ".toCharArray();")
                    .addContentLine("return self();"));
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
                    .addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + argumentName + ");")
                    .addContent("this." + name() + " = ")
                    .addContent(fm.typeWithFactoryMethod().genericTypeName())
                    .addContentLine("." + fm.createMethodName() + "(" + argumentName + ");")
                    .addContentLine("return self();"));
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

            if (!skipBuilderConsumer(builderType)) {
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
                        .addContent(Objects.class)
                        .addContentLine(".requireNonNull(" + argumentName + ");")
                        .addContent("var builder = ")
                        .addContent(fm.typeWithFactoryMethod().genericTypeName())
                        .addContentLine("." + fm.createMethodName() + "();")
                        .addContentLine("consumer.accept(builder);")
                        .addContentLine("this." + name() + "(builder.build());")
                        .addContentLine("return self();"));
            }
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
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + name() + ");")
                .addContentLine("this." + name() + " = " + name() + "::get;")
                .addContentLine("return self();"));
    }
}
