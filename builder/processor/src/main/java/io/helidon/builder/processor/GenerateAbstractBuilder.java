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

import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;

import static io.helidon.builder.processor.Types.CHAR_ARRAY_TYPE;
import static io.helidon.builder.processor.Types.CONFIG_TYPE;
import static io.helidon.builder.processor.Types.OVERRIDE;
import static io.helidon.builder.processor.Types.PROTOTYPE_BUILDER;
import static io.helidon.builder.processor.Types.PROTOTYPE_CONFIGURED_BUILDER;
import static io.helidon.builder.processor.Types.STRING_TYPE;
import static io.helidon.common.processor.GeneratorTools.capitalize;
import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.MAP;
import static io.helidon.common.types.TypeNames.OPTIONAL;
import static io.helidon.common.types.TypeNames.SET;

final class GenerateAbstractBuilder {
    private static final String SOURCE_SPACING = "    ";

    private GenerateAbstractBuilder() {
    }

    @SuppressWarnings("checkstyle:MethodLength") // will be fixed when we switch to model
    static void generate(PrintWriter pw,
                         TypeName prototype,
                         TypeName runtimeType,
                         String typeArguments,
                         TypeContext typeContext) {

        Optional<TypeName> superType = typeContext.typeInfo()
                .superPrototype();
        String prototypeWithTypes = prototype.className() + typeArguments;
        String typeArgumentNames = "";
        if (!typeArguments.isEmpty()) {
            typeArgumentNames = typeArguments.substring(1, typeArguments.length() - 1) + ", ";
        }

        // type declaration
        pw.print(SOURCE_SPACING);
        pw.println("/**");
        pw.print(SOURCE_SPACING);
        pw.print(" * Fluent API builder base for {@link ");
        pw.print(runtimeType.className());
        pw.println("}.");
        pw.print(SOURCE_SPACING);
        pw.println(" *");
        pw.print(SOURCE_SPACING);
        pw.println(" * @param <BUILDER> type of the builder extending this abstract builder");
        pw.print(SOURCE_SPACING);
        pw.println(" * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}");
        pw.print(SOURCE_SPACING);
        pw.println(" */");
        pw.print(SOURCE_SPACING);
        pw.print("abstract class BuilderBase<");
        pw.print(typeArgumentNames);
        pw.print("BUILDER extends BuilderBase<");
        pw.print(typeArgumentNames);
        pw.print("BUILDER, PROTOTYPE>");
        pw.print(", PROTOTYPE extends ");
        pw.print(prototypeWithTypes);
        pw.println(">");
        superType
                .ifPresent(type -> {
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print("extends ");
                    pw.print(type.fqName());
                    pw.println(".BuilderBase<BUILDER, PROTOTYPE>");
                });
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print("implements ");
        if (typeContext.configuredData().configured() || hasConfig(typeContext.propertyData().properties())) {
            pw.print(PROTOTYPE_CONFIGURED_BUILDER);
        } else {
            pw.print(PROTOTYPE_BUILDER);
        }
        pw.println("<BUILDER, PROTOTYPE> {");

        // builder fields
        fields(pw, typeContext, true);

        // builder constructor
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("/**");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" * Protected to support extensibility.");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" *");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" */");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("protected BuilderBase() {");

        // overriding defaults
        for (var prop : typeContext.propertyData().overridingProperties()) {
            if (prop.configuredOption().hasDefault()) {
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.print(prop.setterName());
                pw.print("(");
                pw.print(prop.configuredOption().defaultValue());
                pw.println(");");
            }
        }

        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");
        pw.println();

        // method "from(prototype)"
        fromInstanceMethod(pw, typeContext, prototypeWithTypes);
        fromBuilderMethod(pw, typeContext, typeArgumentNames);

        // method preBuildPrototype() - handles providers, decorator
        preBuildPrototypeMethod(pw, typeContext);
        validatePrototypeMethod(pw, typeContext);

        CustomMethods customMethods = typeContext.customMethods();

        for (CustomMethods.CustomMethod customMethod : customMethods.builderMethods()) {
            // builder specific custom methods (not part of interface)
            CustomMethods.Method generated = customMethod.generatedMethod().method();
            // public Builder type(Type) with implementation
            if (!generated.javadoc().isEmpty()) {
                Javadoc parsedDoc = Javadoc.parse(generated.javadoc())
                        .removeFirstParam()
                        .updateReturns("updated builder instance");
                List<String> docLines = parsedDoc.toLines();
                pw.print(SOURCE_SPACING);
                pw.println("/**");
                for (String docLine : docLines) {
                    pw.print(SOURCE_SPACING);
                    pw.print(" *");
                    pw.println(docLine);
                }
                pw.print(SOURCE_SPACING);
                pw.println(" */");
            }
            for (String annotation : customMethod.generatedMethod().annotations()) {
                pw.print(SOURCE_SPACING);
                pw.print('@');
                pw.println(annotation);
            }
            pw.print(SOURCE_SPACING);
            pw.print("public BUILDER ");
            pw.print(generated.name());
            pw.print("(");
            pw.print(generated.arguments()
                             .stream()
                             .map(it -> it.typeName().fqName() + " " + it.name())
                             .collect(Collectors.joining(", ")));
            pw.println(") {");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(customMethod.generatedMethod().callCode());
            pw.println(";");
            pw.print(SOURCE_SPACING);
            pw.println("}");
            pw.println();
        }

        // setters and getters of builder
        builderMethods(pw, typeContext);

        toString(pw,
                 typeContext,
                 prototype.className() + "Builder",
                 superType.isPresent(),
                 typeContext.customMethods().prototypeMethods(),
                 true);

        // before the builder class is finished, we also generate a protected implementation
        generatePrototypeImpl(pw, typeContext, typeArgumentNames);

        // end of BuilderBase class
        pw.print(SOURCE_SPACING);
        pw.println("}");
    }

    static void buildRuntimeObjectMethod(PrintWriter pw, TypeContext typeContext, boolean isBuilder) {
        TypeContext.TypeInformation typeInformation = typeContext.typeInfo();
        boolean hasRuntimeObject = typeInformation.runtimeObject().isPresent();
        TypeName builtObject = typeInformation.runtimeObject().orElse(typeInformation.prototype());

        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("@Override");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print("public ");
        pw.print(builtObject.fqName());
        pw.println(" build() {");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print("return ");
        if (hasRuntimeObject) {
            pw.print(builtObject.genericTypeName().fqName());
            if (isBuilder) {
                pw.println(".create(this.buildPrototype());");
            } else {
                pw.println(".create(this);");
            }
        } else {
            if (isBuilder) {
                pw.println("return build();");
            } else {
                pw.println("return this;");
            }
        }
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");
        pw.println();

        // if impl, we also need to add the `get()` method from supplier
        if (!isBuilder) {
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("@Override");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print("public ");
            pw.print(builtObject.fqName());
            pw.println(" get() {");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("return build();");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("}");
            pw.println();
        }
    }

    static boolean hasConfig(List<PrototypeProperty> properties) {
        return properties.stream()
                .filter(it -> "config".equals(it.name()))
                .anyMatch(it -> CONFIG_TYPE.equals(it.typeHandler().actualType()));
    }

    @SuppressWarnings("checkstyle:MethodLength") // will be fixed when we switch to model
    private static void builderMethods(PrintWriter pw, TypeContext typeContext) {
        List<PrototypeProperty> properties = typeContext.propertyData().properties();
        TypeContext.ConfiguredData configured = typeContext.configuredData();

        if (configured.configured() || hasConfig(properties)) {
            /*
            public BUILDER config(Config config) {
                this.config = config;
                config.get("server").as(String.class).ifPresent(this::server);
                return self();
            }
             */
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("/**");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            if (configured.configured()) {
                pw.println(" * Update builder from configuration (node of this type).");
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.println(" * If a value is present in configuration, it would override currently configured values.");
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
            } else {
                pw.println(" * Config to use.");
            }
            pw.println(" *");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println(" * @param config configuration instance used to obtain values to update this builder");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println(" * @return updated builder instance");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println(" */");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("@Override");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print("public BUILDER");
            pw.println(" config(Config config) {");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("Objects.requireNonNull(config);");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("this.config = config;");

            if (typeContext.typeInfo().superPrototype().isPresent()) {
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.println("super.config(config);");
            }

            if (configured.configured()) {
                for (PrototypeProperty child : properties) {
                    if (!child.configuredOption().notConfigured()) {
                        Optional<String> fromConfig = child.typeHandler().generateFromConfig(child.configuredOption(),
                                                                                             child.factoryMethods());
                        if (fromConfig.isPresent()) {
                            pw.print(SOURCE_SPACING);
                            pw.print(SOURCE_SPACING);
                            pw.print(SOURCE_SPACING);
                            pw.println(fromConfig.get());
                        }
                    }
                }
            }
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("return self();");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("}");
        }

        TypeName returnType = TypeName.createFromGenericDeclaration("BUILDER");
        // first setters
        for (PrototypeProperty child : properties) {
            for (GeneratedMethod setter : child.setters(returnType, child.configuredOption().description())) {
                // this is builder setters
                Javadoc javadoc = setter.javadoc();
                if (javadoc != null) {
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.println("/**");
                    for (String line : javadoc.toLines()) {
                        pw.print(SOURCE_SPACING);
                        pw.print(SOURCE_SPACING);
                        pw.print(" * ");
                        pw.println(line);
                    }
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.println(" */");
                }
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.print(String.join(" ", setter.modifiers()));
                if (!setter.modifiers().isEmpty()) {
                    pw.print(" ");
                }
                if (setter.genericDeclaration() != null) {
                    pw.print(setter.genericDeclaration());
                    pw.print(" ");
                }
                pw.print(setter.returnType().fqName());
                pw.print(" ");
                pw.print(setter.name());
                pw.print("(");
                pw.print(setter.arguments()
                                 .stream()
                                 .map(it -> it.typeName().fqName() + " " + it.name())
                                 .collect(Collectors.joining(", ")));
                pw.println(") {");
                for (String methodLine : setter.methodLines()) {
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.println(methodLine);
                }

                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.println("}");
            }
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
            /*
            String host() {
              return host;
            }
             */
            if (child.configuredOption().description() != null) {
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.println("/**");
                for (String line : child.configuredOption().description().lines()) {
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print(" * ");
                    pw.println(line);
                }
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.print(" * @return the ");
                pw.println(toHumanReadable(child.name()));
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.println(" */");
            }
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print("public ");
            pw.print(child.builderGetterType().fqName());
            pw.print(" ");
            pw.print(getterName);
            pw.println("() {");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print("return ");
            pw.print(child.builderGetter());
            pw.println(";");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("}");
        }

        if (configured.configured()) {
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("/**");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println(" * If this instance was configured, this would be the config instance used.");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println(" *");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println(" * @return config node used to configure this builder, or empty if not configured");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println(" */");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("public Optional<Config> config() {");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("return Optional.ofNullable(config);");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("}");
        }
    }

    private static void fromInstanceMethod(PrintWriter pw, TypeContext typeContext, String prototypeWithTypes) {
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("/**");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" * Update this builder from an existing prototype instance.");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" *");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" * @param prototype existing prototype to update this builder from");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" * @return updated builder instance");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" */");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print("public BUILDER from(");
        pw.print(prototypeWithTypes);
        pw.println(" prototype) {");
        typeContext.typeInfo()
                .superPrototype()
                .ifPresent(it -> {
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.println("super.from(prototype);");
                });
        for (PrototypeProperty property : typeContext.propertyData().properties()) {
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);

            TypeName declaredType = property.typeHandler().declaredType();

            if (declaredType.isSet() || declaredType.isList() || declaredType.isMap()) {
                pw.print("add");
                pw.print(capitalize(property.name()));
                pw.print("(prototype.");
                pw.print(property.typeHandler().getterName());
                pw.println("());");
            } else {
                pw.print(property.typeHandler().setterName());
                pw.print("(prototype.");
                pw.print(property.typeHandler().getterName());
                pw.println("());");
            }
        }
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("return self();");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");
    }

    private static void fromBuilderMethod(PrintWriter pw, TypeContext typeContext, String typeArgumentNames) {
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("/**");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" * Update this builder from an existing prototype builder instance.");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" *");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" * @param builder existing builder prototype to update this builder from");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" * @return updated builder instance");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" */");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print("public BUILDER from(");
        pw.print(typeContext.typeInfo().prototype().className());
        pw.print(".BuilderBase<");
        pw.print(typeArgumentNames);
        pw.println("?, ?> builder) {");
        typeContext.typeInfo()
                .superPrototype()
                .ifPresent(it -> {
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.println("super.from(builder);");
                });
        for (PrototypeProperty property : typeContext.propertyData().properties()) {
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            TypeName declaredType = property.typeHandler().declaredType();

            if (property.builderGetterOptional()) {
                // property that is either mandatory or internally nullable
                pw.print("builder.");
                pw.print(property.typeHandler().getterName());
                pw.print("().ifPresent(this::");
                pw.print(property.typeHandler().setterName());
                pw.println(");");
            } else {
                if (declaredType.isSet() || declaredType.isList() || declaredType.isMap()) {
                    pw.print("add");
                    pw.print(capitalize(property.name()));
                } else {
                    pw.print(property.typeHandler().setterName());
                }
                pw.print("(builder.");
                pw.print(property.typeHandler().getterName());
                pw.println("());");
            }
        }
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("return self();");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");
    }

    private static void fields(PrintWriter pw, TypeContext typeContext, boolean isBuilder) {
        String spacing = SOURCE_SPACING + SOURCE_SPACING;
        if (!isBuilder) {
            spacing += SOURCE_SPACING;
        }

        boolean hasFields = !typeContext.propertyData().properties().isEmpty();
        if (isBuilder && (typeContext.configuredData().configured() || hasConfig(typeContext.propertyData().properties()))) {
            hasFields = true;
            pw.print(spacing);
            pw.println("private Config config;");
        }
        for (PrototypeProperty child : typeContext.propertyData().properties()) {
            if (!isBuilder || !child.typeHandler().actualType().equals(CONFIG_TYPE)) {
                pw.print(spacing);
                pw.print(child.fieldDeclaration(isBuilder));
                pw.println(";");
            }
            if (isBuilder && child.configuredOption().provider()) {
                pw.print(spacing);
                pw.print("private boolean ");
                pw.print(child.name());
                pw.print("DiscoverServices = ");
                pw.print(child.configuredOption().providerOption().defaultDiscoverServices());
                pw.println(";");
            }
        }
        if (hasFields) {
            pw.println();
        }
    }

    private static void preBuildPrototypeMethod(PrintWriter pw,
                                                TypeContext typeContext) {
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("/**");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" * Handles providers and decorators.");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" */");
        if (typeContext.propertyData().hasProvider()) {
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("@SuppressWarnings(\"unchecked\")");
        }
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("protected void preBuildPrototype() {");
        typeContext.typeInfo()
                .superPrototype()
                .ifPresent(it -> {
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.println("super.preBuildPrototype();");
                });
        if (typeContext.propertyData().hasProvider()) {
            boolean configured = typeContext.configuredData().configured();
            if (configured) {
                // need to have a non-null config instance
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.println("this.config = config == null ? Config.empty() : config;");
            }
            for (PrototypeProperty property : typeContext.propertyData().properties()) {
                PrototypeProperty.ConfiguredOption configuredOption = property.configuredOption();
                if (configuredOption.provider()) {
                    PrototypeProperty.ProviderOption providerOption = configuredOption.providerOption();
                    boolean defaultDiscoverServices = providerOption.defaultDiscoverServices();

                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    // using a code block, so we can reuse the same variable names for multiple providers
                    pw.println("{");
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print("var serviceLoader = io.helidon.common.HelidonServiceLoader.create(java.util.ServiceLoader.load(");
                    pw.print(providerOption.serviceProviderInterface().fqName());
                    pw.println(".class));");
                    if (configured) {
                        pw.print(SOURCE_SPACING);
                        pw.print(SOURCE_SPACING);
                        pw.print(SOURCE_SPACING);
                        pw.print(SOURCE_SPACING);
                        pw.print("java.util.List<");
                        pw.print(property.typeHandler().actualType().fqName());
                        pw.print("> services = discoverServices(config.get(\"");
                        pw.print(configuredOption.configKey());
                        pw.print("\"), serviceLoader, ");
                        pw.print(providerOption.serviceProviderInterface().fqName());
                        pw.print(".class, ");
                        pw.print(property.typeHandler().actualType().fqName());
                        pw.print(".class, ");
                        pw.print(property.name());
                        pw.print("DiscoverServices");
                        pw.println(");");
                        pw.print(SOURCE_SPACING);
                        pw.print(SOURCE_SPACING);
                        pw.print(SOURCE_SPACING);
                        pw.print(SOURCE_SPACING);
                        pw.print("this.add");
                        pw.print(capitalize(property.name()));
                        pw.println("(services);");
                    } else {
                        if (defaultDiscoverServices) {
                            pw.print(SOURCE_SPACING);
                            pw.print(SOURCE_SPACING);
                            pw.print(SOURCE_SPACING);
                            pw.print(SOURCE_SPACING);
                            pw.print(SOURCE_SPACING);
                            pw.print(SOURCE_SPACING);
                            pw.print(SOURCE_SPACING);
                            pw.print(SOURCE_SPACING);
                            pw.print(SOURCE_SPACING);
                            pw.print("this.");
                            pw.print(property.name());
                            pw.println("(serviceLoader.asList());");
                        }
                    }
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.println("}");
                }
            }
        }
        if (typeContext.typeInfo().decorator().isPresent()) {
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print("new ");
            pw.print(typeContext.typeInfo().decorator().get().fqName());
            pw.println("().decorate(this);");
            pw.println();
        }
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");
    }

    private static void validatePrototypeMethod(PrintWriter pw, TypeContext typeContext) {
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("/**");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" * Validates required properties.");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" */");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("protected void validatePrototype() {");
        typeContext.typeInfo()
                .superPrototype()
                .ifPresent(it -> {
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.println("super.validatePrototype();");
                });
        if (typeContext.propertyData().hasRequired() || typeContext.propertyData().hasNonNulls()) {
            requiredValidation(pw, typeContext);
        }
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");
    }

    private static void requiredValidation(PrintWriter pw, TypeContext typeContext) {
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("Errors.Collector collector = Errors.collector();");
        for (PrototypeProperty property : typeContext.propertyData().properties()) {
            if (property.configuredOption().validateNotNull() && !property.configuredOption().hasDefault()) {
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.print("if (");
                pw.print(property.typeHandler().name());
                pw.println(" == null) {");
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.print("collector.fatal(getClass(), ");
                pw.print("\"Property \\\"");
                String configKey = property.configuredOption().configKey();
                pw.print(configKey == null ? property.typeHandler().name() : configKey);
                if (property.configuredOption().required()) {
                    pw.println("\\\" is required, but not set\");");
                } else {
                    pw.println("\\\" must not be null, but not set\");");
                }
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.println("}");
            }
        }
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("collector.collect().checkValid();");
    }

    private static void generatePrototypeImpl(PrintWriter pw, TypeContext typeContext, String typeArgumentNames) {
        Optional<TypeName> superPrototype = typeContext.typeInfo()
                .superPrototype();
        TypeName prototype = typeContext.typeInfo().prototype();
        TypeName prototypeImpl = typeContext.typeInfo().prototypeImpl();

        String ifaceName = prototype.className();
        String implName = prototypeImpl.className();

        String typeArgs = typeContext.blueprintData().typeArguments();
        // inner class of builder base
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("/**");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" * Generated implementation of the prototype, can be extended by descendant prototype implementations.");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" */");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print("protected static class ");
        pw.print(implName);
        pw.print(typeArgs);
        superPrototype.ifPresent(it -> {
            pw.print(" extends ");
            pw.print(it.className());
            pw.print("Impl");
        });
        pw.print(" implements ");
        pw.print(ifaceName);
        pw.print(typeArgs);
        if (typeContext.blueprintData().isFactory()) {
            pw.print(", java.util.function.Supplier<");
            pw.print(typeContext.typeInfo().runtimeObject().orElse(typeContext.typeInfo().prototype()).fqName());
            pw.print(">");
        }
        pw.println("{");
        /*
        Fields
         */
        fields(pw, typeContext, false);
        /*
        Constructor
         */
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("/**");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" * Create an instance providing a builder.");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" * @param builder extending builder base of this prototype");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println(" */");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print("protected ");
        pw.print(implName);
        pw.print("(");
        pw.print(ifaceName);
        pw.print(".BuilderBase<");
        pw.print(typeArgumentNames);
        pw.print("?, ?>");
        pw.println(" builder) {");
        superPrototype.ifPresent(it -> {
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("super(builder);");
        });
        implAssignToFields(pw, typeContext);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");
        /*
        RuntimeType build()
         */
        if (typeContext.blueprintData().isFactory()) {
            buildRuntimeObjectMethod(pw, typeContext, false);
        }
        /*
        Custom prototype methods
         */
        for (CustomMethods.CustomMethod customMethod : typeContext.customMethods().prototypeMethods()) {
            // builder - custom implementation methods for new prototype interface methods
            CustomMethods.Method generated = customMethod.generatedMethod().method();
            // public TypeName boxed() - with implementation
            // no javadoc on impl, it is package local anyway
            for (String annotation : customMethod.generatedMethod().annotations()) {
                pw.print(SOURCE_SPACING);
                pw.print('@');
                pw.println(annotation);
            }
            if (!customMethod.generatedMethod().annotations().contains(OVERRIDE)) {
                pw.print(SOURCE_SPACING);
                pw.println("@Override");
            }
            pw.print(SOURCE_SPACING);
            pw.print("public ");
            pw.print(generated.returnType().fqName());
            pw.print(" ");
            pw.print(generated.name());
            pw.print("(");
            pw.print(generated.arguments()
                             .stream()
                             .map(it -> it.typeName().fqName() + " " + it.name())
                             .collect(Collectors.joining(", ")));
            pw.println(") {");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(customMethod.generatedMethod().callCode());
            pw.println(";");
            pw.print(SOURCE_SPACING);
            pw.println("}");
            pw.println();
        }
        /*
        Implementation methods of prototype interface
         */
        implMethods(pw, typeContext);
        /*
        To string
         */
        toString(pw, typeContext, ifaceName, superPrototype.isPresent(), typeContext.customMethods().prototypeMethods(), false);
        /*
        Hash code and equals
         */
        hashCodeAndEquals(pw, typeContext, ifaceName, superPrototype.isPresent());

        // end of impl
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");
    }

    private static void hashCodeAndEquals(PrintWriter pw, TypeContext typeContext, String ifaceName, boolean hasSuper) {
        List<PrototypeProperty> equalityFields = typeContext.propertyData()
                .properties()
                .stream()
                .filter(PrototypeProperty::equality)
                .toList();

        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("@Override");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("public boolean equals(Object o) {");
        // same instance
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("if (o == this) {");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("return true;");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");

        // same type
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print("if (!(o instanceof ");
        pw.print(ifaceName);
        pw.println(" other)) {");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("return false;");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");

        // compare fields
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print("return ");
        if (hasSuper) {
            pw.print("super.equals(other)");
            if (!equalityFields.isEmpty()) {
                pw.print(" && ");
            }
        }
        if (!hasSuper && equalityFields.isEmpty()) {
            pw.print("true");
        } else {
            pw.print(equalityFields.stream()
                             .map(field -> {
                                 if (field.typeName().array()) {
                                     return "java.util.Arrays.equals(" + field.name() + ", other." + field.getterName() + "())";
                                 }
                                 if (field.typeName().primitive()) {
                                     return field.name() + " == other." + field.getterName() + "()";
                                 }

                                 return "Objects.equals(" + field.name() + ", other." + field.getterName() + "())";
                             })
                             .collect(Collectors.joining(" && ")));
        }
        pw.println(";");

        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");

        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("@Override");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("public int hashCode() {");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        if (equalityFields.isEmpty()) {
            // no fields on this type
            if (hasSuper) {
                pw.println("return super.hashCode();");
            } else {
                // hashcode is a constant, as there are no fields and no super type
                pw.println("return 1;");
            }
        } else {
            if (hasSuper) {
                pw.print("return 31 * super.hashCode() + Objects.hash(");
            } else {
                pw.print("return Objects.hash(");
            }

            pw.print(equalityFields.stream()
                             .map(PrototypeProperty::name)
                             .collect(Collectors.joining(", ")));
            pw.println(");");
        }

        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");
    }

    private static void toString(PrintWriter pw,
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
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("@Override");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("public String toString() {");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print("return \"");
        pw.print(typeName);

        List<PrototypeProperty> toStringFields = typeContext.propertyData()
                .properties()
                .stream()
                .filter(PrototypeProperty::toStringValue)
                .toList();

        String prefix = SOURCE_SPACING.repeat(6);
        if (toStringFields.isEmpty()) {
            pw.println("{};\"");
        } else {
            pw.println("{\"");
            pw.println(toStringFields.stream()
                               .map(it -> {
                                   boolean secret = it.confidential() || it.typeHandler().actualType().equals(CHAR_ARRAY_TYPE);

                                   String name = it.name();
                                   if (secret) {
                                       if (it.typeName().primitive() && !it.typeName().array()) {
                                           return prefix + " + \"" + name + "=****\"";
                                       }
                                       // builder stores fields without optional wrapper
                                       if (!isBuilder && it.typeName().genericTypeName().equals(OPTIONAL)) {
                                           return prefix + " + \"" + name + "=\" + (" + name + ".isPresent() ? \"****\" : "
                                                   + "\"null\")";
                                       }
                                       return prefix + " + \"" + name + "=\" + (" + name + " == null ? \"null\" : \"****\")";
                                   }
                                   return prefix + " + \"" + name + "=\" + " + name;

                               })
                               .collect(Collectors.joining(" + \",\" \n")));

            pw.print(prefix);
            if (hasSuper) {
                pw.print("+ \"};\"");
            } else {
                pw.print("+ \"}\"");
            }
        }
        if (hasSuper) {
            pw.println();
            pw.print(prefix);
            pw.print(" + super.toString()");
        }
        pw.println(";");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");

    }

    private static void implMethods(PrintWriter pw, TypeContext typeContext) {
        // then getters
        for (PrototypeProperty child : typeContext.propertyData().properties()) {
            String fieldName = child.name();
            String getterName = child.getterName();

            pw.println();
            pw.print(SOURCE_SPACING);
            pw.println("@Override");
            pw.print(SOURCE_SPACING);
            pw.print("public ");
            pw.print(child.typeHandler().declaredType().fqName());
            pw.print(" ");
            pw.print(getterName);
            pw.println("() {");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print("return ");
            pw.print(fieldName);
            pw.println(";");
            pw.print(SOURCE_SPACING);
            pw.println("}");
        }
    }

    private static void implAssignToFields(PrintWriter pw, TypeContext typeContext) {
        for (PrototypeProperty child : typeContext.propertyData().properties()) {
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print("this.");
            pw.print(child.name());
            pw.print(" = ");
            TypeName declaredType = child.typeHandler().declaredType();
            if (declaredType.genericTypeName().equals(LIST)) {
                pw.print("java.util.List.copyOf(builder.");
                pw.print(child.getterName());
                pw.println("());");
            } else if (declaredType.genericTypeName().equals(SET)) {
                pw.print("java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(builder.");
                pw.print(child.getterName());
                pw.println("()));");
            } else if (declaredType.genericTypeName().equals(MAP)) {
                pw.print("java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(builder.");
                pw.print(child.getterName());
                pw.println("()));");
            } else {
                if (child.builderGetterOptional() && !declaredType.isOptional()) {
                    // builder getter optional, but type not, we call get (must be present - is validated)
                    pw.print(" builder.");
                    pw.print(child.getterName());
                    pw.println("().get();");
                } else {
                    // optional and other types are just plainly assigned
                    pw.print(" builder.");
                    pw.print(child.getterName());
                    pw.println("();");
                }
            }
        }
    }

    private static String toHumanReadable(String name) {
        StringBuilder result = new StringBuilder();

        char[] nameChars = name.toCharArray();
        for (char nameChar : nameChars) {
            if (Character.isUpperCase(nameChar)) {
                if (result.length() != 0) {
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
