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

package io.helidon.service.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.common.types.Annotations.OVERRIDE;
import static io.helidon.service.codegen.ConfigBeanAnnotation.CONFIG_BEAN_TYPE;
import static io.helidon.service.codegen.ServiceCodegenTypes.CONFIG_COMMON_CONFIG;
import static io.helidon.service.codegen.ServiceCodegenTypes.CONFIG_EXCEPTION;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_INJECT;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_NAMED;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_NAMED_BY_CLASS;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_SINGLETON;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_QUALIFIER;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_SERVICE_INSTANCE;
import static io.helidon.service.codegen.ServiceCodegenTypes.INTERCEPTION_FACTORY;
import static io.helidon.service.codegen.ServiceCodegenTypes.QUALIFIED_INSTANCE;
import static io.helidon.service.codegen.ServiceCodegenTypes.WEIGHT;

class ConfigBeanCodegen implements RegistryCodegenExtension {
    private static final Annotation SINGLETON_ANNOTATION = Annotation.create(INJECTION_SINGLETON);
    private static final Annotation INJECT_ANNOTATION = Annotation.create(INJECTION_INJECT);
    private static final Annotation CONFIG_BEAN_ANNOTATION = Annotation.create(CONFIG_BEAN_TYPE);
    private static final Annotation WILDCARD_NAME_ANNOTATION = Annotation.create(INJECTION_NAMED, "*");

    private final RegistryCodegenContext ctx;
    private final InterceptionSupport interceptionSupport;

    ConfigBeanCodegen(RegistryCodegenContext ctx) {
        this.ctx = ctx;
        this.interceptionSupport = InterceptionSupport.create(ctx);
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        // for each @ConfigDriven.ConfigBean
        // generate a singleton ServicesProvider that uses injected config to create instances
        for (TypeInfo typeInfo : roundContext.annotatedTypes(CONFIG_BEAN_TYPE)) {
            if (typeInfo.hasAnnotation(INJECTION_SINGLETON)) {
                // this type has already been generated, ignore it (this is a follow-up annotation round for a generated type)
                continue;
            }
            ConfigBean cb = ConfigBean.create(typeInfo);

            if (cb.configBeanBuilderType().isEmpty()) {
                // not a blueprint, create a single class that directly produces the target type
                createSimpleConfigBean(typeInfo, cb);
            } else {
                // if this is a blueprint thing, we create two classes - one to produce builder, one to produce prototype
                createBlueprintConfigBean(typeInfo, cb);
            }
        }
    }

