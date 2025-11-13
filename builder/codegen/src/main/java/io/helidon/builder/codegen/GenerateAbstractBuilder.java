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

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.Errors;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.BuilderCodegen.generateCustomPrototypeMethods;
import static io.helidon.builder.codegen.Types.BUILDER_SUPPORT;
import static io.helidon.builder.codegen.Types.COMMON_CONFIG;
import static io.helidon.builder.codegen.Types.CONFIG;
import static io.helidon.builder.codegen.Types.CONFIG_BUILDER_SUPPORT;
import static io.helidon.builder.codegen.Types.REGISTRY_BUILDER_SUPPORT;
import static io.helidon.builder.codegen.Types.SERVICE_NAMED;
import static io.helidon.codegen.CodegenUtil.capitalize;
import static io.helidon.common.types.TypeNames.OPTIONAL;
import static io.helidon.common.types.TypeNames.SET;

final class GenerateAbstractBuilder {

    private static final String SERVICE_REGISTRY_CONFIG_KEY = "service-registry";
    private static final TypeName OPTIONAL_COMMON_CONFIG = TypeName.builder(TypeNames.OPTIONAL)
            .addTypeArgument(COMMON_CONFIG)
            .build();

    private GenerateAbstractBuilder() {
    }

    static void generate(ClassModel.Builder classModel,
                         PrototypeInfo prototypeInfo,
                         TypeName runtimeType,
                         List<TypeArgument> typeArguments,
                         List<TypeName> typeArgumentNames,
                         List<OptionHandler> options,
                         List<BuilderCodegen.NewDefault> newDefaults,
                         List<BuilderCodegen.MethodUpdate> methodUpdate) {
        Optional<TypeName> superType = prototypeInfo.superPrototype();
        TypeName prototype = prototypeInfo.prototypeType();

        classModel.addInnerClass(builder -> {
            typeArguments.forEach(builder::addGenericArgument);
            builder.name("BuilderBase")
                    .isAbstract(true)
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .addGenericArgument(token -> token.token("BUILDER")
                            .bound(TypeName.builder()
                                           .from(TypeName.create(prototype.fqName() + ".BuilderBase"))
                                           .addTypeArguments(typeArgumentNames)
                                           .addTypeArgument(TypeName.createFromGenericDeclaration("BUILDER"))
                                           .addTypeArgument(TypeName.createFromGenericDeclaration("PROTOTYPE"))
                                           .build()))
                    .addGenericArgument(token -> token.token("PROTOTYPE")
                            .bound(prototype))
                    .javadoc(prototypeInfo.builderBaseJavadoc())
                    .addConstructor(constructor -> createConstructor(constructor, prototypeInfo, newDefaults));

            superType.ifPresent(type -> {
                builder.superType(TypeName.builder()
                                          .from(TypeName.create(type.fqName() + ".BuilderBase"))
                                          .addTypeArgument(TypeName.createFromGenericDeclaration("BUILDER"))
                                          .addTypeArgument(TypeName.createFromGenericDeclaration("PROTOTYPE"))
                                          .build());
            });

            if (prototypeInfo.configured().isPresent() || hasConfig(options)) {
                builder.addInterface(TypeName.builder()
                                             .from(Types.CONFIG_CONFIGURED_BUILDER)
                                             .addTypeArgument(TypeName.createFromGenericDeclaration("BUILDER"))
                                             .addTypeArgument(TypeName.createFromGenericDeclaration("PROTOTYPE"))
                                             .build());
            } else {
                builder.addInterface(TypeName.builder()
                                             .from(Types.PROTOTYPE_BUILDER)
                                             .addTypeArgument(TypeName.createFromGenericDeclaration("BUILDER"))
                                             .addTypeArgument(TypeName.createFromGenericDeclaration("PROTOTYPE"))
                                             .build());
            }

            fields(builder, prototypeInfo, options, true);

            // method "from(prototype)"
            fromInstanceMethod(builder, prototypeInfo, options, prototype);
            fromBuilderMethod(builder, prototypeInfo, options, typeArgumentNames);

            // method preBuildPrototype() - handles providers, decorator
            preBuildPrototypeMethod(builder, prototypeInfo, options);
            validatePrototypeMethod(builder, prototypeInfo, options);

            //custom method adding
            addCustomBuilderMethods(builder, prototypeInfo);

            // setters and getters of builder
            builderMethods(builder, prototypeInfo, options);

            toString(builder,
                     prototypeInfo,
                     prototype.className() + "Builder",
                     superType.isPresent(),
                     options,
                     true);

            // before the builder class is finished, we also generate a protected implementation
            generatePrototypeImpl(builder, prototypeInfo, options, typeArguments, typeArgumentNames);
        });
    }

    static void buildRuntimeObjectMethod(InnerClass.Builder classBuilder,
                                         PrototypeInfo prototypeInfo,
                                         TypeName runtimeType,
                                         boolean isBuilder) {

        boolean hasRuntimeObject = !prototypeInfo.prototypeType().equals(runtimeType);

        Method.Builder builder = Method.builder()
                .name("build")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(runtimeType)
                .addContent("return ");
        if (hasRuntimeObject) {
            builder.addContent(runtimeType.genericTypeName());
            if (isBuilder) {
                builder.addContentLine(".create(this.buildPrototype());");
            } else {
                builder.addContentLine(".create(this);");
            }
        } else {
            if (isBuilder) {
                builder.addContentLine("build();");
            } else {
                builder.addContentLine("this;");
            }
        }
        classBuilder.addMethod(builder);

        // if impl, we also need to add the `get()` method from supplier
        if (!isBuilder) {
            classBuilder.addMethod(method -> method.name("get")
                    .returnType(runtimeType)
                    .addAnnotation(Annotations.OVERRIDE)
                    .addContentLine("return build();"));
        }
    }

    static boolean hasConfig(List<OptionHandler> properties) {
        return properties.stream()
                .map(OptionHandler::option)
                .anyMatch(GenerateAbstractBuilder::isConfigOption);
    }

    static boolean hasRequired(List<OptionHandler> properties) {
        return properties.stream()
                .map(OptionHandler::option)
                .anyMatch(OptionInfo::required);
    }

    static boolean hasAllowedValues(List<OptionHandler> properties) {
        return properties.stream()
                .map(OptionHandler::option)
                .anyMatch(it -> !it.allowedValues().isEmpty());
    }

