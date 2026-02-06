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

import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.builder.codegen.spi.BuilderCodegenExtension;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.CHAR_ARRAY;
import static io.helidon.builder.codegen.Types.COMMON_CONFIG;
import static io.helidon.builder.codegen.Types.CONFIG;
import static io.helidon.builder.codegen.Types.PROTOTYPE_BLUEPRINT;
import static io.helidon.codegen.CodegenUtil.capitalize;

class TypeHandlerBasic implements TypeHandler {
    private final Map<OptionMethodType, GeneratedMethod> generatedMethods = new EnumMap<>(OptionMethodType.class);
    private final OptionInfo option;
    private final TypeName type;
    private final PrototypeInfo prototypeInfo;
    private final List<BuilderCodegenExtension> extensions;

    TypeHandlerBasic(List<BuilderCodegenExtension> extensions, PrototypeInfo prototypeInfo, OptionInfo option, TypeName type) {
        this.extensions = extensions;
        this.prototypeInfo = prototypeInfo;
        this.option = option;
        this.type = type;
    }

    protected static TypeName firstTypeArgument(OptionInfo option) {
        // number of type arguments is validated when creating type handler
        return option.declaredType().typeArguments().getFirst();
    }

    @Override
    public Optional<GeneratedMethod> optionMethod(OptionMethodType type) {
        return Optional.ofNullable(generatedMethods.get(type));
    }

    @Override
    public TypeName type() {
        return type;
    }

    @Override
    public void fields(ClassBase.Builder<?, ?> classBuilder, boolean isBuilder) {
        classBuilder.addField(field(isBuilder));
    }

    @Override
    public void setters(InnerClass.Builder classBuilder) {
        optionMethod(OptionMethodType.BUILDER_CLEAR)
                .ifPresent(it -> Utils.addGeneratedMethod(classBuilder, it));
        optionMethod(OptionMethodType.BUILDER_SETTER)
                .ifPresent(it -> Utils.addGeneratedMethod(classBuilder, it));
        optionMethod(OptionMethodType.BUILDER_SETTER_CHAR_ARRAY)
                .ifPresent(it -> Utils.addGeneratedMethod(classBuilder, it));
        optionMethod(OptionMethodType.BUILDER_SETTER_DECLARED)
                .ifPresent(it -> Utils.addGeneratedMethod(classBuilder, it));
        optionMethod(OptionMethodType.BUILDER_SETTER_RUNTIME_TYPE_PROTOTYPE)
                .ifPresent(it -> Utils.addGeneratedMethod(classBuilder, it));
        optionMethod(OptionMethodType.BUILDER_SETTER_CONSUMER)
                .ifPresent(it -> Utils.addGeneratedMethod(classBuilder, it));
        optionMethod(OptionMethodType.BUILDER_SETTER_SUPPLIER)
                .ifPresent(it -> Utils.addGeneratedMethod(classBuilder, it));
        optionMethod(OptionMethodType.BUILDER_ADD_COLLECTION)
                .ifPresent(it -> Utils.addGeneratedMethod(classBuilder, it));
        optionMethod(OptionMethodType.BUILDER_SINGULAR_ADD_TO_MAP_VALUE)
                .ifPresent(it -> Utils.addGeneratedMethod(classBuilder, it));
        optionMethod(OptionMethodType.BUILDER_SINGULAR_ADD_TO_MAP_VALUES)
                .ifPresent(it -> Utils.addGeneratedMethod(classBuilder, it));
        optionMethod(OptionMethodType.BUILDER_SINGULAR_ADD)
                .ifPresent(it -> Utils.addGeneratedMethod(classBuilder, it));
        optionMethod(OptionMethodType.BUILDER_SINGULAR_ADD_CONSUMER)
                .ifPresent(it -> Utils.addGeneratedMethod(classBuilder, it));
    }