    private void createBlueprintConfigBean(TypeInfo typeInfo, ConfigBean cb) {
        TypeName builderType = cb.configBeanBuilderType().get();
        TypeName annotatedType = typeInfo.typeName();
        TypeName generatedTypeBuilder = cb.generatedTypeBuilder();
        TypeName servicesProviderTypeBuilder = servicesProviderType(builderType);
        var annotation = cb.annotation();

        if (annotation.orDefault() && !annotation.repeatable()) {
            // this is a case where we create exactly one instance - no need for fancy types
            createBlueprintSingleton(typeInfo,
                                     cb,
                                     builderType,
                                     annotatedType,
                                     generatedTypeBuilder);
            return;
        }

        // first generate the builder provider
        ClassModel.Builder classModel = configBeanClass(generatedTypeBuilder, servicesProviderTypeBuilder)
                .addAnnotation(CONFIG_BEAN_ANNOTATION)
                .update(this::addSmallWeight)
                .update(this::addConfigField)
                .addConstructor(ctr -> ctr
                        .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                        .addAnnotation(INJECT_ANNOTATION)
                        .update(this::configConstructor))
                .addMethod(servicesMethod -> servicesMethod
                        .accessModifier(AccessModifier.PUBLIC)
                        .addAnnotation(OVERRIDE)
                        .name("services")
                        .returnType(qualifiedInstanceList(builderType))
                        .addContent("var config = this.config.get(\"")
                        .addContent(cb.annotation().configKey())
                        .addContentLine("\");")
                        .addContentLine("")
                        .update(it -> generateServicesMethodBuilder(it, cb, builderType)));

        ctx.addType(generatedTypeBuilder, classModel, annotatedType, typeInfo.originatingElementValue());

        // and now the class that produces the instances
        TypeName generatedType = cb.generatedType();
        TypeName servicesProviderType = servicesProviderType(cb.configBeanType());
        boolean intercepted = interceptionSupport.intercepted(typeInfo);
        TypeName descriptorType = ctx.descriptorType(generatedType);

        TypeName factoryType;
        if (intercepted) {
            interceptionSupport.generateDelegateInterception(typeInfo, cb.configBeanType());
            factoryType = factoryType(cb.configBeanType());
        } else {
            factoryType = null;
        }
        classModel = configBeanClass(cb.generatedType(), servicesProviderType)
                .addAnnotation(CONFIG_BEAN_ANNOTATION)
                .addField(buildersField -> buildersField
                        .accessModifier(AccessModifier.PRIVATE)
                        .isFinal(true)
                        .name("builders")
                        .type(serviceInstanceList(builderType)))
                .update(it -> addFactoryField(it, intercepted, factoryType))
                .addConstructor(ctr -> ctr
                        .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                        .addAnnotation(INJECT_ANNOTATION)
                        .addParameter(buildersParam -> buildersParam
                                .name("builders")
                                .addAnnotation(WILDCARD_NAME_ANNOTATION)
                                .type(serviceInstanceList(builderType)))
                        .addContentLine("this.builders = builders;")
                        .update(it -> addFactoryConstructor(it, intercepted, factoryType, cb.configBeanType())))
                .addMethod(servicesMethod -> servicesMethod
                        .accessModifier(AccessModifier.PUBLIC)
                        .addAnnotation(OVERRIDE)
                        .name("services")
                        .returnType(qualifiedInstanceList(cb.configBeanType()))
                        .addContentLine("return builders.stream()")
                        .increaseContentPadding()
                        .increaseContentPadding()
                        .addContentLine(".map(this::prototype)")
                        .addContent(".collect(")
                        .addContent(Collectors.class)
                        .addContentLine(".toUnmodifiableList());")
                        .decreaseContentPadding()
                        .decreaseContentPadding())
                .addMethod(prototypeMethod -> prototypeMethod
                        .accessModifier(AccessModifier.PRIVATE)
                        .returnType(qualifiedInstance(cb.configBeanType()))
                        .name("prototype")
                        .addParameter(instance -> instance
                                .type(serviceInstance(builderType))
                                .name("instance"))
                        .update(it -> prototypeMethodBody(it, intercepted, descriptorType)));

        ctx.addType(generatedType, classModel, annotatedType, typeInfo.originatingElementValue());
    }

