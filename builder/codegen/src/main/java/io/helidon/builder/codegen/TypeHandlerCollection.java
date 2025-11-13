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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.codegen.CodegenUtil.capitalize;

abstract class TypeHandlerCollection extends TypeHandlerBase {
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

    TypeHandlerCollection(PrototypeInfo prototypeInfo,
                          OptionInfo option,
                          TypeName collectionType,
                          TypeName collectionImplType,
                          Consumer<ContentBuilder<?>> collector,
                          Optional<Consumer<ContentBuilder<?>>> configMapper) {
        super(prototypeInfo, option, firstTypeArgument(option));

        this.collectionType = collectionType;
        this.collectionImplType = collectionImplType;
        this.collector = collector;
        this.configMapper = configMapper;
    }

    static String isMutatedField(String propertyName) {
        return "is" + CodegenUtil.capitalize(propertyName) + "Mutated";
    }

    @Override
    public Field.Builder field(boolean isBuilder) {
        Field.Builder builder = super.field(isBuilder);
        // collections are always final, we clear them if needed
        builder.isFinal(true);

        if (isBuilder && option().defaultValue().isEmpty()) {
            newCollectionInstanceWithoutParams(builder);
            builder.addContent("()");
        }
        return builder;
    }

    @Override
    public void generateFromConfig(Method.Builder method, OptionConfigured optionConfigured) {
        if (option().provider().isPresent()) {
            return;
        }
        TypeName actualType = type().genericTypeName();
        var setter = option().setter();

        Optional<FactoryMethod> factoryMethod = findFactory(prototype(), actualType);
        if (factoryMethod.isPresent()) {
            var fm = factoryMethod.get();
            TypeName returnType = fm.returnType();

            boolean mapList;
            if (returnType.isList() || returnType.isSet()) {
                mapList = false;
            } else {
                // return type is some other type, we must check it is the same as this one,
                // or we expect another method to be used
                mapList = returnType.equals(actualType);
            }
            if (mapList) {
                method.addContent(configGet(optionConfigured))
                        .addContent(".asList(")
                        .update(it -> generateMapListFromConfig(it, fm))
                        .addContentLine(").ifPresent(this::" + setter.elementName() + ");");
            } else {
                method.addContent(configGet(optionConfigured));
                generateFromConfig(method, fm);
                method.addContentLine(".ifPresent(this::" + setter.elementName() + ");");
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
                        .addContent(setter.elementName())
                        .addContentLine(");");
            } else {
                method.addContent(".ifPresent(it -> this.")
                        .addContent(setter.elementName())
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
            method.addContentLine(".get())"
                                          + "." + collector + ")"
                                          + ".ifPresent(this::" + setter.elementName() + ");");
        }
    }

    @Override
    public void setters(InnerClass.Builder classBuilder,
                        TypeName returnType) {

        if (option().provider().isPresent() || option().registryService()) {
            discoverServicesSetter(classBuilder, returnType);
        }

        // we cannot call super. as collections are always final
        // there is always a setter with the declared type, replacing values
        declaredSetters(classBuilder, returnType);

        if (option().singular().isPresent()) {
            singularSetter(classBuilder, returnType, option().singular().get());

            if (option().builderInfo().isPresent()) {
                builderConsumerSetter(classBuilder,
                                      returnType,
                                      option().builderInfo().get(),
                                      option().singular().get());
            }
        }
    }

    void generateMapListFromConfig(ContentBuilder<?> content, FactoryMethod factoryMethod) {
        var declaringType = factoryMethod.declaringType();
        var methodName = factoryMethod.methodName();

        content.addContent(declaringType.genericTypeName())
                .addContent("::")
                .addContent(methodName);
    }

    @Override
    TypeName setterArgumentTypeName() {
        TypeName type = type();
        if (TypeNames.STRING.equals(type) || type.unboxed().primitive() || type.array()) {
            return TypeName.builder(collectionType)
                    .addTypeArgument(type.boxed())
                    .build();
        }

        return TypeName.builder(collectionType)
                .addTypeArgument(toWildcard(type))
                .build();
    }

    Method.Builder extraSetterContent(Method.Builder builder) {
        return builder.addContentLine("this." + isMutatedField() + " = true;");
    }

    Method.Builder extraAdderContent(Method.Builder builder) {
        return builder.addContentLine("this." + isMutatedField() + " = true;");
    }

    protected abstract String decoratorSetMethodName();

    protected abstract String decoratorAddMethodName();

    void builderConsumerSetter(InnerClass.Builder classBuilder,
                               TypeName returnType,
                               OptionBuilder optionBuilder,
                               OptionSingular singular) {

        var setter = option().setter();

        // if there is a factory method for the return type, we also have setters for the type (probably config object)
        TypeName builderType = optionBuilder.builderType();

        TypeName argumentType = TypeName.builder()
                .type(Consumer.class)
                .addTypeArgument(builderType)
                .build();
        String argumentName = "consumer";

        Javadoc origJavadoc = Javadoc.parse(setter.description().orElse(""));
        Javadoc javadoc = Javadoc.builder(origJavadoc)
                .parameters(Map.of())
                .addParameter(argumentName, "consumer of builder for "
                        + String.join("\n", origJavadoc.parameters().get(option().name())))
                .build();

        Method.Builder builder = Method.builder()
                .name(singular.setter())
                .returnType(returnType)
                .addParameter(param -> param.name(argumentName)
                        .type(argumentType))
                .accessModifier(setter.accessModifier())
                .addContent(Objects.class)
                .javadoc(javadoc)
                .addContentLine(".requireNonNull(" + argumentName + ");")
                .addContent("var builder = ");

        if (optionBuilder.builderMethodName().equals("<init>")) {
            builder.addContent("new ")
                    .addContent(optionBuilder.builderType())
                    .addContentLine("();");
        } else {
            builder.addContent(type())
                    .addContentLine("." + optionBuilder.builderMethodName() + "();");
        }

        builder.addContentLine("consumer.accept(builder);")
                .addContentLine("this." + option().name() + ".add(builder.build());")
                .addContentLine("return self();");

        classBuilder.addMethod(builder);
    }

    private void newCollectionInstanceWithoutParams(ContentBuilder<?> content) {
        content.addContent("new ")
                .addContent(collectionImplType.genericTypeName())
                .addContent("<>");
    }

    private String isMutatedField() {
        return isMutatedField(option().name());
    }

    private void discoverServicesSetter(InnerClass.Builder classBuilder,
                                        TypeName returnType) {
        var setter = option().setter();

        Javadoc javadoc = Javadoc.builder(Javadoc.parse(setter.description().orElse("")))
                .parameters(Map.of())
                .addParameter("discoverServices", "whether to discover implementations through service loader")
                .build();

        classBuilder.addMethod(builder -> builder.name(setter.elementName() + "DiscoverServices")
                .returnType(returnType)
                .javadoc(javadoc)
                .addParameter(param -> param.name("discoverServices")
                        .type(boolean.class))
                .accessModifier(setter.accessModifier())
                .addContentLine("this." + option().name() + "DiscoverServices = discoverServices;")
                .addContentLine("return self();"));
    }

    private void singularSetter(InnerClass.Builder classBuilder, TypeName returnType, OptionSingular optionSingular) {
        String methodName = optionSingular.setter();
        String singularName = optionSingular.name();
        TypedElementInfo setter = option().setter();
        Javadoc setterJavadoc = Javadoc.parse(setter.description().orElse(""));
        Javadoc javadoc = Javadoc.builder(setterJavadoc)
                .parameters(Map.of())
                .addParameter(singularName, setterJavadoc.parameters().get(option().name()))
                .build();

        Method.Builder builder = Method.builder()
                .name(methodName)
                .javadoc(javadoc)
                .returnType(returnType)
                .addParameter(param -> param.name(singularName)
                        .type(type()))
                .accessModifier(setter.accessModifier())
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + singularName + ");");

        if (option().decorator().isPresent()) {
            var decorator = option().decorator().get();

            builder.addContent("new ")
                    .addContent(decorator)
                    .addContent("().decorate(this, ")
                    .addContent(singularName)
                    .addContentLine(");");
        }

        builder.addContentLine("this." + option().name() + ".add(" + singularName + ");")
                .update(this::extraAdderContent)
                .addContentLine("return self();");

        classBuilder.addMethod(builder);
    }