    private static void addCustomBuilderMethods(InnerClass.Builder builder, PrototypeInfo prototypeInfo) {
        for (GeneratedMethod builderMethod : prototypeInfo.builderMethods()) {
            var methodInfo = builderMethod.methodDefinition();
            Method.Builder method = Method.builder()
                    .name(methodInfo.elementName())
                    .returnType(TypeName.createFromGenericDeclaration("BUILDER"));

            builderMethod.accept(method);

            for (io.helidon.common.types.Annotation annotation : methodInfo.annotations()) {
                method.addAnnotation(annotation);
            }
            int argSize = methodInfo.parameterArguments().size();

            for (int i = 0; i < argSize; i++) {
                TypedElementInfo argument = methodInfo.parameterArguments().get(i);

                if (i == argSize - 1 && argument.typeName().array() && !argument.typeName().primitive()) {
                    // last argument
                    method.addParameter(param -> param.name(argument.elementName())
                            .type(TypeName.builder(argument.typeName())
                                          .array(false)
                                          .build())
                            .update(it -> argument.annotations().forEach(it::addAnnotation))
                            .vararg(true));
                } else {
                    method.addParameter(param -> param.name(argument.elementName())
                            .update(it -> argument.annotations().forEach(it::addAnnotation))
                            .type(argument.typeName()));
                }
            }

            for (TypeName typeParameter : methodInfo.typeParameters()) {
                method.addGenericArgument(TypeArgument.create(typeParameter));
            }

            method.javadoc(methodInfo.description().map(Javadoc::parse)
                                   .orElseGet(() -> Javadoc.parse("")));

            builder.addMethod(method);
        }
    }

    private static void createConstructor(Constructor.Builder constructor, PrototypeInfo typeContext,
                                          List<BuilderCodegen.NewDefault> newDefaults) {
        constructor.description("Protected to support extensibility.")
                .accessModifier(AccessModifier.PROTECTED);

        // overriding defaults
        for (BuilderCodegen.NewDefault newDefault : newDefaults) {
            constructor.addContent(newDefault.setterName())
                    .addContent("(")
                    .update(it -> newDefault.defaultValue().accept(it))
                    .addContentLine(");");
        }
    }

    private static void builderMethods(InnerClass.Builder classBuilder,
                                       PrototypeInfo prototypeInfo,
                                       List<OptionHandler> options) {

        if (prototypeInfo.configured().isPresent() || hasConfig(options)) {
            createConfigMethod(classBuilder, prototypeInfo, options);
        }

        TypeName returnType = TypeName.createFromGenericDeclaration("BUILDER");

        if (prototypeInfo.registrySupport()) {
            // generate setter for service registry
            serviceRegistrySetter(classBuilder);
        }

        // first setters
        for (OptionHandler optionHandler : options) {
            if (isConfigOption(optionHandler.option())) {
                // this is never done here, config must be defined as a standalone method
                // for methods not named config, we consider this to be "just another" property
                continue;
            }
            optionHandler.typeHandler().setters(classBuilder, returnType);
        }
        // then getters
        /*
        If has default value - return type
        If primitive | optional - return type
        If collection - return type
        Otherwise return Optional<x>
         */
        for (OptionHandler optionHandler : options) {
            var option = optionHandler.option();
            TypedElementInfo getter = option.getter();
            String getterName = getter.elementName();
            if ("config".equals(getterName) && option.configured().isPresent()) {
                if (isConfigOption(option)) {
                    // handled elsewhere
                    continue;
                }
                // now we have a method called config with wrong return type - this is not supported
                throw new CodegenException("Configured property named \"config\" can only be of type "
                                                   + Types.CONFIG.declaredName() + ", but is: "
                                                   + option.declaredType().fqName(),
                                           option.blueprintMethod().map(TypedElementInfo::originatingElementValue)
                                                   .orElse(option.declaredType()));
            }
            /*
            String host() {
              return host;
            }
            */
            Method.Builder method = Method.builder()
                    .accessModifier(getter.accessModifier())
                    .name(getterName)
                    .javadoc(Javadoc.parse(getter.description().orElse("")))
                    .returnType(optionHandler.typeHandler().builderGetterType())
                    .update(it -> getter.annotations().forEach(it::addAnnotation))
                    .accessModifier(getter.accessModifier());

            optionHandler.typeHandler().builderGetterBody(method);

            classBuilder.addMethod(method);
        }

        if (prototypeInfo.configured().isPresent()) {
            TypeName configType = configType(options);

            TypeName configReturnType = TypeName.builder()
                    .type(Optional.class)
                    .addTypeArgument(configType)
                    .build();
            Method.Builder method = Method.builder()
                    .name("config")
                    .description("If this instance was configured, this would be the config instance used.")
                    .returnType(configReturnType, "config node used to configure this builder, or empty if not configured")
                    .addContent("return ")
                    .addContent(Optional.class)
                    .addContentLine(".ofNullable(config);");
            classBuilder.addMethod(method);
        }
    }

    private static TypeName configType(List<OptionHandler> options) {
        for (OptionHandler option : options) {
            if (option.typeHandler().type().equals(CONFIG)) {
                return CONFIG;
            }
            if (option.typeHandler().type().equals(COMMON_CONFIG)) {
                return COMMON_CONFIG;
            }
        }
        return CONFIG;
    }

    private static void serviceRegistrySetter(InnerClass.Builder classBuilder) {
        /*
        public BUILDER config(Config config) {
            this.config = config;
            config.get("server").as(String.class).ifPresent(this::server);
            return self();
        }
         */
        Javadoc javadoc = Javadoc.builder()
                .addLine("Provide an explicit registry instance to use.")
                .addLine("<p>")
                .addLine("If not configured, the {@link "
                                 + Types.GLOBAL_SERVICE_REGISTRY.fqName()
                                 + "} would be used to discover services.")
                .build();

        Method.Builder builder = Method.builder()
                .name("serviceRegistry")
                .javadoc(javadoc)
                .returnType(TypeArgument.create("BUILDER"), "updated builder instance")
                .addParameter(param -> param.name("registry")
                        .type(Types.SERVICE_REGISTRY)
                        .description("service registry instance"))
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(registry);")
                .addContentLine("this.serviceRegistry = registry;")
                .addContentLine("return self();");

        classBuilder.addMethod(builder);
    }

