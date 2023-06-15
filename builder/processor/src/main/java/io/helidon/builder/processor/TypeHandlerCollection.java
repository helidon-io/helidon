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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.processor.GeneratorTools;
import io.helidon.common.types.TypeName;

import static io.helidon.builder.processor.Types.STRING_TYPE;

abstract class TypeHandlerCollection extends TypeHandler.OneTypeHandler {
    private final TypeName collectionType;
    private final String collectionImplType;
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
    String fieldDeclaration(PrototypeProperty.ConfiguredOption configured, boolean isBuilder, boolean alwaysFinal) {
        return super.fieldDeclaration(configured, isBuilder, true)
                + (isBuilder && !configured.hasDefault() ? " = new " + collectionImplType + "<>()" : "");
    }

    @Override
    String toDefaultValue(String defaultValue) {
        String defaults = Stream.of(defaultValue.split(","))
                .map(super::toDefaultValue)
                .collect(Collectors.joining(", "));

        return collectionType.fqName() + ".of(" + defaults + ")";
    }

    @Override
    Optional<String> generateFromConfig(PrototypeProperty.ConfiguredOption configured, FactoryMethods factoryMethods) {
        if (configured.provider()) {
            return Optional.empty();
        }
        if (factoryMethods.createFromConfig().isPresent()) {
            // todo this must be more clever - if a factory method exists, we need to check if it accepts a list
            // or a single value, now hardcoded to single value
            return Optional.of(configGet(configured)
                                       + generateFromConfig(factoryMethods)
                                       + ".ifPresent(this::" + setterName() + ");");
        }

        // String can be simplified
        if (actualType().equals(STRING_TYPE)) {
            return Optional.of(configGet(configured)
                                       + ".asList(String.class)"
                                       + (configMapper.isPresent() ? configMapper.get() : "")
                                       + ".ifPresent(this::" + setterName() + ");");
        }
        return Optional.of(configGet(configured)
                                   + ".asNodeList()"
                                   + ".map(nodeList -> nodeList.stream()"
                                   + ".map(cfg -> cfg"
                                   + generateFromConfig(factoryMethods)
                                   + ".get())"
                                   + "." + collector + ")"
                                   + ".ifPresent(this::" + setterName() + ");");
    }

    @Override
    TypeName argumentTypeName() {
        return TypeName.builder(collectionType)
                .addTypeArgument(toWildcard(actualType()));
    }

    @Override
    void setters(PrototypeProperty.ConfiguredOption configured,
                 PrototypeProperty.Singular singular,
                 List<GeneratedMethod> setters,
                 FactoryMethods factoryMethods,
                 TypeName returnType,
                 Javadoc blueprintJavadoc) {

        if (configured.provider()) {
            discoverServicesSetter(configured, setters, returnType, blueprintJavadoc);
        }

        // we cannot call super. as collections are always final
        // there is always a setter with the declared type, replacing values
        declaredSetters(configured, setters, returnType, blueprintJavadoc);

        if (factoryMethods.createTargetType().isPresent()) {
            // if there is a factory method for the return type, we also have setters for the type (probably config object)
            factorySetter(configured, setters, returnType, blueprintJavadoc, factoryMethods.createTargetType().get());
        }

        if (singular.hasSingular()) {
            singularSetter(configured, setters, returnType, blueprintJavadoc, singular);
        }

        if (factoryMethods.builder().isPresent()) {
            factorySetterConsumer(configured,
                                  setters,
                                  returnType,
                                  blueprintJavadoc,
                                  factoryMethods,
                                  factoryMethods.builder().get(),
                                  singular);
        }
    }

    private void discoverServicesSetter(PrototypeProperty.ConfiguredOption configured,
                                        List<GeneratedMethod> setters,
                                        TypeName returnType,
                                        Javadoc blueprintJavadoc) {

        Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                      List.of(new Javadoc.Tag("discoverServices",
                                                              List.of("whether to discover implementations through service "
                                                                              + "loader"))),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        List<String> methodLines = List.of(
                "this." + name() + "DiscoverServices = discoverServices;",
                "return me();"
        );

        setters.add(new GeneratedMethod(Set.of(setterModifier(configured).trim()),
                                        setterName() + "DiscoverServices",
                                        returnType,
                                        List.of(new GeneratedMethod.Argument("discoverServices", TypeName.create(boolean.class))),
                                        List.of(),
                                        javadoc,
                                        methodLines
        ));
    }

