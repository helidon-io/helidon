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

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.common.processor.GeneratorTools;
import io.helidon.common.processor.classmodel.Field;
import io.helidon.common.processor.classmodel.InnerClass;
import io.helidon.common.processor.classmodel.Javadoc;
import io.helidon.common.processor.classmodel.Method;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.builder.processor.Types.CONFIG_TYPE;
import static io.helidon.common.processor.classmodel.ClassModel.TYPE_TOKEN;

abstract class TypeHandlerCollection extends TypeHandler.OneTypeHandler {
    private static final Set<TypeName> BUILT_IN_MAPPERS = Set.of(
            TypeNames.STRING,
            TypeNames.BOXED_BOOLEAN,
            TypeNames.BOXED_BYTE,
            TypeNames.BOXED_SHORT,
            TypeNames.BOXED_INT,
            TypeNames.BOXED_LONG,
            TypeNames.BOXED_CHAR,
            TypeNames.BOXED_FLOAT,
            TypeNames.BOXED_DOUBLE,
            TypeNames.BOXED_VOID,
            TypeName.create(BigDecimal.class),
            TypeName.create(BigInteger.class),
            TypeName.create(Pattern.class),
            TypeName.create(Class.class),
            TypeName.create(Duration.class),
            TypeName.create(Period.class),
            TypeName.create(LocalDate.class),
            TypeName.create(LocalDateTime.class),
            TypeName.create(LocalTime.class),
            TypeName.create(ZonedDateTime.class),
            TypeName.create(ZoneId.class),
            TypeName.create(ZoneOffset.class),
            TypeName.create(Instant.class),
            TypeName.create(OffsetTime.class),
            TypeName.create(OffsetDateTime.class),
            TypeName.create(YearMonth.class),
            TypeName.create(File.class),
            TypeName.create(Path.class),
            TypeName.create(Charset.class),
            TypeName.create(URI.class),
            TypeName.create(URL.class),
            TypeName.create(UUID.class)
    );
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
    Field.Builder fieldDeclaration(AnnotationDataOption configured, boolean isBuilder, boolean alwaysFinal) {
        Field.Builder builder = super.fieldDeclaration(configured, isBuilder, true);
        if (isBuilder && !configured.hasDefault()) {
            builder.defaultValue(newCollectionInstanceWithouParams() + "()");
        }
        return builder;
    }

    private String newCollectionInstanceWithouParams() {
        return "new " + TYPE_TOKEN + collectionImplType.fqName() + TYPE_TOKEN + "<>";
    }

    @Override
    String toDefaultValue(List<String> defaultValues,
                          List<Integer> defaultInts,
                          List<Long> defaultLongs,
                          List<Double> defaultDoubles,
                          List<Boolean> defaultBooleans) {

        if (defaultValues != null) {
            String defaults = defaultValues.stream()
                    .map(super::toDefaultValue)
                    .collect(Collectors.joining(", "));
            return newCollectionInstanceWithouParams() + "(" + collectionType.fqName() + ".of(" + defaults + "))";
        }

        if (defaultInts != null) {
            return defaultCollection(defaultInts);
        }
        if (defaultLongs != null) {
            String defaults = defaultLongs.stream()
                    .map(String::valueOf)
                    .map(it -> it + "L")
                    .collect(Collectors.joining(", "));
            return newCollectionInstanceWithouParams() + "(" + collectionType.fqName() + ".of(" + defaults + "))";
        }
        if (defaultDoubles != null) {
            return defaultCollection(defaultDoubles);
        }
        if (defaultBooleans != null) {
            return defaultCollection(defaultBooleans);
        }

        return null;
    }