    private static void createConfigMethod(InnerClass.Builder classBuilder,
                                           PrototypeInfo prototypeInfo,
                                           List<OptionHandler> options) {
        /*
        public BUILDER config(Config config) {
            this.config = config;
            config.get("server").as(String.class).ifPresent(this::server);
            return self();
        }
         */
        Javadoc javadoc = Javadoc.builder()
                .addLine("Update builder from configuration (node of this type).")
                .addLine("If a value is present in configuration, it would override currently configured values.")
                .build();

        // backward compatibility
        classBuilder.addMethod(commonConfig -> commonConfig
                .name("config")
                .javadoc(Javadoc.builder(javadoc)
                                 .addTag("deprecated", "use {@link #config(" + Types.CONFIG.fqName() + ")}")
                                 .build())
                .returnType(TypeArgument.create("BUILDER"), "updated builder instance")
                .addParameter(param -> param.name("config")
                        .type(Types.COMMON_CONFIG)
                        .description("configuration instance used to obtain values to update this builder"))
                .addAnnotation(Annotations.DEPRECATED)
                .addContent("return config(")
                .addContent(Types.CONFIG)
                .addContentLine(".config(config));")
        );

        Method.Builder builder = Method.builder()
                .name("config")
                .javadoc(javadoc)
                .returnType(TypeArgument.create("BUILDER"), "updated builder instance")
                .addParameter(param -> param.name("config")
                        .type(Types.CONFIG)
                        .description("configuration instance used to obtain values to update this builder"))
                .addAnnotation(Annotations.OVERRIDE)
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(config);")
                .addContentLine("this.config = config;");

        if (prototypeInfo.superPrototype().isPresent()) {
            builder.addContentLine("super.config(config);");
        }

        for (OptionHandler optionHandler : options) {
            OptionInfo option = optionHandler.option();

            if (option.configured().isPresent() && option.provider().isEmpty()) {
                OptionConfigured optionConfigured = option.configured().get();

                if (option.registryService()) {
                    // Injectable option can have a qualifier configured instead of an actual value
                    builder.addContent("if (!config.get(")
                            .addContentLiteral(optionConfigured.configKey() + "." + SERVICE_REGISTRY_CONFIG_KEY)
                            .addContentLine(").exists()) {")
                            .increaseContentPadding();
                }

                optionHandler.typeHandler()
                        .generateFromConfig(builder,
                                            optionConfigured);
                if (option.registryService()) {
                    builder.decreaseContentPadding()
                            .addContentLine("}");
                }
            }
        }

        builder.addContentLine("return self();");
        classBuilder.addMethod(builder);
    }

    private static void fromInstanceMethod(InnerClass.Builder builder,
                                           PrototypeInfo prototypeInfo,
                                           List<OptionHandler> options,
                                           TypeName prototype) {
        Method.Builder methodBuilder = Method.builder()
                .name("from")
                .returnType(TypeArgument.create("BUILDER"))
                .description("Update this builder from an existing prototype instance. "
                                     + "This method disables automatic service discovery.")
                .addParameter(param -> param.name("prototype")
                        .type(prototype)
                        .description("existing prototype to update this builder from"))
                .returnType(TypeArgument.create("BUILDER"), "updated builder instance");

        prototypeInfo.superPrototype()
                .ifPresent(it -> methodBuilder.addContentLine("super.from(prototype);"));

        for (OptionHandler optionHandler : options) {
            OptionInfo option = optionHandler.option();

            TypeHandler typeHandler = optionHandler.typeHandler();
            TypeName declaredType = option.declaredType();

            if (declaredType.isSet() || declaredType.isList() || declaredType.isMap()) {
                if (declaredType.isList() || declaredType.isSet()) {
                    // A list/set might contain only default values. If it has not been updated, clear it of default values
                    // before adding the values from the other builder.
                    methodBuilder.addContentLine("if (!is" + capitalize(option.name()) + "Mutated) {")
                            .addContentLine(option.name() + ".clear();")
                            .addContentLine("}");
                }
                methodBuilder.addContent("add");
                methodBuilder.addContent(capitalize(option.name()));
                methodBuilder.addContent("(prototype.");
                methodBuilder.addContent(option.getter().elementName());
                methodBuilder.addContentLine("());");
            } else {
                /*
                Special handling from config - we have to assign it to field, we cannot go through (config(Config))
                */
                if (isConfigOption(option)) {
                    if (typeHandler.type().equals(COMMON_CONFIG)) {
                        if (declaredType.isOptional()) {
                            methodBuilder.addContent("this.config = prototype.config().map(")
                                    .addContent(CONFIG)
                                    .addContent("::config).orElse(null)");
                        } else {
                            methodBuilder.addContent("this.config = ")
                                    .addContent(CONFIG)
                                    .addContent(".config(prototype.config())");
                        }
                    } else {
                        methodBuilder.addContent("this.config = prototype.config()");
                        if (declaredType.isOptional()) {
                            methodBuilder.addContent(".orElse(null)");
                        }
                    }
                    methodBuilder.addContentLine(";");
                } else {
                    methodBuilder.addContent(option.setter().elementName());
                    methodBuilder.addContent("(prototype.");
                    methodBuilder.addContent(option.getter().elementName());
                    methodBuilder.addContentLine("());");
                }
            }
            if (option.provider().isPresent() || option.registryService()) {
                methodBuilder.addContentLine(option.name() + "DiscoverServices = false;");
            }
        }
        methodBuilder.addContentLine("return self();");
        builder.addMethod(methodBuilder);
    }