    @Override
    public void generateFromConfig(Method.Builder method, OptionConfigured optionConfigured) {
        method.addContent(configGet(optionConfigured));
        String fqName = type().fqName();

        var setterName = option().setterName();

        if (option().prototypedBy().isPresent()) {
            TypeName prototype = fixPackage(option().prototypedBy().get());

            generateFromConfig(method,
                               FactoryMethod.builder()
                                       .parameterType(CONFIG)
                                       .methodName("create")
                                       .declaringType(prototype)
                                       .returnType(type())
                                       .build());

            method.addContent(".map(")
                    .addContent(prototype)
                    .addContentLine("::build).ifPresent(this::" + setterName + ");");
        } else if (fqName.endsWith(".Builder")) {
            // this is a special case - we have a builder field
            if (option().defaultValue().isPresent()) {
                method.addContent(".as(")
                        .addContent(CONFIG)
                        .addContent(".class).ifPresent(")
                        .addContent(option().name())
                        .addContentLine("::config);");
            } else {
                // a bit dirty hack - we expect builder() method to exist on the class that owns the builder
                int lastDot = fqName.lastIndexOf('.');
                String builderMethod = fqName.substring(0, lastDot) + ".builder()";
                method.addContentLine(".as(" + builderMethod + "::config).ifPresent(this::" + setterName + ");");
            }
        } else {
            Optional<FactoryMethod> factoryMethod = optionConfigured.factoryMethod();
            if (factoryMethod.isPresent()) {
                generateFromConfig(method, factoryMethod.get());
            } else {
                boolean configured = false;
                if (option().runtimeType().isPresent()) {
                    RuntimeTypeInfo runtimeTypeInfo = option().runtimeType().get();
                    var optionBuilder = runtimeTypeInfo.optionBuilder();
                    generateFromConfig(method, FactoryMethod.builder()
                            .parameterType(CONFIG)
                            .methodName("create")
                            .declaringType(optionBuilder.builderMethodType())
                            .returnType(type())
                            .build());
                    configured = true;
                }
                if (!configured) {
                    generateFromConfig(method);
                }
            }

            method.addContentLine(".ifPresent(this::" + setterName + ");");
        }
    }

    @Override
    public String toString() {
        return option().declaredType().fqName() + " " + option().name();
    }

    @Override
    public boolean builderGetterOptional() {
        boolean required = option().required();
        boolean hasDefault = option().defaultValue().isPresent();

        // optional and collections - good return types
        if (option().declaredType().isList()
                || option().declaredType().isMap()
                || option().declaredType().isSet()) {
            return false;
        }
        if (option().declaredType().isOptional()) {
            return true;
        }
        // optional and primitive type - good return type (uses default for primitive if not customized)
        if (!required && option().declaredType().primitive()) {
            return false;
        }
        // has default, and not Optional<X> - return type (never can be null)
        // any other case (required, optional without defaults) - return optional
        return !hasDefault;
    }

    @Override
    public void fromBuilderAssignment(ContentBuilder<?> contentBuilder) {
        if (type().equals(CONFIG) || type().equals(COMMON_CONFIG)) {
            // special handling, must assign to field, to avoid re-configuring
            if (type().equals(COMMON_CONFIG)) {
                if (builderGetterOptional()) {
                    contentBuilder.addContent("this.config = builder.")
                            .addContent(option().getterName())
                            .addContent("().map(")
                            .addContent(CONFIG)
                            .addContentLine("::config).orElse(this.config);");
                } else {
                    contentBuilder.addContentLine("this.config = ")
                            .addContent(CONFIG)
                            .addContentLine(".config(builder.")
                            .addContent(option().getterName())
                            .addContentLine("());");
                }
            } else {
                if (builderGetterOptional()) {
                    contentBuilder.addContent("this.config = builder.")
                            .addContent(option().getterName())
                            .addContentLine("().orElse(this.config);");
                } else {
                    contentBuilder.addContent("this.config = builder.")
                            .addContent(option().getterName())
                            .addContentLine("();");
                }
            }
            return;
        }
        if (builderGetterOptional()) {
            contentBuilder.addContent("builder.")
                    .addContent(option().getterName())
                    .addContentLine("().ifPresent(this::" + option().setterName() + ");");
        } else {
            contentBuilder.addContent(option().setterName())
                    .addContent("(builder.")
                    .addContent(option().getterName())
                    .addContentLine("());");
        }
    }

