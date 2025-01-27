/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MEMORY;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MEMORY_PROVIDER;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CHAT_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_CONTENT_RETRIEVER;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_MODERATION_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_RETRIEVER_AUGMENTOR;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_SERVICE;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_STREAMING_CHAT_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_TOOL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_TOOLS;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_AI_SERVICES;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_CHAT_MEMORY;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_CHAT_MEMORY_PROVIDER;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_CHAT_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_CONTENT_RETRIEVER;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_MODERATION_MODEL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_RETRIEVAL_AUGMENTOR;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_STREAMING_CHAT_MODEL;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON;

class AiServiceCodegen implements CodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(AiServiceCodegen.class);

    private final CodegenContext ctx;

    AiServiceCodegen(CodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RoundContext roundCtx) {
        Collection<TypeInfo> types = roundCtx.annotatedTypes(AI_SERVICE);
        for (TypeInfo type : types) {
            process(roundCtx, type);
        }
    }

    private void process(RoundContext roundCtx, TypeInfo type) {
        // for each annotated interface, generate Iface__AiServices and Iface__Service
        // the type MUST be an interface
        if (type.kind() != ElementKind.INTERFACE) {
            throw new CodegenException("Type annotated with " + AI_SERVICE.fqName() + " must be an interface.",
                                       type.originatingElementValue());
        }

        generateInterfaceSupplier(roundCtx, type);
    }

    private void aiServicesToolParameters(Constructor.Builder ctr, TypeInfo aiInterface) {

        if (aiInterface.hasAnnotation(AI_TOOLS)) {
            // if annotated with tools, use the tool classes specified
            Annotation tools = aiInterface.annotation(AI_TOOLS);
            List<TypeName> toolTypes = tools.typeValues()
                    .orElseGet(List::of);

            Annotation.create(AI_TOOL);
            List<String> toolParameters = new ArrayList<>();
            int index = 1;
            for (TypeName toolType : toolTypes) {
                String toolParameter = "tool_" + index;
                index++;

                ctr.addParameter(tool -> tool
                        .name(toolParameter)
                        .type(toolType)
                        .addAnnotation(LangchainTypes.TOOL_QUALIFIER_ANNOTATION)
                );
                toolParameters.add(toolParameter);
            }
            ctr.addContent("builder.tools(");
            ctr.addContent(String.join(", ", toolParameters));
            ctr.addContentLine(");");
        } else {
            // inject all tools and use them
            ctr.addParameter(tools -> tools
                    .name("tools")
                    .type(listType(TypeNames.OBJECT))
                    .addAnnotation(LangchainTypes.TOOL_QUALIFIER_ANNOTATION)
            );
            ctr.addContentLine("builder.tools(tools);");
        }
    }

    private void aiServicesParameter(Constructor.Builder ctr,
                                     boolean autoDiscovery,
                                     TypeInfo aiInterface,
                                     TypeName aiModelAnnotation,
                                     TypeName lcModelType,
                                     String aiServicesMethodName) {
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
            ctr.addContent(aiServicesMethodName)
                    .addContent(".ifPresent(builder::")
                    .addContent(aiServicesMethodName)
                    .addContentLine(");");
            return;
        }

        // model name is specified, expect injection point
        ctr.addParameter(parameter -> parameter
                .name(aiServicesMethodName)
                .type(lcModelType)
                .addAnnotation(namedAnnotation(modelName)));
        ctr.addContent("builder.")
                .addContent(aiServicesMethodName)
                .addContent("(")
                .addContent(aiServicesMethodName)
                .addContentLine(");");
    }

    private Annotation namedAnnotation(String modelName) {
        return Annotation.create(SERVICE_ANNOTATION_NAMED, modelName);
    }

    private void generateInterfaceSupplier(RoundContext roundCtx, TypeInfo aiInterface) {
        TypeName aiInterfaceType = aiInterface.typeName();
        TypeName generatedType = generatedTypeName(aiInterfaceType, "AiService");

        var classModel = ClassModel.builder()
                .type(generatedType)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 aiInterfaceType,
                                                 generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               aiInterfaceType,
                                                               generatedType,
                                                               "1",
                                                               ""))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addInterface(supplierType(aiInterfaceType))
                .addAnnotation(Annotation.create(SERVICE_ANNOTATION_SINGLETON));

        // field - AiServices (created in constructor)
        TypeName aiServicesType = aiServicesType(aiInterfaceType);
        classModel.addField(aiServices -> aiServices
                .name("aiServices")
                .type(aiServicesType)
                .isFinal(true)
                .accessModifier(AccessModifier.PRIVATE)
        );

        boolean autoDiscovery = aiInterface.annotation(AI_SERVICE)
                .booleanValue("autoDiscovery")
                .orElse(true);

        // constructor (parameters depend on annotations on interface)
        classModel.addConstructor(ctr -> ctr
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .addContent(aiServicesType)
                .addContent(" builder = ")
                .addContent(LC_AI_SERVICES)
                .addContent(".builder(")
                .addContent(aiInterfaceType)
                .addContentLine(".class);")
                .addContentLine("")
                .update(it -> {
                    // all parameters and assignments to builder
                    aiServicesParameter(it,
                                        autoDiscovery,
                                        aiInterface,
                                        AI_CHAT_MODEL,
                                        LC_CHAT_MODEL,
                                        "chatLanguageModel");
                    aiServicesParameter(it,
                                        autoDiscovery,
                                        aiInterface,
                                        AI_STREAMING_CHAT_MODEL,
                                        LC_STREAMING_CHAT_MODEL,
                                        "streamingChatLanguageModel");
                    aiServicesParameter(it,
                                        autoDiscovery,
                                        aiInterface,
                                        AI_CHAT_MEMORY,
                                        LC_CHAT_MEMORY,
                                        "chatMemory");
                    aiServicesParameter(it,
                                        autoDiscovery,
                                        aiInterface,
                                        AI_CHAT_MEMORY_PROVIDER,
                                        LC_CHAT_MEMORY_PROVIDER,
                                        "chatMemoryProvider");
                    aiServicesParameter(it,
                                        autoDiscovery,
                                        aiInterface,
                                        AI_MODERATION_MODEL,
                                        LC_MODERATION_MODEL,
                                        "moderationModel");
                    aiServicesParameter(it,
                                        autoDiscovery,
                                        aiInterface,
                                        AI_RETRIEVER_AUGMENTOR,
                                        LC_RETRIEVAL_AUGMENTOR,
                                        "retrievalAugmentor");
                    aiServicesParameter(it,
                                        autoDiscovery,
                                        aiInterface,
                                        AI_CONTENT_RETRIEVER,
                                        LC_CONTENT_RETRIEVER,
                                        "contentRetriever");
                    aiServicesToolParameters(it,
                                             aiInterface);
                })
                .addContentLine("")
                .addContentLine("this.aiServices = builder;")
        );

        // and the get method (implementation of supplier)
        classModel.addMethod(get -> get
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(aiInterfaceType)
                .name("get")
                .addContentLine("return aiServices.build();")
        );

        roundCtx.addGeneratedType(generatedType, classModel, aiInterfaceType, aiInterface.originatingElementValue());
    }

    private TypeName aiServicesType(TypeName interfaceType) {
        return TypeName.builder(LC_AI_SERVICES)
                .addTypeArgument(interfaceType)
                .build();
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

    private TypeName listType(TypeName listType) {
        return TypeName.builder(TypeNames.LIST)
                .addTypeArgument(listType)
                .build();
    }
}