    private static void fromBuilderMethod(InnerClass.Builder classBuilder,
                                          PrototypeInfo prototypeInfo,
                                          List<OptionHandler> options,
                                          List<TypeName> arguments) {

        TypeName prototype = prototypeInfo.prototypeType();
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

        prototypeInfo.superPrototype()
                .ifPresent(it -> methodBuilder.addContentLine("super.from(builder);"));

        for (OptionHandler optionHandler : options) {
            OptionInfo option = optionHandler.option();
            TypeHandler typeHandler = optionHandler.typeHandler();

            String optionName = option.name();
            TypeName declaredType = option.declaredType();
            String setterName = option.setter().elementName();
            String getterName = option.getter().elementName();

            if (typeHandler.builderGetterOptional()) {
                // property that is either mandatory or internally nullable
                methodBuilder.addContentLine("builder." + getterName + "().ifPresent(this::" + setterName + ");");
            } else {
                if (declaredType.isSet() || declaredType.isList() || declaredType.isMap()) {
                    if (declaredType.isList()) {
                        /*
                        If this builder's list HAS been mutated, add the other builder's values ONLY if they are not defaults.

                        If this builder's list HAS NOT been mutated, clear it (to remove defaults) and add the other builder's
                        values regardless of whether they are defaulted or explicitly set.

                        Generated code:

                        if (isXxxMutated) {
                            if (builder.isXxxMutated) {
                                addXxx(builder.xxx());
                            }
                        } else {
                            xxx.clear();
                            addXxx(builder.xxx());
                        }
                        */
                        String isMutatedProperty = TypeHandlerCollection.isMutatedField(optionName);
                        methodBuilder.addContentLine("if (" + isMutatedProperty + ") {")
                                .addContentLine("if (builder." + isMutatedProperty + ") {")
                                .addContentLine("add" + capitalize(optionName) + "(builder." + optionName + ");")
                                .addContentLine("}")
                                .decreaseContentPadding() // Ideally would not be needed but makes for nicer generated code.
                                .addContentLine("} else {")
                                .addContentLine(optionName + ".clear();")
                                .addContentLine("add" + capitalize(optionName) + "(builder." + optionName + ");")
                                .addContentLine("}");

                    } else {
                        // Non-list collection case.
                        methodBuilder.addContentLine("add" + capitalize(optionName) + "(builder." + optionName + ");");
                    }
                } else {
                    // Non-collection case.
                    methodBuilder.addContentLine(setterName + "(builder." + getterName + "());");
                }
            }
            if (option.provider().isPresent() || option.registryService()) {
                methodBuilder.addContent(optionName + "DiscoverServices");
                methodBuilder.addContentLine(" = builder." + optionName + "DiscoverServices;");
            }
        }
        methodBuilder.addContentLine("return self();");
        classBuilder.addMethod(methodBuilder);
    }

    private static void fields(InnerClass.Builder classBuilder,
                               PrototypeInfo prototypeInfo,
                               List<OptionHandler> options,
                               boolean isBuilder) {

        if (isBuilder && (prototypeInfo.configured().isPresent() || hasConfig(options))) {
            classBuilder.addField(builder -> builder.type(Types.CONFIG).name("config"));
        }
        if (isBuilder && prototypeInfo.registrySupport()) {
            classBuilder.addField(builder -> builder.type(Types.SERVICE_REGISTRY).name("serviceRegistry"));
        }
        for (OptionHandler optionHandler : options) {
            var option = optionHandler.option();
            if (isBuilder && !option.allowedValues().isEmpty()) {
                String allowedValues = option.allowedValues()
                        .stream()
                        .map(OptionAllowedValue::value)
                        .map(it -> "\"" + it + "\"")
                        .collect(Collectors.joining(", "));
                // private static final Set<String> PROPERTY_ALLOWED_VALUES = Set.of("a", "b", "c");
                classBuilder.addField(it -> it.isFinal(true)
                        .isStatic(true)
                        .name(option.name().toUpperCase(Locale.ROOT) + "_ALLOWED_VALUES")
                        .type(TypeName.builder(SET).addTypeArgument(TypeNames.STRING).build())
                        .addContent(Set.class)
                        .addContent(".of(")
                        .addContent(allowedValues)
                        .addContent(")"));
            }

            if (!isBuilder || !isConfigOption(option)) {
                classBuilder.addField(optionHandler.typeHandler().field(isBuilder));
            }
            if (isBuilder && (option.provider().isPresent())) {
                OptionProvider provider = option.provider().get();
                classBuilder.addField(builder -> builder.type(boolean.class)
                        .name(option.name() + "DiscoverServices")
                        .defaultValue(String.valueOf(provider.discoverServices())));
            }
            if (isBuilder && (option.registryService())) {
                classBuilder.addField(builder -> builder.type(boolean.class)
                        .name(option.name() + "DiscoverServices")
                        .defaultValue("true"));
            }
            if (isBuilder && (option.declaredType().isList() || option.declaredType().isSet())) {
                classBuilder.addField(builder -> builder.type(boolean.class)
                        .name("is" + capitalize(option.name()) + "Mutated")
                        .accessModifier(AccessModifier.PRIVATE));
            }
        }
    }

    private static boolean isConfigOption(OptionInfo option) {
        var type = option.declaredType();
        if (type.isOptional()) {
            type = type.typeArguments().getFirst();
        }
        return type.equals(COMMON_CONFIG) || type.equals(CONFIG);
    }

    private static void preBuildPrototypeMethod(InnerClass.Builder classBuilder,
                                                PrototypeInfo prototypeInfo,
                                                List<OptionHandler> options) {
        Method.Builder preBuildBuilder = Method.builder()
                .name("preBuildPrototype")
                .accessModifier(AccessModifier.PROTECTED)
                .description("Handles providers and decorators.");

        boolean hasProvider = hasProvider(options);

        if (hasProvider) {
            preBuildBuilder.addAnnotation(builder -> builder.type(SuppressWarnings.class)
                    .addParameter("value", "unchecked"));
        }
        prototypeInfo.superPrototype()
                .ifPresent(it -> preBuildBuilder.addContentLine("super.preBuildPrototype();"));

        if (prototypeInfo.registrySupport() || hasProvider) {
            boolean configured = prototypeInfo.configured().isPresent();

            if (configured) {
                // need to have a non-null config instance
                preBuildBuilder.addContent("var config = this.config == null ? ")
                        .addContent(Types.CONFIG)
                        .addContentLine(".empty() : this.config;");
            }

            if (prototypeInfo.registrySupport()) {
                preBuildBuilder.addContent("var registry = ")
                        .addContent(Optional.class)
                        .addContentLine(".ofNullable(this.serviceRegistry);");
            }

            for (OptionHandler optionHandler : options) {
                OptionInfo option = optionHandler.option();
                boolean optionConfigured = option.configured().isPresent();

                if (option.provider().isPresent()) {
                    OptionProvider optionProvider = option.provider().get();

                    // using a code block, so we can reuse the same variable names for multiple providers
                    TypeName providerType = optionProvider.providerType();

                    if (prototypeInfo.registrySupport()) {
                        serviceRegistryPropertyDiscovery(preBuildBuilder,
                                                         optionHandler,
                                                         optionConfigured,
                                                         providerType);
                    } else {
                        serviceLoaderPropertyDiscovery(preBuildBuilder,
                                                       optionHandler,
                                                       optionConfigured,
                                                       providerType);
                    }

                } else if (option.registryService()) {
                    serviceRegistryProperty(preBuildBuilder, optionHandler);
                }
            }
        }
        if (prototypeInfo.builderDecorator().isPresent()) {
            preBuildBuilder.addContent("new ")
                    .addContent(prototypeInfo.builderDecorator().get())
                    .addContentLine("().decorate(this);");
        }
        classBuilder.addMethod(preBuildBuilder);
    }

