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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.common.types.AccessModifier.PRIVATE;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_AGENT;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MEMORY;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MEMORY_PROVIDER;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CONTENT_RETRIEVER;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_RETRIEVER_AUGMENTOR;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_AGENTIC_SERVICES;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_CHAT_MODEL;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON;

class AgentCodegen implements CodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(AgentCodegen.class);
    private static final TypeName SERVICE_REGISTRY = TypeName.create("io.helidon.service.registry.ServiceRegistry");
    private static final TypeName CONFIG = TypeName.create("io.helidon.config.Config");
    private static final TypeName AGENTS_CONFIG = TypeName.create("io.helidon.integrations.langchain4j.AgentsConfig");
    private static final TypeName DECLARATIVE_AGENT_CREATION_CONTEXT = TypeName.create(
            "dev.langchain4j.agentic.AgenticServices.DeclarativeAgentCreationContext");
    private static final TypeName JLA_ANNOTATION = TypeName.create(java.lang.annotation.Annotation.class);
    private static final Set<TypeName> AI_AGENT_ANNOTATIONS = Set.of(
            AI_AGENT,
            AI_CHAT_MODEL,
            AI_CHAT_MEMORY,
            AI_CHAT_MEMORY_PROVIDER,
            AI_CONTENT_RETRIEVER,
            AI_RETRIEVER_AUGMENTOR
    );
    private static final Map<TypeName, String> ANNOTATION_CONFIG_MAPPING = Map.of(
            AI_CHAT_MODEL, "chatModel",
            AI_CHAT_MEMORY, "chatMemory",
            AI_CHAT_MEMORY_PROVIDER, "chatMemoryProvider",
            AI_CONTENT_RETRIEVER, "contentRetriever",
            AI_RETRIEVER_AUGMENTOR, "retrievalAugmentor"
    );

    private final Field.Builder metadataField = Field.builder()
            .name("agentsMetadata")
            .isFinal(true)
            .type(TypeName.builder(TypeNames.MAP)
                          .addTypeArgument(TypeName.create(Class.class))
                          .addTypeArgument(TypeName.builder(TypeNames.MAP)
                                                   .addTypeArgument(TypeName.create(Class.class))
                                                   .addTypeArgument(TypeNames.STRING)
                                                   .build())
                          .build())
            .addContent("new ")
            .addContent(HashMap.class)
            .addContentLine("<>()");

    private final Method.Builder initMetadataMethodBuilder = Method.builder()
            .name("initializeAgentsMetadata");

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
        }

        // Second round to generate agent producers
        for (TypeInfo type : types) {
            process(roundCtx, type);
        }
    }

    private void process(RoundContext roundCtx, TypeInfo agentInterface) {
        TypeName agentInterfaceType = agentInterface.typeName();
        TypeName generatedType = generatedTypeName(agentInterfaceType, "AiAgent");

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

        classModel.addField(metadataField);
        classModel.addMethod(initMetadataMethodBuilder);

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
                .addContentLine("configuredModel, this::configureSubAgents);")
                .addContentLine("")
        );

        classModel.addMethod(this::addgetAnnotationValueMethod);

        classModel.addMethod(get -> get
                .accessModifier(PRIVATE)
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

                .update(this::addMapping)
                .addContentLine("agentsConfigBuilder.config(agenticConfig.get(agentName));")
                .addContentLine("var agentsConfig = agentsConfigBuilder.build();")
                .addContentLine("var subAgentBuilder = ctx.agentBuilder();")
                .addContentLine("agentsConfig.configure(subAgentBuilder, registry);")
        );

        roundCtx.addGeneratedType(generatedType, classModel, agentInterfaceType, agentInterface.originatingElementValue());
    }

    private void addgetAnnotationValueMethod(Method.Builder mb) {
        mb.addGenericArgument(TypeArgument.builder()
                                      .token("A")
                                      .bound(JLA_ANNOTATION)
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
                                                    .addTypeArgument(TypeNames.STRING)
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
                        .addContentLine("// or use reflection")
                        .addContent(".or(() -> ")
                        .addContent(TypeNames.OPTIONAL)
                        .addContentLine(".ofNullable(clazz.getDeclaredAnnotation(annotationType)).map(valueMapper));")
                        .decreaseContentPadding()
                )
                .returnType(TypeName.builder(TypeNames.OPTIONAL)
                                    .addTypeArgument(TypeNames.STRING)
                                    .build());
    }

    /**
     * Generates code that maps configuration annotations to corresponding builder methods.
     *
     * <p>This method iterates over {@code ANNOTATION_CONFIG_MAPPING} and, for each entry,
     * adds retrieve logic to get the value of the annotation from a target class and,
     * if present, invoke the appropriate setter on {@code agentsConfigBuilder}.</p>
     *
     * @param mb the method builder
     */
    private void addMapping(Method.Builder mb) {
        mb.addContentLine();
        for (var mapping : ANNOTATION_CONFIG_MAPPING.entrySet()) {
            mb.addContent("getAnnotationValue(cls, ")
                    .increaseContentPadding()
                    .addContent(mapping.getKey())
                    .addContent(".class, ")
                    .addContent(mapping.getKey())
                    .addContentLine("::value)")
                    .addContent(".ifPresent(agentsConfigBuilder::")
                    .addContent(mapping.getValue())
                    .addContentLine(");")
                    .decreaseContentPadding()
                    .addContentLine();
        }
        mb.addContentLine();
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

        ctr.addContentLine("this.agenticConfig = config.get(AgentsConfig.CONFIG_ROOT);");
        ctr.addContentLine("this.registry = registry;");
        ctr.addContent(initMetadataMethodBuilder.build().name()).addContentLine("();");

        // if annotated, we have a named value (and that is mandatory)
        String modelName = aiInterface.findAnnotation(aiModelAnnotation)
                .flatMap(Annotation::stringValue)
                .orElse(null);

        if (modelName == null) {
            // there is no annotation, use only autodiscovered model (if present)
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
            // there is no annotation, use only autodiscovered model (if present)
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
