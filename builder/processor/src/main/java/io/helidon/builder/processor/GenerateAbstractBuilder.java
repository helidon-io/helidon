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

package io.helidon.builder.processor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.Errors;
import io.helidon.common.processor.classmodel.Annotation;
import io.helidon.common.processor.classmodel.ClassModel;
import io.helidon.common.processor.classmodel.Constructor;
import io.helidon.common.processor.classmodel.InnerClass;
import io.helidon.common.processor.classmodel.Javadoc;
import io.helidon.common.processor.classmodel.Method;
import io.helidon.common.processor.classmodel.TypeArgument;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;

import static io.helidon.builder.processor.Types.CHAR_ARRAY_TYPE;
import static io.helidon.builder.processor.Types.CONFIG_CONFIGURED_BUILDER;
import static io.helidon.builder.processor.Types.CONFIG_TYPE;
import static io.helidon.builder.processor.Types.OVERRIDE;
import static io.helidon.builder.processor.Types.PROTOTYPE_BUILDER;
import static io.helidon.builder.processor.Types.STRING_TYPE;
import static io.helidon.common.processor.GeneratorTools.capitalize;
import static io.helidon.common.processor.classmodel.ClassModel.TYPE_TOKEN;
import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.MAP;
import static io.helidon.common.types.TypeNames.OPTIONAL;
import static io.helidon.common.types.TypeNames.SET;

final class GenerateAbstractBuilder {

    private GenerateAbstractBuilder() {
    }

    static void generate(ClassModel.Builder classModel,
                         TypeName prototype,
                         TypeName runtimeType,
                         List<TypeArgument> typeArguments,
                         TypeContext typeContext) {
        Optional<TypeName> superType = typeContext.typeInfo()
                .superPrototype();

        classModel.addInnerClass(builder -> {
            typeArguments.forEach(builder::addGenericArgument);
            builder.name("BuilderBase")
                    .isAbstract(true)
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .description("Fluent API builder base for {@link " + runtimeType.className() + "}.")
                    .addGenericArgument(token -> token.token("BUILDER")
                            .description("type of the builder extending this abstract builder")
                            .bound(TypeName.builder()
                                           .from(TypeName.create(prototype.fqName() + ".BuilderBase"))
                                           .addTypeArguments(typeArguments)
                                           .addTypeArgument(TypeName.createFromGenericDeclaration("BUILDER"))
                                           .addTypeArgument(TypeName.createFromGenericDeclaration("PROTOTYPE"))
                                           .build()))
                    .addGenericArgument(token -> token.token("PROTOTYPE")
                            .description("type of the prototype interface that would be built by {@link #buildPrototype()}")
                            .bound(prototype))
                    .addConstructor(constructor -> createConstructor(constructor, typeContext));
            superType.ifPresent(type -> {
                builder.superType(TypeName.builder()
                                          .from(TypeName.create(type.fqName() + ".BuilderBase"))
                                          .addTypeArgument(TypeName.createFromGenericDeclaration("BUILDER"))
                                          .addTypeArgument(TypeName.createFromGenericDeclaration("PROTOTYPE"))
                                          .build());
            });

            if (typeContext.configuredData().configured() || hasConfig(typeContext.propertyData().properties())) {
                builder.addInterface(TypeName.builder()
                                             .from(CONFIG_CONFIGURED_BUILDER)
                                             .addTypeArgument(TypeName.createFromGenericDeclaration("BUILDER"))
                                             .addTypeArgument(TypeName.createFromGenericDeclaration("PROTOTYPE"))
                                             .build());
            } else {
                builder.addInterface(TypeName.builder()
                                             .from(TypeName.create(PROTOTYPE_BUILDER))
                                             .addTypeArgument(TypeName.createFromGenericDeclaration("BUILDER"))
                                             .addTypeArgument(TypeName.createFromGenericDeclaration("PROTOTYPE"))
                                             .build());
            }

            fields(builder, typeContext, true);

            // method "from(prototype)"
            fromInstanceMethod(builder, typeContext, prototype);
            fromBuilderMethod(builder, typeContext, typeArguments);

            // method preBuildPrototype() - handles providers, decorator
            preBuildPrototypeMethod(builder, typeContext);
            validatePrototypeMethod(builder, typeContext);

            //custom method adding
            addCustomBuilderMethods(typeContext, builder);

            // setters and getters of builder
            builderMethods(builder, typeContext);
            toString(builder,
                     typeContext,
                     prototype.className() + "Builder",
                     superType.isPresent(),
                     typeContext.customMethods().prototypeMethods(),
                     true);

            // before the builder class is finished, we also generate a protected implementation
            generatePrototypeImpl(builder, typeContext, typeArguments);
        });
    }