    private static boolean hasProvider(List<OptionHandler> options) {
        return options.stream()
                .map(OptionHandler::option)
                .anyMatch(it -> it.provider().isPresent());
    }

    private static void serviceLoaderPropertyDiscovery(Method.Builder preBuildBuilder,
                                                       OptionHandler optionHandler,
                                                       boolean propertyConfigured,
                                                       TypeName providerType) {

        TypeHandler typeHandler = optionHandler.typeHandler();
        OptionInfo option = optionHandler.option();
        TypeName typeName = option.declaredType();

        if (propertyConfigured) {
            OptionConfigured configured = option.configured().get();

            if (typeName.isList() || typeName.isSet()) {
                preBuildBuilder.addContent("this.add")
                        .addContent(capitalize(option.name()))
                        .addContent("(")
                        .addContent(CONFIG_BUILDER_SUPPORT)
                        .addContent(".discoverServices(config, \"")
                        .addContent(configured.configKey())
                        .addContent("\", ")
                        .addContent(providerType.genericTypeName())
                        .addContent(".class, ")
                        .addContent(typeHandler.type().genericTypeName())
                        .addContent(".class, ")
                        .addContent(option.name())
                        .addContent("DiscoverServices, ")
                        .addContent(option.name())
                        .addContentLine("));");
            } else {
                preBuildBuilder
                        .addContent(CONFIG_BUILDER_SUPPORT)
                        .addContent(".discoverService(config, \"")
                        .addContent(configured.configKey())
                        .addContent("\", ")
                        .addContent(providerType)
                        .addContent(".class, ")
                        .addContent(typeHandler.type().genericTypeName())
                        .addContent(".class, ")
                        .addContent(option.name())
                        .addContent("DiscoverServices, ")
                        .addContent(Optional.class)
                        .addContent(".ofNullable(")
                        .addContent(option.name())
                        .addContent(")).ifPresent(this::")
                        .addContent(option.setter().elementName())
                        .addContentLine(");");
            }
        } else {
            if (typeName.isList() || typeName.isSet()) {
                preBuildBuilder.addContent("this.add")
                        .addContent(capitalize(option.name()))
                        .addContent("(")
                        .addContent(BUILDER_SUPPORT)
                        .addContent(".discoverServices(")
                        .addContent(providerType.genericTypeName())
                        .addContent(".class, ")
                        .addContent(option.name())
                        .addContent("DiscoverServices, ")
                        .addContent(option.name())
                        .addContentLine("));");
            } else {
                preBuildBuilder
                        .addContent(BUILDER_SUPPORT)
                        .addContent(".discoverService(")
                        .addContent(providerType)
                        .addContent(".class, ")
                        .addContent(option.name())
                        .addContent("DiscoverServices, ")
                        .addContent(Optional.class)
                        .addContent(".ofNullable(")
                        .addContent(option.name())
                        .addContent(")).ifPresent(this::")
                        .addContent(option.setter().elementName())
                        .addContentLine(");");
            }
        }
    }

    private static void serviceRegistryProperty(Method.Builder preBuildBuilder,
                                                OptionHandler optionHandler) {
        OptionInfo option = optionHandler.option();
        TypeName typeName = option.declaredType();

        // Example: .of("bean-name") or .empty()
        var namedQualifierFromAnnotation = option.qualifiers()
                .stream()
                .filter(a -> a.typeName().equals(SERVICE_NAMED))
                .flatMap(annotation -> annotation.stringValue().stream())
                .map(s -> ".of(\"" + s + "\")")
                .findFirst()
                .orElse(".empty()");

        // Example: Optional<String> regionQualifier =
        preBuildBuilder
                .addContent("Optional<String> ")
                .addContent(option.name())
                .addContent("Qualifier = ");

        if (option.configured().isPresent()) {
            OptionConfigured optionConfigured = option.configured().get();

            /* Configured named qualifier wins over annotation
            Example:
            Optional<String> regionQualifier = config.get("region.service-registry.named")
                    .asString()
                    .orElse(Optional.of("bean-name"));
            */
            preBuildBuilder
                    .addContent("config.get(")
                    .addContentLiteral(optionConfigured.configKey() + "." + SERVICE_REGISTRY_CONFIG_KEY + ".named")
                    .addContentLine(")")
                    .increaseContentPadding()
                    .addContentLine(".asString()")
                    .addContent(".or(() -> ")
                    .addContent(OPTIONAL)
                    .addContent(namedQualifierFromAnnotation)
                    .addContentLine(");")
                    .decreaseContentPadding();
        } else {
            /* Example:
            Optional<String> regionQualifier = Optional.of("bean-name");
            */
            preBuildBuilder
                    .addContent(OPTIONAL)
                    .addContent(namedQualifierFromAnnotation)
                    .addContentLine(";");
        }

        if (typeName.isList()) {

            /*
            this.addRegion(RegistryBuilderSupport.serviceList(registry,
                                           TypeName.create("com.oracle.bmc.Region"),
                                           regionQualifier,
                                           Optional.ofNullable(region),
                                           regionDiscoverServices));
            */
            preBuildBuilder
                    .addContent("this.add")
                    .addContent(capitalize(option.name()))
                    .addContent("(")
                    .addContent(REGISTRY_BUILDER_SUPPORT)
                    .addContent(".serviceList(registry, ")
                    .addContentCreate(optionHandler.typeHandler().type())
                    .addContent(", ")
                    .addContent(option.name())
                    .addContent("DiscoverServices, ")
                    .addContent(option.name())
                    .addContentLine("Qualifier));");

        } else if (typeName.isSet()) {
             /*
            this.addRegion(RegistryBuilderSupport.serviceSet(registry,
                                           TypeName.create("com.oracle.bmc.Region"),
                                           regionQualifier,
                                           Optional.ofNullable(region),
                                           regionDiscoverServices));
            */
            preBuildBuilder
                    .addContent("this.add")
                    .addContent(capitalize(option.name()))
                    .addContent("(")
                    .addContent(REGISTRY_BUILDER_SUPPORT)
                    .addContent(".serviceSet(registry, ")
                    .addContentCreate(optionHandler.typeHandler().type())
                    .addContent(", ")
                    .addContent(option.name())
                    .addContent("DiscoverServices, ")
                    .addContent(option.name())
                    .addContentLine("Qualifier));");
        } else {

            /*
            RegistryBuilderSupport.service(registry,
                                           TypeName.create("com.oracle.bmc.Region"),
                                           regionQualifiers,
                                           Optional.ofNullable(region),
                                           regionDiscoverServices)
                                  .ifPresent(this::region);
            */
            preBuildBuilder
                    .addContent(REGISTRY_BUILDER_SUPPORT)
                    .addContent(".service(registry, ")
                    .addContentCreate(optionHandler.typeHandler().type())
                    .addContent(", ")
                    .addContent(Optional.class)
                    .addContent(".ofNullable(")
                    .addContent(option.name())
                    .addContent("), ")
                    .addContent(option.name())
                    .addContent("DiscoverServices, ")
                    .addContent(option.name())
                    .addContentLine("Qualifier)")
                    .increaseContentPadding()
                    .addContent(".ifPresent(this::")
                    .addContent(option.setter().elementName())
                    .addContentLine(");")
                    .decreaseContentPadding();
        }
    }

