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

package io.helidon.inject.codegen;

import java.util.ArrayList;
import java.util.List;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.inject.codegen.spi.InjectCodegenExtension;

import static io.helidon.common.types.Annotations.OVERRIDE;
import static io.helidon.inject.codegen.ConfigBeanAnnotation.CONFIG_BEAN_TYPE;
import static io.helidon.inject.codegen.InjectCodegenTypes.COMMON_CONFIG;
import static io.helidon.inject.codegen.InjectCodegenTypes.COMMON_CONFIG_EXCEPTION;
import static io.helidon.inject.codegen.InjectCodegenTypes.INJECTION_INJECT;
import static io.helidon.inject.codegen.InjectCodegenTypes.INJECTION_NAMED;
import static io.helidon.inject.codegen.InjectCodegenTypes.INJECTION_SINGLETON;
import static io.helidon.inject.codegen.InjectCodegenTypes.QUALIFIED_INSTANCE;

class ConfigBeanCodegen implements InjectCodegenExtension {
    private static final Annotation SINGLETON_ANNOTATION = Annotation.create(INJECTION_SINGLETON);
    private static final Annotation INJECT_ANNOTATION = Annotation.create(INJECTION_INJECT);
    private static final Annotation CONFIG_BEAN_ANNOTATION = Annotation.create(CONFIG_BEAN_TYPE);
    private static final Annotation WILDCARD_NAME_ANNOTATION = Annotation.create(INJECTION_NAMED, "*");

    private final InjectionCodegenContext ctx;

    ConfigBeanCodegen(InjectionCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RoundContext roundContext) {
        // for each @ConfigDriven.ConfigBean
        // generate an eager singleton ServicesProvider that uses injected config to create instances
        for (TypeInfo typeInfo : roundContext.annotatedTypes(CONFIG_BEAN_TYPE)) {
            if (typeInfo.hasAnnotation(INJECTION_SINGLETON)) {
                // this type has already been generated, ignore it (this is a follow up annotation round for a generated type)
                continue;
            }
            ConfigBean cb = ConfigBean.create(typeInfo);

            TypeName annotatedType = typeInfo.typeName();
            TypeName generatedType = cb.generatedType();
            TypeName servicesProviderType = servicesProviderType(cb.configBeanType());

            ClassModel.Builder classModel = ClassModel.builder()
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .addAnnotation(SINGLETON_ANNOTATION)
                    .addAnnotation(CONFIG_BEAN_ANNOTATION)
                    .addAnnotation(WILDCARD_NAME_ANNOTATION)
                    .type(generatedType)
                    .addInterface(servicesProviderType)
                    .addField(configField -> configField
                            .accessModifier(AccessModifier.PRIVATE)
                            .isFinal(true)
                            .name("config")
                            .type(COMMON_CONFIG))
                    .addConstructor(ctr -> ctr
                            .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                            .addAnnotation(INJECT_ANNOTATION)
                            .addParameter(configParam -> configParam
                                    .name("config")
                                    .type(COMMON_CONFIG))
                            .addContentLine("this.config = config;"))
                    .addMethod(servicesMethod -> servicesMethod
                            .accessModifier(AccessModifier.PUBLIC)
                            .addAnnotation(OVERRIDE)
                            .name("services")
                            .returnType(qualifiedInstanceList(cb.configBeanType()))
                            .addContent("var config = this.config.get(\"")
                            .addContent(cb.annotation().configKey())
                            .addContentLine("\");")
                            .addContentLine("")
                            .update(it -> generateServicesMethod(it, cb)));

            ctx.addType(generatedType, classModel, annotatedType, typeInfo.originatingElement().orElse(annotatedType));
        }
    }

    private TypeName qualifiedInstanceList(TypeName typeName) {
        return TypeName.builder(TypeNames.LIST)
                .addTypeArgument(TypeName.builder(QUALIFIED_INSTANCE)
                                         .addTypeArgument(typeName)
                                         .build())
                .build();
    }