    static void buildRuntimeObjectMethod(InnerClass.Builder classBuilder, TypeContext typeContext, boolean isBuilder) {
        TypeContext.TypeInformation typeInformation = typeContext.typeInfo();
        boolean hasRuntimeObject = typeInformation.runtimeObject().isPresent();
        TypeName builtObject = typeInformation.runtimeObject().orElse(typeInformation.prototype());

        Method.Builder builder = Method.builder()
                .name("build")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(builtObject)
                .add("return ");
        if (hasRuntimeObject) {
            builder.typeName(builtObject.genericTypeName());
            if (isBuilder) {
                builder.addLine(".create(this.buildPrototype());");
            } else {
                builder.addLine(".create(this);");
            }
        } else {
            if (isBuilder) {
                builder.addLine("build();");
            } else {
                builder.addLine("this;");
            }
        }
        classBuilder.addMethod(builder);

        // if impl, we also need to add the `get()` method from supplier
        if (!isBuilder) {
            classBuilder.addMethod(method -> method.name("get")
                    .returnType(builtObject)
                    .addAnnotation(Annotations.OVERRIDE)
                    .addLine("return build();"));
        }
    }

    static boolean hasConfig(List<PrototypeProperty> properties) {
        return properties.stream()
                .filter(it -> "config".equals(it.name()))
                .anyMatch(it -> CONFIG_TYPE.equals(it.typeHandler().actualType()));
    }

    private static void addCustomBuilderMethods(TypeContext typeContext, InnerClass.Builder builder) {
        for (CustomMethods.CustomMethod customMethod : typeContext.customMethods().builderMethods()) {
            // builder specific custom methods (not part of interface)
            CustomMethods.Method generated = customMethod.generatedMethod().method();
            // public Builder type(Type) with implementation
            Method.Builder method = Method.builder()
                    .name(generated.name())
                    .returnType(TypeName.createFromGenericDeclaration("BUILDER"))
                    .addLine(customMethod.generatedMethod().callCode() + ";");
            for (String annotation : customMethod.generatedMethod().annotations()) {
                method.addAnnotation(Annotation.parse(annotation));
            }
            for (CustomMethods.Argument argument : generated.arguments()) {
                method.addParameter(param -> param.name(argument.name())
                        .type(argument.typeName()));
            }
            if (!generated.javadoc().isEmpty()) {
                Javadoc javadoc = Javadoc.builder()
                        .from(Javadoc.parse(generated.javadoc()))
                        .returnDescription("updated builder instance")
                        .build();
                method.javadoc(javadoc);
            }
            builder.addMethod(method);
        }
    }

    private static void createConstructor(Constructor.Builder constructor, TypeContext typeContext) {
        constructor.description("Protected to support extensibility.")
                .accessModifier(AccessModifier.PROTECTED);
        // overriding defaults
        for (var prop : typeContext.propertyData().overridingProperties()) {
            if (prop.configuredOption().hasDefault()) {
                constructor.addLine(prop.setterName() + "(" + prop.configuredOption().defaultValue() + ");");
            }
        }
    }