    private static void serviceRegistryPropertyDiscovery(Method.Builder preBuildBuilder,
                                                         OptionHandler optionHandler,
                                                         boolean propertyConfigured,
                                                         TypeName providerType) {
        TypeHandler typeHandler = optionHandler.typeHandler();
        OptionInfo option = optionHandler.option();
        TypeName typeName = option.declaredType();

        if (propertyConfigured) {
            OptionConfigured configured = option.configured().get();

            if (typeName.isList() || typeName.isSet()) {
                preBuildBuilder.addContent("this.add")
                        .addContent(capitalize(option.name()))
                        .addContent("(")
                        .addContent(CONFIG_BUILDER_SUPPORT)
                        .addContentLine(".discoverServices(config,")
                        .increaseContentPadding()
                        .increaseContentPadding()
                        .increaseContentPadding()
                        .addContent("\"")
                        .addContent(configured.configKey())
                        .addContentLine("\",")
                        .addContentLine("registry,")
                        .addContent(providerType.genericTypeName())
                        .addContentLine(".class,")
                        .addContent(typeHandler.type().genericTypeName())
                        .addContentLine(".class,")
                        .addContent(option.name())
                        .addContentLine("DiscoverServices,")
                        .addContent(option.name())
                        .addContentLine("));")
                        .decreaseContentPadding()
                        .decreaseContentPadding()
                        .decreaseContentPadding();
            } else {
                preBuildBuilder
                        .addContent(CONFIG_BUILDER_SUPPORT)
                        .addContentLine(".discoverService(config,")
                        .increaseContentPadding()
                        .increaseContentPadding()
                        .increaseContentPadding()
                        .addContent("\"")
                        .addContent(configured.configKey())
                        .addContentLine("\",")
                        .addContentLine("registry,")
                        .addContent(providerType)
                        .addContentLine(".class,")
                        .addContent(typeHandler.type().genericTypeName())
                        .addContentLine(".class,")
                        .addContent(option.name())
                        .addContentLine("DiscoverServices,")
                        .addContent(Optional.class)
                        .addContent(".ofNullable(")
                        .addContent(option.name())
                        .addContentLine("))")
                        .decreaseContentPadding()
                        .decreaseContentPadding()
                        .addContent(".ifPresent(this::")
                        .addContent(option.setter().elementName())
                        .addContentLine(");")
                        .decreaseContentPadding();
            }
        } else {
            if (typeName.isList()) {
                preBuildBuilder
                        .addContent("this.add")
                        .addContent(capitalize(option.name()))
                        .addContent("(")
                        .addContent(REGISTRY_BUILDER_SUPPORT)
                        .addContent(".serviceList(registry, ")
                        .addContentCreate(typeHandler.type())
                        .addContent(", ")
                        .addContent(option.name())
                        .addContentLine("DiscoverServices));");
            } else if (typeName.isSet()) {
                preBuildBuilder
                        .addContent("this.add")
                        .addContent(capitalize(option.name()))
                        .addContent("(")
                        .addContent(REGISTRY_BUILDER_SUPPORT)
                        .addContent(".serviceSet(registry, ")
                        .addContentCreate(typeHandler.type())
                        .addContent(", ")
                        .addContent(option.name())
                        .addContentLine("DiscoverServices));");
            } else {
                preBuildBuilder
                        .addContent(REGISTRY_BUILDER_SUPPORT)
                        .addContent(".service(registry, ")
                        .addContentCreate(typeHandler.type())
                        .addContent(", ")
                        .addContent(Optional.class)
                        .addContent(".ofNullable(")
                        .addContent(option.name())
                        .addContent("), ")
                        .addContent(option.name())
                        .addContent("DiscoverServices).ifPresent(this::")
                        .addContent(option.setter().elementName())
                        .addContentLine(");");
            }
        }
    }

    private static void validatePrototypeMethod(InnerClass.Builder classBuilder,
                                                PrototypeInfo prototypeInfo,
                                                List<OptionHandler> options) {

        Method.Builder validateBuilder = Method.builder()
                .name("validatePrototype")
                .accessModifier(AccessModifier.PROTECTED)
                .description("Validates required properties.");

        prototypeInfo
                .superPrototype()
                .ifPresent(it -> validateBuilder.addContentLine("super.validatePrototype();"));

        if (hasRequired(options)
                || hasAllowedValues(options)) {
            requiredValidation(validateBuilder, options);
        }
        classBuilder.addMethod(validateBuilder);
    }

