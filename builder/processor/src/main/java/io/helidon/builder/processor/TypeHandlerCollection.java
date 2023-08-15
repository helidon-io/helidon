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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.processor.GeneratorTools;
import io.helidon.common.processor.classmodel.Field;
import io.helidon.common.processor.classmodel.InnerClass;
import io.helidon.common.processor.classmodel.Javadoc;
import io.helidon.common.processor.classmodel.Method;
import io.helidon.common.types.TypeName;

import static io.helidon.builder.processor.Types.STRING_TYPE;
import static io.helidon.common.processor.classmodel.ClassModel.TYPE_TOKEN;

abstract class TypeHandlerCollection extends TypeHandler.OneTypeHandler {
    private final TypeName collectionType;
    private final TypeName collectionImplType;
    private final String collector;
    private final Optional<String> configMapper;

    TypeHandlerCollection(String name,
                          String getterName,
                          String setterName,
                          TypeName declaredType,
                          TypeName collectionType,
                          String collector,
                          Optional<String> configMapper) {
        super(name, getterName, setterName, declaredType);
        this.collectionType = collectionType;
        this.collectionImplType = collectionImplType(collectionType);
        this.collector = collector;
        this.configMapper = configMapper;
    }

    @Override
    Field.Builder fieldDeclaration(PrototypeProperty.ConfiguredOption configured, boolean isBuilder, boolean alwaysFinal) {
        Field.Builder builder = super.fieldDeclaration(configured, isBuilder, true);
        if (isBuilder && !configured.hasDefault()) {
            builder.defaultValue("new " + TYPE_TOKEN + collectionImplType.fqName() + TYPE_TOKEN + "<>()");
        }
        return builder;
    }

    @Override
    String toDefaultValue(String defaultValue) {
        String defaults = Stream.of(defaultValue.split(","))
                .map(super::toDefaultValue)
                .collect(Collectors.joining(", "));

        return collectionType.fqName() + ".of(" + defaults + ")";
    }

    @Override
    void generateFromConfig(Method.Builder method,
                            PrototypeProperty.ConfiguredOption configured,
                            FactoryMethods factoryMethods) {
        if (configured.provider()) {
            return;
        }
        if (factoryMethods.createFromConfig().isPresent()) {
            // todo this must be more clever - if a factory method exists, we need to check if it accepts a list
            // or a single value, now hardcoded to single value
            method.addLine(configGet(configured)
                                   + generateFromConfig(factoryMethods)
                                   + ".ifPresent(this::" + setterName() + ");");
        } else if (actualType().equals(STRING_TYPE)) {
            // String can be simplified
            method.addLine(configGet(configured)
                                   + ".asList(String.class)"
                                   + (configMapper.orElse(""))
                                   + ".ifPresent(this::" + setterName() + ");");
        } else {
            method.addLine(configGet(configured)
                                   + ".asNodeList()"
                                   + ".map(nodeList -> nodeList.stream()"
                                   + ".map(cfg -> cfg"
                                   + generateFromConfig(factoryMethods)
                                   + ".get())"
                                   + "." + collector + ")"
                                   + ".ifPresent(this::" + setterName() + ");");
        }
    }

    @Override
    TypeName argumentTypeName() {
        return TypeName.builder(collectionType)
                .addTypeArgument(toWildcard(actualType()))
                .build();
    }

    @Override
    void setters(InnerClass.Builder classBuilder,
                 PrototypeProperty.ConfiguredOption configured,
                 PrototypeProperty.Singular singular,
                 FactoryMethods factoryMethods,
                 TypeName returnType,
                 Javadoc blueprintJavadoc) {

        if (configured.provider()) {
            discoverServicesSetter(classBuilder, configured, returnType, blueprintJavadoc);
        }

        // we cannot call super. as collections are always final
        // there is always a setter with the declared type, replacing values
        declaredSetters(classBuilder, configured, returnType, blueprintJavadoc);

        if (factoryMethods.createTargetType().isPresent()) {
            // if there is a factory method for the return type, we also have setters for the type (probably config object)
            factorySetter(classBuilder, configured, returnType, blueprintJavadoc, factoryMethods.createTargetType().get());
        }

        if (singular.hasSingular()) {
            singularSetter(classBuilder, configured, returnType, blueprintJavadoc, singular);
        }

        if (factoryMethods.builder().isPresent()) {
            factorySetterConsumer(classBuilder,
                                  configured,
                                  returnType,
                                  blueprintJavadoc,
                                  factoryMethods,
                                  factoryMethods.builder().get(),
                                  singular);
        }
    }