    private void createBlueprintSingleton(TypeInfo typeInfo,
                                          ConfigBean cb,
                                          TypeName builderType,
                                          TypeName annotatedType,
                                          TypeName generatedTypeBuilder) {
        // singleton producing config builder
        var builderClass = ClassModel.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(SINGLETON_ANNOTATION)
                .type(generatedTypeBuilder)
                .addInterface(TypeName.builder()
                                      .from(TypeNames.SUPPLIER)
                                      .addTypeArgument(builderType)
                                      .build())
                .update(this::addSmallWeight)
                .update(this::addConfigField)
                .addConstructor(ctr -> ctr
                        .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                        .addAnnotation(INJECT_ANNOTATION)
                        .update(this::configConstructor))
                .addMethod(get -> get
                        .accessModifier(AccessModifier.PUBLIC)
                        .addAnnotation(OVERRIDE)
                        .name("get")
                        .returnType(builderType)
                        .addContent("var config = this.config.get(\"")
                        .addContent(cb.annotation().configKey())
                        .addContentLine("\");")
                        .addContentLine("")
                        .addContentLine("if (config.exists()) {")
                        .addContent("return ")
                        .addContent(cb.configBeanType())
                        .addContentLine(".builder().config(config);")
                        .addContentLine("}")
                        .addContentLine("")
                        .addContent("return ")
                        .addContent(cb.configBeanType())
                        .addContentLine(".builder();")
                );

        ctx.addType(generatedTypeBuilder, builderClass, annotatedType, typeInfo.originatingElementValue());

        // singleton accepting the config builder and producing the config
        TypeName generatedType = cb.generatedType();
        boolean intercepted = interceptionSupport.intercepted(typeInfo);

        TypeName factoryType;
        if (intercepted) {
            interceptionSupport.generateDelegateInterception(typeInfo, cb.configBeanType());
            factoryType = factoryType(cb.configBeanType());
        } else {
            factoryType = null;
        }
        var configBeanClass = ClassModel.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(SINGLETON_ANNOTATION)
                .type(generatedType)
                .addInterface(TypeName.builder()
                                      .from(TypeNames.SUPPLIER)
                                      .addTypeArgument(cb.configBeanType())
                                      .build())
                .addField(builderField -> builderField
                        .accessModifier(AccessModifier.PRIVATE)
                        .isFinal(true)
                        .name("builder")
                        .type(builderType))
                .update(it -> this.addFactoryField(it, intercepted, factoryType))
                .addConstructor(ctr -> ctr
                        .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                        .addAnnotation(INJECT_ANNOTATION)
                        .addParameter(builder -> builder
                                .name("builder")
                                .type(builderType))
                        .addContentLine("this.builder = builder;")
                        .update(it -> addFactoryConstructor(it, intercepted, factoryType, cb.configBeanType())))
                .addMethod(get -> get
                        .accessModifier(AccessModifier.PUBLIC)
                        .addAnnotation(OVERRIDE)
                        .name("get")
                        .returnType(cb.configBeanType())
                        .addContentLine("return builder.buildPrototype();")
                );

        ctx.addType(generatedType, configBeanClass, annotatedType, typeInfo.originatingElementValue());
    }

    private void createSimpleConfigBean(TypeInfo typeInfo, ConfigBean cb) {
        TypeName annotatedType = typeInfo.typeName();
        TypeName generatedType = cb.generatedType();
        TypeName servicesProviderType = servicesProviderType(cb.configBeanType());

        boolean intercepted = interceptionSupport.intercepted(typeInfo);
        TypeName factoryType;
        if (intercepted) {
            interceptionSupport.generateDelegateInterception(typeInfo, cb.configBeanType());
            factoryType = factoryType(cb.configBeanType());
        } else {
            factoryType = null;
        }

        ClassModel.Builder classModel = configBeanClass(generatedType, servicesProviderType)
                .addAnnotation(CONFIG_BEAN_ANNOTATION)
                .update(this::addConfigField)
                .update(it -> addFactoryField(it, intercepted, factoryType))
                .addConstructor(ctr -> ctr
                        .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                        .addAnnotation(INJECT_ANNOTATION)
                        .update(this::configConstructor)
                        .update(it -> addFactoryConstructor(it, intercepted, factoryType, cb.configBeanType())))
                .addMethod(servicesMethod -> servicesMethod
                        .accessModifier(AccessModifier.PUBLIC)
                        .addAnnotation(OVERRIDE)
                        .name("services")
                        .returnType(qualifiedInstanceList(cb.configBeanType()))
                        .addContent("var config = this.config.get(\"")
                        .addContent(cb.annotation().configKey())
                        .addContentLine("\");")
                        .addContentLine("")
                        .update(it -> generateServicesMethod(it, cb, intercepted)));

        ctx.addType(generatedType, classModel, annotatedType, typeInfo.originatingElementValue());
    }

