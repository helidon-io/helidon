/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.common.types.AccessModifier.PACKAGE_PRIVATE;
import static io.helidon.common.types.AccessModifier.PRIVATE;
import static io.helidon.integrations.langchain4j.codegen.AgentCodegen.AGENTS_CONFIG;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_AGENT;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MEMORY;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MEMORY_PROVIDER;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CONTENT_RETRIEVER;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_MCP_CLIENTS;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_RETRIEVER_AUGMENTOR;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_TOOLS;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_TOOL_PROVIDER;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.CONFIG;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_REGISTRY;

class SubAgentConfiguratorBuilder {
    private static final TypeName GENERATOR = TypeName.create(SubAgentConfiguratorBuilder.class);
    private static final TypeName JLA_ANNOTATION = TypeName.create(java.lang.annotation.Annotation.class);

    private static final TypeName DECLARATIVE_AGENT_CREATION_CONTEXT = TypeName.create(
            "dev.langchain4j.agentic.AgenticServices.DeclarativeAgentCreationContext");

    private static final Set<TypeName> AI_AGENT_ANNOTATIONS = Set.of(
            AI_AGENT,
            AI_CHAT_MODEL,
            AI_CHAT_MEMORY,
            AI_CHAT_MEMORY_PROVIDER,
            AI_CONTENT_RETRIEVER,
            AI_RETRIEVER_AUGMENTOR,
            AI_MCP_CLIENTS,
            AI_TOOLS
    );

    private final Field.Builder metadataField = Field.builder()
            .name("agentsMetadata")
            .isFinal(true)
            .type(TypeName.builder(TypeNames.MAP)
                          .addTypeArgument(TypeName.create(Class.class))
                          .addTypeArgument(TypeName.builder(TypeNames.MAP)
                                                   .addTypeArgument(TypeName.create(Class.class))
                                                   .addTypeArgument(TypeNames.OBJECT)
                                                   .build())
                          .build())
            .addContent("new ")
            .addContent(HashMap.class)
            .addContentLine("<>()");

    private final Method.Builder initMetadataMethodBuilder = Method.builder()
            .accessModifier(PRIVATE)
            .name("initializeAgentsMetadata");

    private final Set<String> triggerPackages = new HashSet<>();

    SubAgentConfiguratorBuilder add(TypeInfo type) {
        triggerPackages.add(type.typeName().packageName());
        initMetadataMethodBuilder
                .addContent("agentsMetadata")
                .addContent(".put(").addContent(type.typeName()).addContent(".class, Map.of(");
        AtomicBoolean first = new AtomicBoolean(true);
        type.annotations().stream()
                .filter(a -> AI_AGENT_ANNOTATIONS.contains(a.typeName()))
                .filter(a -> a.stringValue().isPresent())
                .forEach(a -> {
                    initMetadataMethodBuilder
                            .addContent(first.getAndSet(false) ? "" : ", ")
                            .addContent(a.typeName())
                            .addContent(".class, ")
                            .addContentLiteral(a.stringValue().orElseThrow());
                });

        initMetadataMethodBuilder.addContentLine("));");
        return this;
    }

    void build(RoundContext ctx) {
        for (String packageName : triggerPackages) {
            // Build one for each package (package private)
            buildInternal(packageName, ctx);
        }
    }

