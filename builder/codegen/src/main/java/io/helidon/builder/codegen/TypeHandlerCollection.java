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

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.COMMON_CONFIG;
import static io.helidon.codegen.CodegenUtil.capitalize;

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

    TypeHandlerCollection(TypeName blueprintType,
                          TypedElementInfo annotatedMethod,
                          String name,
                          String getterName,
                          String setterName,
                          TypeName declaredType,
                          TypeName collectionType,
                          String collector,
                          Optional<String> configMapper) {
        super(blueprintType, annotatedMethod, name, getterName, setterName, declaredType);
        this.collectionType = collectionType;
        this.collectionImplType = collectionImplType(collectionType);
        this.collector = collector;
        this.configMapper = configMapper;
    }

    @Override
    Field.Builder fieldDeclaration(AnnotationDataOption configured, boolean isBuilder, boolean alwaysFinal) {
        Field.Builder builder = super.fieldDeclaration(configured, isBuilder, true);
        if (isBuilder && !configured.hasDefault()) {
            newCollectionInstanceWithoutParams(builder);
            builder.addContent("()");
        }
        return builder;
    }

    @Override
    Consumer<ContentBuilder<?>> toDefaultValue(List<String> defaultValues,
                                               List<Integer> defaultInts,
                                               List<Long> defaultLongs,
                                               List<Double> defaultDoubles,
                                               List<Boolean> defaultBooleans) {

        if (defaultValues != null) {
            return content -> {
                newCollectionInstanceWithoutParams(content);
                content.addContent("(")
                        .addContent(collectionType.genericTypeName())
                        .addContent(".of(");

                for (int i = 0; i < defaultValues.size(); i++) {
                    toDefaultValue(defaultValues.get(i)).accept(content);
                    if (i != defaultValues.size() - 1) {
                        content.addContent(", ");
                    }
                }
                content.addContent("))");
            };
        }

        if (defaultInts != null) {
            return defaultCollection(defaultInts);
        }
        if (defaultLongs != null) {
            return content -> {
                newCollectionInstanceWithoutParams(content);
                content.addContent("(")
                        .addContent(collectionType.genericTypeName())
                        .addContent(".of(");

                for (int i = 0; i < defaultLongs.size(); i++) {
                    content.addContent(String.valueOf(defaultLongs.get(i)))
                            .addContent("L");
                    if (i != defaultLongs.size() - 1) {
                        content.addContent(", ");
                    }
                }
                content.addContent("))");
            };
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
                method.addContentLine(configGet(configured)
                                              + ".mapList("
                                              + generateMapListFromConfig(factoryMethods)
                                              + ").ifPresent(this::" + setterName() + ");");
            } else {
                method.addContent(configGet(configured));
                generateFromConfig(method, factoryMethods);
                method.addContentLine(".ifPresent(this::" + setterName() + ");");
            }
        } else if (BUILT_IN_MAPPERS.contains(actualType)) {
            // types we support in config can be simplified,
            // this also supports comma separated lists for string based types
            method.addContent(configGet(configured))
                    .addContent(".asList(")
                    .addContent(actualType.genericTypeName())
                    .addContent(".class")
                    .addContent(")");
            configMapper.ifPresent(method::addContent);
            method.addContent(".ifPresent(this::")
                    .addContent(setterName())
                    .addContentLine(");");
        } else {
            method.addContent(configGet(configured)
                                      + ".asNodeList()"
                                      + ".map(nodeList -> nodeList.stream()"
                                      + ".map(cfg -> cfg");
            generateFromConfig(method, factoryMethods);
            method.addContentLine(".get())"
                                          + "." + collector + ")"
                                          + ".ifPresent(this::" + setterName() + ");");
        }
    }

    String generateMapListFromConfig(FactoryMethods factoryMethods) {
        return factoryMethods.createFromConfig()
                .map(it -> it.typeWithFactoryMethod().genericTypeName().fqName() + "::" + it.createMethodName())
                .orElseThrow(() -> new IllegalStateException("This should have been called only if factory method is present for "
                                                                     + declaredType() + " " + name()));

    }

    @Override
    TypeName argumentTypeName() {
        TypeName type = actualType();
        if (TypeNames.STRING.equals(type) || toPrimitive(type).primitive() || type.array()) {
            return declaredType();
        }

        return TypeName.builder(collectionType)
                .addTypeArgument(toWildcard(type))
                .build();
    }

    @Override
    void setters(InnerClass.Builder classBuilder,
                 AnnotationDataOption configured,
                 FactoryMethods factoryMethods,
                 TypeName returnType,
                 Javadoc blueprintJavadoc) {

        if (configured.provider() || configured.registryService()) {
            discoverServicesSetter(classBuilder, configured, returnType, blueprintJavadoc);
        }

        // we cannot call super. as collections are always final
        // there is always a setter with the declared type, replacing values
        declaredSetters(classBuilder, configured, returnType, blueprintJavadoc);

        if (factoryMethods.createTargetType().isPresent()) {
            FactoryMethods.FactoryMethod factoryMethod = factoryMethods.createTargetType().get();
            if (factoryMethod.factoryMethodReturnType().isList() || factoryMethod.factoryMethodReturnType().isSet()) {
                // if there is a factory method for the return type, we also have setters for the type (probably config object)
                factorySetter(classBuilder, configured, returnType, blueprintJavadoc, factoryMethod);
            }
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

    private void newCollectionInstanceWithoutParams(ContentBuilder<?> content) {
        content.addContent("new ")
                .addContent(collectionImplType.genericTypeName())
                .addContent("<>");
    }

    private Consumer<ContentBuilder<?>> defaultCollection(List<?> list) {
        return content -> {
            newCollectionInstanceWithoutParams(content);
            content.addContent("(")
                    .addContent(collectionType.genericTypeName())
                    .addContent(".of(");

            for (int i = 0; i < list.size(); i++) {
                content.addContent(String.valueOf(list.get(i)));
                if (i != list.size() - 1) {
                    content.addContent(", ");
                }
            }
            content.addContent("))");
        };
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
                .addContentLine("this." + name() + "DiscoverServices = discoverServices;")
                .addContentLine("return self();"));
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
                .addContent(Objects.class)
                .javadoc(javadoc)
                .addContentLine(".requireNonNull(" + argumentName + ");")
                .addContent("var builder = ")
                .addContent(factoryMethod.typeWithFactoryMethod().genericTypeName())
                .addContentLine("." + factoryMethod.createMethodName() + "();")
                .addContentLine("consumer.accept(builder);");

        if (factoryMethods.createTargetType()
                .map(FactoryMethods.FactoryMethod::factoryMethodReturnType)
                .map(m -> m.genericTypeName().equals(collectionType))
                .orElse(false)) {
            builder.addContentLine("this." + name() + "(builder.build());")
                    .addContentLine("return self();");
            classBuilder.addMethod(builder);
        } else if (configured.singular()) {
            String singularName = configured.singularName();
            String methodName = "add" + capitalize(singularName);
            builder.name(methodName)
                    .addContentLine("this." + name() + ".add(builder.build());")
                    .addContentLine("return self();");
            classBuilder.addMethod(builder);
        }
    }

    private void singularSetter(InnerClass.Builder classBuilder,
                                AnnotationDataOption configured,
                                TypeName returnType,
                                Javadoc blueprintJavadoc,
                                String singularName) {
        String methodName = "add" + capitalize(singularName);

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
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + singularName + ");");

        if (configured.decorator() != null) {
            builder.addContent("new ")
                    .addContent(configured.decorator())
                    .addContent("().decorate(this, ")
                    .addContent(singularName)
                    .addContentLine(");");
        }

        builder.addContentLine("this." + name() + ".add(" + singularName + ");")
                .update(this::extraAdderContent)
                .addContentLine("return self();");

        classBuilder.addMethod(builder);
    }

    private void factorySetter(InnerClass.Builder classBuilder,
                               AnnotationDataOption configured,
                               TypeName returnType,
                               Javadoc blueprintJavadoc,
                               FactoryMethods.FactoryMethod factoryMethod) {
        if (factoryMethod.argumentType().equals(COMMON_CONFIG)) {
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
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + argumentName + ");")
                .addContentLine("this." + name() + ".clear();")
                .addContent("this." + name() + ".addAll(")
                .addContent(factoryMethod.typeWithFactoryMethod().genericTypeName())
                .addContentLine("." + factoryMethod.createMethodName() + "(" + argumentName + "));")
                .addContentLine("return self();");
        classBuilder.addMethod(builder);
    }

    private void declaredSetters(InnerClass.Builder classBuilder,
                                 AnnotationDataOption configured,
                                 TypeName returnType,
                                 Javadoc blueprintJavadoc) {
        // we cannot call super. as collections are always final
        // there is always a setter with the declared type, replacing values
        Method.Builder setMethod = Method.builder()
                .name(setterName())
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(name())
                        .type(argumentTypeName())
                        .description(blueprintJavadoc.returnDescription()))
                .accessModifier(setterAccessModifier(configured))
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + name() + ");");

        Method.Builder addMethod = Method.builder()
                .name("add" + capitalize(name()))
                .returnType(returnType, "updated builder instance")
                .description(blueprintJavadoc.content())
                .addJavadocTag("see", "#" + getterName() + "()")
                .addParameter(param -> param.name(name())
                        .type(argumentTypeName())
                        .description(blueprintJavadoc.returnDescription()))
                .accessModifier(setterAccessModifier(configured))
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + name() + ");");

        // in case the method has a decorator, we need to handle it as well
        if (configured.decorator() != null) {
            setMethod.addContent("new ")
                    .addContent(configured.decorator())
                    .addContent("().")
                    .addContent(decoratorSetMethodName())
                    .addContent("(this, ")
                    .addContent(name())
                    .addContentLine(");");

            addMethod.addContent("new ")
                    .addContent(configured.decorator())
                    .addContent("().")
                    .addContent(decoratorAddMethodName())
                    .addContent("(this, ")
                    .addContent(name())
                    .addContentLine(");");
        }

        // first decorate (above), then set the values
        setMethod.update(this::extraSetterContent)
                .addContentLine("this." + name() + ".clear();")
                .addContentLine("this." + name() + ".addAll(" + name() + ");")
                .addContentLine("return self();");

        addMethod.update(this::extraAdderContent)
                .addContentLine("this." + name() + ".addAll(" + name() + ");")
                .addContentLine("return self();");

        classBuilder.addMethod(setMethod);
        classBuilder.addMethod(addMethod);
    }

    Method.Builder extraSetterContent(Method.Builder builder) {
        return builder;
    }

    Method.Builder extraAdderContent(Method.Builder builder) {
        return builder;
    }

    protected abstract String decoratorSetMethodName();
    protected abstract String decoratorAddMethodName();
}