    private void generateServicesMethodBuilder(Method.Builder method, ConfigBean cb, TypeName builderType) {
        ConfigBeanAnnotation cbAnnot = cb.annotation();
        TypeName cbType = cb.configBeanType();
        String configKey = cbAnnot.configKey();
        boolean wantDefault = cbAnnot.wantDefault();
        boolean atLeastOne = cbAnnot.atLeastOne();
        boolean repeatable = cbAnnot.repeatable();

        if (atLeastOne) {
            method.addContentLine("if (!config.exists()) {")
                    .addContent("throw new ")
                    .addContent(CONFIG_EXCEPTION)
                    .addContent("(\"Expecting configuration node at \\\"")
                    .addContent(configKey)
                    .addContentLine("\\\". Remove @ConfigDriven.AtLeastOne if this behavior is not desired for type \\\""
                                            + cb.configBeanType().fqName() + "\\\"\");")
                    .addContentLine("}");
        }

        if (!atLeastOne && !wantDefault) {
            // not required, not creating default instance - just return empty list, skip the rest if config not there
            method.addContentLine("if (!config.exists()) {")
                    .addContent("return ")
                    .addContent(TypeNames.LIST)
                    .addContentLine(".of();")
                    .addContentLine("}");
        }

        /*
        Generate exact code that is needed, rather than doing it via if in the code
         */
        if (!repeatable) {
            // if list and not repeatable, fail
            method.addContentLine("if (config.isList()) {")
                    .addContent("throw new ")
                    .addContent(CONFIG_EXCEPTION)
                    .addContent("(\"Expecting a single node at \\\"")
                    .addContent(configKey)
                    .addContentLine("\\\", but got a list. Add @ConfigDriven.Repeatable to your config bean"
                                            + " if a list is expected.\");")
                    .addContentLine("}");
        }

        // prepare the result list
        method.addContent(qualifiedInstanceList(builderType))
                .addContent(" result = new ")
                .addContent(ArrayList.class)
                .addContentLine("<>();")
                .addContentLine("");

        if (wantDefault) {
            method.addContentLine("boolean addedDefault = false;");
        }

        if (repeatable) {
            generateServicesMethodRepeatableBuilder(method, cbType, wantDefault);
        } else {
            generateServicesMethodSingleBuilder(method, cbType, wantDefault, atLeastOne);
        }

        // we have all the named instances, now resolve default
        if (wantDefault) {
            method.addContentLine("if (!addedDefault) {")
                    .addContent("var defaultInstance = ")
                    .addContent(cbType)
                    .addContentLine(".builder();")
                    .addContent("result.add(")
                    .addContent(QUALIFIED_INSTANCE)
                    .addContent(".create(defaultInstance, ")
                    .update(this::addConfigBeanQualifier)
                    .addContent(INJECT_QUALIFIER)
                    .addContentLine(".DEFAULT_NAMED));")
                    .addContentLine("}");
        }

        // return the result
        method.addContent("return ")
                .addContent(List.class)
                .addContentLine(".copyOf(result);");
    }

    private void generateServicesMethodSingleBuilder(Method.Builder method,
                                                     TypeName cbType,
                                                     boolean wantDefault,
                                                     boolean atLeastOne) {
        if (atLeastOne) {
            createSingleInstanceAndAddToResultBuilder(method, cbType, wantDefault);

        } else {
            method.addContentLine("if (config.exists()) {");
            createSingleInstanceAndAddToResultBuilder(method, cbType, wantDefault);
            method.addContentLine("}");
        }
    }

    private void createSingleInstanceAndAddToResultBuilder(Method.Builder method,
                                                           TypeName cbType,
                                                           boolean wantDefault) {
        // var instance = AConfig.builder().config(config);
        method.addContent("var instance = ")
                .addContent(cbType)
                .addContentLine(".builder().config(config);");

        // var name = config.get("name").asString().orElse(Injection.Named.DEFAULT_NAME);
        method.addContent("var name = config.get(\"name\").asString().orElse(")
                .addContent(INJECTION_NAMED)
                .addContentLine(".DEFAULT_NAME);");
        // result.add(QualifiedInstance.create(instance, CONFIG_BEAN, Qualifier.createNamed(name));
        method.addContent("result.add(")
                .addContent(QUALIFIED_INSTANCE)
                .addContent(".create(instance, ")
                .update(this::addConfigBeanQualifier)
                .addContent(INJECT_QUALIFIER)
                .addContentLine(".createNamed(name)));");
        // addedDefault = DEFAULT_NAMED.equals(name);

        if (wantDefault) {
            // either use config or default
            method.addContent("addedDefault = ")
                    .addContent(INJECTION_NAMED)
                    .addContentLine(".DEFAULT_NAME.equals(name);");
        }
    }