    @Override
    public void fromPrototypeAssignment(ContentBuilder<?> contentBuilder) {
        if (option().builderOptionOnly()) {
            return;
        }

        if (type().equals(CONFIG) || type().equals(COMMON_CONFIG)) {
            // special handling, must assign to field, to avoid re-configuring
            if (type().equals(COMMON_CONFIG)) {
                if (option().declaredType().isOptional()) {
                    contentBuilder.addContentLine("this.config = prototype.config().map(")
                            .addContent(CONFIG)
                            .addContentLine("::config).orElse(null);");
                } else {
                    contentBuilder.addContentLine("this.config = ")
                            .addContent(CONFIG)
                            .addContentLine(".config(prototype.config());");
                }
            } else {
                if (option().declaredType().isOptional()) {
                    contentBuilder.addContentLine("this.config = prototype.config().orElse(null);");
                } else {
                    contentBuilder.addContentLine("this.config = prototype.config();");
                }
            }
            return;
        }
        contentBuilder.addContent(option().setterName())
                .addContent("(prototype.")
                .addContent(option().getterName())
                .addContentLine("());");

        if (option().provider().isPresent()) {
            // disable service discovery, as we have copied the value from a prototype
            contentBuilder.addContent("this.")
                    .addContent(option().name() + "DiscoverServices")
                    .addContentLine(" = false;");
        }
    }

    Field.Builder field(boolean isBuilder) {
        var field = Field.builder()
                .name(option().name())
                .isFinal(!isBuilder);

        if (isBuilder && option().required()) {
            // required fields must be nullable, so we can check they were explicitly configured
            field.type(option().declaredType().boxed());
        } else {
            field.type(option().declaredType());
        }

        if (isBuilder && option().defaultValue().isPresent()) {
            option().defaultValue().get().accept(field);
        }

        return field;
    }

    void prepareMethods() {
        Javadoc getterJavadoc = deprecation(getterJavadoc());

        generatedMethod(OptionMethodType.PROTOTYPE_GETTER, preparePrototypeGetter(getterJavadoc));
        generatedMethod(OptionMethodType.IMPL_GETTER, prepareImplGetter());
        generatedMethod(OptionMethodType.BUILDER_GETTER, Optional.of(prepareBuilderGetter(getterJavadoc)));
        generatedMethod(OptionMethodType.BUILDER_SETTER, Optional.of(prepareBuilderSetter(getterJavadoc)));
        generatedMethod(OptionMethodType.BUILDER_SETTER_CHAR_ARRAY, prepareBuilderSetterCharArray(getterJavadoc));
        generatedMethod(OptionMethodType.BUILDER_SETTER_DECLARED, prepareBuilderSetterDeclared(getterJavadoc));
        generatedMethod(OptionMethodType.BUILDER_ADD_COLLECTION, prepareBuilderAddCollection(getterJavadoc));
        generatedMethod(OptionMethodType.BUILDER_CLEAR, prepareBuilderClear(getterJavadoc));
        generatedMethod(OptionMethodType.BUILDER_SINGULAR_ADD, prepareBuilderSingularAdd(getterJavadoc));
        generatedMethod(OptionMethodType.BUILDER_SINGULAR_ADD_CONSUMER, prepareBuilderSingularAddConsumer(getterJavadoc));
        generatedMethod(OptionMethodType.BUILDER_SINGULAR_ADD_TO_MAP_VALUE, prepareBuilderSingularAddToMapValue(getterJavadoc));
        generatedMethod(OptionMethodType.BUILDER_SINGULAR_ADD_TO_MAP_VALUES, prepareBuilderSingularAddToMapValues(getterJavadoc));
        generatedMethod(OptionMethodType.BUILDER_SETTER_CONSUMER, prepareSetterConsumer(getterJavadoc));
        generatedMethod(OptionMethodType.BUILDER_SETTER_RUNTIME_TYPE_PROTOTYPE,
                        prepareSetterPrototypeOfRuntimeType(getterJavadoc));
        generatedMethod(OptionMethodType.BUILDER_SETTER_SUPPLIER, prepareSetterSupplier(getterJavadoc));
    }

    TypeName asTypeArgument(TypeName topLevel) {
        return TypeName.builder(topLevel)
                .addTypeArgument(Utils.toWildcard(type()))
                .build();
    }

    TypeName builderGetterType() {
        return type();
    }