    @Override
    void generateFromConfig(Method.Builder method,
                            AnnotationDataOption configured,
                            FactoryMethods factoryMethods) {
        if (configured.provider()) {
            return;
        }
        TypeName actualType = actualType().genericTypeName();

        if (factoryMethods.createFromConfig().isPresent()) {
            FactoryMethods.FactoryMethod factoryMethod = factoryMethods.createFromConfig().get();
            TypeName returnType = factoryMethod.factoryMethodReturnType();
            boolean mapList = true;
            if (returnType.isList() || returnType.isSet()) {
                mapList = false;
            } else {
                // return type is some other type, we must check it is the same as this one,
                // or we expect another method to be used
                mapList = returnType.equals(actualType);
            }
            if (mapList) {
                method.addLine(configGet(configured)
                                       + ".mapList("
                                       + generateMapListFromConfig(factoryMethods)
                                       + ").ifPresent(this::" + setterName() + ");");
            } else {
                method.addLine(configGet(configured)
                                       + generateFromConfig(factoryMethods)
                                       + ".ifPresent(this::" + setterName() + ");");
            }
        } else if (BUILT_IN_MAPPERS.contains(actualType)) {
            // types we support in config can be simplified,
            // this also supports comma separated lists for string based types
            method.addLine(configGet(configured)
                                   + ".asList(" + TYPE_TOKEN + actualType.fqName() + TYPE_TOKEN + ".class)"
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

    String generateMapListFromConfig(FactoryMethods factoryMethods) {
        return factoryMethods.createFromConfig()
                .map(it -> it.typeWithFactoryMethod().genericTypeName().fqName() + "::" + it.createMethodName())
                .orElseThrow(() -> new IllegalStateException("This should have been called only if factory method is present for "
                                                                     + declaredType()  + " " + name()));

    }

    @Override
    TypeName argumentTypeName() {
        return TypeName.builder(collectionType)
                .addTypeArgument(toWildcard(actualType()))
                .build();
    }

    @Override
    void setters(InnerClass.Builder classBuilder,
                 AnnotationDataOption configured,
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

        if (configured.singular()) {
            singularSetter(classBuilder, configured, returnType, blueprintJavadoc, configured.singularName());
        }

        if (factoryMethods.builder().isPresent()) {
            factorySetterConsumer(classBuilder,
                                  configured,
                                  returnType,
                                  blueprintJavadoc,
                                  factoryMethods,
                                  factoryMethods.builder().get());
        }
    }

    private String defaultCollection(List<?> list) {
        String defaults = list.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        return newCollectionInstanceWithouParams() + "(" + collectionType.fqName() + ".of(" + defaults + "))";
    }

    private void discoverServicesSetter(InnerClass.Builder classBuilder,
                                        AnnotationDataOption configured,
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
                                       AnnotationDataOption configured,
                                       TypeName returnType,
                                       Javadoc blueprintJavadoc,
                                       FactoryMethods factoryMethods,
                                       FactoryMethods.FactoryMethod factoryMethod) {
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

        Javadoc javadoc = setterJavadoc(blueprintJavadoc)
                .addParameter(argumentName, blueprintJavadoc.returnDescription())
                .build();

        Method.Builder builder = Method.builder()
                .name(setterName())
                .returnType(returnType)
                .addParameter(param -> param.name(argumentName)
                        .type(argumentType))
                .accessModifier(setterAccessModifier(configured))
                .typeName(Objects.class)
                .javadoc(javadoc)
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
        } else if (configured.singular()) {
            String singularName = configured.singularName();
            String methodName = "add" + GeneratorTools.capitalize(singularName);
            builder.name(methodName)
                    .addLine("this." + name() + ".add(builder.build());")
                    .addLine("return self();");
            classBuilder.addMethod(builder);
        }
    }

    private void singularSetter(InnerClass.Builder classBuilder, AnnotationDataOption configured,
                                TypeName returnType,
                                Javadoc blueprintJavadoc,
                                String singularName) {
        String methodName = "add" + GeneratorTools.capitalize(singularName);

        Method.Builder builder = Method.builder()
                .name(methodName)
                .javadoc(setterJavadoc(blueprintJavadoc)
                                 .addParameter(singularName, blueprintJavadoc.returnDescription())
                                 .build())
                .returnType(returnType)
                .update(it -> configured.annotations().forEach(it::addAnnotation))
                .addParameter(param -> param.name(singularName)
                        .type(actualType()))
                .accessModifier(setterAccessModifier(configured))
                .typeName(Objects.class)
                .addLine(".requireNonNull(" + singularName + ");")
                .addLine("this." + name() + ".add(" + singularName + ");")
                .addLine("return self();");
        classBuilder.addMethod(builder);
    }

    private void factorySetter(InnerClass.Builder classBuilder,
                               AnnotationDataOption configured,
                               TypeName returnType,
                               Javadoc blueprintJavadoc,
                               FactoryMethods.FactoryMethod factoryMethod) {
        if (factoryMethod.argumentType().equals(CONFIG_TYPE)) {
            // if the factory method uses config as a parameter, then it is not desired on the builder
            return;
        }
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
                                 AnnotationDataOption configured,
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