    private void generateServicesMethodRepeatableBuilder(Method.Builder method,
                                                         TypeName cbType,
                                                         boolean wantDefault) {
        method.addContent("var childNodes = config.asNodeList().orElseGet(")
                .addContent(List.class)
                .addContentLine("::of);")
                .addContentLine("");

        // now iterate through the child nodes and create an instance for each
        method.addContentLine("for (var childNode : childNodes) {")
                // by default use the current node name - for lists, this would be the index
                .addContentLine("var name = childNode.name();")
                // use "name" node if list and present
                .addContentLine("name = childNode.get(\"name\").asString().orElse(name);");
        if (wantDefault) {
            // mark that we have added default instance explicitly (explicit default is strange, but whatever the
            // user does, we follow)
            method.addContent("if (")
                    .addContent(INJECTION_NAMED)
                    .addContentLine(".DEFAULT_NAME.equals(name)) {")
                    .addContentLine("addedDefault = true;")
                    .addContentLine("}");
        }
        method.addContent("var instance = ")
                .addContent(cbType)
                .addContentLine(".builder().config(childNode);");

        method.addContent("result.add(")
                .addContent(QUALIFIED_INSTANCE)
                .addContent(".create(instance, ")
                .update(this::addConfigBeanQualifier)
                .addContent(INJECT_QUALIFIER)
                .addContentLine(".createNamed(name)));")
                .addContentLine("}"); // and of for cycle going through config nodes
    }

    private ClassModel.Builder configBeanClass(TypeName generatedType, TypeName servicesProviderType) {
        return ClassModel.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(SINGLETON_ANNOTATION)
                .addAnnotation(WILDCARD_NAME_ANNOTATION)
                .type(generatedType)
                .addInterface(servicesProviderType);
    }

    private Annotation namedByClassAnnotation(TypeName typeName) {
        return Annotation.builder()
                .typeName(INJECTION_NAMED_BY_CLASS)
                .putValue("value", typeName)
                .build();
    }

    private TypeName factoryType(TypeName typeName) {
        return TypeName.builder(INTERCEPTION_FACTORY)
                .addTypeArgument(typeName)
                .build();
    }