    private static void builderMethods(InnerClass.Builder classBuilder, TypeContext typeContext) {
        List<PrototypeProperty> properties = typeContext.propertyData().properties();
        AnnotationDataConfigured configured = typeContext.configuredData();

        if (configured.configured() || hasConfig(properties)) {
            createConfigMethod(classBuilder, typeContext, configured, properties);
        }

        TypeName returnType = TypeName.createFromGenericDeclaration("BUILDER");
        // first setters
        for (PrototypeProperty child : properties) {
            if (child.typeHandler().actualType().equals(CONFIG_TYPE)) {
                // this is never done here, config must be defined as a standalone method
                continue;
            }
            child.setters(classBuilder, returnType, child.configuredOption().javadoc());
        }
        // then getters
        /*
        If has default value - return type
        If primitive & optional - return type
        If collection - return type
        Otherwise return Optional<x>
         */
        for (PrototypeProperty child : properties) {
            String getterName = child.getterName();
            if ("config".equals(getterName) && configured.configured()) {
                if (child.typeHandler().actualType().equals(CONFIG_TYPE)) {
                    // this will always exist
                    continue;
                }
                // now we have a method called config with wrong return type - this is not supported
                throw new IllegalArgumentException("Configured property named \"config\" can only be of type "
                                                           + CONFIG_TYPE.declaredName() + ", but is: "
                                                           + child.typeName().declaredName());
            }
            /*
            String host() {
              return host;
            }
             */
            Method.Builder method = Method.builder()
                    .name(getterName)
                    .returnType(child.builderGetterType())
                    .addLine("return " + child.builderGetter() + ";");

            for (io.helidon.common.types.Annotation annotation : child.configuredOption().annotations()) {
                method.addAnnotation(annotation);
            }

            Javadoc javadoc = child.configuredOption().javadoc();

            if (javadoc != null) {
                method.javadoc(Javadoc.builder(javadoc)
                                       .returnDescription("the " + toHumanReadable(child.name()))
                                       .build());
            }
            classBuilder.addMethod(method);
        }

        if (configured.configured()) {
            TypeName configReturnType = TypeName.builder()
                    .type(Optional.class)
                    .addTypeArgument(CONFIG_TYPE)
                    .build();
            Method.Builder method = Method.builder()
                    .name("config")
                    .description("If this instance was configured, this would be the config instance used.")
                    .returnType(configReturnType, "config node used to configure this builder, or empty if not configured")
                    .add("return ")
                    .typeName(Optional.class)
                    .addLine(".ofNullable(config);");
            classBuilder.addMethod(method);
        }
    }

    private static void createConfigMethod(InnerClass.Builder classBuilder, TypeContext typeContext,
                                           AnnotationDataConfigured configured,
                                           List<PrototypeProperty> properties) {
        /*
        public BUILDER config(Config config) {
            this.config = config;
            config.get("server").as(String.class).ifPresent(this::server);
            return self();
        }
         */
        Javadoc javadoc;
        if (configured.configured()) {
            javadoc = Javadoc.builder()
                    .addLine("Update builder from configuration (node of this type).")
                    .addLine("If a value is present in configuration, it would override currently configured values.")
                    .build();
        } else {
            javadoc = Javadoc.builder()
                    .addLine("Config to use.")
                    .build();
        }
        Method.Builder builder = Method.builder()
                .name("config")
                .javadoc(javadoc)
                .returnType(TypeArgument.create("BUILDER"), "updated builder instance")
                .addParameter(param -> param.name("config")
                        .type(CONFIG_TYPE)
                        .description("configuration instance used to obtain values to update this builder"))
                .addAnnotation(Annotations.OVERRIDE)
                .typeName(Objects.class)
                .addLine(".requireNonNull(config);")
                .addLine("this.config = config;");

        if (typeContext.typeInfo().superPrototype().isPresent()) {
            builder.addLine("super.config(config);");
        }

        if (configured.configured()) {
            for (PrototypeProperty child : properties) {
                if (child.configuredOption().configured() && !child.configuredOption().provider()) {
                    child.typeHandler().generateFromConfig(builder,
                                                           child.configuredOption(),
                                                           child.factoryMethods());
                }
            }
        }
        builder.addLine("return self();");
        classBuilder.addMethod(builder);
    }

    private static void fromInstanceMethod(InnerClass.Builder builder, TypeContext typeContext, TypeName prototype) {
        Method.Builder methodBuilder = Method.builder()
                .name("from")
                .returnType(TypeArgument.create("BUILDER"))
                .description("Update this builder from an existing prototype instance.")
                .addParameter(param -> param.name("prototype")
                        .type(prototype)
                        .description("existing prototype to update this builder from"))
                .returnType(TypeArgument.create("BUILDER"), "updated builder instance");

        typeContext.typeInfo()
                .superPrototype()
                .ifPresent(it -> methodBuilder.addLine("super.from(prototype);"));
        for (PrototypeProperty property : typeContext.propertyData().properties()) {
            TypeName declaredType = property.typeHandler().declaredType();
            if (declaredType.isSet() || declaredType.isList() || declaredType.isMap()) {
                methodBuilder.add("add");
                methodBuilder.add(capitalize(property.name()));
                methodBuilder.add("(prototype.");
                methodBuilder.add(property.typeHandler().getterName());
                methodBuilder.addLine("());");
            } else {
                /*
                Special handling from config - we have to assign it to field, we cannot go through (config(Config))
                */
                if ("config".equals(property.name()) && property.typeHandler().actualType().equals(CONFIG_TYPE)) {
                    methodBuilder.add("this.config = prototype.config()");
                    if (declaredType.isOptional()) {
                        methodBuilder.add(".orElse(null)");
                    }
                    methodBuilder.addLine(";");
                } else {
                    methodBuilder.add(property.typeHandler().setterName());
                    methodBuilder.add("(prototype.");
                    methodBuilder.add(property.typeHandler().getterName());
                    methodBuilder.addLine("());");
                }
            }
        }
        methodBuilder.addLine("return self();");
        builder.addMethod(methodBuilder);
    }