    private void declaredSetters(InnerClass.Builder classBuilder,
                                 TypeName returnType) {

        var setter = option().setter();
        Javadoc javadoc = Javadoc.parse(setter.description().orElse(""));

        // we cannot call super. as collections are always final
        // there is always a setter with the declared type, replacing values
        Method.Builder setMethod = Method.builder()
                .name(setter.elementName())
                .returnType(returnType)
                .javadoc(javadoc)
                .addParameter(param -> param.name(option().name())
                        .type(setterArgumentTypeName()))
                .accessModifier(setter.accessModifier())
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + option().name() + ");");

        Method.Builder addMethod = Method.builder()
                .name("add" + capitalize(option().name()))
                .returnType(returnType)
                .javadoc(javadoc)
                .addParameter(param -> param.name(option().name())
                        .type(setterArgumentTypeName()))
                .accessModifier(setter.accessModifier())
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + option().name() + ");");

        // in case the method has a decorator, we need to handle it as well
        if (option().decorator().isPresent()) {
            TypeName decorator = option().decorator().get();
            setMethod.addContent("new ")
                    .addContent(decorator)
                    .addContent("().")
                    .addContent(decoratorSetMethodName())
                    .addContent("(this, ")
                    .addContent(option().name())
                    .addContentLine(");");

            addMethod.addContent("new ")
                    .addContent(decorator)
                    .addContent("().")
                    .addContent(decoratorAddMethodName())
                    .addContent("(this, ")
                    .addContent(option().name())
                    .addContentLine(");");
        }

        // first decorate (above), then set the values
        setMethod.update(this::extraSetterContent)
                .addContentLine("this." + option().name() + ".clear();")
                .addContentLine("this." + option().name() + ".addAll(" + option().name() + ");")
                .addContentLine("return self();");

        addMethod.update(this::extraAdderContent)
                .addContentLine("this." + option().name() + ".addAll(" + option().name() + ");")
                .addContentLine("return self();");

        classBuilder.addMethod(setMethod);
        classBuilder.addMethod(addMethod);
    }
}
