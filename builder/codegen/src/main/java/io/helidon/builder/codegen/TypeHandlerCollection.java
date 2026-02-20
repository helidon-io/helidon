/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import io.helidon.builder.codegen.spi.BuilderCodegenExtension;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.codegen.CodegenUtil.capitalize;

abstract class TypeHandlerCollection extends TypeHandlerContainer {
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
    private final Consumer<ContentBuilder<?>> collector;
    private final Optional<Consumer<ContentBuilder<?>>> configMapper;

    TypeHandlerCollection(List<BuilderCodegenExtension> extensions,
                          PrototypeInfo prototypeInfo,
                          OptionInfo option,
                          TypeName collectionType,
                          TypeName collectionImplType,
                          Consumer<ContentBuilder<?>> collector,
                          Optional<Consumer<ContentBuilder<?>>> configMapper) {
        super(extensions, prototypeInfo, option, firstTypeArgument(option));

        this.collectionType = collectionType;
        this.collectionImplType = collectionImplType;
        this.collector = collector;
        this.configMapper = configMapper;
    }

    @Override
    public void addFields(ClassBase.Builder<?, ?> classBuilder, boolean isBuilder) {
        Field.Builder builder = super.field(isBuilder);
        // collections are always final, we clear them if needed
        builder.isFinal(true);

        if (isBuilder && option().defaultValue().isEmpty()) {
            newCollectionInstanceWithoutParams(builder);
            builder.addContent("()");
        }
        classBuilder.addField(builder.build());
    }

    @Override
    public void generateFromConfig(Method.Builder method, OptionConfigured optionConfigured) {
        if (option().provider().isPresent()) {
            return;
        }
        TypeName actualType = type().genericTypeName();
        String setterName = option().setterName();

        Optional<FactoryMethod> factoryMethod = optionConfigured.factoryMethod();
        if (factoryMethod.isPresent()) {
            var fm = factoryMethod.get();
            TypeName returnType = fm.returnType();

            boolean mapList;
            if (returnType.isList() || returnType.isSet()) {
                mapList = false;
            } else {
                // return type is some other type, we must check it is the same as this one,
                // or we expect another method to be used
                mapList = Utils.typesEqual(actualType, returnType);
            }
            if (mapList) {
                method.addContent(configGet(optionConfigured))
                        .addContent(".asList(")
                        .update(it -> generateMapListFromConfig(it, fm))
                        .addContent(")");
                configMapper.ifPresent(it -> it.accept(method));
                method.addContentLine(".ifPresent(this::" + setterName + ");");
            } else {
                method.addContent(configGet(optionConfigured));
                generateFromConfig(method, fm);
                configMapper.ifPresent(it -> it.accept(method));
                method.addContentLine(".ifPresent(this::" + setterName + ");");
            }
        } else if (BUILT_IN_MAPPERS.contains(actualType)) {
            // types we support in config can be simplified,
            // this also supports comma separated lists for string based types

            method.addContent(configGet(optionConfigured))
                    .addContent(".asList(")
                    .addContent(actualType.genericTypeName())
                    .addContent(".class")
                    .addContent(")");
            configMapper.ifPresent(it -> it.accept(method));

            if (type().typeArguments().isEmpty()) {
                method.addContent(".ifPresent(this::")
                        .addContent(setterName)
                        .addContentLine(");");
            } else {
                method.addContent(".ifPresent(it -> this.")
                        .addContent(setterName)
                        .addContent("((")
                        .addContent(collectionType)
                        .addContentLine(")it));");
                // maybe we should add @SuppressWarnings("unchecked")
            }

        } else {
            method.addContent(configGet(optionConfigured)
                                      + ".asNodeList()"
                                      + ".map(nodeList -> nodeList.stream()"
                                      + ".map(cfg -> cfg");
            generateFromConfig(method);
            method.addContent(".get()).");
            collector.accept(method);
            method.addContentLine(").ifPresent(this::" + setterName + ");");
        }
    }

    void generateMapListFromConfig(ContentBuilder<?> content, FactoryMethod factoryMethod) {
        var declaringType = factoryMethod.declaringType();
        var methodName = factoryMethod.methodName();

        content.addContent(declaringType.genericTypeName())
                .addContent("::")
                .addContent(methodName);
    }

    protected abstract String decoratorSetMethodName();

    protected abstract String decoratorAddMethodName();

    @Override
    GeneratedMethod prepareBuilderSetter(Javadoc getterJavadoc) {
        TypeName returnType = Utils.builderReturnType();

        String name = option().name();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName(option().setterName())
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(asTypeArgument(collectionType))
                .elementName(name)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + name + ");");
            option().decorator()
                    .ifPresent(decorator -> it.addContent("new ")
                            .addContent(decorator)
                            .addContent("().")
                            .addContent(decoratorSetMethodName())
                            .addContent("(this, ")
                            .addContent(name)
                            .addContentLine(");"));