    private void factorySetterConsumer(PrototypeProperty.ConfiguredOption configured,
                                       List<GeneratedMethod> setters,
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
        String argumentName = "consumer";

        if (factoryMethods.createTargetType()
                .map(FactoryMethods.FactoryMethod::factoryMethodReturnType)
                .map(m -> m.genericTypeName().equals(collectionType))
                .orElse(false)) {

            Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                          List.of(new Javadoc.Tag(argumentName, blueprintJavadoc.returns())),
                                          List.of("updated builder instance"),
                                          List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

            List<String> lines = new ArrayList<>();
            lines.add("Objects.requireNonNull(" + argumentName + ");");
            lines.add("var builder = " + factoryMethod.typeWithFactoryMethod().genericTypeName().fqName()
                              + "." + factoryMethod.createMethodName() + "();");
            lines.add("consumer.accept(builder);");
            lines.add("this." + name() + "(builder.build());");
            lines.add("return me();");

            TypeName argumentType = TypeName.builder()
                    .type(Consumer.class)
                    .addTypeArgument(builderType)
                    .build();

            setters.add(new GeneratedMethod(
                    Set.of(setterModifier(configured).trim()),
                    setterName(),
                    returnType,
                    List.of(new GeneratedMethod.Argument(argumentName, argumentType)),
                    List.of(),
                    javadoc,
                    lines
            ));
        } else {
            if (singular.hasSingular()) {
                String singularName = singular.singularName();
                String methodName = "add" + GeneratorTools.capitalize(singularName);

                Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                              List.of(new Javadoc.Tag(argumentName, blueprintJavadoc.returns())),
                                              List.of("updated builder instance"),
                                              List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

                List<String> lines = new ArrayList<>();
                lines.add("Objects.requireNonNull(" + argumentName + ");");
                lines.add("var builder = " + factoryMethod.typeWithFactoryMethod().genericTypeName().fqName()
                                  + "." + factoryMethod.createMethodName() + "();");
                lines.add("consumer.accept(builder);");
                lines.add("this." + name() + ".add(builder.build());");
                lines.add("return me();");

                TypeName argumentType = TypeName.builder()
                        .type(Consumer.class)
                        .addTypeArgument(builderType)
                        .build();

                setters.add(new GeneratedMethod(
                        Set.of(setterModifier(configured).trim()),
                        methodName,
                        returnType,
                        List.of(new GeneratedMethod.Argument(argumentName, argumentType)),
                        List.of(),
                        javadoc,
                        lines
                ));
            }
        }
    }

    private void singularSetter(PrototypeProperty.ConfiguredOption configured,
                                List<GeneratedMethod> setters,
                                TypeName returnType,
                                Javadoc blueprintJavadoc,
                                PrototypeProperty.Singular singular) {
        String singularName = singular.singularName();
        String methodName = "add" + GeneratorTools.capitalize(singularName);

        Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                      List.of(new Javadoc.Tag(singularName, blueprintJavadoc.returns())),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        List<String> lines = new ArrayList<>();
        lines.add("Objects.requireNonNull(" + singularName + ");");
        lines.addAll(resolveBuilderLines(actualType(), singularName));
        lines.add("this." + name() + ".add(" + singularName + ");");
        lines.add("return me();");

        setters.add(new GeneratedMethod(
                Set.of(setterModifier(configured).trim()),
                methodName,
                returnType,
                List.of(new GeneratedMethod.Argument(singularName, actualType())),
                List.of(),
                javadoc,
                lines
        ));
    }

    private void factorySetter(PrototypeProperty.ConfiguredOption configured,
                               List<GeneratedMethod> setters,
                               TypeName returnType,
                               Javadoc blueprintJavadoc,
                               FactoryMethods.FactoryMethod factoryMethod) {
        String argumentName = name() + "Config";

        Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                      List.of(new Javadoc.Tag(argumentName, blueprintJavadoc.returns())),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        List<String> lines = new ArrayList<>();
        lines.add("Objects.requireNonNull(" + argumentName + ");");
        lines.add("this." + name() + ".clear();");
        lines.add("this." + name() + ".addAll(" + factoryMethod.typeWithFactoryMethod().genericTypeName().fqName() + "."
                          + factoryMethod.createMethodName() + "(" + argumentName + "));");
        lines.add("return me();");

        setters.add(new GeneratedMethod(
                Set.of(setterModifier(configured).trim()),
                setterName(),
                returnType,
                List.of(new GeneratedMethod.Argument(argumentName, factoryMethod.argumentType())),
                List.of(),
                javadoc,
                lines
        ));
    }

    private void declaredSetters(PrototypeProperty.ConfiguredOption configured,
                                 List<GeneratedMethod> setters,
                                 TypeName returnType,
                                 Javadoc blueprintJavadoc) {
        // we cannot call super. as collections are always final
        // there is always a setter with the declared type, replacing values
        List<String> lines = new ArrayList<>();
        lines.add("Objects.requireNonNull(" + name() + ");");
        lines.add("this." + name() + ".clear();");
        lines.add("this." + name() + ".addAll(" + name() + ");");
        lines.add("return me();");

        Javadoc javadoc = new Javadoc(blueprintJavadoc.lines(),
                                      List.of(new Javadoc.Tag(name(), blueprintJavadoc.returns())),
                                      List.of("updated builder instance"),
                                      List.of(new Javadoc.Tag("see", List.of("#" + getterName() + "()"))));

        setters.add(new GeneratedMethod(
                Set.of(setterModifier(configured).trim()),
                setterName(),
                returnType,
                List.of(new GeneratedMethod.Argument(name(), argumentTypeName())),
                List.of(),
                javadoc,
                lines
        ));

        // adding values
        lines = new ArrayList<>();
        lines.add("Objects.requireNonNull(" + name() + ");");
        lines.add("this." + name() + ".addAll(" + name() + ");");
        lines.add("return me();");

        setters.add(new GeneratedMethod(
                Set.of(setterModifier(configured).trim()),
                "add" + GeneratorTools.capitalize(name()), // use plural, as this expects whole collection
                returnType,
                List.of(new GeneratedMethod.Argument(name(), argumentTypeName())),
                List.of(),
                javadoc,
                lines
        ));
    }
}