    private static void requiredValidation(Method.Builder validateBuilder,
                                           List<OptionHandler> options) {
        validateBuilder.addContent(Errors.Collector.class)
                .addContent(" collector = ")
                .addContent(Errors.class)
                .addContentLine(".collector();");

        for (OptionHandler optionHandler : options) {
            OptionInfo option = optionHandler.option();

            String propertyName = option.name();

            if (option.required() && option.defaultValue().isEmpty()) {
                validateBuilder.addContentLine("if (" + propertyName + " == null) {")
                        .addContent("collector.fatal(getClass(), \"Property \\\"")
                        .addContent(propertyName)
                        .addContentLine("\\\" must not be null, but not set\");")
                        .addContentLine("}");
            }
            if (!option.allowedValues().isEmpty()) {
                String allowedValuesConstant = propertyName.toUpperCase(Locale.ROOT) + "_ALLOWED_VALUES";
                TypeName declaredType = option.declaredType();

                if (declaredType.isList() || declaredType.isSet()) {
                    String single = "single" + capitalize(propertyName);
                    validateBuilder.addContentLine("for (var " + single + " : " + propertyName + ") {");
                    validateBuilder.addContentLine("if (!" + allowedValuesConstant + ".contains(String.valueOf(" + single + "))"
                                                           + ") {")
                            .addContent("collector.fatal(getClass(), \"Property \\\"")
                            .addContent(propertyName)
                            .addContent("\\\" contains value that is not within allowed values. Configured: \\\"\" + "
                                                + single + " + \"\\\"")
                            .addContentLine(", expected one of: \\\"\" + " + allowedValuesConstant + " + \"\\\"\");");
                    validateBuilder.addContentLine("}");
                    validateBuilder.addContentLine("}");
                } else {
                    validateBuilder.addContent("if (");
                    if (!declaredType.primitive()) {
                        validateBuilder.addContent(propertyName + " != null && ");
                    }
                    validateBuilder.addContentLine("!" + allowedValuesConstant + ".contains(String.valueOf(" + propertyName
                                                           + "))) {")
                            .addContent("collector.fatal(getClass(), \"Property \\\"")
                            .addContent(propertyName)
                            .addContent("\\\" value is not within allowed values. Configured: \\\"\" + " + propertyName + " + "
                                                + "\"\\\"")
                            .addContentLine(", expected one of: \\\"\" + " + allowedValuesConstant + " + \"\\\"\");");
                    validateBuilder.addContentLine("}");
                }
            }
        }
        validateBuilder.addContentLine("collector.collect().checkValid();");
    }