    private static void fromBuilderMethod(InnerClass.Builder classBuilder,
                                          TypeContext typeContext,
                                          List<TypeArgument> arguments) {
        TypeName prototype = typeContext.typeInfo().prototype();
        TypeName parameterType = TypeName.builder()
                .from(TypeName.create(prototype.fqName() + ".BuilderBase"))
                .addTypeArguments(arguments)
                .addTypeArgument(TypeName.createFromGenericDeclaration("?"))
                .addTypeArgument(TypeName.createFromGenericDeclaration("?"))
                .build();
        Method.Builder methodBuilder = Method.builder()
                .name("from")
                .addParameter(param -> param.name("builder")
                        .type(parameterType)
                        .description("existing builder prototype to update this builder from"))
                .returnType(TypeArgument.create("BUILDER"), "updated builder instance")
                .description("Update this builder from an existing prototype builder instance.");

        typeContext.typeInfo()
                .superPrototype()
                .ifPresent(it -> methodBuilder.addLine("super.from(builder);"));

        for (PrototypeProperty property : typeContext.propertyData().properties()) {
            TypeName declaredType = property.typeHandler().declaredType();
            String setterName = property.typeHandler().setterName();
            String getterName = property.typeHandler().getterName();
            if (property.builderGetterOptional()) {
                // property that is either mandatory or internally nullable
                methodBuilder.addLine("builder." + getterName + "().ifPresent(this::" + setterName + ");");
            } else {
                if (declaredType.isSet() || declaredType.isList() || declaredType.isMap()) {
                    methodBuilder.add("add" + capitalize(property.name()));
                } else {
                    methodBuilder.add(setterName);
                }
                methodBuilder.addLine("(builder." + getterName + "());");
            }

        }
        methodBuilder.addLine("return self();");
        classBuilder.addMethod(methodBuilder);
    }

    private static void fields(InnerClass.Builder classBuilder, TypeContext typeContext, boolean isBuilder) {
        if (isBuilder && (typeContext.configuredData().configured() || hasConfig(typeContext.propertyData().properties()))) {
            classBuilder.addField(builder -> builder.type(CONFIG_TYPE).name("config"));
        }
        for (PrototypeProperty child : typeContext.propertyData().properties()) {
            if (isBuilder && child.configuredOption().hasAllowedValues()) {
                String allowedValues = child.configuredOption().allowedValues()
                        .stream()
                        .map(AnnotationDataOption.AllowedValue::value)
                        .map(it -> "\"" + it + "\"")
                        .collect(Collectors.joining(", "));
                // private static final Set<String> PROPERTY_ALLOWED_VALUES = Set.of("a", "b", "c");
                classBuilder.addField(it -> it.isFinal(true)
                        .isStatic(true)
                        .name(child.name().toUpperCase(Locale.ROOT) + "_ALLOWED_VALUES")
                        .type(TypeName.builder(SET).addTypeArgument(STRING_TYPE).build())
                        .defaultValue(TYPE_TOKEN + Set.class.getName() + TYPE_TOKEN + ".of(" + allowedValues + ")")
                );
            }
            if (!isBuilder || !child.typeHandler().actualType().equals(CONFIG_TYPE)) {
                classBuilder.addField(child.fieldDeclaration(isBuilder));
            }
            if (isBuilder && child.configuredOption().provider()) {
                classBuilder.addField(builder -> builder.type(boolean.class)
                        .name(child.name() + "DiscoverServices")
                        .defaultValue(String.valueOf(child.configuredOption().providerDiscoverServices())));
            }
        }
    }