    private TypeName qualifiedInstanceList(TypeName typeName) {
        return TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder(QUALIFIED_INSTANCE)
                                         .addTypeArgument(typeName)
                                         .build())
                .build();
    }

    private TypeName qualifiedInstance(TypeName typeName) {
        return TypeName.builder(QUALIFIED_INSTANCE)
                .addTypeArgument(typeName)
                .build();
    }

    private TypeName serviceInstanceList(TypeName typeName) {
        return TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder(INJECT_SERVICE_INSTANCE)
                                         .addTypeArgument(typeName)
                                         .build())
                .build();
    }

    private TypeName serviceInstance(TypeName typeName) {
        return TypeName.builder(INJECT_SERVICE_INSTANCE)
                .addTypeArgument(typeName)
                .build();
    }

    private void generateServicesMethod(Method.Builder method, ConfigBean cb, boolean intercepted) {
        ConfigBeanAnnotation cbAnnot = cb.annotation();
        TypeName cbType = cb.configBeanType();
        String configKey = cbAnnot.configKey();
        boolean wantDefault = cbAnnot.wantDefault();
        boolean atLeastOne = cbAnnot.atLeastOne();
        boolean repeatable = cbAnnot.repeatable();

        TypeName descriptor = ctx.descriptorType(cb.generatedType());

        if (atLeastOne) {
            method.addContentLine("if (!config.exists()) {")
                    .addContent("throw new ")
                    .addContent(CONFIG_EXCEPTION)
                    .addContent("(\"Expecting configuration node at \\\"")
                    .addContent(configKey)
                    .addContentLine("\\\". Remove @ConfigDriven.AtLeastOne if this behavior is not desired for type \\\""
                                            + cb.configBeanType().fqName() + "\\\"\");")
                    .addContentLine("}");
        }

        if (!atLeastOne && !wantDefault) {
            // not required, not creating default instance - just return empty list, skip the rest if config not there
            method.addContentLine("if (!config.exists()) {")
                    .addContent("return ")
                    .addContent(TypeNames.LIST)
                    .addContentLine(".of();")
                    .addContentLine("}");
        }

        /*
        Generate exact code that is needed, rather than doing it via if in the code
         */
        if (!repeatable) {
            // if list and not repeatable, fail
            method.addContentLine("if (config.isList()) {")
                    .addContent("throw new ")
                    .addContent(CONFIG_EXCEPTION)
                    .addContent("(\"Expecting a single node at \\\"")
                    .addContent(configKey)
                    .addContentLine("\\\", but got a list. Add @ConfigDriven.Repeatable to your config bean"
                                            + " if a list is expected.\");")
                    .addContentLine("}");
        }

        // prepare the result list
        method.addContent(qualifiedInstanceList(cbType))
                .addContent(" result = new ")
                .addContent(ArrayList.class)
                .addContentLine("<>();")
                .addContentLine("");

        if (wantDefault) {
            method.addContentLine("boolean addedDefault = false;");
        }

        if (repeatable) {
            generateServicesMethodRepeatable(method, cbType, wantDefault, intercepted, descriptor);
        } else {
            generateServicesMethodSingle(method, cbType, wantDefault, atLeastOne, intercepted, descriptor);
        }

        // we have all the named instances, now resolve default
        if (wantDefault) {
            method.addContentLine("if (!addedDefault) {")
                    .addContent("var defaultInstance = ")
                    .addContent(cbType)
                    .addContentLine(".create();")
                    .addContent("result.add(")
                    .addContent(QUALIFIED_INSTANCE)
                    .addContent(".create(defaultInstance, ")
                    .update(this::addConfigBeanQualifier)
                    .addContent(INJECT_QUALIFIER)
                    .addContentLine(".DEFAULT_NAMED));")
                    .addContentLine("}");
        }

        // return the result
        method.addContent("return ")
                .addContent(List.class)
                .addContentLine(".copyOf(result);");
    }

    private void generateServicesMethodRepeatable(Method.Builder method,
                                                  TypeName cbType,
                                                  boolean wantDefault,
                                                  boolean intercepted,
                                                  TypeName descriptorType) {

        method.addContent("var childNodes = config.asNodeList().orElseGet(")
                .addContent(List.class)
                .addContentLine("::of);")
                .addContentLine("");

        // now iterate through the child nodes and create an instance for each
        method.addContentLine("for (var childNode : childNodes) {")
                // by default use the current node name - for lists, this would be the index
                .addContentLine("var name = childNode.name();")
                // use "name" node if list and present
                .addContentLine("name = childNode.get(\"name\").asString().orElse(name);");
        if (wantDefault) {
            // mark that we have added default instance explicitly (explicit default is strange, but whatever the
            // user does, we follow)
            method.addContent("if (")
                    .addContent(INJECTION_NAMED)
                    .addContentLine(".DEFAULT_NAME.equals(name)) {")
                    .addContentLine("addedDefault = true;")
                    .addContentLine("}");
        }
        method.addContent("var instance = ")
                .addContent(cbType)
                .addContentLine(".create(childNode);");

        if (intercepted) {
            method.addContent("instance = factory.create(")
                    .addContent(descriptorType)
                    .addContentLine(".INSTANCE, instance);");
        }

        method.addContent("result.add(")
                .addContent(QUALIFIED_INSTANCE)
                .addContent(".create(instance, ")
                .update(this::addConfigBeanQualifier)
                .addContent(INJECT_QUALIFIER)
                .addContentLine(".createNamed(name)));")
                .addContentLine("}"); // and of for cycle going through config nodes
    }

    private void generateServicesMethodSingle(Method.Builder method,
                                              TypeName cbType,
                                              boolean wantDefault,
                                              boolean atLeastOne,
                                              boolean intercepted,
                                              TypeName descriptorType) {

        if (atLeastOne) {
            createSingleInstanceAndAddToResult(method, cbType, wantDefault, intercepted, descriptorType);

        } else {
            method.addContentLine("if (config.exists()) {");
            createSingleInstanceAndAddToResult(method, cbType, wantDefault, intercepted, descriptorType);
            method.addContentLine("}");
        }
    }

    private void createSingleInstanceAndAddToResult(Method.Builder method,
                                                    TypeName cbType,
                                                    boolean wantDefault,
                                                    boolean intercepted,
                                                    TypeName descriptorType) {
        // var instance = AConfig.create(config);
        method.addContent("var instance = ")
                .addContent(cbType)
                .addContentLine(".create(config);");
        if (intercepted) {
            method.addContent("instance = factory.create(")
                    .addContent(descriptorType)
                    .addContentLine(".INSTANCE, instance);");
        }
        // var name = config.get("name").asString().orElse(Injection.Named.DEFAULT_NAME);
        method.addContent("var name = config.get(\"name\").asString().orElse(")
                .addContent(INJECTION_NAMED)
                .addContentLine(".DEFAULT_NAME);");
        // result.add(QualifiedInstance.create(instance, CONFIG_BEAN, Qualifier.createNamed(name));
        method.addContent("result.add(")
                .addContent(QUALIFIED_INSTANCE)
                .addContent(".create(instance, ")
                .update(this::addConfigBeanQualifier)
                .addContent(INJECT_QUALIFIER)
                .addContentLine(".createNamed(name)));");
        // addedDefault = DEFAULT_NAMED.equals(name);

        if (wantDefault) {
            // either use config or default
            method.addContent("addedDefault = ")
                    .addContent(INJECTION_NAMED)
                    .addContentLine(".DEFAULT_NAME.equals(name);");
        }
    }

    private TypeName servicesProviderType(TypeName typeName) {
        return TypeName.builder(ServiceCodegenTypes.SERVICES_PROVIDER)
                .addTypeArgument(typeName)
                .build();
    }

    private void addConfigBeanQualifier(ContentBuilder<?> builder) {
        builder.addContent(INJECT_QUALIFIER)
                .addContent(".CONFIG_BEAN, ");
    }

    private void configConstructor(Constructor.Builder builder) {
        builder.addParameter(configParam -> configParam
                        .name("config")
                        .type(CONFIG_COMMON_CONFIG))
                .addContentLine("this.config = config;");
    }

    private void addConfigField(ClassModel.Builder builder) {
        builder.addField(configField -> configField
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .name("config")
                .type(CONFIG_COMMON_CONFIG));
    }

    private void addSmallWeight(ClassModel.Builder builder) {
        // a very small weight, so others can update the builder
        builder.addAnnotation(Annotation.builder()
                                      .typeName(WEIGHT)
                                      .putValue("value", 10D)
                                      .build());
    }

    private void prototypeMethodBody(Method.Builder method, boolean intercepted, TypeName descriptorType) {
        if (intercepted) {
            method.addContent("var bean = factory.create(")
                    .addContent(descriptorType)
                    .addContentLine(".INSTANCE, instance.get().buildPrototype());");
        } else {
            method.addContentLine("var bean = instance.get().buildPrototype();");
        }
        method.addContent("return ")
                .addContent(QUALIFIED_INSTANCE)
                .addContentLine(".create(bean, instance.qualifiers());");
    }

    private void addFactoryField(ClassModel.Builder builder, boolean intercepted, TypeName factoryType) {
        if (intercepted) {
            builder.addField(factory -> factory
                    .name("factory")
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(factoryType));
        }
    }

    private void addFactoryConstructor(Constructor.Builder builder,
                                       boolean intercepted,
                                       TypeName factoryType,
                                       TypeName typeName) {
        if (intercepted) {
            builder.addParameter(factory -> factory
                            .name("factory")
                            .type(factoryType)
                            .addAnnotation(namedByClassAnnotation(typeName)))
                    .addContentLine("this.factory = factory;");
        }
    }

}