    private static void generatePrototypeImpl(InnerClass.Builder classBuilder,
                                              PrototypeInfo prototypeInfo,
                                              List<OptionHandler> options,
                                              List<TypeArgument> typeArguments,
                                              List<TypeName> typeArgumentNames) {
        Optional<TypeName> superPrototype = prototypeInfo.superPrototype();

        TypeName prototype = prototypeInfo.prototypeType();
        String ifaceName = prototype.className();
        // inner class of the builder
        String implName = ifaceName + "Impl";

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
            if (prototypeInfo.runtimeType().isPresent()) {
                TypeName runtimeType = prototypeInfo.runtimeType().get();
                builder.addInterface(TypeName.builder()
                                             .type(Supplier.class)
                                             .addTypeArgument(runtimeType)
                                             .build());
            }
            /*
            Fields
             */
            fields(builder, prototypeInfo, options, false);
            /*
            Constructor
             */
            builder.addConstructor(constructor -> {
                constructor.description("Create an instance providing a builder.")
                        .accessModifier(AccessModifier.PROTECTED)
                        .addParameter(param -> param.name("builder")
                                .type(TypeName.builder()
                                              .from(TypeName.create(ifaceName + ".BuilderBase"))
                                              .addTypeArguments(typeArgumentNames)
                                              .addTypeArgument(TypeArgument.create("?"))
                                              .addTypeArgument(TypeArgument.create("?"))
                                              .build())
                                .description("extending builder base of this prototype"));
                superPrototype.ifPresent(it -> {
                    constructor.addContentLine("super(builder);");
                });
                implAssignToFields(constructor, options);
            });
            /*
            RuntimeType build()
             */
            if (prototypeInfo.runtimeType().isPresent()) {
                buildRuntimeObjectMethod(builder, prototypeInfo, prototypeInfo.runtimeType().get(), false);
            }
            /*
            Custom methods that cannot be added to the prototype
             */
            generateCustomPrototypeMethods(builder, prototypeInfo.prototypeMethods(), true);

            /*
            Implementation methods of prototype interface
             */
            implMethods(builder, options);

            /*
            To string
             */
            toString(builder,
                     prototypeInfo,
                     ifaceName,
                     superPrototype.isPresent(),
                     options,
                     false);
            /*
            Hash code and equals
             */
            hashCodeAndEquals(builder, options, ifaceName, superPrototype.isPresent());
        });
    }

    private static void hashCodeAndEquals(InnerClass.Builder classBuilder,
                                          List<OptionHandler> options,
                                          String ifaceName,
                                          boolean hasSuper) {
        List<OptionHandler> equalityFields = options.stream()
                .filter(it -> it.option().includeInEqualsAndHashCode())
                .toList();

        equalsMethod(classBuilder, ifaceName, hasSuper, equalityFields);
        hashCodeMethod(classBuilder, hasSuper, equalityFields);
    }

    private static void equalsMethod(InnerClass.Builder classBuilder,
                                     String ifaceName,
                                     boolean hasSuper,
                                     List<OptionHandler> equalityFields) {
        String newLine = "\n" + ClassModel.PADDING_TOKEN + ClassModel.PADDING_TOKEN + "&& ";
        Method.Builder method = Method.builder()
                .name("equals")
                .returnType(TypeName.create(boolean.class))
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(param -> param.name("o")
                        .type(Object.class))
                // same instance
                .addContentLine("if (o == this) {")
                .addContentLine("return true;")
                .addContentLine("}")
                // same type
                .addContentLine("if (!(o instanceof " + ifaceName + " other)) {")
                .addContentLine("return false;")
                .addContentLine("}");
        // compare fields
        method.addContent("return ")
                .increaseContentPadding();
        if (hasSuper) {
            method.addContent("super.equals(other)");
            if (!equalityFields.isEmpty()) {
                method.addContent(newLine);
            }
        }
        if (!hasSuper && equalityFields.isEmpty()) {
            method.addContent("true");
        } else {
            Iterator<OptionHandler> equalIterator = equalityFields.iterator();
            while (equalIterator.hasNext()) {
                OptionHandler field = equalIterator.next();
                OptionInfo option = field.option();
                TypeName type = option.declaredType();
                TypeName actualType = field.typeHandler().type();

                if (type.array()) {
                    method.addContent(Arrays.class)
                            .addContent(".equals(")
                            .addContent(option.name())
                            .addContent(", other.")
                            .addContent(option.getter().elementName())
                            .addContent("())");
                } else if (type.primitive()) {
                    method.addContent(option.name())
                            .addContent(" == other.")
                            .addContent(option.getter().elementName())
                            .addContent("()");
                } else if (type.isOptional() && actualType.equals(Types.CHAR_ARRAY)) {
                    method.addContent(Types.GENERATED_EQUALITY_UTIL)
                            .addContent(".optionalCharArrayEquals(")
                            .addContent(option.name())
                            .addContent(", other.")
                            .addContent(option.getter().elementName())
                            .addContent("())");
                } else {
                    method.addContent(Objects.class)
                            .addContent(".equals(")
                            .addContent(option.name())
                            .addContent(", other.")
                            .addContent(option.getter().elementName())
                            .addContent("())");
                }

                if (equalIterator.hasNext()) {
                    method.addContentLine("")
                            .addContent("&& ");
                }
            }
        }
        method.addContentLine(";");
        classBuilder.addMethod(method);
    }

    private static void hashCodeMethod(InnerClass.Builder classBuilder,
                                       boolean hasSuper,
                                       List<OptionHandler> equalityFields) {
        Method.Builder method = Method.builder()
                .name("hashCode")
                .returnType(TypeName.create(int.class))
                .addAnnotation(Annotations.OVERRIDE);

        if (equalityFields.isEmpty()) {
            // no fields on this type
            if (hasSuper) {
                method.addContentLine("return super.hashCode();");
            } else {
                // hashcode is a constant, as there are no fields and no super type
                method.addContentLine("return 1;");
            }
        } else {
            if (hasSuper) {
                method.addContent("return 31 * super.hashCode() + ")
                        .addContent(Objects.class)
                        .addContent(".hash(");
            } else {
                method.addContent("return ")
                        .addContent(Objects.class)
                        .addContent(".hash(");
            }

            method.addContent(equalityFields.stream()
                                      .filter(it -> !(
                                              it.option().declaredType().isOptional()
                                                      && it.typeHandler().type().equals(Types.CHAR_ARRAY)))
                                      .map(OptionHandler::option)
                                      .map(OptionInfo::name)
                                      .collect(Collectors.joining(", ")))
                    .addContent(")");

            for (OptionHandler field : equalityFields) {
                if (field.option().declaredType().isOptional()
                        && field.typeHandler().type().equals(Types.CHAR_ARRAY)) {
                    // Optional<char[]>
                    method.addContent(" + 31 * ")
                            .addContent(Types.GENERATED_EQUALITY_UTIL)
                            .addContent(".optionalCharArrayHash(")
                            .addContent(field.option().name())
                            .addContent(")");
                }
            }
            method.addContent(";");
        }

        classBuilder.addMethod(method);
    }

    private static void toString(InnerClass.Builder classBuilder,
                                 PrototypeInfo prototypeInfo,
                                 String typeName,
                                 boolean hasSuper,
                                 List<OptionHandler> options,
                                 boolean isBuilder) {
        if (!isBuilder && prototypeInfo.prototypeMethods()
                .stream()
                .map(GeneratedMethod::methodDefinition)
                .filter(it -> "toString".equals(it.elementName()))
                .filter(it -> it.typeName().equals(TypeNames.STRING))
                .anyMatch(it -> it.parameterArguments().isEmpty())) {
            // do not create toString() if defined as a custom method
            return;
        }
        // only create to string if not part of prototype methods
        Method.Builder method = Method.builder()
                .name("toString")
                .returnType(TypeName.create(String.class))
                .addAnnotation(Annotations.OVERRIDE)
                .addContent("return \"" + typeName);

        List<OptionHandler> toStringFields = options.stream()
                .filter(it -> it.option().includeInToString())
                .toList();

        if (toStringFields.isEmpty()) {
            method.addContentLine("{};\"");
        } else {
            method.addContentLine("{\"")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContentLine(toStringFields.stream()
                                            .map(it -> GenerateAbstractBuilder.toStringBody(it, isBuilder))
                                            .collect(Collectors.joining(" + \",\"\n")));
            if (hasSuper) {
                method.addContentLine("+ \"};\"");
            } else {
                method.addContent("+ \"}\"");
            }
        }
        if (hasSuper) {
            method.addContent("+ super.toString()");
        }
        method.addContentLine(";");
        classBuilder.addMethod(method);
    }

    private static String toStringBody(OptionHandler it, boolean isBuilder) {
        var option = it.option();
        var typeName = it.typeHandler().type();

        boolean secret = option.confidential() || it.typeHandler().type()
                .equals(Types.CHAR_ARRAY);

        String name = option.name();
        if (secret) {
            if (typeName.primitive() && !typeName.array()) {
                return "+ \"" + name + "=****\"";
            }
            // builder stores fields without optional wrapper
            if (!isBuilder && typeName.genericTypeName().equals(OPTIONAL)) {
                return "+ \"" + name + "=\" + (" + name + ".isPresent() ? \"****\" : "
                        + "\"null\")";
            }
            return "+ \"" + name + "=\" + (" + name + " == null ? \"null\" : "
                    + "\"****\")";
        }
        return "+ \"" + name + "=\" + " + name;
    }

    private static void implMethods(InnerClass.Builder classBuilder,
                                    List<OptionHandler> options) {
        // then getters
        for (OptionHandler optionHandler : options) {
            OptionInfo option = optionHandler.option();
            String fieldName = option.name();
            String getterName = option.getter().elementName();

            classBuilder.addMethod(method -> method.name(getterName)
                    .returnType(option.declaredType())
                    .addAnnotation(Annotations.OVERRIDE)
                    .addContentLine("return " + fieldName + ";"));
        }
    }

    private static void implAssignToFields(Constructor.Builder constructor,
                                           List<OptionHandler> options) {
        for (OptionHandler optionHandler : options) {
            OptionInfo option = optionHandler.option();
            String getterName = option.getter().elementName();

            constructor.addContent("this." + option.name() + " = ");
            TypeName declaredType = option.declaredType();

            if (declaredType.isOptional() || declaredType.equals(OPTIONAL_COMMON_CONFIG)) {
                constructor.addContent("builder." + getterName + "().map(")
                        .addContent(Function.class)
                        .addContentLine(".identity());");
            } else if (declaredType.isList()) {
                constructor.addContent(List.class)
                        .addContentLine(".copyOf(builder." + getterName + "());");
            } else if (declaredType.isSet()) {
                constructor.addContent(Collections.class)
                        .addContent(".unmodifiableSet(new ")
                        .addContent(LinkedHashSet.class)
                        .addContentLine("<>(builder." + getterName + "()));");
            } else if (declaredType.isMap()) {
                constructor.addContent(Collections.class)
                        .addContent(".unmodifiableMap(new ")
                        .addContent(LinkedHashMap.class)
                        .addContentLine("<>(builder." + getterName + "()));");
            } else {
                if (optionHandler.typeHandler().builderGetterOptional() && !declaredType.isOptional()) {
                    // builder getter optional, but type not, we call get (must be present - is validated)
                    constructor.addContentLine("builder." + getterName + "().get();");
                } else {
                    // optional and other types are just plainly assigned
                    constructor.addContentLine("builder." + getterName + "();");
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