    private static void preBuildPrototypeMethod(InnerClass.Builder classBuilder,
                                                TypeContext typeContext) {
        Method.Builder preBuildBuilder = Method.builder()
                .name("preBuildPrototype")
                .accessModifier(AccessModifier.PROTECTED)
                .description("Handles providers and decorators.");

        if (typeContext.propertyData().hasProvider()) {
            preBuildBuilder.addAnnotation(builder -> builder.type(SuppressWarnings.class)
                    .addParameter("value", "unchecked"));
        }
        typeContext.typeInfo()
                .superPrototype()
                .ifPresent(it -> preBuildBuilder.addLine("super.preBuildPrototype();"));
        if (typeContext.propertyData().hasProvider()) {
            boolean configured = typeContext.configuredData().configured();
            if (configured) {
                // need to have a non-null config instance
                preBuildBuilder.addLine("this.config = config == null ? Config.empty() : config;");
            }
            for (PrototypeProperty property : typeContext.propertyData().properties()) {
                AnnotationDataOption configuredOption = property.configuredOption();
                if (configuredOption.provider()) {
                    boolean defaultDiscoverServices = configuredOption.providerDiscoverServices();

                    // using a code block, so we can reuse the same variable names for multiple providers
                    preBuildBuilder.addLine("{");
                    TypeName providerType = configuredOption.providerType();
                    preBuildBuilder.add("var serviceLoader = ")
                            .typeName("io.helidon.common.HelidonServiceLoader")
                            .add(".create(")
                            .typeName("java.util.ServiceLoader")
                            .add(".load(")
                            .typeName(providerType.fqName())
                            .addLine(".class));");
                    if (configured) {
                        TypeName typeName = property.typeHandler().declaredType();
                        if (typeName.isList() || typeName.isSet()) {
                            preBuildBuilder.add("this.add")
                                    .add(capitalize(property.name()))
                                    .add("(discoverServices(config, \"")
                                    .add(configuredOption.configKey())
                                    .add("\", serviceLoader, ")
                                    .typeName(providerType)
                                    .add(".class, ")
                                    .typeName(property.typeHandler().actualType())
                                    .add(".class, ")
                                    .add(property.name())
                                    .add("DiscoverServices, ")
                                    .add(property.name())
                                    .addLine("));");
                        } else {
                            preBuildBuilder.add("discoverService(config, \"")
                                    .add(configuredOption.configKey())
                                    .add("\", serviceLoader, ")
                                    .typeName(providerType)
                                    .add(".class, ")
                                    .typeName(property.typeHandler().actualType().genericTypeName())
                                    .add(".class, ")
                                    .add(property.name())
                                    .add("DiscoverServices, @java.util.Optional@.ofNullable(")
                                    .add(property.name())
                                    .add(")).ifPresent(this::")
                                    .add(property.setterName())
                                    .addLine(");");
                        }
                    } else {
                        if (defaultDiscoverServices) {
                            preBuildBuilder.addLine("this." + property.name() + "(serviceLoader.asList());");
                        }
                    }
                    preBuildBuilder.addLine("}");
                }
            }
        }
        if (typeContext.typeInfo().decorator().isPresent()) {
            preBuildBuilder.add("new ")
                    .typeName(typeContext.typeInfo().decorator().get())
                    .addLine("().decorate(this);");
        }
        classBuilder.addMethod(preBuildBuilder);
    }

    private static void validatePrototypeMethod(InnerClass.Builder classBuilder, TypeContext typeContext) {
        Method.Builder validateBuilder = Method.builder()
                .name("validatePrototype")
                .accessModifier(AccessModifier.PROTECTED)
                .description("Validates required properties.");

        typeContext.typeInfo()
                .superPrototype()
                .ifPresent(it -> validateBuilder.addLine("super.validatePrototype();"));

        TypeContext.PropertyData propertyData = typeContext.propertyData();
        if (propertyData.hasRequired()
                || propertyData.hasNonNulls()
                || propertyData.hasAllowedValues()) {
            requiredValidation(validateBuilder, typeContext);
        }
        classBuilder.addMethod(validateBuilder);
    }