            extraSetterContent(it);
            it.addContentLine("this." + name + ".clear();")
                    .addContentLine("this." + name + ".addAll(" + name + ");")
                    .addContentLine("return self();");
        };

        return GeneratedMethod.builder()
                .method(method.build())
                .javadoc(setterJavadoc(getterJavadoc, name, ""))
                .contentBuilder(contentConsumer)
                .build();
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderAddCollection(Javadoc getterJavadoc) {
        TypeName returnType = Utils.builderReturnType();

        String name = option().name();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName("add" + capitalize(option().name()))
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(asTypeArgument(collectionType))
                .elementName(name)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + name + ");");
            extraSetterContent(it);
            option().decorator()
                    .ifPresent(decorator -> it.addContent("new ")
                            .addContent(decorator)
                            .addContent("().")
                            .addContent(decoratorAddMethodName())
                            .addContent("(this, ")
                            .addContent(name)
                            .addContentLine(");"));

            it.addContentLine("this." + name + ".addAll(" + name + ");")
                    .addContentLine("return self();");
        };

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(setterJavadoc(getterJavadoc, name, ""))
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderSingularAdd(Javadoc getterJavadoc) {
        if (option().singular().isEmpty()) {
            return Optional.empty();
        }

        TypeName returnType = Utils.builderReturnType();
        String name = option().name();

        OptionSingular optionSingular = option().singular().get();
        String methodName = optionSingular.methodName();
        String singularName = optionSingular.name();

        Javadoc setterJavadoc = setterJavadoc(getterJavadoc, singularName, "add single ");

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName(methodName)
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(type())
                .elementName(singularName)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + singularName + ");");

            option().decorator()
                    .ifPresent(decorator -> it.addContent("new ")
                            .addContent(decorator)
                            .addContent("().decorate(this, ")
                            .addContent(singularName)
                            .addContentLine(");"));

            it.addContentLine("this." + name + ".add(" + singularName + ");");
            extraAdderContent(it);
            it.addContentLine("return self();");
        };

        return Optional.ofNullable(GeneratedMethod.builder()
                                           .method(method.build())
                                           .javadoc(setterJavadoc)
                                           .contentBuilder(contentConsumer)
                                           .build());
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderSingularAddConsumer(Javadoc getterJavadoc) {
        if (option().singular().isEmpty() || option().builderInfo().isEmpty()) {
            return Optional.empty();
        }
        TypeName returnType = Utils.builderReturnType();

        OptionSingular optionSingular = option().singular().get();
        String methodName = optionSingular.methodName();

        // if there is a factory method for the return type, we also have setters for the type (probably config object)
        OptionBuilder optionBuilder = option().builderInfo().get();
        TypeName builderType = optionBuilder.builderType();
        String builderMethod = optionBuilder.builderMethodName();
        String buildMethod = optionBuilder.buildMethodName();

        TypeName paramType = TypeName.builder()
                .type(Consumer.class)
                .addTypeArgument(builderType)
                .build();

        String paramName = "consumer";

        Javadoc setterJavadoc = setterJavadoc(getterJavadoc, paramName, "consumer of builder for ");

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName(methodName)
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(paramType)
                .elementName(paramName)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + paramName + ");");

            it.addContent("var builder = ");

            if (optionBuilder.builderMethodName().equals("<init>")) {
                it.addContent("new ")
                        .addContent(builderType)
                        .addContentLine("();");
            } else {
                it.addContent(type())
                        .addContentLine("." + builderMethod + "();");
            }

            // decorator and add will be called as part of singular setter
            it.addContentLine("consumer.accept(builder);")
                    .addContentLine("this." + methodName + "(builder." + buildMethod + "());")
                    .addContentLine("return self();");
        };

        return Optional.ofNullable(GeneratedMethod.builder()
                                           .method(method.build())
                                           .javadoc(setterJavadoc)
                                           .contentBuilder(contentConsumer)
                                           .build());
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderClear(Javadoc getterJavadoc) {
        TypeName returnType = Utils.builderReturnType();

        String name = option().name();

        Javadoc javadoc = Javadoc.builder(setterJavadoc(getterJavadoc, "ignore", ""))
                .content(List.of("Clear all " + name + "."))
                .parameters(Map.of())
                .build();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName("clear" + capitalize(option().name()))
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        Consumer<ContentBuilder<?>> contentConsumer = it -> {

            option().decorator()
                    .ifPresent(decorator -> it.addContent("new ")
                            .addContent(decorator)
                            .addContent("().")
                            .addContent(decoratorSetMethodName())
                            .addContent("(this, ")
                            .addContent(collectionType)
                            .addContentLine(".of());"));

            extraSetterContent(it);
            it.addContentLine("this." + name + ".clear();")
                    .addContentLine("return self();");
        };

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(javadoc)
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    @Override
    Optional<GeneratedMethod> prepareSetterPrototypeOfRuntimeType(Javadoc getterJavadoc) {
        if (option().runtimeType().isEmpty()) {
            return Optional.empty();
        }

        RuntimeTypeInfo rti = option().runtimeType().get();
        var factoryMethod = rti.factoryMethod();
        if (factoryMethod.isEmpty()) {
            return Optional.empty();
        }
        var fm = factoryMethod.get();
        if (!Utils.resolvedTypesEqual(fm.returnType(), option().declaredType())) {
            return Optional.empty();
        }

        var optionBuilder = rti.optionBuilder();
        String optionName = option().name();
        Javadoc javadoc = setterJavadoc(getterJavadoc, optionName, "prototype of ");

        TypeName paramType = optionBuilder.builderMethodType();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(Utils.builderReturnType())
                .elementName(option().setterName())
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(paramType)
                .elementName(optionName)
        );

        /*
        public BUILDER option(Prototype prototype) {
            Objects.requireNonNull(prototype);
            option(FactoryType.factoryMethod(prototype));
            return self();
        }
         */

        /*
        public BUILDER option(Prototype prototype) {
            Objects.requireNonNull(prototype);
            option(prototype.build());
            return self();
        }
         */

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + optionName + ");")
                    .addContent(option().setterName())
                    .addContent("(");

            it.addContent(fm.declaringType().genericTypeName())
                    .addContent(".")
                    .addContent(fm.methodName())
                    .addContent("(")
                    .addContent(optionName)
                    .addContent(")");

            it.addContentLine(");");
            it.addContentLine("return self();");
        };

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(javadoc)
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    private void newCollectionInstanceWithoutParams(ContentBuilder<?> content) {
        content.addContent("new ")
                .addContent(collectionImplType.genericTypeName())
                .addContent("<>");
    }

}