    GeneratedMethod prepareBuilderGetter(Javadoc javadoc) {
        TypeName returnType;
        if (builderGetterOptional()) {
            if (option().declaredType().isOptional()) {
                // already wrapped
                returnType = option().declaredType();
            } else {
                returnType = TypeName.builder(TypeNames.OPTIONAL)
                        .addTypeArgument(builderGetterType().boxed())
                        .build();
            }
        } else {
            returnType = option().declaredType();
        }

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName(option().getterName())
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent("return ");
            if (builderGetterOptional()) {
                it.addContent(Optional.class)
                        .addContent(".ofNullable(")
                        .addContent(option().name())
                        .addContent(")");
            } else {
                it.addContent(option().name());
            }
            it.addContentLine(";");
        };

        return GeneratedMethod.builder()
                .method(method.build())
                .javadoc(javadoc)
                .contentBuilder(contentConsumer)
                .build();
    }

    GeneratedMethod prepareBuilderSetter(Javadoc getterJavadoc) {
        TypeName typeName = type();
        if (typeName.equals(CHAR_ARRAY)) {
            return stringSetterForCharArrayBuilderSetter(getterJavadoc);
        }
        return realDeclaredBuilderSetter(getterJavadoc);
    }

    GeneratedMethod realDeclaredBuilderSetter(Javadoc getterJavadoc) {
        TypeName typeName = type();
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
                .typeName(typeName.unboxed())
                .elementName(name)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            if (!typeName.unboxed().primitive()) {
                it.addContent(Objects.class)
                        .addContentLine(".requireNonNull(" + name + ");");
            }
            option().decorator()
                    .ifPresent(decorator -> {
                        it.addContent("new ")
                                .addContent(decorator)
                                .addContent("().decorate(this, ");
                        decorateValue(it, name);
                        it.addContentLine(");");
                    });

            it.addContentLine("this." + name + " = " + name + ";");

            it.addContentLine("return self();");
        };

        return GeneratedMethod.builder()
                .method(method.build())
                .javadoc(setterJavadoc(getterJavadoc, name, ""))
                .contentBuilder(contentConsumer)
                .build();
    }

    void decorateValue(ContentBuilder<?> contentBuilder, String optionName) {
        contentBuilder.addContent(optionName);
    }

    Optional<GeneratedMethod> prepareBuilderSetterCharArray(Javadoc getterJavadoc) {
        TypeName typeName = type();
        if (!typeName.equals(CHAR_ARRAY)) {
            return Optional.empty();
        }
        return Optional.of(realDeclaredBuilderSetter(getterJavadoc));
    }

    Optional<GeneratedMethod> prepareBuilderSetterDeclared(Javadoc getterJavadoc) {
        return Optional.empty();
    }

    Optional<GeneratedMethod> prepareBuilderAddCollection(Javadoc getterJavadoc) {
        return Optional.empty();
    }

    Optional<GeneratedMethod> prepareBuilderClear(Javadoc getterJavadoc) {
        return Optional.empty();
    }

    Optional<GeneratedMethod> prepareBuilderSingularAdd(Javadoc getterJavadoc) {
        return Optional.empty();
    }

    Optional<GeneratedMethod> prepareBuilderSingularAddConsumer(Javadoc getterJavadoc) {
        return Optional.empty();
    }

    Optional<GeneratedMethod> prepareBuilderSingularAddToMapValue(Javadoc getterJavadoc) {
        return Optional.empty();
    }

    Optional<GeneratedMethod> prepareBuilderSingularAddToMapValues(Javadoc getterJavadoc) {
        return Optional.empty();
    }

    Optional<GeneratedMethod> prepareSetterPrototypeOfRuntimeType(Javadoc getterJavadoc) {
        if (option().runtimeType().isEmpty() && option.prototypedBy().isEmpty()) {
            return Optional.empty();
        }

        String optionName = option().name();
        Javadoc javadoc = setterJavadoc(getterJavadoc, optionName, "prototype of ");

        TypeName parameterType;
        String methodName;
        TypeName factoryType;

        if (option.prototypedBy().isPresent()) {
            parameterType = fixPackage(option.prototypedBy().get());
            methodName = "build";
            factoryType = null;
        } else {
            RuntimeTypeInfo rti = option().runtimeType().get();
            var optionBuilder = rti.optionBuilder();
            var factoryMethod = rti.factoryMethod();
            parameterType = optionBuilder.builderMethodType();
            if (factoryMethod.isPresent()) {
                var fm = factoryMethod.get();
                factoryType = fm.declaringType().genericTypeName();
                methodName = fm.methodName();
            } else {
                methodName = "build";
                factoryType = null;
            }
        }

        var method = setterMethodBuilder(parameterType, optionName);

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

            if (factoryType != null) {
                it.addContent(factoryType)
                        .addContent(".")
                        .addContent(methodName)
                        .addContent("(");
            }

            it.addContent(optionName);

            if (factoryType == null) {
                it.addContent("." + methodName + "()");

            } else {
                it.addContent(")");
            }

            it.addContentLine(");");
            it.addContentLine("return self();");
        };

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(javadoc)
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    TypeName fixPackage(TypeName typeName) {
        String packageName = typeName.packageName();
        if (!packageName.isBlank()) {
            return typeName;
        }

        // probably generated within this round
        return TypeName.builder(typeName)
                .packageName(prototypeInfo.blueprint().typeName().packageName())
                .build();

    }

    Optional<GeneratedMethod> prepareSetterConsumer(Javadoc getterJavadoc) {
        if (option().builderInfo().isEmpty()
                && option().runtimeType().isEmpty()
                && option().prototypedBy().isEmpty()
                && !option().declaredType().fqName().endsWith(".Builder")) {
            return Optional.empty();
        }

        if (option().prototypedBy().isPresent()) {
            return prepareSetterConsumerPrototypedBy(getterJavadoc, option().prototypedBy().get());
        }

        if (option().runtimeType().isPresent()) {
            RuntimeTypeInfo rti = option().runtimeType().get();
            var optionBuilder = rti.optionBuilder();
            var factoryMethod = rti.factoryMethod();
            Javadoc javadoc = setterJavadoc(getterJavadoc, "consumer", "consumer of builder of ");

            var method = setterMethodBuilder(TypeName.builder()
                                                     .type(Consumer.class)
                                                     .addTypeArgument(optionBuilder.builderType())
                                                     .build(),
                                             "consumer");

            Consumer<ContentBuilder<?>> contentConsumer = it -> {
                it.addContent(Objects.class)
                        .addContentLine(".requireNonNull(consumer);")
                        .addContent("var builder = ");

                if (optionBuilder.builderMethodName().equals("<init>")) {
                    it.addContent("new ")
                            .addContent(optionBuilder.builderType())
                            .addContentLine("();");
                } else {
                    it.addContent(optionBuilder.builderMethodType())
                            .addContentLine("." + optionBuilder.builderMethodName() + "();");
                }

                it.addContentLine("consumer.accept(builder);")
                        .addContent("this." + option().setterName() + "(");

                factoryMethod.ifPresent(fm -> it.addContent(fm.declaringType().genericTypeName())
                        .addContent(".")
                        .addContent(fm.methodName())
                        .addContent("("));

                it.addContent("builder.")
                        .addContent(optionBuilder.buildMethodName())
                        .addContent("()");

                factoryMethod.ifPresent(f -> it.addContent(")"));

                it.addContentLine(");");
                it.addContentLine("return self();");
            };

            return Optional.of(GeneratedMethod.builder()
                                       .method(method.build())
                                       .javadoc(javadoc)
                                       .contentBuilder(contentConsumer)
                                       .build());
        }

        if (option().builderInfo().isPresent()) {
            var optionBuilder = option().builderInfo().get();
            Javadoc javadoc = setterJavadoc(getterJavadoc, "consumer", "consumer of builder of ");

            var method = setterMethodBuilder(TypeName.builder()
                                                     .type(Consumer.class)
                                                     .addTypeArgument(optionBuilder.builderType())
                                                     .build(), "consumer");

            Consumer<ContentBuilder<?>> contentConsumer = it -> {
                it.addContent(Objects.class)
                        .addContentLine(".requireNonNull(consumer);")
                        .addContent("var builder = ");

                if (optionBuilder.builderMethodName().equals("<init>")) {
                    it.addContent("new ")
                            .addContent(optionBuilder.builderType())
                            .addContentLine("();");
                } else {
                    it.addContent(optionBuilder.builderMethodType())
                            .addContentLine("." + optionBuilder.builderMethodName() + "();");
                }

                it.addContentLine("consumer.accept(builder);")
                        .addContent("this." + option().setterName() + "(builder.")
                        .addContent(optionBuilder.buildMethodName())
                        .addContentLine("());")
                        .addContentLine("return self();");
            };

            return Optional.of(GeneratedMethod.builder()
                                       .method(method.build())
                                       .javadoc(javadoc)
                                       .contentBuilder(contentConsumer)
                                       .build());
        }
        // we have a builder field
        Javadoc javadoc = Javadoc.builder(setterJavadoc(getterJavadoc, "consumer", "consumer of "))
                .parameters(Map.of())
                .build();

        var method = setterMethodBuilder(TypeName.builder()
                                                 .type(Consumer.class)
                                                 .addTypeArgument(type())
                                                 .build(), "consumer");

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(consumer);")
                    .addContent("var builder = ");

            if (option().defaultValue().isPresent()) {
                it.addContentLine("this." + option().name() + ";");
            } else {
                String fqName = option().declaredType().fqName();
                // a bit dirty hack - we expect builder() method to exist on the class that owns the builder
                int lastDot = fqName.lastIndexOf('.');
                String builderMethod = fqName.substring(0, lastDot) + ".builder()";
                it.addContentLine(builderMethod + ";");
            }

            it.addContentLine("consumer.accept(builder);")
                    .addContentLine("this." + option().setterName() + "(builder);")
                    .addContentLine("return self();");
        };

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(javadoc)
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    private Optional<GeneratedMethod> prepareSetterConsumerPrototypedBy(Javadoc getterJavadoc, TypeName typeName) {
        TypeName prototype = option().prototypedBy().get();
        Javadoc javadoc = setterJavadoc(getterJavadoc, "consumer", "consumer of builder of ");

        TypeName builderType = fixPackage(TypeName.builder()
                                                  .className("Builder")
                                                  .addEnclosingName(prototype.className())
                                                  .packageName(prototype.packageName())
                                                  .build());

        var method = setterMethodBuilder(TypeName.builder()
                                                 .type(Consumer.class)
                                                 .addTypeArgument(builderType)
                                                 .build(),
                                         "consumer");

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(consumer);")
                    .addContent("var builder = ")
                    .addContent(prototype)
                    .addContentLine(".builder();")
                    .addContentLine("consumer.accept(builder);")
                    .addContentLine("this." + option().setterName() + "(builder.build());")
                    .addContentLine("return self();");
        };

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(javadoc)
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    Optional<GeneratedMethod> prepareSetterSupplier(Javadoc getterJavadoc) {
        if (option().builderInfo().isEmpty()) {
            return Optional.empty();
        }

        Javadoc javadoc = setterJavadoc(getterJavadoc, "supplier", "supplier of ");

        var method = setterMethodBuilder(asTypeArgument(TypeNames.SUPPLIER), "supplier");

        Consumer<ContentBuilder<?>> contentConsumer = it ->
                it.addContent(Objects.class)
                        .addContentLine(".requireNonNull(supplier);")
                        .addContent("this.")
                        .addContent(option().setterName())
                        .addContentLine("(supplier.get());")
                        .addContentLine("return self();");

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(javadoc)
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    void generateFromConfig(ContentBuilder<?> content) {
        TypeName usedType = type();

        if (usedType.fqName().equals("char[]")) {
            content.addContent(".asString().as(")
                    .addContent(String.class)
                    .addContent("::toCharArray)");
            return;
        }

        if (usedType.equals(TypeNames.STRING)) {
            content.addContent(".asString()");
            return;
        }

        if (usedType.boxed().equals(TypeNames.BOXED_INT)) {
            content.addContent(".asInt()");
            return;
        }

        if (usedType.boxed().equals(TypeNames.BOXED_DOUBLE)) {
            content.addContent(".asDouble()");
            return;
        }

        if (usedType.boxed().equals(TypeNames.BOXED_BOOLEAN)) {
            content.addContent(".asBoolean()");
            return;
        }

        if (usedType.boxed().equals(TypeNames.BOXED_LONG)) {
            content.addContent(".asLong()");
            return;
        }

        content.addContent(".as(")
                .addContent(type().boxed().genericTypeName())
                .addContent(".class)");

    }

    void generateFromConfig(ContentBuilder<?> content, FactoryMethod factoryMethod) {
        if (type().fqName().equals("char[]")) {
            content.addContent(".asString().as(")
                    .addContent(String.class)
                    .addContent("::toCharArray)");
            return;
        }

        content.addContent(".as(")
                .addContent(factoryMethod.declaringType().genericTypeName())
                .addContent("::");
        if (!type().typeArguments().isEmpty()) {
            content.addContent("<");
            Iterator<TypeName> iterator = type().typeArguments().iterator();
            while (iterator.hasNext()) {
                content.addContent(iterator.next());
                if (iterator.hasNext()) {
                    content.addContent(", ");
                }
            }
            content.addContent(">");
        }
        content.addContent(factoryMethod.methodName())
                .addContent(")");
    }

    String configGet(OptionConfigured configured) {
        if (configured.merge()) {
            return "config";
        }
        return "config.get(\"" + configured.configKey() + "\")";
    }

    OptionInfo option() {
        return option;
    }

    PrototypeInfo prototype() {
        return prototypeInfo;
    }

    void deprecation(TypedElementInfo.Builder method) {
        if (option().deprecation().isEmpty()) {
            return;
        }

        var deprecation = option().deprecation().get();
        Optional<String> since = deprecation.since();
        boolean forRemoval = deprecation.forRemoval();

        /*
        Method annotation
         */
        var builder = Annotation.builder()
                .typeName(TypeName.create(Deprecated.class));
        since.ifPresent(it -> builder.putValue("since", it));
        if (forRemoval) {
            builder.putValue("forRemoval", true);
        }

        method.addAnnotation(builder.build());
    }

    Javadoc deprecation(Javadoc javadoc) {
        if (option().deprecation().isEmpty()) {
            return javadoc;
        }

        var deprecation = option().deprecation().get();
        String message = deprecation.message();
        Optional<String> alternative = deprecation.alternative();

        /*
        Javadoc
         */
        var javadocBuilder = Javadoc.builder(javadoc);
        if (alternative.isPresent()) {
            javadocBuilder.deprecation(message + ", use {@link #" + alternative.get() + "} instead");
        } else {
            javadocBuilder.deprecation(message);
        }

        return javadocBuilder.build();
    }

    Javadoc setterJavadoc(Javadoc getterJavadoc, String parameterName, String paramDescriptionPrefix) {
        return Javadoc.builder(getterJavadoc)
                .addParameter(parameterName, paramDescriptionPrefix + String.join("\n", getterJavadoc.returnDescription()))
                .returnDescription("updated builder instance")
                .addTag("see", "#" + option().getterName() + "()")
                .build();
    }

    GeneratedMethod stringSetterForCharArrayBuilderSetter(Javadoc getterJavadoc) {
        TypeName typeName = TypeNames.STRING;
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
                .typeName(typeName)
                .elementName(name)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            if (!typeName.primitive()) {
                it.addContent(Objects.class)
                        .addContentLine(".requireNonNull(" + name + ");");
            }
            it.addContentLine("this." + option().setterName() + "(" + name + ".toCharArray());");

            it.addContentLine("return self();");
        };

        return GeneratedMethod.builder()
                .method(method.build())
                .javadoc(setterJavadoc(getterJavadoc, name, ""))
                .contentBuilder(contentConsumer)
                .build();
    }

    private TypedElementInfo.Builder setterMethodBuilder(TypeName paramType, String paramName) {
        var method = methodBuilderNoParam();

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(paramType)
                .elementName(paramName)
        );
        return method;
    }

    private TypedElementInfo.Builder methodBuilderNoParam() {
        return TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(Utils.builderReturnType())
                .elementName(option().setterName())
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));
    }

    /*
     * This method is always the same, regardless of option type.
     */
    private Optional<GeneratedMethod> prepareImplGetter() {
        if (option().builderOptionOnly()) {
            return Optional.empty();
        }

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(AccessModifier.PUBLIC)
                .typeName(option().declaredType())
                .elementName(option().getterName())
                .addAnnotation(Annotations.OVERRIDE)
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .contentBuilder(it -> it.addContentLine("return " + option().name() + ";"))
                                   .build());
    }

    /*
    This method is always the same, regardless of option type
     */
    private Optional<GeneratedMethod> preparePrototypeGetter(Javadoc javadoc) {
        if (option().builderOptionOnly()) {
            return Optional.empty();
        }

        boolean override = false;
        if (option().declaringType().isPresent()) {
            var declaringType = option().declaringType().get();

            if (isBlueprint(declaringType)) {
                override = !prototypeInfo.detachBlueprint();
            } else {
                // some other interface
                if (declaringType.accessModifier() == AccessModifier.PUBLIC) {
                    // no prototype getter required
                    return Optional.empty();
                }
                override = true;
            }
        }

        boolean callSuper = false;
        TypeName declaringType;
        if (override) {
            TypeName tmpDeclaringType = null;
            if (option().interfaceMethod().isPresent()) {
                var interfaceMethod = option().interfaceMethod().get();
                if (interfaceMethod.elementModifiers().contains(Modifier.DEFAULT)) {
                    // in case we do not have a declaring type, we cannot call the super method
                    // as default methods on interfaces may inherit from more than one super interface, and the
                    // invocation is SuperType.super.methodName()
                    tmpDeclaringType = interfaceMethod.enclosingType().orElse(null);
                    callSuper = tmpDeclaringType != null;
                }
            }
            declaringType = tmpDeclaringType;
        } else {
            declaringType = null;
        }

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(AccessModifier.PUBLIC)
                .typeName(option().declaredType())
                .elementName(option().getterName())
                .update(it -> option().annotations().forEach(it::addAnnotation))
                .update(this::deprecation);

        if (override) {
            method.addAnnotation(Annotations.OVERRIDE);
        }

        Consumer<ContentBuilder<?>> contentConsumer;
        if (callSuper) {
            method.addElementModifier(Modifier.DEFAULT);
            contentConsumer = it -> it.addContent("return ")
                    .addContent(declaringType)
                    .addContent(".super.")
                    .addContent(option().getterName())
                    .addContentLine("();");
        } else {
            // interface method
            contentConsumer = it -> {
            };
        }

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(javadoc)
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    private Javadoc getterJavadoc() {
        Javadoc getterJavadoc;
        if (option().interfaceMethod().isPresent()
                && option().interfaceMethod().get().description().isPresent()
                && !option().interfaceMethod().get().description().get().isBlank()) {
            getterJavadoc = Javadoc.parse(option().interfaceMethod().get().description().get());
        } else if (option.interfaceMethod().isPresent()
                && option.declaringType().isPresent()
                && option.declaringType().get().accessModifier() == AccessModifier.PUBLIC) {
            // reference the method
            getterJavadoc = Javadoc.builder()
                    .add(capitalize(option().name()) + " option. Defined in {@link " + option.declaringType().get().typeName()
                            .fqName() + "#" + option.interfaceMethod().get().elementName() + "()}")
                    .returnDescription("the " + option().name() + " option")
                    .build();
        } else {
            // we must construct Javadoc, as it is missing
            getterJavadoc = Javadoc.builder()
                    .add(capitalize(option().name()) + " option.")
                    .returnDescription("the " + option().name() + " option")
                    .build();
        }
        var builder = Javadoc.builder(getterJavadoc);
        option().description().ifPresent(it -> builder.content(List.of(it.split("\n"))));
        option().paramDescription().ifPresent(builder::returnDescription);

        return builder.build();
    }

    private boolean isBlueprint(TypeInfo declaringType) {
        return declaringType.hasAnnotation(PROTOTYPE_BLUEPRINT);
    }

    private void generatedMethod(OptionMethodType type, Optional<GeneratedMethod> generatedMethod) {
        generatedMethod
                .flatMap(method -> extensions(type, method))
                .ifPresent(method -> generatedMethods.put(type, method));
    }

    private Optional<GeneratedMethod> extensions(OptionMethodType type, GeneratedMethod method) {
        GeneratedMethod result = method;
        for (BuilderCodegenExtension extension : extensions) {
            result = extension.method(option(), result, type)
                    .orElse(null);
            if (result == null) {
                // extension removed this method
                return Optional.empty();
            }
        }
        return Optional.of(result);
    }
}
