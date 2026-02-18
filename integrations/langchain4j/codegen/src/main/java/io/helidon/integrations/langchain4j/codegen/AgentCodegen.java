/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.codegen;

import java.util.Collection;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.common.types.AccessModifier.PACKAGE_PRIVATE;
import static io.helidon.common.types.AccessModifier.PRIVATE;
import static io.helidon.common.types.TypeNames.CLASS_WILDCARD;
import static io.helidon.common.types.TypeNames.STRING;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AGENTS_CONFIG;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AGENT_METADATA;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_AGENT;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.CONFIG;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_AGENTIC_SERVICES;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_CHAT_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_DECLARATIVE_AGENT_CREATION_CONTEXT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_QUALIFIER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_REGISTRY;

class AgentCodegen implements CodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(AgentCodegen.class);
    static final String AGENTS_CONFIG_KEY = "langchain4j.agents";

    @Override
    public void process(RoundContext roundCtx) {
        Collection<TypeInfo> types = roundCtx.annotatedTypes(AI_AGENT);

        // First round to get annotation metadata of all known agents
        for (TypeInfo type : types) {
            // for each annotated interface, generate Iface__AiServices and Iface__Service
            // the type MUST be an interface
            if (type.kind() != ElementKind.INTERFACE) {
                throw new CodegenException("Type annotated with " + AI_AGENT.fqName() + " must be an interface.",
                                           type.originatingElementValue());
            }

        }
        // Second round to generate agent producers
        for (TypeInfo type : types) {
            process(roundCtx, type);
        }
    }

    private void process(RoundContext roundCtx, TypeInfo agentInterface) {
        TypeName agentInterfaceType = agentInterface.typeName();
        TypeName generatedType = generatedTypeName(agentInterfaceType, "AiAgent");
        AgentMetadataSupplierBuilder.build(agentInterface, roundCtx);

        var classModel = ClassModel.builder()
                .type(generatedType)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 agentInterfaceType,
                                                 generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               agentInterfaceType,
                                                               generatedType,
                                                               "1",
                                                               ""))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addInterface(supplierType(agentInterfaceType))
                .addAnnotation(Annotation.create(SERVICE_ANNOTATION_SINGLETON));

        classModel.addField(aiServices -> aiServices
                .name("agenticConfig")
                .type(CONFIG)
                .isFinal(true)
                .accessModifier(PRIVATE)
        );

        classModel.addField(aiServices -> aiServices
                .name("registry")
                .type(SERVICE_REGISTRY)
                .isFinal(true)
                .accessModifier(PRIVATE)
        );

        classModel.addField(aiServices -> aiServices
                .name("chatModel")
                .type(LC_CHAT_MODEL)
                .isFinal(true)
                .accessModifier(PRIVATE)
        );

        // constructor (parameters depend on annotations on interface)
        classModel.addConstructor(ctr -> ctr
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .update(it -> {
                    aiAgentsParameter(it,
                                      true,
                                      agentInterface,
                                      AI_CHAT_MODEL,
                                      LC_CHAT_MODEL,
                                      "chatModel");
                })
        );

        // and the get method (implementation of supplier)
        classModel.addMethod(get -> get
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(agentInterfaceType)
                .name("get")
                .addContent("var topAgentConfig = this.agenticConfig.get(")
                .addContentLiteral(agentInterface.annotation(AI_AGENT).stringValue().orElseThrow())
                .addContentLine(");")
                .addContent("var configuredModel = topAgentConfig")
                .addContent(".get(")
                .addContentLiteral("chat-model")
                .addContentLine(")")
                .increaseContentPadding()
                .addContentLine(".asString()")
                .addContent(".map(n -> registry.getNamed(ChatModel.class, n))")
                .addContentLine(".orElse(chatModel);")
                .decreaseContentPadding()
                .addContent("return ")
                .addContent(LC_AGENTIC_SERVICES)
                .addContent(".createAgenticSystem(")
                .addContent(agentInterfaceType)
                .addContent(".class, ")
                .addContent("configuredModel, ")
                .addContent("this")
                .addContentLine("::configureSubAgents);")
                .addContentLine("")
        );

        classModel.addMethod(this::addConfigureSubAgentsMethod);

        roundCtx.addGeneratedType(generatedType, classModel, agentInterfaceType, agentInterface.originatingElementValue());
    }

    private void aiAgentsParameter(Constructor.Builder ctr,
                                   boolean autoDiscovery,
                                   TypeInfo aiInterface,
                                   TypeName aiModelAnnotation,
                                   TypeName lcModelType,
                                   String aiServicesMethodName) {

        ctr.addParameter(Parameter.builder()
                                 .type(CONFIG)
                                 .name("config")
                                 .build());

        ctr.addParameter(Parameter.builder()
                                 .type(SERVICE_REGISTRY)
                                 .name("registry")
                                 .build());

        ctr.addContent("this.agenticConfig = config.get(")
                .addContentLiteral(AGENTS_CONFIG_KEY)
                .addContentLine(");");
        ctr.addContentLine("this.registry = registry;");

        // if annotated, we have a named value (and that is mandatory)
        String modelName = aiInterface.findAnnotation(aiModelAnnotation)
                .flatMap(Annotation::stringValue)
                .orElse(null);

        if (modelName == null) {
            // there is no annotation, use only auto-discovered model (if present)
            if (!autoDiscovery) {
                // no autodiscovery, this model will not be configured
                return;
            }
            ctr.addParameter(parameter -> parameter
                    .name(aiServicesMethodName)
                    .type(optionalType(lcModelType)));
            ctr
                    .addContent("this.chatModel = chatModel.orElse(null);");
        } else {
            // there is no annotation, use only auto-discovered model (if present)
            if (!autoDiscovery) {
                // no autodiscovery, this model will not be configured
                return;
            }
            ctr.addParameter(parameter -> parameter
                    .name(aiServicesMethodName)
                    .type(optionalType(lcModelType))
                    .addAnnotation(namedAnnotation(modelName)));
            ctr
                    .addContent("this.chatModel = chatModel.orElse(null);");
        }
    }

    private void addConfigureSubAgentsMethod(Method.Builder mb) {
        mb
                .accessModifier(PACKAGE_PRIVATE)
                .addParameter(Parameter.builder()
                                      .name("ctx")
                                      .type(LC_DECLARATIVE_AGENT_CREATION_CONTEXT)
                                      .build())
                .name("configureSubAgents")
                .addContent(CLASS_WILDCARD)
                .addContentLine(" cls = ctx.agentServiceClass();")
                .addContentLine()
                .addContentLine("// Get Agent metadata created from it's annotations in build-time")
                .addContent("var metadata = registry.first(")
                .addContent(AGENT_METADATA)
                .addContent(".class, ")
                .addContent(SERVICE_QUALIFIER)
                .addContentLine(".createNamed(cls))")
                .increaseContentPadding()
                .addContent(".orElseThrow(() -> new ")
                .addContent(IllegalStateException.class)
                .addContent("(")
                .addContentLiteral("Agent ")
                .addContent("+ cls +")
                .addContentLiteral(" has no build time metadata available!")
                .addContentLine("));")

                .decreaseContentPadding()
                .addContent(STRING)
                .addContentLine(" agentName = metadata.agentName();")
                .addContent("var agentsConfigBuilder = ")
                .addContent(AGENTS_CONFIG)
                .addContent(".builder(metadata.buildTimeConfig());")
                .decreaseContentPadding()
                .addContentLine()
                .addContentLine()
                .addContentLine("// Override annotation setup with config")
                .addContentLine("agentsConfigBuilder.config(agenticConfig.get(agentName));")
                .addContentLine("var agentsConfig = agentsConfigBuilder.build();")
                .addContentLine("agentsConfig.configure(ctx, registry);");
    }

    private TypeName generatedTypeName(TypeName aiInterfaceType, String suffix) {
        return TypeName.builder()
                .packageName(aiInterfaceType.packageName())
                .className(aiInterfaceType.classNameWithEnclosingNames().replace('.', '_') + "__" + suffix)
                .build();
    }

    private TypeName supplierType(TypeName suppliedType) {
        return TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(suppliedType)
                .build();
    }

    private TypeName optionalType(TypeName optionalType) {
        return TypeName.builder(TypeNames.OPTIONAL)
                .addTypeArgument(optionalType)
                .build();
    }

    private Annotation namedAnnotation(String modelName) {
        return Annotation.create(SERVICE_ANNOTATION_NAMED, modelName);
    }
}
