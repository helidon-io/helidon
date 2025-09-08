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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.common.types.AccessModifier.PACKAGE_PRIVATE;
import static io.helidon.common.types.AccessModifier.PRIVATE;
import static io.helidon.common.types.TypeNames.MAP;
import static io.helidon.common.types.TypeNames.STRING;
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
    private static final TypeName DECLARATIVE_AGENT_CREATION_CONTEXT = TypeName.create(
            "dev.langchain4j.agentic.AgenticServices.DeclarativeAgentCreationContext");

    private static final BiConsumer<Method.Builder, Optional<Annotation>> STRING_MAPPER =
            (b, a) ->
                    a.flatMap(Annotation::stringValue).ifPresentOrElse(b::addContentLiteral, () -> b.addContent("null"));
    private static final BiConsumer<Method.Builder, Optional<Annotation>> STRINGS_MAPPER =
            (b, a) -> {
                b.addContent(Stream.class).addContent(".<String>of(");
                AtomicBoolean first = new AtomicBoolean(true);
                a.flatMap(Annotation::stringValues).orElse(List.of())
                        .forEach(c -> {
                            if (!first.getAndSet(false)) {
                                b.addContent(", ");
                            }
                            b.addContentLiteral(c);
                        });
                b.addContent(").collect(")
                        .addContent(Collectors.class)
                        .addContent(".toSet())");
            };

    private static final Map<TypeName, BiConsumer<Method.Builder, Optional<Annotation>>> AI_AGENT_ANNOTATIONS =
            new LinkedHashMap<>();

    static {
        AI_AGENT_ANNOTATIONS.put(AI_AGENT, STRING_MAPPER);
        AI_AGENT_ANNOTATIONS.put(AI_CHAT_MODEL, STRING_MAPPER);
        AI_AGENT_ANNOTATIONS.put(AI_CHAT_MEMORY, STRING_MAPPER);
        AI_AGENT_ANNOTATIONS.put(AI_CHAT_MEMORY_PROVIDER, STRING_MAPPER);
        AI_AGENT_ANNOTATIONS.put(AI_CONTENT_RETRIEVER, STRING_MAPPER);
        AI_AGENT_ANNOTATIONS.put(AI_RETRIEVER_AUGMENTOR, STRING_MAPPER);
        AI_AGENT_ANNOTATIONS.put(AI_TOOL_PROVIDER, STRING_MAPPER);
        AI_AGENT_ANNOTATIONS.put(AI_TOOLS, STRINGS_MAPPER);
        AI_AGENT_ANNOTATIONS.put(AI_MCP_CLIENTS, STRINGS_MAPPER);
    }

    private final InnerClass metadataRecord = InnerClass.builder()
            .classType(ElementKind.RECORD)
            .name("Metadata")
            .accessModifier(PRIVATE)
            .sortFields(false)
            .addField(b -> b.name("agentName").type(String.class))
            .addField(b -> b.name("chatModel").type(String.class))
            .addField(b -> b.name("chatMemory").type(String.class))
            .addField(b -> b.name("chatMemoryProvider").type(String.class))
            .addField(b -> b.name("contentRetriever").type(String.class))
            .addField(b -> b.name("retrievalAugmentor").type(String.class))
            .addField(b -> b.name("toolProvider").type(String.class))
            .addField(b -> b.name("tools")
                    .type(TypeName.builder().type(Set.class).addTypeArgument(STRING).build()))
            .addField(b -> b.name("mcpClients")
                    .type(TypeName.builder().type(Set.class).addTypeArgument(STRING).build()))
            .build();

    private final Field.Builder metadataField = Field.builder()
            .name("agentsMetadata")
            .isFinal(true)
            .type(TypeName.builder(MAP)
                          .addTypeArgument(TypeName.create(Class.class))
                          .addTypeArgument(TypeName.create(metadataRecord.name()))
                          .build())
            .addContent("new ")
            .addContent(HashMap.class)
            .addContentLine("<>()");

    private final Method.Builder initMetadataMethodBuilder = Method.builder()
            .accessModifier(PRIVATE)
            .name("initializeAgentsMetadata");

    private final Set<String> triggerPackages = new HashSet<>();

    void add(TypeInfo type) {
        triggerPackages.add(type.typeName().packageName());
        initMetadataMethodBuilder
                .addContent("agentsMetadata")
                .addContent(".put(")
                .addContent(type.typeName())
                .addContent(".class, new Metadata(")
                .increaseContentPadding()
                .increaseContentPadding()
                .increaseContentPadding();

        AtomicBoolean first = new AtomicBoolean(true);
        AI_AGENT_ANNOTATIONS.forEach((annotationType, mapper) -> {
            initMetadataMethodBuilder
                    .addContent(first.getAndSet(false) ? "" : ", ")
                    .addContentLine()
                    .addContent("/* " + annotationType.className().toLowerCase() + " */ ")
                    .update(builder -> mapper.accept(builder, type.findAnnotation(annotationType)));
        });

        initMetadataMethodBuilder
                .decreaseContentPadding()
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("));");
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
                .className("SubAgentConfigurator__AiMetadata")
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
        classModel.addMethod(this::addClassForNameMethod);
        classModel.addInnerClass(metadataRecord);
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
                .addContentLine("var metadata = agentsMetadata.get(cls);")

                .addContentLine("String agentName = metadata.agentName();")
                .addContent("var agentsConfigBuilder = ")
                .addContent(AGENTS_CONFIG)
                .addContentLine(".builder();")
                .addContentLine()

                .update(b -> {
                    List.of("chatModel",
                            "chatMemory",
                            "chatMemoryProvider",
                            "contentRetriever",
                            "retrievalAugmentor",
                            "toolProvider")
                            .forEach(propName -> {
                                mb.addContent(TypeNames.OPTIONAL)
                                        .addContent(".ofNullable(metadata.")
                                        .addContent(propName)
                                        .addContent("()).ifPresent(agentsConfigBuilder::")
                                        .addContent(propName)
                                        .addContentLine(");");
                            });
                    b.addContentLine();
                    b.addContentLine("if (!metadata.mcpClients().isEmpty()) {")
                            .increaseContentPadding()
                            .addContent("agentsConfigBuilder.mcpClients(metadata.mcpClients());")
                            .decreaseContentPadding()
                            .addContentLine("")
                            .addContentLine("}");
                    b.addContentLine("if (!metadata.tools().isEmpty()) {")
                            .increaseContentPadding()
                            .addContentLine("// Tools are special case, we need to postpone class resolution to runtime")
                            .addContent("agentsConfigBuilder.tools(metadata.tools().stream().map(this::classForName).collect(")
                            .addContent(Collectors.class)
                            .addContentLine(".toSet()));")
                            .decreaseContentPadding()
                            .addContentLine("}");
                })
                .addContentLine()
                .addContentLine("// Override annotation setup with config")
                .addContentLine("agentsConfigBuilder.config(agenticConfig.get(agentName));")
                .addContentLine("var agentsConfig = agentsConfigBuilder.build();")
                .addContentLine("agentsConfig.configure(ctx, registry);");
    }

    private void addClassForNameMethod(Method.Builder mb) {
        mb.accessModifier(PRIVATE)
                .name("classForName")
                .addParameter(STRING, "className")
                .returnType(TypeName.builder()
                                    .type(Class.class)
                                    .addTypeArgument(TypeNames.WILDCARD)
                                    .build())
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("return Class.forName(className);")
                .decreaseContentPadding()
                .addContentLine("} catch (ClassNotFoundException e) {")
                .increaseContentPadding()
                .addContent("throw new IllegalArgumentException(")
                .addContentLiteral("Can't resolve configured class ")
                .addContentLine(" + className, e);")
                .decreaseContentPadding()
                .addContentLine("}");
    }
}