    private static void requiredValidation(Method.Builder validateBuilder, TypeContext typeContext) {
        validateBuilder.typeName(Errors.Collector.class)
                .add(" collector = ")
                .typeName(Errors.class)
                .addLine(".collector();");

        for (PrototypeProperty property : typeContext.propertyData().properties()) {
            String configKey = property.configuredOption().configKey();
            String propertyName = property.name();

            if (property.configuredOption().validateNotNull() && !property.configuredOption().hasDefault()) {
                validateBuilder.addLine("if (" + propertyName + " == null) {")
                        .add("collector.fatal(getClass(), \"Property \\\"")
                        .add(configKey == null ? propertyName : configKey);

                if (property.configuredOption().required()) {
                    validateBuilder.addLine("\\\" is required, but not set\");");
                } else {
                    validateBuilder.addLine("\\\" must not be null, but not set\");");
                }
                validateBuilder.addLine("}");
            }
            if (property.configuredOption().hasAllowedValues()) {
                String allowedValuesConstant = propertyName.toUpperCase(Locale.ROOT) + "_ALLOWED_VALUES";
                TypeName declaredType = property.typeHandler().declaredType();

                if (declaredType.isList() || declaredType.isSet()) {
                    String single = "single" + capitalize(propertyName);
                    validateBuilder.addLine("for (var " + single + " : " + propertyName + ") {");
                    validateBuilder.addLine("if (!" + allowedValuesConstant + ".contains(String.valueOf(" + single + "))) {")
                            .add("collector.fatal(getClass(), \"Property \\\"")
                            .add(configKey == null ? propertyName : configKey)
                            .add("\\\" contains value that is not within allowed values. Configured: \\\"\" + "
                                         + single + " + \"\\\"")
                            .addLine(", expected one of: \\\"\" + " + allowedValuesConstant + " + \"\\\"\");");
                    validateBuilder.addLine("}");
                    validateBuilder.addLine("}");

                } else {
                    validateBuilder.add("if (");
                    if (!declaredType.primitive()) {
                        validateBuilder.add(propertyName + " != null && ");
                    }
                    validateBuilder.addLine("!" + allowedValuesConstant + ".contains(String.valueOf(" + propertyName + "))) {")
                            .add("collector.fatal(getClass(), \"Property \\\"")
                            .add(configKey == null ? propertyName : configKey)
                            .add("\\\" value is not within allowed values. Configured: \\\"\" + " + propertyName + " + \"\\\"")
                            .addLine(", expected one of: \\\"\" + " + allowedValuesConstant + " + \"\\\"\");");
                    validateBuilder.addLine("}");
                }
            }
        }
        validateBuilder.addLine("collector.collect().checkValid();");
    }

    private static void generatePrototypeImpl(InnerClass.Builder classBuilder,
                                              TypeContext typeContext,
                                              List<TypeArgument> typeArguments) {
        Optional<TypeName> superPrototype = typeContext.typeInfo()
                .superPrototype();
        TypeName prototype = typeContext.typeInfo().prototype();
        TypeName prototypeImpl = typeContext.typeInfo().prototypeImpl();

        String ifaceName = prototype.className();
        String implName = prototypeImpl.className();

        // inner class of builder base
        classBuilder.addInnerClass(builder -> {
            typeArguments.forEach(builder::addGenericArgument);
            builder.name(implName)
                    .accessModifier(AccessModifier.PROTECTED)
                    .isStatic(true)
                    .description("Generated implementation of the prototype, "
                                         + "can be extended by descendant prototype implementations.");
            superPrototype.ifPresent(it -> {
                builder.superType(TypeName.create(it.className() + "Impl"));
            });
            builder.addInterface(prototype);
            if (typeContext.blueprintData().isFactory()) {
                builder.addInterface(TypeName.builder()
                                             .type(Supplier.class)
                                             .addTypeArgument(typeContext.typeInfo().runtimeObject()
                                                                      .orElse(typeContext.typeInfo().prototype()))
                                             .build());
            }
            /*
            Fields
             */
            fields(builder, typeContext, false);
            /*
            Constructor
             */
            builder.addConstructor(constructor -> {
                constructor.description("Create an instance providing a builder.")
                        .accessModifier(AccessModifier.PROTECTED)
                        .addParameter(param -> param.name("builder")
                                .type(TypeName.builder()
                                              .from(TypeName.create(ifaceName + ".BuilderBase"))
                                              .addTypeArguments(typeArguments)
                                              .addTypeArgument(TypeArgument.create("?"))
                                              .addTypeArgument(TypeArgument.create("?"))
                                              .build())
                                .description("extending builder base of this prototype"));
                superPrototype.ifPresent(it -> {
                    constructor.addLine("super(builder);");
                });
                implAssignToFields(constructor, typeContext);
            });
            /*
            RuntimeType build()
             */
            if (typeContext.blueprintData().isFactory()) {
                buildRuntimeObjectMethod(builder, typeContext, false);
            }
            /*
            Custom prototype methods
             */
            for (CustomMethods.CustomMethod customMethod : typeContext.customMethods().prototypeMethods()) {
                // builder - custom implementation methods for new prototype interface methods
                CustomMethods.Method generated = customMethod.generatedMethod().method();
                Method.Builder method = Method.builder()
                        .name(generated.name())
                        .returnType(generated.returnType());

                // public TypeName boxed() - with implementation
                // no javadoc on impl, it is package local anyway
                for (String annotation : customMethod.generatedMethod().annotations()) {
                    method.addAnnotation(Annotation.parse(annotation));
                }
                if (!customMethod.generatedMethod().annotations().contains(OVERRIDE)) {
                    method.addAnnotation(Annotations.OVERRIDE);
                }
                generated.arguments()
                        .forEach(argument -> method.addParameter(param -> param.name(argument.name()).type(argument.typeName())));
                method.addLine(customMethod.generatedMethod().callCode() + ";");
                builder.addMethod(method);
            }
            /*
            Implementation methods of prototype interface
             */
            implMethods(builder, typeContext);
            /*
            To string
             */
            toString(builder,
                     typeContext,
                     ifaceName,
                     superPrototype.isPresent(),
                     typeContext.customMethods().prototypeMethods(),
                     false);
            /*
            Hash code and equals
             */
            hashCodeAndEquals(builder, typeContext, ifaceName, superPrototype.isPresent());
        });
    }