    private void buildInternal(String packageName, RoundContext ctx) {
        TypeName generatedType = TypeName.builder()
                .packageName(packageName)
                .className("SubAgentConfigurator")
                .build();
        var classModel = ClassModel.builder()
                .type(generatedType)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 generatedType,
                                                 generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               generatedType,
                                                               generatedType,
                                                               "1",
                                                               ""))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(SERVICE_ANNOTATION_SINGLETON));

        classModel.addField(metadataField);
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
        classModel.addMethod(initMetadataMethodBuilder);
        classModel.addMethod(this::addGetAnnotationValueMethod);
        classModel.addConstructor(ctr -> ctr
                .addParameter(Parameter.builder()
                                      .type(CONFIG)
                                      .name("config")
                                      .build())

                .addParameter(Parameter.builder()
                                      .type(SERVICE_REGISTRY)
                                      .name("registry")
                                      .build())
                .addContent("this.agenticConfig = config.get(")
                .addContent(AGENTS_CONFIG)
                .addContentLine(".CONFIG_ROOT);")
                .addContentLine("this.registry = registry;")
                .addContent("this.").addContent(initMetadataMethodBuilder.build().name())
                .addContentLine("();"));
        classModel.addMethod(this::addConfigureSubAgentsMethod);
        ctx.addGeneratedType(generatedType, classModel, generatedType);
    }

    private void addConfigureSubAgentsMethod(Method.Builder mb) {
        mb
                .accessModifier(PACKAGE_PRIVATE)
                .addParameter(Parameter.builder()
                                      .name("ctx")
                                      .type(DECLARATIVE_AGENT_CREATION_CONTEXT)
                                      .build())
                .name("configureSubAgents")
                .addContentLine("Class<?> cls = ctx.agentServiceClass();")

                .addContent("String agentName = getAnnotationValue(cls, ")
                .addContent(AI_AGENT)
                .addContent(".class, ")
                .addContent(AI_AGENT)
                .addContent("::value")
                .addContentLine(").orElseThrow();")
                .addContent("var agentsConfigBuilder = ")
                .addContent(AGENTS_CONFIG)
                .addContentLine(".builder();")
                .addContentLine()

                .update(b -> {
                    // Annotations with simple String values
                    addMapping(b, AI_CHAT_MODEL, "chatModel");
                    addMapping(b, AI_CHAT_MEMORY, "chatMemory");
                    addMapping(b, AI_CHAT_MEMORY_PROVIDER, "chatMemoryProvider");
                    addMapping(b, AI_CONTENT_RETRIEVER, "contentRetriever");
                    addMapping(b, AI_RETRIEVER_AUGMENTOR, "retrievalAugmentor");
                    addMapping(b, AI_TOOL_PROVIDER, "toolProvider");
                    // Annotations with array values
                    addArrayMapping(b, AI_TOOLS, "tools");
                    addArrayMapping(b, AI_MCP_CLIENTS, "mcpClients");
                })
                .addContentLine("agentsConfigBuilder.config(agenticConfig.get(agentName));")
                .addContentLine("var agentsConfig = agentsConfigBuilder.build();")
                .addContentLine("var subAgentBuilder = ctx.agentBuilder();")
                .addContentLine("agentsConfig.configure(subAgentBuilder, registry);");
    }

    private void addGetAnnotationValueMethod(Method.Builder mb) {
        mb
                .addGenericArgument(TypeArgument.builder()
                                            .token("A")
                                            .bound(JLA_ANNOTATION)
                                            .build())
                .addGenericArgument(TypeArgument.builder()
                                            .token("R")
                                            .build())
                .accessModifier(PRIVATE)
                .addParameter(Parameter.builder()
                                      .name("clazz")
                                      .type(TypeName.builder()
                                                    .type(Class.class)
                                                    .addTypeArgument(TypeNames.WILDCARD)
                                                    .build())
                                      .build())
                .addParameter(Parameter.builder()
                                      .name("annotationType")
                                      .type(TypeName.builder()
                                                    .type(Class.class)
                                                    .addTypeArgument(TypeName.create("A"))
                                                    .build())
                                      .build())
                .addParameter(Parameter.builder()
                                      .name("valueMapper")
                                      .type(TypeName.builder()
                                                    .type(Function.class)
                                                    .addTypeArgument(TypeName.create("A"))
                                                    .addTypeArgument(TypeName.create("R"))
                                                    .build())
                                      .build())
                .name("getAnnotationValue")
                .update(mbc -> mbc
                        .addContentLine("// Check build time metadata")
                        .addContent("return ")
                        .addContent(TypeNames.OPTIONAL)
                        .addContentLine(".ofNullable(agentsMetadata.get(clazz))")
                        .increaseContentPadding()
                        .addContentLine(".map(m -> m.get(annotationType))")
                        .addContentLine(".map(o -> (R) o)")
                        .addContentLine("// or use reflection")
                        .addContent(".or(() -> ")
                        .addContent(TypeNames.OPTIONAL)
                        .addContentLine(".ofNullable(clazz.getDeclaredAnnotation(annotationType)).map(valueMapper));")
                        .decreaseContentPadding()
                )
                .returnType(TypeName.builder(TypeNames.OPTIONAL)
                                    .addTypeArgument(TypeName.create("R"))
                                    .build());
    }

    private void addMapping(Method.Builder mb,
                            TypeName annotationType,
                            String configName,
                            Consumer<Method.Builder> valueMapper) {
        mb.addContent("getAnnotationValue(cls, ")
                .increaseContentPadding()
                .addContent(annotationType)
                .addContent(".class, ")
                .update(valueMapper)
                .addContentLine(")")
                .addContent(".ifPresent(agentsConfigBuilder::")
                .addContent(configName)
                .addContentLine(");")
                .decreaseContentPadding()
                .addContentLine();
    }

    private void addMapping(Method.Builder mb, TypeName annotationType, String configName) {
        addMapping(mb, annotationType, configName, vm -> vm.addContent(annotationType).addContent("::value"));
    }

    private void addArrayMapping(Method.Builder mb, TypeName annotationType, String configName) {
        addMapping(mb, annotationType, configName, vm -> vm
                .addContent(configName)
                .addContent(" -> ")
                .addContent(TypeNames.SET)
                .addContent(".of(")
                .addContent(configName)
                .addContent(".value())"));
    }

}