    private void generateServicesMethod(Method.Builder method, ConfigBean cb) {
        ConfigBeanAnnotation cbAnnot = cb.annotation();
        TypeName cbType = cb.configBeanType();
        String configKey = cbAnnot.configKey();
        boolean wantDefault = cbAnnot.wantDefault();
        boolean atLeastOne = cbAnnot.atLeastOne();

        if (atLeastOne && !wantDefault) {
            method.addContentLine("if (!config.exists()) {")
                    .addContent("throw new ")
                    .addContent(COMMON_CONFIG_EXCEPTION)
                    .addContent("(\"Expecting configuration node at \\\"")
                    .addContent(configKey)
                    .addContentLine("\\\". Add @ConfigDriven.WantDefault if default instance should be used "
                                            + "instead, or remove @ConfigDriven.AtLeastOne\");")
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
        if (cbAnnot.repeatable()) {
            generateServicesMethodRepeatable(method, cbType, wantDefault);
        } else {
            generateServicesMethodSingle(method, cbType, configKey, wantDefault, atLeastOne);
        }

    }

    private void generateServicesMethodRepeatable(Method.Builder method,
                                                  TypeName cbType,
                                                  boolean wantDefault) {

        // now a multi-step process, this is generated for each service, so we do not need to introduce
        // a runtime module dependency just for this one method
        method.addContent(qualifiedInstanceList(cbType))
                .addContent(" result = new ")
                .addContent(ArrayList.class)
                .addContentLine("<>();")
                .addContentLine("");

        if (wantDefault) {
            method.addContentLine("boolean addedDefault = false;");
        }

        method.addContent("var childNodes = config.asNodeList().orElseGet(")
                .addContent(List.class)
                .addContentLine("::of);")
                .addContentLine("boolean isList = config.isList();")
                .addContentLine("");

        // now iterate through the child nodes and create an instance for each
        method.addContentLine("for (var childNode : childNodes) {")
                // by default use the current node name - for lists, this would be the index
                .addContentLine("var name = childNode.name();")
                // use "name" node if list and present
                .addContentLine("name = isList ? childNode.get(\"name\").asString().orElse(name) : name;");
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
                .addContentLine(".create(childNode);")
                .addContent("result.add(")
                .addContent(QUALIFIED_INSTANCE)
                .addContent(".create(instance, ")
                .update(this::addConfigBeanQualifier)
                .addContent(InjectCodegenTypes.QUALIFIER)
                .addContentLine(".createNamed(name)));")
                .addContentLine("}"); // and of for cycle going through config nodes

        // we have all the named instances, now resolve default
        if (wantDefault) {
            method.addContentLine("if (!addedDefault) {")
                    .addContent("var instance = ")
                    .addContent(cbType)
                    .addContentLine(".create();")
                    .addContent("result.add(")
                    .addContent(QUALIFIED_INSTANCE)
                    .addContent(".create(instance, ")
                    .update(this::addConfigBeanQualifier)
                    .addContent(InjectCodegenTypes.QUALIFIER)
                    .addContentLine(".DEFAULT_NAMED));")
                    .addContentLine("}"); // end of if - want default and not added
        }

        // return the result
        method.addContent("return ")
                .addContent(List.class)
                .addContentLine(".copyOf(result);");
    }

    private void generateServicesMethodSingle(Method.Builder method,
                                              TypeName cbType,
                                              String configKey,
                                              boolean wantDefault,
                                              boolean atLeastOne) {

        // if list and not repeatable, fail
        method.addContentLine("if (config.isList()) {")
                .addContent("throw new ")
                .addContent(COMMON_CONFIG_EXCEPTION)
                .addContent("(\"Expecting a single node at \\\"")
                .addContent(configKey)
                .addContentLine("\\\", but got a list. Add @ConfigDriven.Repeatable to your config bean"
                                        + " if a list is expected.\");")
                .addContentLine("}");

        if (atLeastOne) {
            if (wantDefault) {
                // either use config or default
                generateServicesMethodWantDefaultSingle(method, cbType);
            } else {
                // only use config fails otherwise (handled in calling method)
                method.addContent(cbType)
                        .addContent(" instance = ")
                        .addContent(cbType)
                        .addContentLine(".create(config);");
            }
            // return a single instance
            method.addContent("return ")
                    .addContent(TypeNames.LIST)
                    .addContent(".of(")
                    .addContent(QUALIFIED_INSTANCE)
                    .addContent(".create(instance, ")
                    .update(this::addConfigBeanQualifier)
                    .addContent(InjectCodegenTypes.QUALIFIER)
                    .addContentLine(".DEFAULT_NAMED));");
        } else {
            if (wantDefault) {
                // either use config or default (same as if we have at least once defined)
                generateServicesMethodWantDefaultSingle(method, cbType);
            } else {
                // either config exists, or return emtpy list (empty list check is already done above)
                // return a single instance
                method.addContent(cbType)
                        .addContent(" instance = ")
                        .addContent(cbType)
                        .addContentLine(".create(config);");
                method.addContent("return ")
                        .addContent(TypeNames.LIST)
                        .addContent(".of(")
                        .addContent(QUALIFIED_INSTANCE)
                        .addContent(".create(instance, ")
                        .update(this::addConfigBeanQualifier)
                        .addContent(InjectCodegenTypes.QUALIFIER)
                        .addContentLine(".DEFAULT_NAMED));");
            }
        }
    }

    private void generateServicesMethodWantDefaultSingle(Method.Builder method, TypeName cbType) {
        method.addContent(cbType)
                .addContentLine(" instance;")
                .addContentLine("if (config.exists()) {")
                .addContent("instance = ")
                .addContent(cbType)
                .addContentLine(".create(config);")
                .addContentLine("} else {")
                .addContent("instance = ")
                .addContent(cbType)
                .addContentLine(".create();")
                .addContentLine("}");
    }

    private TypeName servicesProviderType(TypeName typeName) {
        return TypeName.builder(InjectCodegenTypes.SERVICES_PROVIDER)
                .addTypeArgument(typeName)
                .build();
    }

    private void addConfigBeanQualifier(ContentBuilder<?> builder) {
        builder.addContent(InjectCodegenTypes.QUALIFIER)
                .addContent(".CONFIG_BEAN, ");
    }
}