    private static void hashCodeAndEquals(InnerClass.Builder classBuilder,
                                          TypeContext typeContext,
                                          String ifaceName,
                                          boolean hasSuper) {
        List<PrototypeProperty> equalityFields = typeContext.propertyData()
                .properties()
                .stream()
                .filter(PrototypeProperty::equality)
                .toList();

        equalsMethod(classBuilder, ifaceName, hasSuper, equalityFields);
        hashCodeMethod(classBuilder, hasSuper, equalityFields);
    }

    private static void equalsMethod(InnerClass.Builder classBuilder,
                                     String ifaceName,
                                     boolean hasSuper,
                                     List<PrototypeProperty> equalityFields) {
        String newLine = "\n" + ClassModel.PADDING_TOKEN + ClassModel.PADDING_TOKEN + "&& ";
        Method.Builder method = Method.builder()
                .name("equals")
                .returnType(TypeName.create(boolean.class))
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(param -> param.name("o")
                        .type(Object.class))
                // same instance
                .addLine("if (o == this) {")
                .addLine("return true;")
                .addLine("}")
                // same type
                .addLine("if (!(o instanceof " + ifaceName + " other)) {")
                .addLine("return false;")
                .addLine("}");
        // compare fields
        method.add("return ");
        if (hasSuper) {
            method.add("super.equals(other)");
            if (!equalityFields.isEmpty()) {
                method.add(newLine);
            }
        }
        if (!hasSuper && equalityFields.isEmpty()) {
            method.add("true");
        } else {
            method.add(equalityFields.stream()
                               .map(field -> {
                                   if (field.typeName().array()) {
                                       return "java.util.Arrays.equals(" + field.name() + ", other."
                                               + field.getterName() + "())";
                                   }
                                   if (field.typeName().primitive()) {
                                       return field.name() + " == other." + field.getterName() + "()";
                                   }
                                   return "Objects.equals(" + field.name() + ", other." + field.getterName() + "())";
                               })
                               .collect(Collectors.joining(newLine)));
        }
        method.addLine(";");
        classBuilder.addMethod(method);
    }

    private static void hashCodeMethod(InnerClass.Builder classBuilder,
                                       boolean hasSuper,
                                       List<PrototypeProperty> equalityFields) {
        Method.Builder method = Method.builder()
                .name("hashCode")
                .returnType(TypeName.create(int.class))
                .addAnnotation(Annotations.OVERRIDE);
        if (equalityFields.isEmpty()) {
            // no fields on this type
            if (hasSuper) {
                method.addLine("return super.hashCode();");
            } else {
                // hashcode is a constant, as there are no fields and no super type
                method.addLine("return 1;");
            }
        } else {
            if (hasSuper) {
                method.add("return 31 * super.hashCode() + ")
                        .typeName(Objects.class)
                        .add(".hash(");
            } else {
                method.add("return ")
                        .typeName(Objects.class)
                        .add(".hash(");
            }

            method.add(equalityFields.stream()
                               .map(PrototypeProperty::name)
                               .collect(Collectors.joining(", ")))
                    .addLine(");");
        }

        classBuilder.addMethod(method);
    }