    private void discoverServicesSetter(InnerClass.Builder classBuilder,
                                        PrototypeProperty.ConfiguredOption configured,
                                        TypeName returnType,
                                        Javadoc blueprintJavadoc) {
        classBuilder.addMethod(builder -> builder.name(setterName() + "DiscoverServices")
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name("discoverServices")
                        .type(boolean.class)
                        .description("whether to discover implementations through service loader"))
                .accessModifier(setterAccessModifier(configured))
                .addLine("this." + name() + "DiscoverServices = discoverServices;")
                .addLine("return self();"));
    }

    private void factorySetterConsumer(InnerClass.Builder classBuilder,
                                       PrototypeProperty.ConfiguredOption configured,
                                       TypeName returnType,
                                       Javadoc blueprintJavadoc,
                                       FactoryMethods factoryMethods,
                                       FactoryMethods.FactoryMethod factoryMethod,
                                       PrototypeProperty.Singular singular) {
        // if there is a factory method for the return type, we also have setters for the type (probably config object)
        TypeName builderType;
        if (factoryMethod.factoryMethodReturnType().className().equals("Builder")) {
            builderType = factoryMethod.factoryMethodReturnType();
        } else {
            builderType = TypeName.create(factoryMethod.factoryMethodReturnType().fqName() + ".Builder");
        }
        TypeName argumentType = TypeName.builder()
                .type(Consumer.class)
                .addTypeArgument(builderType)
                .build();
        String argumentName = "consumer";

        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(argumentName)
                        .type(argumentType)
                        .description(blueprintJavadoc.returnDescription()))
                .accessModifier(setterAccessModifier(configured))
                .typeName(Objects.class)
                .addLine(".requireNonNull(" + argumentName + ");")
                .add("var builder = ")
                .typeName(factoryMethod.typeWithFactoryMethod().genericTypeName())
                .addLine("." + factoryMethod.createMethodName() + "();")
                .addLine("consumer.accept(builder);");

        if (factoryMethods.createTargetType()
                .map(FactoryMethods.FactoryMethod::factoryMethodReturnType)
                .map(m -> m.genericTypeName().equals(collectionType))
                .orElse(false)) {
            builder.addLine("this." + name() + "(builder.build());")
                    .addLine("return self();");
            classBuilder.addMethod(builder);
        } else if (singular.hasSingular()) {
            String singularName = singular.singularName();
            String methodName = "add" + GeneratorTools.capitalize(singularName);
            builder.name(methodName)
                    .addLine("this." + name() + ".add(builder.build());")
                    .addLine("return self();");
            classBuilder.addMethod(builder);
        }
    }

    private void singularSetter(InnerClass.Builder classBuilder, PrototypeProperty.ConfiguredOption configured,
                                TypeName returnType,
                                Javadoc blueprintJavadoc,
                                PrototypeProperty.Singular singular) {
        String singularName = singular.singularName();
        String methodName = "add" + GeneratorTools.capitalize(singularName);

        Method.Builder builder = Method.builder()
                .name(methodName)
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(singularName)
                        .type(actualType())
                        .description(blueprintJavadoc.returnDescription()))
                .accessModifier(setterAccessModifier(configured))
                .typeName(Objects.class)
                .addLine(".requireNonNull(" + singularName + ");")
                .addLine("this." + name() + ".add(" + singularName + ");")
                .addLine("return self();");
        classBuilder.addMethod(builder);
    }

    private void factorySetter(InnerClass.Builder classBuilder,
                               PrototypeProperty.ConfiguredOption configured,
                               TypeName returnType,
                               Javadoc blueprintJavadoc,
                               FactoryMethods.FactoryMethod factoryMethod) {
        String argumentName = name() + "Config";
        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(argumentName)
                        .type(factoryMethod.argumentType())
                        .description(blueprintJavadoc.returnDescription()))
                .accessModifier(setterAccessModifier(configured))
                .typeName(Objects.class)
                .addLine(".requireNonNull(" + argumentName + ");")
                .addLine("this." + name() + ".clear();")
                .add("this." + name() + ".addAll(")
                .typeName(factoryMethod.typeWithFactoryMethod().genericTypeName())
                .addLine("." + factoryMethod.createMethodName() + "(" + argumentName + "));")
                .addLine("return self();");
        classBuilder.addMethod(builder);
    }

    private void declaredSetters(InnerClass.Builder classBuilder,
                                 PrototypeProperty.ConfiguredOption configured,
                                 TypeName returnType,
                                 Javadoc blueprintJavadoc) {
        // we cannot call super. as collections are always final
        // there is always a setter with the declared type, replacing values
        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(name())
                        .type(argumentTypeName())
                        .description(blueprintJavadoc.returnDescription()))
                .accessModifier(setterAccessModifier(configured))
                .typeName(Objects.class)
                .addLine(".requireNonNull(" + name() + ");")
                .addLine("this." + name() + ".clear();")
                .addLine("this." + name() + ".addAll(" + name() + ");")
                .addLine("return self();");
        classBuilder.addMethod(builder);

        builder.name("add" + GeneratorTools.capitalize(name()))
                .clearContent()
                .addLine("Objects.requireNonNull(" + name() + ");") //Overwrites existing content
                .addLine("this." + name() + ".addAll(" + name() + ");")
                .addLine("return self();");
        classBuilder.addMethod(builder);
    }
}