    private static void toString(InnerClass.Builder classBuilder,
                                 TypeContext typeContext,
                                 String typeName,
                                 boolean hasSuper,
                                 List<CustomMethods.CustomMethod> prototypeMethods,
                                 boolean isBuilder) {
        if (prototypeMethods.stream()
                .map(CustomMethods.CustomMethod::generatedMethod)
                .map(CustomMethods.GeneratedMethod::method)
                .filter(it -> "toString".equals(it.name()))
                .filter(it -> it.returnType().equals(STRING_TYPE))
                .anyMatch(it -> it.arguments().isEmpty())) {
            // do not create toString() if defined as a custom method
            return;
        }
        // only create to string if not part of prototype methods
        Method.Builder method = Method.builder()
                .name("toString")
                .returnType(TypeName.create(String.class))
                .addAnnotation(Annotations.OVERRIDE)
                .add("return \"" + typeName);

        List<PrototypeProperty> toStringFields = typeContext.propertyData()
                .properties()
                .stream()
                .filter(PrototypeProperty::toStringValue)
                .toList();

        if (toStringFields.isEmpty()) {
            method.addLine("{};\"");
        } else {
            method.addLine("{\"")
                    .increasePadding()
                    .increasePadding()
                    .addLine(toStringFields.stream()
                                     .map(it -> {
                                         boolean secret = it.confidential() || it.typeHandler().actualType()
                                                 .equals(CHAR_ARRAY_TYPE);

                                         String name = it.name();
                                         if (secret) {
                                             if (it.typeName().primitive() && !it.typeName().array()) {
                                                 return "+ \"" + name + "=****\"";
                                             }
                                             // builder stores fields without optional wrapper
                                             if (!isBuilder && it.typeName().genericTypeName().equals(OPTIONAL)) {
                                                 return "+ \"" + name + "=\" + (" + name + ".isPresent() ? \"****\" : "
                                                         + "\"null\")";
                                             }
                                             return "+ \"" + name + "=\" + (" + name + " == null ? \"null\" : "
                                                     + "\"****\")";
                                         }
                                         return "+ \"" + name + "=\" + " + name;

                                     })
                                     .collect(Collectors.joining(" + \",\"\n")));
            if (hasSuper) {
                method.addLine("+ \"};\"");
            } else {
                method.add("+ \"}\"");
            }
        }
        if (hasSuper) {
            method.add("+ super.toString()");
        }
        method.addLine(";");
        classBuilder.addMethod(method);
    }

    private static void implMethods(InnerClass.Builder classBuilder, TypeContext typeContext) {
        // then getters
        for (PrototypeProperty child : typeContext.propertyData().properties()) {
            String fieldName = child.name();
            String getterName = child.getterName();

            classBuilder.addMethod(method -> method.name(getterName)
                    .returnType(child.typeHandler().declaredType())
                    .addAnnotation(Annotations.OVERRIDE)
                    .addLine("return " + fieldName + ";"));
        }
    }

    private static void implAssignToFields(Constructor.Builder constructor, TypeContext typeContext) {
        for (PrototypeProperty child : typeContext.propertyData().properties()) {
            constructor.add("this." + child.name() + " = ");
            TypeName declaredType = child.typeHandler().declaredType();
            if (declaredType.genericTypeName().equals(LIST)) {
                constructor.typeName(List.class)
                        .addLine(".copyOf(builder." + child.getterName() + "());");
            } else if (declaredType.genericTypeName().equals(SET)) {
                constructor.typeName(Collections.class)
                        .add(".unmodifiableSet(new ")
                        .typeName(LinkedHashSet.class)
                        .addLine("<>(builder." + child.getterName() + "()));");
            } else if (declaredType.genericTypeName().equals(MAP)) {
                constructor.typeName(Collections.class)
                        .add(".unmodifiableMap(new ")
                        .typeName(LinkedHashMap.class)
                        .addLine("<>(builder." + child.getterName() + "()));");
            } else {
                if (child.builderGetterOptional() && !declaredType.isOptional()) {
                    // builder getter optional, but type not, we call get (must be present - is validated)
                    constructor.addLine("builder." + child.getterName() + "().get();");
                } else {
                    // optional and other types are just plainly assigned
                    constructor.addLine("builder." + child.getterName() + "();");
                }
            }
        }
    }

    private static String toHumanReadable(String name) {
        StringBuilder result = new StringBuilder();

        char[] nameChars = name.toCharArray();
        for (char nameChar : nameChars) {
            if (Character.isUpperCase(nameChar)) {
                if (!result.isEmpty()) {
                    result.append(' ');
                }
                result.append(Character.toLowerCase(nameChar));
            } else {
                result.append(nameChar);
            }
        }

        return result.toString();
    }
}
